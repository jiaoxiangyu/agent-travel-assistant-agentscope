package com.example.travelassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

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
