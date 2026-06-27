package com.example.travelassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 旅行工具共享支持类，封装 HTTP/JSON 处理和本地兜底数据。 */
@Component
public class TravelToolSupport {

    /** 轻量 HTTP 客户端，用于访问外部旅行和天气 API。 */
    private final RestClient restClient = RestClient.create();

    @Resource
    private ObjectMapper objectMapper;

    /** 发起 GET 请求并把响应体解析为 JsonNode。 */
    JsonNode getJson(URI uri) {
        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse travel API response", e);
        }
    }

    /** 将工具返回的结构化 Map 序列化为 Agent 可读取的 JSON 字符串。 */
    String toJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize travel tool result", e);
        }
    }

    /** 根据城市和偏好返回候选景点，作为无外部景点 API 时的本地知识库。 */
    List<String> attractionsFor(String city, String preference) {
        String safePreference = preference == null ? "" : preference;
        boolean food = safePreference.contains("美食");
        boolean family = safePreference.contains("亲子");
        boolean culture = safePreference.contains("人文") || safePreference.contains("历史");
        boolean nature = safePreference.contains("自然") || safePreference.contains("休闲");

        switch (city) {
            case "杭州":
                if (nature) {
                    return List.of("西湖苏堤", "西溪湿地", "九溪烟树", "龙井村");
                }
                if (culture) {
                    return List.of("灵隐寺", "中国茶叶博物馆", "南宋御街", "河坊街");
                }
                if (family) {
                    return List.of("西湖游船", "杭州动物园", "浙江自然博物院", "西溪湿地");
                }
                return List.of("西湖", "灵隐寺", "龙井村", "河坊街");
            case "北京":
                if (culture) {
                    return List.of("故宫", "天坛", "颐和园", "国家博物馆");
                }
                if (family) {
                    return List.of("北京动物园", "中国科技馆", "颐和园", "奥林匹克公园");
                }
                return List.of("故宫", "天安门广场", "颐和园", "什刹海");
            case "上海":
                if (family) {
                    return List.of("上海迪士尼度假区", "上海自然博物馆", "上海科技馆", "陆家嘴");
                }
                if (food) {
                    return List.of("城隍庙", "南京东路", "武康路", "田子坊");
                }
                return List.of("外滩", "陆家嘴", "武康路", "豫园");
            case "成都":
                if (food) {
                    return List.of("建设路小吃街", "宽窄巷子", "玉林路", "奎星楼街");
                }
                if (family) {
                    return List.of("成都大熊猫繁育研究基地", "人民公园", "四川科技馆", "锦里");
                }
                return List.of("成都大熊猫繁育研究基地", "武侯祠", "锦里", "人民公园");
            default:
                return List.of("城市地标景点", "当地博物馆", "特色街区", "代表性美食街");
        }
    }

    /** 给行程草稿生成晚间安排建议。 */
    String eveningSuggestion(String city, String preference) {
        String safePreference = preference == null ? "" : preference;
        if (safePreference.contains("美食")) {
            return "安排当地特色餐厅或夜市，节奏放松";
        }
        switch (city) {
            case "杭州":
                return "西湖或湖滨商圈散步";
            case "北京":
                return "什刹海或胡同区域散步";
            case "上海":
                return "外滩夜景或商圈轻松逛街";
            case "成都":
                return "茶馆、火锅或城市夜生活体验";
            default:
                return "选择酒店附近商圈用餐和休息";
        }
    }
}
