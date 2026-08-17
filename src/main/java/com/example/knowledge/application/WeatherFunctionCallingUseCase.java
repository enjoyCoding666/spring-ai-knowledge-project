package com.example.knowledge.application;

import com.example.knowledge.domain.WeatherFunctionCallingResult;

@FunctionalInterface
public interface WeatherFunctionCallingUseCase {

    /**
     * 使用 Ollama Function Calling 处理一条消息。
     */
    WeatherFunctionCallingResult chat(String message);
}
