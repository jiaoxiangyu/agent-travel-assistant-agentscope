package com.example.travelassistant.controller;

import com.example.travelassistant.domain.TravelAgentChatResult;
import com.example.travelassistant.domain.TravelAgentRequest;
import com.example.travelassistant.domain.TravelAgentResponse;
import com.example.travelassistant.domain.TravelAgentStreamResponse;
import com.example.travelassistant.service.TravelAgentService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/** 旅行助手 HTTP API，负责入参校验和会话 ID 分配。 */
@RestController
@RequestMapping("/api/travel-agent")
public class TravelAgentController {

    /** 当前 Agent 暂不支持 token 流，因此先将最终回答切成较小的 SSE delta。 */
    private static final int STREAM_DELTA_CODE_POINTS = 120;

    @Resource
    private TravelAgentService travelAgentService;

    /**
     * 处理单轮或多轮聊天请求。
     *
     * <p>客户端未传 {@code conversationId} 时会创建新会话；继续对话时复用旧会话 ID。
     */
    @PostMapping("/chat")
    public TravelAgentResponse chat(@RequestBody TravelAgentRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }

        // conversationId 是业务会话和 AgentScope session 的共同标识。
        String conversationId =
                StringUtils.hasText(request.getConversationId())
                        ? request.getConversationId()
                        : UUID.randomUUID().toString();

        TravelAgentChatResult result =
                travelAgentService.chat(
                        conversationId, request.getUserId(), request.getMessage().trim());
        return new TravelAgentResponse(conversationId, result.getAnswer(), result.getArtifactPath());
    }

    /**
     * 处理聊天请求，并通过 SSE 输出响应式结果。
     *
     * <p>当前 Agent 调用返回完整消息，因此该接口会将最终回答拆成多个 delta 事件。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TravelAgentStreamResponse> chatStream(@RequestBody TravelAgentRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }

        // conversationId 是业务会话和 AgentScope session 的共同标识。
        String conversationId =
                StringUtils.hasText(request.getConversationId())
                        ? request.getConversationId()
                        : UUID.randomUUID().toString();

        return travelAgentService
                .chatStream(conversationId, request.getUserId(), request.getMessage().trim())
                .flatMapIterable(
                        result ->
                                buildStreamResponses(
                                        conversationId, result.getAnswer(), result.getArtifactPath()));
    }

    private List<TravelAgentStreamResponse> buildStreamResponses(
            String conversationId, String answer, String artifactPath) {
        List<TravelAgentStreamResponse> responses = new ArrayList<>();
        if (StringUtils.hasText(answer)) {
            int offset = 0;
            while (offset < answer.length()) {
                int end =
                        answer.offsetByCodePoints(
                                offset,
                                Math.min(
                                        STREAM_DELTA_CODE_POINTS,
                                        answer.codePointCount(offset, answer.length())));
                responses.add(
                        new TravelAgentStreamResponse(
                                conversationId, answer.substring(offset, end), null, false));
                offset = end;
            }
        }
        responses.add(new TravelAgentStreamResponse(conversationId, "", artifactPath, true));
        return responses;
    }
}
