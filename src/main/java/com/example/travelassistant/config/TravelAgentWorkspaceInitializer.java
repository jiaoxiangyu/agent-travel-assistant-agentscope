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
            - 信息足够时，优先调用旅行工具或匹配的 skill 查询城市画像、天气、景点候选，并生成结构化行程草稿。
            - 涉及实时、外部或会变化的信息时，必须优先使用匹配的 skill 或工具查询；没有查询结果时，不要编造具体数值。
            - 需要使用特定能力时，先查看可用 skills 中是否有匹配项，并通过 `load_skill_through_path` 读取说明后执行。
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
            writeIfAbsent(workspaceDir.resolve("knowledge").resolve("KNOWLEDGE.md"), KNOWLEDGE_MD);
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
}
