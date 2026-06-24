package com.example.travelassistant.controller;

import com.example.travelassistant.domain.TravelAgentChatResult;
import com.example.travelassistant.domain.TravelAgentRequest;
import com.example.travelassistant.domain.TravelAgentResponse;
import com.example.travelassistant.service.TravelAgentService;
import jakarta.annotation.Resource;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 旅行助手 HTTP API，负责入参校验和会话 ID 分配。 */
@RestController
@RequestMapping("/api/travel-agent")
public class TravelAgentController {

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
}
