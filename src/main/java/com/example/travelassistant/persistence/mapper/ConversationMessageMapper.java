package com.example.travelassistant.persistence.mapper;

import com.example.travelassistant.persistence.entity.ConversationMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 旅行会话消息表的 MyBatis Mapper。 */
@Mapper
public interface ConversationMessageMapper {

    /** 保存一条会话消息。 */
    @Insert(
            """
            INSERT INTO travel_messages (conversation_id, role, content, artifact_path, created_at)
            VALUES (#{conversationId}, #{role}, #{content}, #{artifactPath}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ConversationMessageEntity message);

    /** 查询某个会话的全部消息，按创建时间正序返回。 */
    @Select(
            """
            SELECT id, conversation_id, role, content, artifact_path, created_at
            FROM travel_messages
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /** 查询某个会话的最近消息，调用方可通过 limit 限制条数。 */
    @Select(
            """
            SELECT id, conversation_id, role, content, artifact_path, created_at
            FROM travel_messages
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ConversationMessageEntity> findRecentByConversationId(
            @Param("conversationId") String conversationId, @Param("limit") int limit);
}
