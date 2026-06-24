package com.example.travelassistant.domain;

/** 服务层返回的 Agent 聊天结果。 */
public class TravelAgentChatResult {

    /** Agent 生成的中文 Markdown 回答。 */
    private final String answer;

    /** 已保存的旅行策略 Markdown 文件路径。 */
    private final String artifactPath;

    public TravelAgentChatResult(String answer, String artifactPath) {
        this.answer = answer;
        this.artifactPath = artifactPath;
    }

    public String getAnswer() {
        return answer;
    }

    public String getArtifactPath() {
        return artifactPath;
    }
}
