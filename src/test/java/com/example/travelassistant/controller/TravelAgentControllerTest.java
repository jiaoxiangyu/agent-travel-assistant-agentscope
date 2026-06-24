package com.example.travelassistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.travelassistant.domain.TravelAgentChatResult;
import com.example.travelassistant.domain.TravelAgentRequest;
import com.example.travelassistant.domain.TravelAgentResponse;
import com.example.travelassistant.service.TravelAgentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TravelAgentControllerTest {

    @Mock
    private TravelAgentService travelAgentService;

    @InjectMocks
    private TravelAgentController controller;

    // 验证三轮对话逐步补齐上下文后，controller 返回最终行程结果。
    @Test
    void chatReturnsItineraryAfterThreeTurnContextCompletion() {
        // 第一轮只给出城市、天数和出游人群，服务层需要继续追问关键信息。
        TravelAgentRequest firstRequest = new TravelAgentRequest();
        firstRequest.setConversationId("conversation-1");
        firstRequest.setUserId("alice");
        firstRequest.setMessage("帮我规划北京3天2晚情侣游");

        // 第二轮沿用同一会话补充预算，但仍缺少旅行偏好。
        TravelAgentRequest secondRequest = new TravelAgentRequest();
        secondRequest.setConversationId("conversation-1");
        secondRequest.setUserId("alice");
        secondRequest.setMessage("预算3000元，不含住宿");

        // 第三轮补齐偏好，参考 agent_state 中最终成行的上下文。
        TravelAgentRequest thirdRequest = new TravelAgentRequest();
        thirdRequest.setConversationId("conversation-1");
        thirdRequest.setUserId("alice");
        thirdRequest.setMessage("  偏人文和美食  ");

        when(travelAgentService.chat("conversation-1", "alice", "帮我规划北京3天2晚情侣游"))
                .thenReturn(new TravelAgentChatResult("请补充预算", null));
        when(travelAgentService.chat("conversation-1", "alice", "预算3000元，不含住宿"))
                .thenReturn(new TravelAgentChatResult("请补充旅行偏好", null));
        when(travelAgentService.chat("conversation-1", "alice", "偏人文和美食"))
                .thenReturn(
                        new TravelAgentChatResult(
                                "北京3天2晚情侣人文美食游行程", "artifacts/beijing-couple.md"));

        TravelAgentResponse firstResponse = controller.chat(firstRequest);
        TravelAgentResponse secondResponse = controller.chat(secondRequest);
        TravelAgentResponse thirdResponse = controller.chat(thirdRequest);

        assertThat(firstResponse.getConversationId()).isEqualTo("conversation-1");
        assertThat(firstResponse.getAnswer()).isEqualTo("请补充预算");
        assertThat(firstResponse.getArtifactPath()).isNull();
        assertThat(secondResponse.getConversationId()).isEqualTo("conversation-1");
        assertThat(secondResponse.getAnswer()).isEqualTo("请补充旅行偏好");
        assertThat(secondResponse.getArtifactPath()).isNull();
        assertThat(thirdResponse.getConversationId()).isEqualTo("conversation-1");
        assertThat(thirdResponse.getAnswer()).isEqualTo("北京3天2晚情侣人文美食游行程");
        assertThat(thirdResponse.getArtifactPath()).isEqualTo("artifacts/beijing-couple.md");

        InOrder inOrder = inOrder(travelAgentService);
        inOrder.verify(travelAgentService).chat("conversation-1", "alice", "帮我规划北京3天2晚情侣游");
        inOrder.verify(travelAgentService).chat("conversation-1", "alice", "预算3000元，不含住宿");
        inOrder.verify(travelAgentService).chat("conversation-1", "alice", "偏人文和美食");
    }

    // 验证请求未传会话 ID 时，controller 会生成新的会话 ID 并透传给服务层。
    @Test
    void chatGeneratesConversationIdWhenMissing() {
        // 准备未传入会话 ID 的请求。
        TravelAgentRequest request = new TravelAgentRequest();
        request.setUserId("alice");
        request.setMessage("北京两日游");

        // 模拟服务层接受任意新会话 ID 并返回北京行程方案。
        when(travelAgentService.chat(anyString(), eq("alice"), eq("北京两日游")))
                .thenReturn(new TravelAgentChatResult("北京方案", "artifacts/beijing.md"));

        // 调用 controller，由 controller 负责生成会话 ID。
        TravelAgentResponse response = controller.chat(request);

        // 捕获生成的会话 ID，校验响应与服务层调用保持一致。
        ArgumentCaptor<String> conversationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(travelAgentService).chat(conversationIdCaptor.capture(), eq("alice"), eq("北京两日游"));
        assertThat(response.getConversationId()).isEqualTo(conversationIdCaptor.getValue());
        assertThat(UUID.fromString(response.getConversationId())).isNotNull();
    }

    // 验证空白消息会被 controller 拒绝，且不会触发服务层调用。
    @Test
    void chatRejectsBlankMessage() {
        // 准备空白消息请求。
        TravelAgentRequest request = new TravelAgentRequest();
        request.setMessage("   ");

        // 校验 controller 拒绝空白消息，并且不会调用服务层。
        assertThatThrownBy(() -> controller.chat(request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(travelAgentService);
    }
}
