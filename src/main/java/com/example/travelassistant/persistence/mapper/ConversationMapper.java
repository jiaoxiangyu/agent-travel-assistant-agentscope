package com.example.travelassistant.persistence.mapper;

import com.example.travelassistant.persistence.entity.ConversationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 旅行会话表的 MyBatis Mapper。 */
@Mapper
public interface ConversationMapper {

    /** 按会话 ID 查询业务会话。 */
    @Select(
            """
            SELECT id, user_id, title, created_at, updated_at
            FROM travel_conversations
            WHERE id = #{id}
            """)
    ConversationEntity findById(String id);

    /** 新建会话；若会话已存在，只刷新更新时间。 */
    @Insert(
            """
            INSERT INTO travel_conversations (id, user_id, title, created_at, updated_at)
            VALUES (#{conversation.id}, #{conversation.userId}, #{conversation.title},
                    #{conversation.createdAt}, #{conversation.updatedAt})
            ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
            """)
    int insertOrTouch(@Param("conversation") ConversationEntity conversation);

    /** 刷新会话更新时间。 */
    @Update(
            """
            UPDATE travel_conversations
            SET updated_at = #{conversation.updatedAt}
            WHERE id = #{conversation.id}
            """)
    int updateUpdatedAt(@Param("conversation") ConversationEntity conversation);
}
