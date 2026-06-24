package com.example.travelassistant.persistence.service;

import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import java.util.List;

/** 负责将业务会话和聊天消息保存到 MySQL，并提供上下文恢复查询。 */
public interface ConversationPersistenceService {

    /** 保存用户消息；如果会话不存在，则用首条消息创建会话标题。 */
    void saveUserMessage(String conversationId, String userId, String message);

    /** 保存助手回答，并记录对应 Markdown 产物路径。 */
    void saveAssistantMessage(String conversationId, String answer, String artifactPath);

    /** 按时间正序返回最近 N 条消息，用于重建 Agent 输入上下文。 */
    List<ConversationMessageEntity> findRecentMessages(String conversationId, int limit);
}
