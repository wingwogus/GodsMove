package com.chamchamcham.application.weather

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class WeatherFlagsTest {

    private fun detail(
        temperature: Int = 20,
        minTemperature: Int? = 18,
        maxTemperature: Int? = 22,
        humidity: Int? = 60,
        windSpeed: Double? = 2.0,
        precipitationProbability: Int? = 10,
        uvIndex: Int? = 3,
        condition: WeatherCondition = WeatherCondition.CLEAR
    ): DetailWeather = DetailWeather(
        farmId = UUID.randomUUID(),
        address = "서울 중구 세종대로 110",
        observedAt = LocalDateTime.of(2026, 7, 15, 21, 0),
        temperature = temperature,
        feelsLikeTemperature = temperature,
        condition = condition,
        minTemperature = minTemperature,
        maxTemperature = maxTemperature,
        humidity = humidity,
        windSpeed = windSpeed,
        precipitationProbability = precipitationProbability,
        uvIndex = uvIndex,
        forecast = emptyList(),
        partial = PartialFailure.of()
    )

    @Test
    fun `heatWave는 maxTemperature 33 이상에서만 true`() {
        assertThat(WeatherFlags.from(detail(maxTemperature = 32)).heatWave).isFalse()
        assertThat(WeatherFlags.from(detail(maxTemperature = 33)).heatWave).isTrue()
    }

    @Test
    fun `frost는 minTemperature 4 이하에서만 true`() {
        assertThat(WeatherFlags.from(detail(minTemperature = 5)).frost).isFalse()
        assertThat(WeatherFlags.from(detail(minTemperature = 4)).frost).isTrue()
    }

    @Test
    fun `rainLikely는 강수확률 60 이상에서만 true`() {
        assertThat(WeatherFlags.from(detail(precipitationProbability = 59)).rainLikely).isFalse()
        assertThat(WeatherFlags.from(detail(precipitationProbability = 60)).rainLikely).isTrue()
    }

    @Test
    fun `humid는 습도 80 이상에서만 true`() {
        assertThat(WeatherFlags.from(detail(humidity = 79)).humid).isFalse()
        assertThat(WeatherFlags.from(detail(humidity = 80)).humid).isTrue()
    }

    @Test
    fun `dry는 습도 40 이하에서만 true`() {
        assertThat(WeatherFlags.from(detail(humidity = 41)).dry).isFalse()
        assertThat(WeatherFlags.from(detail(humidity = 40)).dry).isTrue()
    }

    @Test
    fun `windy는 풍속 7_0 이상에서만 true`() {
        assertThat(WeatherFlags.from(detail(windSpeed = 6.9)).windy).isFalse()
        assertThat(WeatherFlags.from(detail(windSpeed = 7.0)).windy).isTrue()
    }

    @Test
    fun `highUv는 자외선지수 8 이상에서만 true`() {
        assertThat(WeatherFlags.from(detail(uvIndex = 7)).highUv).isFalse()
        assertThat(WeatherFlags.from(detail(uvIndex = 8)).highUv).isTrue()
    }

    @Test
    fun `mild는 온도 15~25 범위에서만 true`() {
        assertThat(WeatherFlags.from(detail(temperature = 14)).mild).isFalse()
        assertThat(WeatherFlags.from(detail(temperature = 15)).mild).isTrue()
        assertThat(WeatherFlags.from(detail(temperature = 25)).mild).isTrue()
        assertThat(WeatherFlags.from(detail(temperature = 26)).mild).isFalse()
    }

    @Test
    fun `모든 nullable 필드가 null이어도 NPE 없이 계산되고 dry는 false`() {
        val flags = WeatherFlags.from(
            detail(
                minTemperature = null,
                maxTemperature = null,
                humidity = null,
                windSpeed = null,
                precipitationProbability = null,
                uvIndex = null
            )
        )

        assertThat(flags.dry).isFalse()
        assertThat(flags.rainLikely).isFalse()
        assertThat(flags.humid).isFalse()
        assertThat(flags.windy).isFalse()
        assertThat(flags.highUv).isFalse()
    }

    @Test
    fun `maxTemperature가 null이면 temperature로 heatWave를 판정한다`() {
        val flags = WeatherFlags.from(detail(temperature = 35, maxTemperature = null))

        assertThat(flags.heatWave).isTrue()
    }

    @Test
    fun `minTemperature가 null이면 temperature로 frost를 판정한다`() {
        val flags = WeatherFlags.from(detail(temperature = 2, minTemperature = null))

        assertThat(flags.frost).isTrue()
    }

    @Test
    fun `강수 계열 condition은 모두 raining true`() {
        val precipitationConditions = setOf(
            WeatherCondition.RAIN, WeatherCondition.RAIN_SNOW, WeatherCondition.SNOW,
            WeatherCondition.SHOWER, WeatherCondition.DRIZZLE,
            WeatherCondition.DRIZZLE_SNOW, WeatherCondition.SNOW_FLURRY
        )

        precipitationConditions.forEach { condition ->
            val flags = WeatherFlags.from(detail(condition = condition))
            assertThat(flags.raining).isTrue()
            assertThat(flags.clearSky).isFalse()
        }
    }

    @Test
    fun `CLEAR와 PARTLY_CLOUDY는 clearSky true이고 raining false`() {
        setOf(WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY).forEach { condition ->
            val flags = WeatherFlags.from(detail(condition = condition))
            assertThat(flags.clearSky).isTrue()
            assertThat(flags.raining).isFalse()
        }
    }

    @Test
    fun `CLOUDY와 UNKNOWN은 raining과 clearSky 모두 false`() {
        setOf(WeatherCondition.CLOUDY, WeatherCondition.UNKNOWN).forEach { condition ->
            val flags = WeatherFlags.from(detail(condition = condition))
            assertThat(flags.raining).isFalse()
            assertThat(flags.clearSky).isFalse()
        }
    }
}
