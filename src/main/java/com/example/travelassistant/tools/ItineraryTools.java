package com.example.travelassistant.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 行程草稿工具，把城市、天数、预算和偏好组合成结构化每日安排。 */
@Component
public class ItineraryTools {

    @Resource
    private TravelToolSupport support;

    @Tool(
            name = "build_itinerary",
            description = "根据城市、天数、预算、偏好和同行人生成结构化行程草稿。",
            readOnly = true,
            concurrencySafe = true)
    public String buildItinerary(
            @ToolParam(name = "city", description = "目的地城市中文名") String city,
            @ToolParam(name = "days", description = "旅行天数，1 到 7 天") Integer days,
            @ToolParam(name = "budget", description = "预算描述，例如3000元、不含住宿、人均2000") String budget,
            @ToolParam(name = "preference", description = "旅行偏好，例如人文、美食、亲子、自然") String preference,
            @ToolParam(name = "travelers", description = "同行人描述，例如情侣、亲子、朋友、独自旅行") String travelers) {
        // 工具层限制天数范围，避免模型传入异常值导致行程过长或为空。
        int normalizedDays = Math.max(1, Math.min(days == null ? 3 : days, 7));
        List<String> attractions = support.attractionsFor(city, preference);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("days", normalizedDays);
        result.put("budget", budget);
        result.put("preference", preference);
        result.put("travelers", travelers);

        List<Map<String, Object>> itinerary = new ArrayList<>();
        for (int i = 1; i <= normalizedDays; i++) {
            // 用候选景点轮转填充每天上午/下午，最终文案仍由模型润色和取舍。
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", i);
            day.put("morning", attractions.get((i - 1) % attractions.size()));
            day.put("afternoon", attractions.get(i % attractions.size()));
            day.put("evening", support.eveningSuggestion(city, preference));
            day.put("pace", normalizedDays <= 2 ? "紧凑" : "适中");
            itinerary.add(day);
        }

        result.put("itinerary", itinerary);
        result.put(
                "notes",
                List.of(
                        "真实营业时间、门票和预约要求以出行当天官方信息为准",
                        "室外景点可根据天气和体力灵活调换",
                        "预算未细分时，建议把住宿和大交通单独核算"));
        return support.toJson(result);
    }
}
