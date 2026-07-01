package com.example.travelassistant.service.impl;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.config.TravelAgentWorkspaceInitializer;
import com.example.travelassistant.domain.TravelAgentChatResult;
import com.example.travelassistant.middleware.AgentRunLogMiddleware;
import com.example.travelassistant.persistence.enums.MessageRole;
import com.example.travelassistant.persistence.service.ConversationPersistenceService;
import com.example.travelassistant.service.TravelAgentService;
import com.example.travelassistant.persistence.service.TravelStrategyArtifactService;
import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.exception.RateLimitException;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.memory.MemoryFlushManager;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 旅行助手核心编排服务，负责连接业务会话、AgentScope、模型和产物持久化。 */
@Service
public class TravelAgentServiceImpl implements TravelAgentService {

    /** 未显式传入用户 ID 时使用的默认隔离维度。 */
    private static final String DEFAULT_USER_ID = "default-user";

    /** Harness 的最小启动提示；详细人格、规则和知识由 Workspace 文件每轮注入。 */
    private static final String BOOTSTRAP_PROMPT =
            """
            你是旅行助手主 Agent，负责理解用户需求、决定是否委派子 Agent，并输出最终旅行方案。
            请严格遵循 Workspace 中的 AGENTS.md、知识库和 skills。
            信息不足时先追问；只要用户至少提供目的地和旅行天数，就必须先用 agent_spawn 同步委派 researcher 收集资料摘要。
            生成最终回答前，必须先整理一份完整方案草稿，并把完整草稿正文、用户原始约束、预算边界和 researcher 摘要一起放进 agent_spawn 的 task 中，委派 reviewer 审阅；不能只传“审阅预算/节奏”这类摘要任务。
            reviewer 返回意见后，必须根据审稿意见修订，再输出最终回答。
            涉及实时、外部或会变化的信息时，必须优先使用匹配的 skill、工具或 researcher 子 Agent 查询；没有查询结果时不要编造具体数值。
            最终回答只面向用户，不要暴露内部 agent_spawn、子 Agent 结果或审稿过程。
            """;

    /** 约束 Memory 抽取与合并过程，避免默认英文 prompt 产出英文长期记忆。 */
    private static final String CHINESE_MEMORY_PROMPT_RULES =
            """

            Additional project rules:
            - All extracted and consolidated long-term memories MUST be written in Simplified Chinese.
            - Keep Markdown headings and bullet points concise, but translate field names and explanations into Chinese.
            - Preserve useful travel facts such as destination, days, budget, companions, preferences, weather constraints, reservations and follow-up tasks.
            - Do not output English section titles unless they are proper nouns, product names, tool names or original user text that should be preserved.
            """;

    /** 模型服务返回 429 限流时展示给用户的友好提示。 */
    private static final String RATE_LIMIT_ANSWER =
            "当前模型服务触发限流（429），暂时无法生成旅行方案。这不是你的输入问题，请稍后重试；如果持续出现，请切换到额度更高或更稳定的模型服务。";

    /** 模型或工具调用超过配置时间时展示给用户的友好提示。 */
    private static final String TIMEOUT_ANSWER =
            "本次旅行方案生成时间过长，已超过服务端等待上限。请稍后重试，或先缩小需求范围（例如减少天数、景点数量或预算约束）后再生成。";

    @Resource
    private TravelAgentProperties properties;

    @Resource(name = "travelToolkit")
    private Toolkit toolkit;

    @Resource(name = "travelAgentStateStore")
    private AgentStateStore stateStore;

    @Resource
    private ConversationPersistenceService conversationPersistenceService;

    @Resource
    private AgentRunLogMiddleware agentRunLogMiddleware;

    @Resource
    private TravelStrategyArtifactService artifactService;

    @Resource
    private TravelAgentWorkspaceInitializer workspaceInitializer;

    @Override
    public TravelAgentChatResult chat(String conversationId, String userId, String message) {
        return executeChat(conversationId, userId, message);
    }

