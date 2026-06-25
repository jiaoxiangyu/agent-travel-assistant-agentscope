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
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 旅行助手核心编排服务，负责连接业务会话、AgentScope、模型和产物持久化。 */
@Service
public class TravelAgentServiceImpl implements TravelAgentService {

    /** 未显式传入用户 ID 时使用的默认隔离维度。 */
    private static final String DEFAULT_USER_ID = "default-user";

    /** Harness 的最小启动提示；详细人格、规则和知识由 Workspace 文件每轮注入。 */
    private static final String BOOTSTRAP_PROMPT = "你是旅行助手。请严格遵循 Workspace 中的 AGENTS.md 和知识库。";

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
            String answer;
            if (response == null || !StringUtils.hasText(response.getTextContent())) {
                answer = "抱歉，我暂时没有生成有效的旅行方案，请补充目的地、天数、预算和同行人后再试。";
            } else {
                answer = response.getTextContent();
            }
            // 每次成功回答都保存为 Markdown，方便用户或后续系统引用完整旅行策略。
            String artifactPath =
                    artifactService.writeMarkdown(
                            conversationId, normalizedUserId, message, answer);
            conversationPersistenceService.saveAssistantMessage(conversationId, answer, artifactPath);
            return new TravelAgentChatResult(answer, artifactPath);
        } catch (Exception e) {
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
                .disableShellTool()
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
