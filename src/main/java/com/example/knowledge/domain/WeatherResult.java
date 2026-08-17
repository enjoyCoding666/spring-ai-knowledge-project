package com.example.knowledge.domain;

/**
 * 天气工具返回给模型的结构化结果。
 *
 * <p>Function Calling 的工具结果应该尽量结构化。相比直接返回一段字符串，字段明确的对象
 * 更便于模型理解，也方便 Java 代码和测试验证每个字段的含义。
 */
public record WeatherResult(
        String city,
        String condition,
        Integer temperatureCelsius,
        boolean available,
        String message) {
}
