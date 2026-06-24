package com.example.travelassistant.domain;

/** 聊天接口响应体。 */
public class TravelAgentResponse {

    /** 当前会话 ID，客户端后续多轮对话需要原样带回。 */
    private final String conversationId;

    /** Agent 生成的中文 Markdown 回答。 */
    private final String answer;

    /** 旅行策略 Markdown 产物路径。 */
    private final String artifactPath;

    public TravelAgentResponse(String conversationId, String answer, String artifactPath) {
        this.conversationId = conversationId;
        this.answer = answer;
        this.artifactPath = artifactPath;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public String getArtifactPath() {
        return artifactPath;
    }
}
