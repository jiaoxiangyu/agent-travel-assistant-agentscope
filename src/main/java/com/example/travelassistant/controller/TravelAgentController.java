package com.example.travelassistant.controller;

import com.example.travelassistant.model.TravelAgentRequest;
import com.example.travelassistant.model.TravelAgentResponse;
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

@RestController
@RequestMapping("/api/travel-agent")
public class TravelAgentController {

    @Resource
    private TravelAgentService travelAgentService;

    @PostMapping("/chat")
    public TravelAgentResponse chat(@RequestBody TravelAgentRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }

        String conversationId =
                StringUtils.hasText(request.getConversationId())
                        ? request.getConversationId()
                        : UUID.randomUUID().toString();

        String answer =
                travelAgentService.chat(
                        conversationId, request.getUserId(), request.getMessage().trim());
        return new TravelAgentResponse(conversationId, answer);
    }
}
