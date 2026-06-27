package com.example.travelassistant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.domain.TravelAgentChatResult;
import com.example.travelassistant.persistence.entity.ConversationEntity;
import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import com.example.travelassistant.persistence.enums.MessageRole;
import com.example.travelassistant.persistence.mapper.ConversationMapper;
import com.example.travelassistant.persistence.mapper.ConversationMessageMapper;
import com.example.travelassistant.service.TravelAgentService;
import io.agentscope.core.state.AgentStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 真实 service 集成测试。
 *
 * <p>该测试会连接配置中的 MySQL、Redis 和 OpenAI 兼容模型服务，因此默认不在普通单元测试中执行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_TRAVEL_AGENT_INTEGRATION_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "MODELSCOPE_API_KEY", matches = ".+")
@TestPropertySource(
        properties = {
            "travel.agent.artifact-dir=target/it-artifacts/travel-strategies",
            "travel.agent.run-log-dir=target/it-logs/agent-runs",
            "travel.agent.compaction-trigger-messages=0",
            "travel.agent.compaction-trigger-tokens=0",
            "travel.agent.timeout=120s"
        })
class TravelAgentServiceIntegrationTest {

    private static final String USER_ID = "service-it-user-001";

    @Autowired
    private TravelAgentService travelAgentService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper messageMapper;

    @Autowired
    private AgentStateStore stateStore;

    @Autowired
    private TravelAgentProperties properties;

    private String conversationId;

    @BeforeEach
    void setUp() {
        conversationId = "service-it-" + UUID.randomUUID();
    }

//    @AfterEach
//    void tearDown() {
//        if (conversationId != null) {
//            stateStore.delete(USER_ID, conversationId);
//        }
//    }

    @Test
    void chatPersistsMessagesWritesArtifactAndRunLog() throws Exception {
        String firstMessage = "帮我规划北京3天2晚情侣游";
        String secondMessage = "预算3000元，不含住宿";
        String thirdMessage = "偏人文和美食";

        travelAgentService.chat(conversationId, USER_ID, firstMessage);
        travelAgentService.chat(conversationId, USER_ID, secondMessage);
        TravelAgentChatResult result = travelAgentService.chat(conversationId, USER_ID, thirdMessage);

        assertThat(result.getAnswer()).isNotBlank();
        assertThat(result.getArtifactPath()).isNotBlank();

        Path artifactPath = Path.of(result.getArtifactPath());
        assertThat(artifactPath).exists().isRegularFile();
        String artifact = Files.readString(artifactPath);
        assertThat(artifact)
                .contains("# 旅行策略")
                .contains("- 会话 ID：" + conversationId)
                .contains("- 用户 ID：" + USER_ID)
                .contains(thirdMessage)
                .contains(result.getAnswer());

        ConversationEntity conversation = conversationMapper.findById(conversationId);
        assertThat(conversation).isNotNull();
        assertThat(conversation.getUserId()).isEqualTo(USER_ID);

        List<ConversationMessageEntity> messages =
                messageMapper.findByConversationIdOrderByCreatedAtAsc(conversationId);
        assertThat(messages).hasSizeGreaterThanOrEqualTo(6);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo(firstMessage);
        assertThat(messages.get(2).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(2).getContent()).isEqualTo(secondMessage);
        assertThat(messages.get(4).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(4).getContent()).isEqualTo(thirdMessage);

        ConversationMessageEntity assistantMessage = messages.get(messages.size() - 1);
        assertThat(assistantMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistantMessage.getContent()).isEqualTo(result.getAnswer());
        assertThat(assistantMessage.getArtifactPath()).isEqualTo(result.getArtifactPath());

        Path runLogPath =
                properties.getRunLogDir().resolve(USER_ID).resolve(conversationId + ".log");
        assertThat(runLogPath).exists().isRegularFile();
        assertThat(Files.readString(runLogPath))
                .contains("RUNNING")
                .contains("COMPLETED")
                .contains("conversationId=" + conversationId)
                .contains("userId=" + USER_ID);
    }
}
