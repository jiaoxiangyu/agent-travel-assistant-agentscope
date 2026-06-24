package com.example.travelassistant.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 城市旅游画像工具，为 Agent 提供目的地的基础旅行建议。 */
@Component
public class CityProfileTools {

    @Resource
    private TravelToolSupport support;

    @Tool(
            name = "get_city_profile",
            description = "查询城市旅游画像，包括适合天数、旅行风格、交通建议、消费水平和避坑提示。",
            readOnly = true,
            concurrencySafe = true)
    public String getCityProfile(
            @ToolParam(name = "city", description = "目的地城市中文名") String city) {
        // 当前使用内置城市画像；未知城市返回通用建议，保证 Agent 始终有结构化参考。
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("city", city);

        switch (city) {
            case "杭州":
                profile.put("recommendedDays", "2-4天");
                profile.put("style", "湖景、人文、茶文化、轻松休闲");
                profile.put("transport", "市区地铁和打车方便，西湖周边更适合步行或骑行");
                profile.put("costLevel", "中等");
                profile.put("tips", List.of("西湖热门区域节假日人流大", "灵隐寺和西溪湿地建议错峰", "龙井村适合安排半天慢游"));
                break;
            case "北京":
                profile.put("recommendedDays", "4-6天");
                profile.put("style", "历史人文、博物馆、城市地标、胡同体验");
                profile.put("transport", "地铁覆盖广，热门景区安检和预约耗时较长");
                profile.put("costLevel", "中等偏高");
                profile.put("tips", List.of("故宫、国家博物馆等建议提前预约", "行程不要排太满", "冬季注意防风保暖"));
                break;
            case "上海":
                profile.put("recommendedDays", "2-4天");
                profile.put("style", "都市观光、建筑、美食、购物、亲子乐园");
                profile.put("transport", "地铁便利，跨江通勤需预留时间");
                profile.put("costLevel", "偏高");
                profile.put("tips", List.of("外滩夜景适合晚间安排", "迪士尼建议单独留一整天", "热门餐厅最好提前排队或预约"));
                break;
            case "成都":
                profile.put("recommendedDays", "3-5天");
                profile.put("style", "美食、熊猫、慢生活、川西中转");
                profile.put("transport", "市区地铁方便，美食点位分散时可打车");
                profile.put("costLevel", "中等");
                profile.put("tips", List.of("火锅和川菜口味较重", "熊猫基地建议早到", "可预留一天给都江堰或青城山"));
                break;
            default:
                profile.put("recommendedDays", "2-4天");
                profile.put("style", "城市观光、当地美食、代表性景点");
                profile.put("transport", "建议优先使用公共交通，跨区域行程预留通勤时间");
                profile.put("costLevel", "中等");
                profile.put("tips", List.of("热门景点建议提前预约", "根据天气调整室外活动", "每天保留机动时间"));
                break;
        }
        return support.toJson(profile);
    }
}
