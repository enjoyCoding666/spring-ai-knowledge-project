package com.example.knowledge.web;

import com.example.knowledge.application.WeatherFunctionCallingUseCase;
import com.example.knowledge.domain.WeatherFunctionCallingResult;
import com.example.knowledge.web.dto.ApiResponse;
import com.example.knowledge.web.dto.WeatherFunctionCallingRequest;
import com.example.knowledge.web.dto.WeatherFunctionCallingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ollama/function-calling")
public class WeatherFunctionCallingController {

    private final WeatherFunctionCallingUseCase weatherFunctionCallingUseCase;

    public WeatherFunctionCallingController(
            WeatherFunctionCallingUseCase weatherFunctionCallingUseCase) {
        this.weatherFunctionCallingUseCase = weatherFunctionCallingUseCase;
    }

    /**
     * 演示 Qwen 如何选择并调用本地 Java 天气工具。
     */
    @PostMapping("/weather")
    public ApiResponse<WeatherFunctionCallingResponse> weather(
            @Valid @RequestBody WeatherFunctionCallingRequest request) {
        WeatherFunctionCallingResult result = weatherFunctionCallingUseCase.chat(request.message());
        return ApiResponse.success(WeatherFunctionCallingResponse.from(result));
    }
}
