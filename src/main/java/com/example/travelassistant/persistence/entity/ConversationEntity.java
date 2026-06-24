package com.example.travelassistant.persistence.entity;

import java.time.Instant;

/** 业务会话实体，一条记录代表一次可持续多轮对话的旅行规划会话。 */
public class ConversationEntity {

    /** 会话 ID，同时作为 AgentScope sessionId。 */
    private String id;

    /** 发起会话的用户 ID，用于多用户隔离。 */
    private String userId;

    /** 会话标题，默认由首条用户消息截断生成。 */
    private String title;

    /** 会话创建时间。 */
    private Instant createdAt;

    /** 最近一次用户或助手消息写入时间。 */
    private Instant updatedAt;

    /** MyBatis 映射查询结果时使用。 */
    public ConversationEntity() {}

    public ConversationEntity(String id, String userId, String title, Instant now) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 刷新会话更新时间。 */
    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
