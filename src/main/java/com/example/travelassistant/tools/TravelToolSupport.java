package com.example.travelassistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TravelToolSupport {

    private final RestClient restClient = RestClient.create();

    @Resource
    private ObjectMapper objectMapper;

    JsonNode getJson(URI uri) {
        String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse travel API response", e);
        }
    }

    String toJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize travel tool result", e);
        }
    }

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

    String weatherText(int code) {
        switch (code) {
            case 0:
                return "晴天";
            case 1:
                return "大部晴朗";
            case 2:
                return "局部多云";
            case 3:
                return "阴天";
            case 45:
            case 48:
                return "雾";
            case 51:
                return "小毛毛雨";
            case 53:
                return "中等毛毛雨";
            case 55:
                return "大毛毛雨";
            case 56:
                return "轻微冻毛毛雨";
            case 57:
                return "强冻毛毛雨";
            case 61:
                return "小雨";
            case 63:
                return "中雨";
            case 65:
                return "大雨";
            case 66:
                return "轻微冻雨";
            case 67:
                return "强冻雨";
            case 71:
                return "小雪";
            case 73:
                return "中雪";
            case 75:
                return "大雪";
            case 77:
                return "雪粒";
            case 80:
                return "小阵雨";
            case 81:
                return "中等阵雨";
            case 82:
                return "强阵雨";
            case 85:
                return "小阵雪";
            case 86:
                return "强阵雪";
            case 95:
                return "雷暴";
            case 96:
                return "雷暴伴小冰雹";
            case 99:
                return "雷暴伴大冰雹";
            default:
                return "未知天气";
        }
    }
}
