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
            "你是旅行助手。请严格遵循 Workspace 中的 AGENTS.md、知识库和 skills。"
                    + "涉及实时、外部或会变化的信息时，必须优先使用匹配的 skill 或工具查询；没有查询结果时不要编造具体数值。";

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
        String normalizedUserId = StringUtils.hasText(userId) ? userId : DEFAULT_USER_ID;
        // Redis 中已有 AgentScope 状态时，只需输入本轮消息；否则从 MySQL 恢复最近对话。
        boolean hasAgentState = stateStore.exists(normalizedUserId, conversationId);
        List<Msg> inputMessages =
                hasAgentState
                        ? List.of(new UserMessage(message))
                        : buildRecoveredInputMessages(conversationId, message);

        // 先落业务消息，AgentScope middleware 会记录本次 Agent 运行状态。
        conversationPersistenceService.saveUserMessage(conversationId, normalizedUserId, message);

        // RuntimeContext 中的 sessionId/userId 用来隔离 AgentScope 的 Redis 状态。
        RuntimeContext context =
                RuntimeContext.builder()
                        .sessionId(conversationId)
                        .userId(normalizedUserId)
                        .build();

        try (HarnessAgent agent = buildAgent(conversationId)) {
            Msg response =
                    agent.call(inputMessages, context)
                            .block(properties.getTimeout());
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
        String normalizedUserId = StringUtils.hasText(userId) ? userId : DEFAULT_USER_ID;
        // Redis 中已有 AgentScope 状态时，只需输入本轮消息；否则从 MySQL 恢复最近对话。
        boolean hasAgentState = stateStore.exists(normalizedUserId, conversationId);
        List<Msg> inputMessages =
                hasAgentState
                        ? List.of(new UserMessage(message))
                        : buildRecoveredInputMessages(conversationId, message);

        // 先落业务消息，AgentScope middleware 会记录本次 Agent 运行状态。
        conversationPersistenceService.saveUserMessage(conversationId, normalizedUserId, message);

        // RuntimeContext 中的 sessionId/userId 用来隔离 AgentScope 的 Redis 状态。
        RuntimeContext context =
                RuntimeContext.builder()
                        .sessionId(conversationId)
                        .userId(normalizedUserId)
                        .build();

        HarnessAgent agent = buildAgent(conversationId);
        try {
            return agent.call(inputMessages, context)
                    .timeout(properties.getTimeout())
                    .map(this::resolveAnswer)
                    .map(answer -> saveAssistantResult(conversationId, normalizedUserId, message, answer))
                    .doFinally(signalType -> closeAgent(agent))
                    .onErrorResume(
                            e -> {
                                if (isRateLimitException(e)) {
                                    return Mono.just(
                                            saveAssistantErrorResult(conversationId, RATE_LIMIT_ANSWER));
                                }
                                if (isTimeoutException(e)) {
                                    return Mono.just(
                                            saveAssistantErrorResult(conversationId, TIMEOUT_ANSWER));
                                }
                                return Mono.error(mapExecutionException(e));
                            })
                    .flux();
        } catch (Exception e) {
            closeAgent(agent);
            if (isRateLimitException(e)) {
                return Flux.just(saveAssistantErrorResult(conversationId, RATE_LIMIT_ANSWER));
            }
            if (isTimeoutException(e)) {
                return Flux.just(saveAssistantErrorResult(conversationId, TIMEOUT_ANSWER));
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Travel agent execution failed", e);
        }
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

    private RuntimeException mapExecutionException(Throwable e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Travel agent execution failed", e);
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

    private void closeAgent(HarnessAgent agent) {
        try {
            agent.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close travel agent", e);
        }
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
                .filesystem(
                        new LocalFilesystemSpec()
                                .project(Path.of("").toAbsolutePath())
                                .projectWritable(false))
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
