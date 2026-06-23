package com.example.travelassistant.model;

public class TravelAgentResponse {

    private final String conversationId;

    private final String answer;

    public TravelAgentResponse(String conversationId, String answer) {
        this.conversationId = conversationId;
        this.answer = answer;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getAnswer() {
        return answer;
    }
}
