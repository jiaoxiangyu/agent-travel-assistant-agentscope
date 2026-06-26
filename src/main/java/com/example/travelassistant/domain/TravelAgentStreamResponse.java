package com.example.travelassistant.domain;

/** 聊天流式接口响应事件。 */
public class TravelAgentStreamResponse {

    /** 当前会话 ID，客户端后续多轮对话需要原样带回。 */
    private final String conversationId;

    /** 本次 SSE 事件新增的回答片段。 */
    private final String delta;

    /** 旅行策略 Markdown 产物路径；通常仅最终事件携带。 */
    private final String artifactPath;

    /** 是否为本轮回答的最终事件。 */
    private final boolean done;

    public TravelAgentStreamResponse(
            String conversationId, String delta, String artifactPath, boolean done) {
        this.conversationId = conversationId;
        this.delta = delta;
        this.artifactPath = artifactPath;
        this.done = done;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getDelta() {
        return delta;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public boolean isDone() {
        return done;
    }
}
