package com.chamchamcham.api.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeelsLikeTemperatureCalculatorTest {

    @Test
    fun `여름철에는 열지수 공식으로 체감온도를 계산한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 30,
            humidity = 50,
            windSpeedMps = null,
            month = 7
        )

        assertThat(result).isEqualTo(30)
    }

    @Test
    fun `겨울철 적용조건을 만족하면 풍속냉각 공식으로 체감온도를 계산한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 0,
            humidity = null,
            windSpeedMps = 10.0,
            month = 1
        )

        assertThat(result).isEqualTo(-7)
    }

    @Test
    fun `겨울철 기온이 10도를 초과하면 공식을 적용하지 않고 원본 기온을 반환한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 15,
            humidity = null,
            windSpeedMps = 10.0,
            month = 12
        )

        assertThat(result).isEqualTo(15)
    }

    @Test
    fun `겨울철 풍속이 1_3 미만이면 공식을 적용하지 않고 원본 기온을 반환한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 0,
            humidity = null,
            windSpeedMps = 1.0,
            month = 2
        )

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `여름철에 습도가 없으면 null을 반환한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 30,
            humidity = null,
            windSpeedMps = null,
            month = 8
        )

        assertThat(result).isNull()
    }

    @Test
    fun `겨울철에 풍속이 없으면 null을 반환한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 0,
            humidity = null,
            windSpeedMps = null,
            month = 10
        )

        assertThat(result).isNull()
    }

    @Test
    fun `겨울철 기온이 정확히 10도이면 조건을 만족해 공식을 적용한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 10,
            humidity = null,
            windSpeedMps = 10.0,
            month = 11
        )

        assertThat(result).isEqualTo(6)
    }

    @Test
    fun `겨울철 풍속이 정확히 1_3이면 조건을 만족해 공식을 적용한다`() {
        val result = FeelsLikeTemperatureCalculator.of(
            temperature = 0,
            humidity = null,
            windSpeedMps = 1.3,
            month = 3
        )

        assertThat(result).isEqualTo(-1)
    }
}
