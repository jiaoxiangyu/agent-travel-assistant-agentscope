package com.example.travelassistant.persistence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.travelassistant.persistence.entity.ConversationEntity;
import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import com.example.travelassistant.persistence.enums.MessageRole;
import com.example.travelassistant.persistence.mapper.ConversationMapper;
import com.example.travelassistant.persistence.mapper.ConversationMessageMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationPersistenceServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ConversationMessageMapper messageMapper;

    @InjectMocks
    private ConversationPersistenceServiceImpl service;

    @Test
    void saveUserMessageCreatesConversationTitleAndInsertsUserMessage() {
        service.saveUserMessage("conversation-1", "alice", "  杭州\n3 天亲子游  ");

        ArgumentCaptor<ConversationEntity> conversationCaptor =
                ArgumentCaptor.forClass(ConversationEntity.class);
        ArgumentCaptor<ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(conversationMapper).insertOrTouch(conversationCaptor.capture());
        verify(messageMapper).insert(messageCaptor.capture());

        ConversationEntity conversation = conversationCaptor.getValue();
        assertThat(conversation.getId()).isEqualTo("conversation-1");
        assertThat(conversation.getUserId()).isEqualTo("alice");
        assertThat(conversation.getTitle()).isEqualTo("杭州 3 天亲子游");
        assertThat(conversation.getCreatedAt()).isNotNull();
        assertThat(conversation.getUpdatedAt()).isEqualTo(conversation.getCreatedAt());

        ConversationMessageEntity message = messageCaptor.getValue();
        assertThat(message.getConversationId()).isEqualTo("conversation-1");
        assertThat(message.getRole()).isEqualTo(MessageRole.USER);
        assertThat(message.getContent()).isEqualTo("  杭州\n3 天亲子游  ");
        assertThat(message.getArtifactPath()).isNull();
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @Test
    void saveUserMessageTruncatesLongConversationTitle() {
        String longMessage = "a".repeat(70);

        service.saveUserMessage("conversation-1", "alice", longMessage);

        ArgumentCaptor<ConversationEntity> conversationCaptor =
                ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationMapper).insertOrTouch(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getTitle()).hasSize(60);
    }

    @Test
    void saveAssistantMessageTouchesExistingConversationAndInsertsAssistantMessage() {
        ConversationEntity existing =
                new ConversationEntity("conversation-1", "alice", "杭州", Instant.parse("2026-01-01T00:00:00Z"));
        when(conversationMapper.findById("conversation-1")).thenReturn(existing);

        service.saveAssistantMessage("conversation-1", "推荐行程", "artifacts/plan.md");

        ArgumentCaptor<ConversationEntity> conversationCaptor =
                ArgumentCaptor.forClass(ConversationEntity.class);
        ArgumentCaptor<ConversationMessageEntity> messageCaptor =
                ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(conversationMapper).updateUpdatedAt(conversationCaptor.capture());
        verify(messageMapper).insert(messageCaptor.capture());

        assertThat(conversationCaptor.getValue().getUpdatedAt()).isAfter(existing.getCreatedAt());
        ConversationMessageEntity message = messageCaptor.getValue();
        assertThat(message.getConversationId()).isEqualTo("conversation-1");
        assertThat(message.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(message.getContent()).isEqualTo("推荐行程");
        assertThat(message.getArtifactPath()).isEqualTo("artifacts/plan.md");
    }

    @Test
    void saveAssistantMessageStillInsertsMessageWhenConversationMissing() {
        when(conversationMapper.findById("conversation-1")).thenReturn(null);

        service.saveAssistantMessage("conversation-1", "推荐行程", "artifacts/plan.md");

        verify(conversationMapper, never()).updateUpdatedAt(any());
        verify(messageMapper).insert(any(ConversationMessageEntity.class));
    }

    @Test
    void findRecentMessagesReturnsOldestFirst() {
        ConversationMessageEntity newest =
                new ConversationMessageEntity("conversation-1", MessageRole.ASSISTANT, "第二条", null, Instant.now());
        ConversationMessageEntity oldest =
                new ConversationMessageEntity("conversation-1", MessageRole.USER, "第一条", null, Instant.now());
        when(messageMapper.findRecentByConversationId("conversation-1", 2))
                .thenReturn(List.of(newest, oldest));

        List<ConversationMessageEntity> messages = service.findRecentMessages("conversation-1", 2);

        assertThat(messages).containsExactly(oldest, newest);
    }

    @Test
    void findRecentMessagesReturnsEmptyWhenLimitIsNotPositive() {
        assertThat(service.findRecentMessages("conversation-1", 0)).isEmpty();

        verifyNoInteractions(messageMapper);
    }
}
