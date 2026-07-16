package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AsosConditionMapperTest {

    @Test
    fun `일강수량이 0보다 크면 전운량과 무관하게 비를 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = 0.0, sumRn = 0.1)

        assertThat(result).isEqualTo(WeatherCondition.RAIN)
    }

    @Test
    fun `일강수량이 0이면 강수로 취급하지 않는다`() {
        val result = AsosConditionMapper.of(avgTca = 1.0, sumRn = 0.0)

        assertThat(result).isEqualTo(WeatherCondition.CLEAR)
    }

    @Test
    fun `일강수량이 null이면 강수로 취급하지 않는다`() {
        val result = AsosConditionMapper.of(avgTca = 1.0, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.CLEAR)
    }

    @Test
    fun `평균 전운량이 null이면 정보없음을 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = null, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.UNKNOWN)
    }

    @Test
    fun `평균 전운량이 2 point 0이면 맑음을 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = 2.0, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.CLEAR)
    }

    @Test
    fun `평균 전운량이 2 point 1이면 구름많음을 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = 2.1, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
    }

    @Test
    fun `평균 전운량이 7 point 0이면 구름많음을 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = 7.0, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
    }

    @Test
    fun `평균 전운량이 7 point 1이면 흐림을 반환한다`() {
        val result = AsosConditionMapper.of(avgTca = 7.1, sumRn = null)

        assertThat(result).isEqualTo(WeatherCondition.CLOUDY)
    }
}
