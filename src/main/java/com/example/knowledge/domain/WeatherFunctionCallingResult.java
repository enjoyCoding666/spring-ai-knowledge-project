package com.example.knowledge.domain;

/**
 * Function Calling 教学接口的完整结果。
 *
 * <p>answer 是模型最终生成的自然语言；其余字段展示 Java 工具执行阶段。把两部分同时返回，
 * 可以直观看出“模型生成回答”和“应用执行工具”是两个不同职责。
 */
public record WeatherFunctionCallingResult(
        String answer,
        boolean toolCalled,
        String toolName,
        WeatherToolArguments toolArguments,
        WeatherResult toolResult) {
}
