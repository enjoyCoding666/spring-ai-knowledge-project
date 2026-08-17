package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.WeatherFunctionCallingResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

class WeatherFunctionCallingServiceTest {

    @Test
    void shouldExecuteWeatherToolAndReturnObservableTrace() {
        WeatherToolCallingChatModel chatModel = new WeatherToolCallingChatModel();
        WeatherFunctionCallingService service = new WeatherFunctionCallingService(
                ChatClient.builder(chatModel).build(),
                new WeatherDataProvider());

        WeatherFunctionCallingResult result = service.chat("广东今天天气怎么样？");

        assertThat(chatModel.callCount).isEqualTo(2);
        assertThat(chatModel.receivedToolResponse).isTrue();
        assertThat(result.answer()).isEqualTo("广东今天晴，温度为 26℃。");
        assertThat(result.toolCalled()).isTrue();
        assertThat(result.toolName()).isEqualTo("getWeather");
        assertThat(result.toolArguments().city()).isEqualTo("广东");
        assertThat(result.toolResult().temperatureCelsius()).isEqualTo(26);
    }

    private static final class WeatherToolCallingChatModel implements ChatModel {

        private int callCount;
        private boolean receivedToolResponse;

        @Override
        public ChatOptions getOptions() {
            // OllamaChatOptions 同样实现 ToolCallingChatOptions；测试替身必须声明此能力。
            return DefaultToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount++;
            if (callCount == 1) {
                AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                        "weather-call-1",
                        "function",
                        "getWeather",
                        "{\"city\":\"广东\"}");
                AssistantMessage message = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall))
                        .build();
                return new ChatResponse(List.of(new Generation(message)));
            }

            receivedToolResponse = prompt.getInstructions().stream()
                    .anyMatch(ToolResponseMessage.class::isInstance);
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage("广东今天晴，温度为 26℃。"))));
        }
    }
}
