package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class SkyPtyConditionMapperTest {

    @ParameterizedTest
    @CsvSource(
        "1, RAIN",
        "2, RAIN_SNOW",
        "3, SNOW",
        "4, SHOWER",
        "5, DRIZZLE",
        "6, DRIZZLE_SNOW",
        "7, SNOW_FLURRY"
    )
    fun `PTY가 1부터 7까지면 PTY로 판정한다`(pty: String, expected: WeatherCondition) {
        val result = SkyPtyConditionMapper.of(sky = "1", pty = pty)

        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "1, CLEAR",
        "3, PARTLY_CLOUDY",
        "4, CLOUDY"
    )
    fun `PTY가 0이면 SKY로 판정한다`(sky: String, expected: WeatherCondition) {
        val result = SkyPtyConditionMapper.of(sky = sky, pty = "0")

        assertThat(result).isEqualTo(expected)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `PTY가 null이거나 빈 문자열이면 SKY로 판정한다`(pty: String?) {
        val result = SkyPtyConditionMapper.of(sky = "1", pty = pty)

        assertThat(result).isEqualTo(WeatherCondition.CLEAR)
    }

    @Test
    fun `PTY가 강수 중이면 SKY보다 우선한다`() {
        val result = SkyPtyConditionMapper.of(sky = "1", pty = "1")

        assertThat(result).isEqualTo(WeatherCondition.RAIN)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `SKY가 null이거나 빈 문자열이고 PTY도 없으면 정보없음을 반환한다`(sky: String?) {
        val result = SkyPtyConditionMapper.of(sky = sky, pty = null)

        assertThat(result).isEqualTo(WeatherCondition.UNKNOWN)
    }

    @ParameterizedTest
    @ValueSource(strings = ["2", "9", "abc"])
    fun `알 수 없는 SKY 코드면 정보없음을 반환한다`(sky: String) {
        val result = SkyPtyConditionMapper.of(sky = sky, pty = null)

        assertThat(result).isEqualTo(WeatherCondition.UNKNOWN)
    }

    @Test
    fun `알 수 없는 PTY 코드면 SKY로 판정한다`() {
        val result = SkyPtyConditionMapper.of(sky = "1", pty = "abc")

        assertThat(result).isEqualTo(WeatherCondition.CLEAR)
    }
}
