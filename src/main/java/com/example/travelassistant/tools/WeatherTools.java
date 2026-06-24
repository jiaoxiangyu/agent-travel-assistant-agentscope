package com.example.travelassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 天气查询工具，供 Agent 在规划行程时判断室内外安排。 */
@Component
public class WeatherTools {

    @Resource
    private TravelToolSupport support;

    @Tool(
            name = "get_weather",
            description = "根据城市名查询实时天气，返回温度、湿度、风速和中文天气描述。",
            readOnly = true,
            concurrencySafe = true)
    public String getWeather(
            @ToolParam(name = "city", description = "目的地城市中文名，例如北京、杭州、上海") String city) {
        // Open-Meteo 天气接口需要经纬度，因此先通过地理编码接口定位城市。
        JsonNode geo =
                support.getJson(
                        UriComponentsBuilder.fromUriString(
                                        "https://geocoding-api.open-meteo.com/v1/search")
                                .queryParam("name", city)
                                .queryParam("count", 1)
                                .queryParam("language", "zh")
                                .queryParam("format", "json")
                                .build()
                                .encode()
                                .toUri());

        JsonNode first = geo.path("results").path(0);
        if (first.isMissingNode()) {
            return "{\"error\":\"未找到城市\"}";
        }

        // 查询实时天气，并保留原始 weatherCode 方便模型做进一步判断。
        JsonNode weather =
                support.getJson(
                        UriComponentsBuilder.fromUriString("https://api.open-meteo.com/v1/forecast")
                                .queryParam("latitude", first.path("latitude").asDouble())
                                .queryParam("longitude", first.path("longitude").asDouble())
                                .queryParam(
                                        "current",
                                        "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code")
                                .queryParam("timezone", "auto")
                                .build()
                                .encode()
                                .toUri());

        JsonNode current = weather.path("current");
        int weatherCode = current.path("weather_code").asInt();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", first.path("name").asText(city));
        result.put("country", first.path("country").asText(""));
        result.put("temperature", current.path("temperature_2m").asDouble());
        result.put("humidity", current.path("relative_humidity_2m").asInt());
        result.put("windSpeed", current.path("wind_speed_10m").asDouble());
        result.put("weatherCode", weatherCode);
        result.put("weatherText", support.weatherText(weatherCode));
        return support.toJson(result);
    }
}
