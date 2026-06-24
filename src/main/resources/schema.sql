CREATE TABLE IF NOT EXISTS travel_conversations (
    id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    title VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_travel_conversations_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS travel_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    artifact_path VARCHAR(512),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_travel_messages_conversation_created (conversation_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
