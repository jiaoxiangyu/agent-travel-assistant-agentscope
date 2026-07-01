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
            - 信息足够时，必须先通过 `agent_spawn` 同步委派 `researcher` 子 Agent 汇总城市画像、天气、景点和约束，再生成方案。
            - 生成完整方案草稿后，必须把完整草稿正文、用户原始约束、预算边界和 researcher 摘要一起放进 `agent_spawn` 的 `task` 中，委派 `reviewer` 子 Agent 审阅；不能只传“审阅预算/节奏”这类摘要任务。
            - 涉及实时、外部或会变化的信息时，必须优先使用匹配的 skill 或工具查询；没有查询结果时，不要编造具体数值。
            - 需要使用特定能力时，先查看可用 skills 中是否有匹配项，并通过 `load_skill_through_path` 读取说明后执行。
            - 最终回答请用中文 Markdown，包含天气提示、每日行程、交通建议、餐饮/预算建议和注意事项。
            - 预算未说明住宿或大交通时，要明确说明估算边界；涉及门票、营业时间、预约要求时提醒以官方信息为准。
            - 行程不要过满，每天保留机动时间，并根据天气给出室内/室外备选方案。
            """;

    private static final String RESEARCHER_SUBAGENT_MD =
            """
            ---
            description: 旅行资料研究员。当旅行规划需要汇总城市画像、实时天气、景点候选、交通/预约/预算约束时使用。
            mode: subagent
            steps: 6
            ---

            你是旅行资料研究员，负责为主旅行助手收集和归纳事实依据。

            ## 工作要求

            1. 从主 Agent 给出的任务中提取目的地、天数、预算、同行人、偏好和特殊约束。
            2. 优先使用可用工具、知识库或 weather skill 查询城市画像、天气、景点候选和行程约束。
            3. 只输出结构化中文资料摘要，不要生成最终旅行方案。
            4. 对实时天气、营业时间、预约要求、票价等不确定信息，必须说明信息来源限制或建议以官方为准。
            5. 没有查询结果时明确说明缺口，不要编造具体数值。
            """;

    private static final String REVIEWER_SUBAGENT_MD =
            """
            ---
            description: 旅行方案审稿员。当主 Agent 已有旅行方案草稿，需要检查预算、节奏、天气备选、遗漏偏好和风险提醒时使用。
            mode: subagent
            steps: 4
            ---

            你是旅行方案审稿员，负责审阅主旅行助手生成的方案草稿。

            ## 工作要求

            1. 检查方案是否满足用户目的地、天数、预算、同行人和偏好。
            2. 检查行程是否过满、跨区域移动是否过多、是否保留机动时间。
            3. 检查天气提示、室内/室外备选、预算边界、预约提醒和官方信息提示是否完整。
            4. 如果任务中没有完整方案草稿正文，请明确指出“缺少草稿正文”，不要尝试从记忆中猜测。
            5. 输出简洁中文审稿意见：先列必须修改的问题，再列可选优化建议。
            6. 不要直接面向用户生成最终方案，最终回答由主 Agent 统一输出。
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

    private static final String SUBAGENT_RULES_MARKER = "<!-- travel-assistant-subagent-rules -->";

    private static final String SUBAGENT_RULES_HEADING = "## 子 Agent 委派规则";

    private static final String SUBAGENT_RULES_MD =
            """

            %s
            ## 子 Agent 委派规则

            - 用户至少提供目的地和旅行天数时，必须先通过 `agent_spawn` 同步委派 `researcher` 子 Agent 汇总城市画像、天气、景点和约束。
            - 生成完整方案草稿后，必须把完整草稿正文、用户原始约束、预算边界和 researcher 摘要一起放进 `agent_spawn` 的 `task` 中，委派 `reviewer` 子 Agent 审阅；不能只传“审阅预算/节奏”这类摘要任务。
            - 最终回答由主 Agent 汇总输出，不要向用户暴露 `agent_spawn`、子 Agent 结果或审稿过程。
            """
                    .formatted(SUBAGENT_RULES_MARKER);

    private static final String WEATHER_SKILL_MD =
            """
            ---
            name: weather
            description: 旅行规划需要天气提示、室内外备选、当前天气、温度、湿度或风速时必须使用；不要凭知识库编造实时天气。
            ---

            # Weather

            这个 skill 用于查询城市实时天气，并把结果用于旅行规划中的天气提示、室内外备选和行程调整。

            ## 使用步骤

            1. 从用户需求中提取目的地城市中文名，例如 `北京`、`杭州`、`上海`。
            2. 使用当前 skill 的 files-root 拼接脚本路径，运行 `scripts/get_weather.py`，将城市名作为第一个参数。
            3. 读取脚本返回的 JSON，并在最终旅行方案中用中文说明天气、温度、湿度、风速和出行建议。

            ## 调用示例

            ```bash
            python3 <files-root>/scripts/get_weather.py 北京
            ```

            ## 输出约定

            脚本成功时返回如下 JSON 字段：

            - `city`：匹配到的城市名。
            - `country`：国家或地区。
            - `temperature`：当前气温，单位摄氏度。
            - `humidity`：相对湿度百分比。
            - `windSpeed`：10 米风速。
            - `weatherCode`：Open-Meteo 天气代码。
            - `weatherText`：中文天气描述。
            - `travelAdvice`：面向旅行安排的简短建议。

            如果返回 `error` 字段，不要编造天气；请说明暂时无法查询，并给出通用的室内外两套备选安排。
            """;

    private static final String WEATHER_SCRIPT =
            """
            #!/usr/bin/env python3
            import json
            import sys
            import urllib.parse
            import urllib.request


            WEATHER_TEXT = {
                0: "晴天",
                1: "大部晴朗",
                2: "局部多云",
                3: "阴天",
                45: "雾",
                48: "雾",
                51: "小毛毛雨",
                53: "中等毛毛雨",
                55: "大毛毛雨",
                56: "轻微冻毛毛雨",
                57: "强冻毛毛雨",
                61: "小雨",
                63: "中雨",
                65: "大雨",
                66: "轻微冻雨",
                67: "强冻雨",
                71: "小雪",
                73: "中雪",
                75: "大雪",
                77: "雪粒",
                80: "小阵雨",
                81: "中等阵雨",
                82: "强阵雨",
                85: "小阵雪",
                86: "强阵雪",
                95: "雷暴",
                96: "雷暴伴小冰雹",
                99: "雷暴伴大冰雹",
            }


            def get_json(url):
                request = urllib.request.Request(url, headers={"User-Agent": "travel-assistant/1.0"})
                with urllib.request.urlopen(request, timeout=10) as response:
                    return json.loads(response.read().decode("utf-8"))


            def weather_text(code):
                return WEATHER_TEXT.get(code, "未知天气")


            def travel_advice(code):
                if code in {61, 63, 65, 80, 81, 82, 95, 96, 99}:
                    return "建议减少长时间户外步行，优先安排博物馆、商场、展馆等室内活动，并准备雨具。"
                if code in {71, 73, 75, 77, 85, 86}:
                    return "建议控制户外停留时间，注意防寒防滑，预留交通延误时间。"
                if code in {45, 48}:
                    return "能见度可能偏低，建议放慢行程节奏，减少远距离换乘。"
                if code in {0, 1, 2}:
                    return "天气适合户外游览，注意防晒补水，并保留午间休息时间。"
                return "可正常安排行程，但建议保留室内备选以应对天气变化。"


            def main():
                if len(sys.argv) < 2 or not sys.argv[1].strip():
                    print(json.dumps({"error": "缺少城市名"}, ensure_ascii=False))
                    return 1

                city = sys.argv[1].strip()
                query = urllib.parse.urlencode(
                    {"name": city, "count": 1, "language": "zh", "format": "json"}
                )
                geo = get_json(f"https://geocoding-api.open-meteo.com/v1/search?{query}")
                results = geo.get("results") or []
                if not results:
                    print(json.dumps({"error": "未找到城市", "city": city}, ensure_ascii=False))
                    return 0

                first = results[0]
                weather_query = urllib.parse.urlencode(
                    {
                        "latitude": first["latitude"],
                        "longitude": first["longitude"],
                        "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
                        "timezone": "auto",
                    }
                )
                weather = get_json(f"https://api.open-meteo.com/v1/forecast?{weather_query}")
                current = weather.get("current") or {}
                code = int(current.get("weather_code", -1))
                result = {
                    "city": first.get("name", city),
                    "country": first.get("country", ""),
                    "temperature": current.get("temperature_2m"),
                    "humidity": current.get("relative_humidity_2m"),
                    "windSpeed": current.get("wind_speed_10m"),
                    "weatherCode": code,
                    "weatherText": weather_text(code),
                    "travelAdvice": travel_advice(code),
                }
                print(json.dumps(result, ensure_ascii=False))
                return 0


            if __name__ == "__main__":
                try:
                    raise SystemExit(main())
                except Exception as exc:
                    print(json.dumps({"error": str(exc)}, ensure_ascii=False))
                    raise SystemExit(1)
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
            Files.createDirectories(workspaceDir.resolve("skills").resolve("weather"));
            Files.createDirectories(
                    workspaceDir.resolve("skills").resolve("weather").resolve("scripts"));
            writeIfAbsent(workspaceDir.resolve("AGENTS.md"), AGENTS_MD);
            ensureSubagentRules(workspaceDir.resolve("AGENTS.md"));
            writeIfAbsent(workspaceDir.resolve("knowledge").resolve("KNOWLEDGE.md"), KNOWLEDGE_MD);
            writeIfAbsent(
                    workspaceDir.resolve("subagents").resolve("researcher.md"),
                    RESEARCHER_SUBAGENT_MD);
            writeIfAbsent(
                    workspaceDir.resolve("subagents").resolve("reviewer.md"),
                    REVIEWER_SUBAGENT_MD);
            writeIfAbsent(
                    workspaceDir.resolve("skills").resolve("weather").resolve("SKILL.md"),
                    WEATHER_SKILL_MD);
            writeIfAbsent(
                    workspaceDir
                            .resolve("skills")
                            .resolve("weather")
                            .resolve("scripts")
                            .resolve("get_weather.py"),
                    WEATHER_SCRIPT);
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

    private void ensureSubagentRules(Path agentsPath) throws IOException {
        if (!Files.exists(agentsPath)) {
            return;
        }
        String content = Files.readString(agentsPath, StandardCharsets.UTF_8);
        if (!content.contains("# TravelAssistant")) {
            return;
        }
        String updated =
                content.contains(SUBAGENT_RULES_MARKER)
                        ? replaceManagedSubagentRules(content)
                        : content.stripTrailing() + SUBAGENT_RULES_MD;
        Files.writeString(agentsPath, updated, StandardCharsets.UTF_8);
    }

    private String replaceManagedSubagentRules(String content) {
        int markerIndex = content.indexOf(SUBAGENT_RULES_MARKER);
        int nextHeadingIndex = content.indexOf("\n## ", markerIndex + SUBAGENT_RULES_MARKER.length());
        if (nextHeadingIndex < 0) {
            return content.substring(0, markerIndex).stripTrailing() + SUBAGENT_RULES_MD;
        }
        String afterManagedBlock = content.substring(nextHeadingIndex);
        if (afterManagedBlock.startsWith("\n" + SUBAGENT_RULES_HEADING)) {
            int followingHeadingIndex =
                    content.indexOf("\n## ", nextHeadingIndex + SUBAGENT_RULES_HEADING.length() + 1);
            if (followingHeadingIndex < 0) {
                return content.substring(0, markerIndex).stripTrailing() + SUBAGENT_RULES_MD;
            }
            afterManagedBlock = content.substring(followingHeadingIndex);
        }
        return content.substring(0, markerIndex).stripTrailing() + SUBAGENT_RULES_MD + afterManagedBlock;
    }
}
