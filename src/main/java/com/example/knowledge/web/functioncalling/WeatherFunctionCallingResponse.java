package com.example.knowledge.web.functioncalling;

import com.example.knowledge.domain.WeatherFunctionCallingResult;
import com.example.knowledge.domain.WeatherResult;
import com.example.knowledge.domain.WeatherToolArguments;

/**
 * 同时展示模型回答与 Java 工具执行轨迹的教学响应。
 */
public record WeatherFunctionCallingResponse(
        String answer,
        boolean toolCalled,
        String toolName,
        WeatherToolArguments toolArguments,
        WeatherResult toolResult) {

    /**
     * 将应用层结果转换成接口响应。
     */
    public static WeatherFunctionCallingResponse from(WeatherFunctionCallingResult result) {
        return new WeatherFunctionCallingResponse(
                result.answer(),
                result.toolCalled(),
                result.toolName(),
                result.toolArguments(),
                result.toolResult());
    }
}
