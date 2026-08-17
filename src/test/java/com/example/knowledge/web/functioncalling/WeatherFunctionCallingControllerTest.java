package com.example.knowledge.web.functioncalling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.knowledge.service.functioncalling.WeatherFunctionCallingUseCase;
import com.example.knowledge.domain.WeatherFunctionCallingResult;
import com.example.knowledge.domain.WeatherResult;
import com.example.knowledge.domain.WeatherToolArguments;
import com.example.knowledge.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

class WeatherFunctionCallingControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WeatherResult weather = new WeatherResult(
                "广东", "晴", 26, true, "模拟天气查询成功");
        WeatherFunctionCallingUseCase useCase = message -> new WeatherFunctionCallingResult(
                "广东今天晴，温度为 26℃。",
                true,
                "getWeather",
                new WeatherToolArguments("广东"),
                weather);
        WeatherFunctionCallingController controller = new WeatherFunctionCallingController();
        ReflectionTestUtils.setField(controller, "weatherFunctionCallingUseCase", useCase);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnAnswerAndToolTrace() throws Exception {
        mockMvc.perform(post("/api/ollama/function-calling/weather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"广东今天天气怎么样？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.answer").value("广东今天晴，温度为 26℃。"))
                .andExpect(jsonPath("$.data.toolCalled").value(true))
                .andExpect(jsonPath("$.data.toolName").value("getWeather"))
                .andExpect(jsonPath("$.data.toolArguments.city").value("广东"))
                .andExpect(jsonPath("$.data.toolResult.temperatureCelsius").value(26));
    }

    @Test
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/ollama/function-calling/weather")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Message must not be blank"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
