package com.example.travelassistant.persistence.service;

/** 将每次成功生成的旅行方案保存为 Markdown 文件。 */
public interface TravelStrategyArtifactService {

    /** 写入 Markdown 产物，并返回相对或绝对文件路径字符串。 */
    String writeMarkdown(String conversationId, String userId, String userMessage, String answer);
}
