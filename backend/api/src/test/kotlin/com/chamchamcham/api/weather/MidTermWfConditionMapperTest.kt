package com.chamchamcham.api.weather

import com.chamchamcham.application.weather.WeatherCondition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource

class MidTermWfConditionMapperTest {

    @ParameterizedTest
    @CsvSource(
        "맑음, CLEAR",
        "구름많음, PARTLY_CLOUDY",
        "흐림, CLOUDY",
        "구름많고 비, RAIN",
        "구름많고 눈, SNOW",
        "구름많고 비/눈, RAIN_SNOW",
        "구름많고 소나기, SHOWER",
        "흐리고 비, RAIN",
        "흐리고 눈, SNOW",
        "흐리고 비/눈, RAIN_SNOW",
        "흐리고 소나기, SHOWER"
    )
    fun `기상청 어휘 11개를 판정한다`(wf: String, expected: WeatherCondition) {
        val result = MidTermWfConditionMapper.of(wf)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `흐림이 흐리 부분매칭 실패 없이 흐림으로 판정된다`() {
        val result = MidTermWfConditionMapper.of("흐림")

        assertThat(result).isEqualTo(WeatherCondition.CLOUDY)
    }

    @Test
    fun `흐리고 비 눈은 강수 표현이 우선해 비 눈으로 판정된다`() {
        val result = MidTermWfConditionMapper.of("흐리고 비/눈")

        assertThat(result).isEqualTo(WeatherCondition.RAIN_SNOW)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `null이거나 빈 문자열이면 정보없음을 반환한다`(wf: String?) {
        val result = MidTermWfConditionMapper.of(wf)

        assertThat(result).isEqualTo(WeatherCondition.UNKNOWN)
    }
}