    /**
     * 执行一次对用户可见的聊天请求。
     *
     * <p>外部只调用一个 HarnessAgent。多 Agent 协作交给 Harness subagent 机制：
     * 主 Agent 在推理时可通过 agent_spawn 委派 Workspace 中声明的 researcher / reviewer。
     */
    private TravelAgentChatResult executeChat(String conversationId, String userId, String message) {
        String normalizedUserId = StringUtils.hasText(userId) ? userId : DEFAULT_USER_ID;
        // 主 Agent 使用业务 conversationId 保存 AgentScope 状态；缺失时从 MySQL 恢复最近业务消息。
        boolean hasAgentState = stateStore.exists(normalizedUserId, conversationId);
        List<Msg> inputMessages =
                hasAgentState
                        ? List.of(new UserMessage(message))
                        : buildRecoveredInputMessages(conversationId, message);

        // 先落业务消息，AgentScope middleware 会记录本次 Agent 运行状态。
        conversationPersistenceService.saveUserMessage(conversationId, normalizedUserId, message);

        // RuntimeContext 中的 sessionId/userId 会透传给子 Agent，保持多租户与会话隔离链路一致。
        RuntimeContext context =
                RuntimeContext.builder()
                        .sessionId(conversationId)
                        .userId(normalizedUserId)
                        .build();

        try (HarnessAgent agent = buildAgent(conversationId)) {
            List<AgentEvent> events =
                    agent.streamEvents(inputMessages, context)
                            .timeout(properties.getTimeout())
                            .collectList()
                            .block(properties.getTimeout());
            Msg response = extractFinalResponse(events);
            return saveAssistantResult(conversationId, normalizedUserId, message, resolveAnswer(response));
        } catch (Exception e) {
            if (isRateLimitException(e)) {
                return saveAssistantErrorResult(conversationId, RATE_LIMIT_ANSWER);
            }
            if (isTimeoutException(e)) {
                return saveAssistantErrorResult(conversationId, TIMEOUT_ANSWER);
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Travel agent execution failed", e);
        }
    }

    @Override
    public Flux<TravelAgentChatResult> chatStream(String conversationId, String userId, String message) {
        // AgentScope Java 当前返回完整消息；SSE 层仍复用同步编排结果再由 controller 切 delta。
        return Mono.fromCallable(() -> executeChat(conversationId, userId, message)).flux();
    }

    /** Redis 状态过期或缺失时，将 MySQL 中的最近消息重放为 Agent 输入上下文。 */
    private List<Msg> buildRecoveredInputMessages(String conversationId, String message) {
        List<ConversationMessageEntity> history =
                conversationPersistenceService.findRecentMessages(
                        conversationId, properties.getHistoryLimit());
        List<Msg> messages = new ArrayList<>(history.size() + 1);
        for (ConversationMessageEntity historyMessage : history) {
            if (!StringUtils.hasText(historyMessage.getContent())) {
                continue;
            }
            if (historyMessage.getRole() == MessageRole.USER) {
                messages.add(new UserMessage(historyMessage.getContent()));
            } else if (historyMessage.getRole() == MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(historyMessage.getContent()));
            }
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    private String resolveAnswer(Msg response) {
        if (response == null || !StringUtils.hasText(response.getTextContent())) {
            return "抱歉，我暂时没有生成有效的旅行方案，请补充目的地、天数、预算和同行人后再试。";
        }
        return response.getTextContent();
    }

    private Msg extractFinalResponse(List<AgentEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        Msg fallback = null;
        Msg parentResult = null;
        for (AgentEvent event : events) {
            if (event instanceof AgentResultEvent resultEvent) {
                Msg result = resultEvent.getResult();
                if (event.getSource() == null) {
                    parentResult = result;
                } else {
                    fallback = result;
                }
            }
        }
        return parentResult == null ? fallback : parentResult;
    }

    private TravelAgentChatResult saveAssistantResult(
            String conversationId, String normalizedUserId, String message, String answer) {
        // 每次成功回答都保存为 Markdown，方便用户或后续系统引用完整旅行策略。
        String artifactPath =
                artifactService.writeMarkdown(
                        conversationId, normalizedUserId, message, answer);
        conversationPersistenceService.saveAssistantMessage(conversationId, answer, artifactPath);
        return new TravelAgentChatResult(answer, artifactPath);
    }

    private TravelAgentChatResult saveAssistantErrorResult(String conversationId, String answer) {
        conversationPersistenceService.saveAssistantMessage(conversationId, answer, null);
        return new TravelAgentChatResult(answer, null);
    }

    private boolean isRateLimitException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof RateLimitException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.hasText(message)
                    && (message.contains("status 429")
                            || message.toLowerCase().contains("rate limit"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 按当前配置构造一次性 HarnessAgent，调用结束后由 try-with-resources 关闭。 */
    private HarnessAgent buildAgent(String conversationId) {
        workspaceInitializer.initialize();
        return HarnessAgent.builder()
                .name(properties.getName())
                .agentId(properties.getAgentId())
                .sysPrompt(BOOTSTRAP_PROMPT)
                .model(buildModel())
                .toolkit(toolkit)
                .stateStore(stateStore)
                .middleware(agentRunLogMiddleware)
                .defaultSessionId(conversationId)
                .workspace(properties.getWorkspaceDir())
                .memory(
                        MemoryConfig.builder()
                                .flushPrompt(
                                        MemoryFlushManager.DEFAULT_FLUSH_PROMPT
                                                + CHINESE_MEMORY_PROMPT_RULES)
                                .consolidationPrompt(
                                        MemoryConsolidator.DEFAULT_CONSOLIDATION_PROMPT
                                                + CHINESE_MEMORY_PROMPT_RULES)
                                .build())
                .filesystem(
                        new LocalFilesystemSpec()
                                .project(Path.of("").toAbsolutePath())
                                .projectWritable(false))
                .compaction(
                        CompactionConfig.builder()
                                .triggerMessages(properties.getCompactionTriggerMessages())
                                .triggerTokens(properties.getCompactionTriggerTokens())
                                .keepMessages(properties.getCompactionKeepMessages())
                                .keepTokens(properties.getCompactionKeepTokens())
                                .build())
                .maxContextTokens(properties.getMaxContextTokens())
                .maxIters(properties.getMaxIters())
                .build();
    }

    /** 构造 OpenAI 兼容模型客户端，并从环境变量读取访问令牌。 */
    private Model buildModel() {
        String apiKey = System.getenv(properties.getApiKeyEnv());
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing environment variable: " + properties.getApiKeyEnv());
        }

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getModel())
                .baseUrl(properties.getBaseUrl())
                .httpTransport(OkHttpTransport.builder().build())
                .build();
    }
}
