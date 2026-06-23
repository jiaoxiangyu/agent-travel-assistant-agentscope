package com.example.travelassistant.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AttractionTools {

    @Resource
    private TravelToolSupport support;

    @Tool(
            name = "get_attractions",
            description = "按城市和旅行偏好返回候选景点、街区或体验。",
            readOnly = true,
            concurrencySafe = true)
    public String getAttractions(
            @ToolParam(name = "city", description = "目的地城市中文名") String city,
            @ToolParam(name = "preference", description = "旅行偏好，例如人文、亲子、美食、自然、休闲") String preference) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("preference", preference);
        result.put("attractions", support.attractionsFor(city, preference));
        return support.toJson(result);
    }
}
