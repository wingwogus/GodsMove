package com.chamchamcham.application.weather

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 외부 날씨 API 장애(해외 VPN·기상청 장애 등) 시 서비스가 멈추지 않도록 반환하는 고정 폴백 값.
 *
 * 실측이 아니라 폴백임은 응답의 [PartialFailure.degraded]=true로 정직하게 표기하고, 흐름은 정상
 * 진행시킨다. 값 구성은 계획(vpn-humming-tulip)에서 확정: 흐림/20도 + 중립 수치.
 */
object WeatherFallback {
    val CONDITION = WeatherCondition.CLOUDY
    const val TEMPERATURE = 20
    const val FEELS_LIKE_TEMPERATURE = 20
    const val HUMIDITY = 50
    const val WIND_SPEED = 0.0
    const val PRECIPITATION_PROBABILITY = 0
    const val UV_INDEX = 0
    const val MIN_TEMPERATURE = 20
    const val MAX_TEMPERATURE = 20

    fun currentObservation(clock: Clock): CurrentObservation =
        CurrentObservation(
            temperature = TEMPERATURE,
            // 강수형태는 폴백에서 '강수 없음'(null)으로 두고, 하늘상태(CLOUDY)는 단기예보 폴백의
            // currentSky가 resolveCondition을 통해 채운다.
            precipitationType = null,
            observedAt = LocalDateTime.now(clock),
            humidity = HUMIDITY,
            windSpeed = WIND_SPEED,
            feelsLikeTemperature = FEELS_LIKE_TEMPERATURE
        )

    fun shortTermForecast(today: LocalDate): ShortTermForecast =
        ShortTermForecast(
            currentSky = CONDITION,
            precipitationProbability = PRECIPITATION_PROBABILITY,
            // buildForecast의 D0~D4를 모두 커버하도록 오늘부터 4일 뒤까지 채운다.
            dailyForecasts = (0L..4L).map { dailyForecast(today.plusDays(it)) }
        )

    fun dailyForecast(date: LocalDate): DailyForecast =
        DailyForecast(
            date = date,
            condition = CONDITION,
            minTemperature = MIN_TEMPERATURE,
            maxTemperature = MAX_TEMPERATURE
        )

    fun dailyWeather(date: LocalDate): DailyWeather =
        DailyWeather(
            date = date,
            condition = CONDITION,
            minTemperature = MIN_TEMPERATURE,
            maxTemperature = MAX_TEMPERATURE
        )
}
