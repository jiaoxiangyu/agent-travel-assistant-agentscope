package com.example.travelassistant.persistence.service.impl;

import com.example.travelassistant.persistence.enums.MessageRole;
import com.example.travelassistant.persistence.entity.ConversationEntity;
import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import com.example.travelassistant.persistence.mapper.ConversationMapper;
import com.example.travelassistant.persistence.mapper.ConversationMessageMapper;
import com.example.travelassistant.persistence.service.ConversationPersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 负责将业务会话和聊天消息保存到 MySQL，并提供上下文恢复查询。 */
@Service
public class ConversationPersistenceServiceImpl implements ConversationPersistenceService {

    /** 会话标题截断长度，避免首条消息过长影响列表展示。 */
    private static final int TITLE_MAX_LENGTH = 60;

    private final ConversationMapper conversationMapper;

    private final ConversationMessageMapper messageMapper;

    public ConversationPersistenceServiceImpl(
            ConversationMapper conversationMapper,
            ConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /** 保存用户消息；如果会话不存在，则用首条消息创建会话标题。 */
    @Override
    @Transactional
    public void saveUserMessage(String conversationId, String userId, String message) {
        Instant now = Instant.now();
        ConversationEntity conversation =
                new ConversationEntity(conversationId, userId, buildTitle(message), now);
        conversation.touch(now);
        conversationMapper.insertOrTouch(conversation);
        messageMapper.insert(
                new ConversationMessageEntity(
                        conversationId, MessageRole.USER, message, null, now));
    }

    /** 保存助手回答，并记录对应 Markdown 产物路径。 */
    @Override
    @Transactional
    public void saveAssistantMessage(String conversationId, String answer, String artifactPath) {
        Instant now = Instant.now();
        ConversationEntity conversation = conversationMapper.findById(conversationId);
        if (conversation != null) {
            conversation.touch(now);
            conversationMapper.updateUpdatedAt(conversation);
        }
        messageMapper.insert(
                new ConversationMessageEntity(
                        conversationId, MessageRole.ASSISTANT, answer, artifactPath, now));
    }

    /** 按时间正序返回最近 N 条消息，用于重建 Agent 输入上下文。 */
    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageEntity> findRecentMessages(String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<ConversationMessageEntity> messages =
                new ArrayList<>(
                        messageMapper.findRecentByConversationId(conversationId, limit));
        Collections.reverse(messages);
        return messages;
    }

    /** 使用首条用户消息生成简短会话标题。 */
    private String buildTitle(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH);
    }
}
