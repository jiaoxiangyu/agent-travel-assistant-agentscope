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
