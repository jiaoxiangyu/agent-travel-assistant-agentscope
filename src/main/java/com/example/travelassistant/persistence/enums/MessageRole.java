package com.example.travelassistant.persistence.enums;

/** 聊天消息角色，用于在恢复上下文时转换为 AgentScope 消息类型。 */
public enum MessageRole {
    /** 用户输入消息。 */
    USER,
    /** 助手回答消息。 */
    ASSISTANT
}
