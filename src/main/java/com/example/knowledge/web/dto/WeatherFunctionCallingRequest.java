package com.example.knowledge.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Function Calling 天气示例请求。
 */
public record WeatherFunctionCallingRequest(
        @NotBlank(message = "Message must not be blank") String message) {
}
