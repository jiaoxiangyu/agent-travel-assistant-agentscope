package com.example.travelassistant.domain;

/** 聊天接口请求体。 */
public class TravelAgentRequest {

    /** 多轮会话 ID；为空时服务端会创建新会话。 */
    private String conversationId;

    /** 用户 ID，用于隔离不同用户的 Agent 状态和产物目录。 */
    private String userId;

    /** 用户本轮输入的旅行需求或追问。 */
    private String message;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
