package com.example.travelassistant.service;

import com.example.travelassistant.domain.TravelAgentChatResult;

/** 旅行助手对话服务，封装 Agent 调用、会话持久化和产物生成。 */
public interface TravelAgentService {

    /**
     * 基于指定会话处理用户消息，并返回最终回答及可选 Markdown 产物路径。
     */
    TravelAgentChatResult chat(String conversationId, String userId, String message);
}
