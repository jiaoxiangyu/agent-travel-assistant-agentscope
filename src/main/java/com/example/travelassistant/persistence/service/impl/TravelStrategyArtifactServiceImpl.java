package com.example.travelassistant.persistence.service.impl;

import com.example.travelassistant.config.TravelAgentProperties;
import com.example.travelassistant.persistence.service.TravelStrategyArtifactService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/** 将每次成功生成的旅行方案保存为 Markdown 文件。 */
@Service
public class TravelStrategyArtifactServiceImpl implements TravelStrategyArtifactService {

    /** 文件名时间戳格式，保证同一会话多次生成时文件名可排序。 */
    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final TravelAgentProperties properties;

    public TravelStrategyArtifactServiceImpl(TravelAgentProperties properties) {
        this.properties = properties;
    }

    /** 写入 Markdown 产物，并返回相对或绝对文件路径字符串。 */
    @Override
    public String writeMarkdown(
            String conversationId, String userId, String userMessage, String answer) {
        Instant now = Instant.now();
        Path directory =
                properties
                        .getArtifactDir()
                        .resolve(sanitizePathSegment(userId))
                        .resolve(sanitizePathSegment(conversationId));
        String fileName = FILE_TIME_FORMATTER.format(now) + ".md";
        Path file = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.writeString(
                    file,
                    buildMarkdown(conversationId, userId, userMessage, answer, now),
                    StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write travel strategy artifact", e);
        }
    }

    /** 组装包含会话元信息、用户需求和最终策略内容的 Markdown。 */
    private String buildMarkdown(
            String conversationId, String userId, String userMessage, String answer, Instant now) {
        return """
                # 旅行策略

                - 会话 ID：%s
                - 用户 ID：%s
                - 生成时间：%s

                ## 用户需求

                %s

                ## 策略内容

                %s
                """
                .formatted(
                        conversationId,
                        userId,
                        now,
                        userMessage,
                        answer);
    }

    /** 清理路径片段，避免用户输入中的特殊字符影响本地文件路径。 */
    private String sanitizePathSegment(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
