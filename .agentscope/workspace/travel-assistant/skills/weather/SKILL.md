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
