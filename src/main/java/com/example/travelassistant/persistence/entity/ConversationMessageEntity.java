package com.example.travelassistant.persistence.entity;

import com.example.travelassistant.persistence.enums.MessageRole;
import java.time.Instant;

/** 会话消息实体，保存用户输入、助手回答以及回答对应的 Markdown 产物路径。 */
public class ConversationMessageEntity {

    /** 消息自增主键。 */
    private Long id;

    /** 所属业务会话 ID。 */
    private String conversationId;

    /** 消息角色，决定上下文恢复时转换成 UserMessage 还是 AssistantMessage。 */
    private MessageRole role;

    /** 消息正文，可能包含较长的 Markdown 策略内容。 */
    private String content;

    /** 助手消息对应的本地 Markdown 文件路径；用户消息为空。 */
    private String artifactPath;

    /** 消息创建时间。 */
    private Instant createdAt;

    /** MyBatis 映射查询结果时使用。 */
    public ConversationMessageEntity() {}

    public ConversationMessageEntity(
            String conversationId,
            MessageRole role,
            String content,
            String artifactPath,
            Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.artifactPath = artifactPath;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
