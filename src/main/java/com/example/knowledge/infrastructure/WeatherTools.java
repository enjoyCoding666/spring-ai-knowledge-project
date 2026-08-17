package com.example.knowledge.infrastructure;

import com.example.knowledge.application.WeatherDataProvider;
import com.example.knowledge.domain.WeatherResult;
import com.example.knowledge.domain.WeatherToolArguments;
import com.example.knowledge.domain.WeatherToolInvocation;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WeatherTools {

    private static final String TOOL_NAME = "getWeather";

    private final WeatherDataProvider weatherDataProvider;
    private WeatherToolInvocation lastInvocation;

    public WeatherTools(WeatherDataProvider weatherDataProvider) {
        this.weatherDataProvider = weatherDataProvider;
    }

    /**
     * 提供给 Qwen 的天气查询工具。
     *
     * <p>{@link Tool} 的描述会连同参数结构一起发送给模型。Qwen 只能返回“调用
     * getWeather，并传入某个 city”的请求，无法直接执行这个 Java 方法。Spring AI 收到
     * 工具调用请求后，才会在当前应用进程中调用本方法，并把返回值再次交给模型生成回答。
     */
    @Tool(
            name = TOOL_NAME,
            description = "查询指定中国城市的本地模拟天气。用户询问天气、温度或是否适合出行时调用。")
    public WeatherResult getWeather(
            @ToolParam(description = "需要查询天气的中国城市或省份名称，例如广东、上海或深圳")
            String city) {
        WeatherResult result = weatherDataProvider.findByCity(city);

        // 工具对象按请求创建，因此这里记录轨迹不会与其他请求共享可变状态。
        lastInvocation = new WeatherToolInvocation(
                TOOL_NAME,
                new WeatherToolArguments(city),
                result);
        return result;
    }

    /**
     * 返回本次请求中最近一次工具调用轨迹；模型未调用工具时为 null。
     */
    public WeatherToolInvocation lastInvocation() {
        return lastInvocation;
    }
}
