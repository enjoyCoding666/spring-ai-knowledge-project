package com.example.knowledge.service.functioncalling;

import com.example.knowledge.config.AiBeanNames;
import com.example.knowledge.domain.WeatherFunctionCallingResult;
import com.example.knowledge.domain.WeatherToolInvocation;
import com.example.knowledge.infrastructure.WeatherTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WeatherFunctionCallingService implements WeatherFunctionCallingUseCase {

    private static final String SYSTEM_PROMPT = """
            你是一个天气助手。
            回答天气、温度或出行相关问题前，必须调用提供的天气工具获取数据。
            不要自行编造天气；工具提示没有数据时，应如实说明暂无模拟数据。
            非天气问题可以直接回答，不需要调用天气工具。
            """;

    @Autowired
    @Qualifier(AiBeanNames.OLLAMA_CHAT_CLIENT)
    private ChatClient chatClient;

    @Autowired
    private ObjectProvider<WeatherTools> weatherToolsProvider;

    /**
     * 让 Qwen 判断是否需要调用天气工具，并返回最终回答及工具执行轨迹。
     */
    @Override
    public WeatherFunctionCallingResult chat(String message) {
        // 每次请求创建独立工具对象，使 lastInvocation 只属于当前请求。
        WeatherTools weatherTools = weatherToolsProvider.getObject();

        /*
         * tools(weatherTools) 不会立即执行工具。Spring AI 会先读取 @Tool 元数据，
         * 将工具名称、描述和参数 JSON Schema 发给 Qwen。只有 Qwen 返回 Tool Call 后，
         * Spring AI 的工具调用编排器才反射调用 WeatherTools.getWeather，并把结果再次交给 Qwen。
         */
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(weatherTools)
                .call()
                .content();

        WeatherToolInvocation invocation = weatherTools.lastInvocation();
        if (invocation == null) {
            return new WeatherFunctionCallingResult(answer, false, null, null, null);
        }
        return new WeatherFunctionCallingResult(
                answer,
                true,
                invocation.toolName(),
                invocation.arguments(),
                invocation.result());
    }
}
