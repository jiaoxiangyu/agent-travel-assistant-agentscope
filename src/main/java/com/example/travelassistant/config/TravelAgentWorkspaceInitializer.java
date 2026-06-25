package com.example.travelassistant.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** 初始化 Harness 本机 Workspace 的静态种子文件，保留用户后续手工编辑。 */
@Component
public class TravelAgentWorkspaceInitializer {

    private static final String AGENTS_MD =
            """
            # TravelAssistant

            你是一个中文旅行规划助手，目标是给用户产出可执行、预算友好、兼顾天气和体力的旅行方案。

            ## 工作方式

            - 先判断用户是否至少提供了目的地和旅行天数；缺少这些关键信息时，先用一句话追问，不要编造。
            - 信息足够时，优先调用旅行工具查询城市画像、天气、景点候选，并生成结构化行程草稿。
            - 最终回答请用中文 Markdown，包含天气提示、每日行程、交通建议、餐饮/预算建议和注意事项。
            - 预算未说明住宿或大交通时，要明确说明估算边界；涉及门票、营业时间、预约要求时提醒以官方信息为准。
            - 行程不要过满，每天保留机动时间，并根据天气给出室内/室外备选方案。
            """;

    private static final String KNOWLEDGE_MD =
            """
            # 旅行规划知识库

            ## 通用原则

            - 先补齐目的地、旅行天数、预算、同行人、偏好、出发地或交通约束等关键信息。
            - 每天安排不宜过满，优先减少跨城区往返，保留午休、排队和临时变更时间。
            - 天气不确定时，同时给出室内备选和行程替换建议。
            - 预算估算要说明是否包含住宿、大交通、门票、餐饮和市内交通。
            - 所有营业时间、预约要求、票价和交通班次都提醒用户以官方实时信息为准。

            ## 输出结构

            - 先给一句总体策略摘要。
            - 再给天气和预算提示。
            - 按天输出行程、交通、餐饮和备选方案。
            - 最后输出注意事项和可继续追问的方向。
            """;

    private final TravelAgentProperties properties;

    public TravelAgentWorkspaceInitializer(TravelAgentProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        Path workspaceDir = properties.getWorkspaceDir();
        try {
            Files.createDirectories(workspaceDir);
            Files.createDirectories(workspaceDir.resolve("knowledge"));
            Files.createDirectories(workspaceDir.resolve("skills"));
            Files.createDirectories(workspaceDir.resolve("subagents"));
            Files.createDirectories(workspaceDir.resolve("plans"));
            writeIfAbsent(workspaceDir.resolve("AGENTS.md"), AGENTS_MD);
            writeIfAbsent(workspaceDir.resolve("knowledge").resolve("KNOWLEDGE.md"), KNOWLEDGE_MD);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize travel agent workspace", e);
        }
    }

    private void writeIfAbsent(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
