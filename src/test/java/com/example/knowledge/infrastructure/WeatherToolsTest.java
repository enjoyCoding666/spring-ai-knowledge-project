package com.example.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.application.WeatherDataProvider;
import com.example.knowledge.domain.WeatherResult;
import org.junit.jupiter.api.Test;

class WeatherToolsTest {

    @Test
    void shouldRecordToolArgumentsAndResult() {
        WeatherTools weatherTools = new WeatherTools(new WeatherDataProvider());

        WeatherResult result = weatherTools.getWeather("广东");

        assertThat(result.available()).isTrue();
        assertThat(weatherTools.lastInvocation()).isNotNull();
        assertThat(weatherTools.lastInvocation().toolName()).isEqualTo("getWeather");
        assertThat(weatherTools.lastInvocation().arguments().city()).isEqualTo("广东");
        assertThat(weatherTools.lastInvocation().result()).isEqualTo(result);
    }
}
