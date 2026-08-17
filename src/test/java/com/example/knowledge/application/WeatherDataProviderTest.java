package com.example.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knowledge.domain.WeatherResult;
import org.junit.jupiter.api.Test;

class WeatherDataProviderTest {

    private final WeatherDataProvider weatherDataProvider = new WeatherDataProvider();

    @Test
    void shouldReturnWeatherForKnownCity() {
        WeatherResult result = weatherDataProvider.findByCity("广东");

        assertThat(result.available()).isTrue();
        assertThat(result.city()).isEqualTo("广东");
        assertThat(result.condition()).isEqualTo("晴");
        assertThat(result.temperatureCelsius()).isEqualTo(26);
    }

    @Test
    void shouldNotInventWeatherForUnknownCity() {
        WeatherResult result = weatherDataProvider.findByCity("未知城市");

        assertThat(result.available()).isFalse();
        assertThat(result.city()).isEqualTo("未知城市");
        assertThat(result.condition()).isNull();
        assertThat(result.temperatureCelsius()).isNull();
        assertThat(result.message()).isEqualTo("暂无该城市的模拟天气数据");
    }
}
