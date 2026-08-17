package com.example.knowledge.domain;

/**
 * 一次天气工具调用的可观察轨迹。
 */
public record WeatherToolInvocation(
        String toolName,
        WeatherToolArguments arguments,
        WeatherResult result) {
}
