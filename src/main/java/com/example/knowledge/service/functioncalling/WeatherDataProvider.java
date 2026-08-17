package com.example.knowledge.service.functioncalling;

import com.example.knowledge.domain.WeatherResult;
import java.util.Map;

public class WeatherDataProvider {

    private static final String UNAVAILABLE_MESSAGE = "暂无该城市的模拟天气数据";
    private static final Map<String, WeatherResult> WEATHER_BY_CITY = Map.of(
            "广东", new WeatherResult("广东", "晴", 26, true, "模拟天气查询成功"),
            "上海", new WeatherResult("上海", "多云", 28, true, "模拟天气查询成功"),
            "深圳", new WeatherResult("深圳", "阵雨", 30, true, "模拟天气查询成功"));

    /**
     * 查询本地模拟天气。
     *
     * <p>这里故意不调用真实天气服务，让示例专注于模型如何选择并调用 Java 工具。未知城市
     * 不生成虚假温度，而是返回明确的不可用状态。
     */
    public WeatherResult findByCity(String city) {
        String normalizedCity = city == null ? "" : city.trim();
        WeatherResult result = WEATHER_BY_CITY.get(normalizedCity);
        if (result != null) {
            return result;
        }
        return new WeatherResult(normalizedCity, null, null, false, UNAVAILABLE_MESSAGE);
    }
}
