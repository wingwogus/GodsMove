package com.chamchamcham.application.weather

import java.time.LocalDateTime
import java.util.UUID

/**
 * 홈 화면 유스케이스 결과.
 */
data class HomeWeather(
    val farmId: UUID,
    val temperature: Int,
    val condition: WeatherCondition,
    val minTemperature: Int?,
    val maxTemperature: Int?,
    val observedAt: LocalDateTime,
    val partial: PartialFailure
)

/**
 * 상세 화면 유스케이스 결과.
 */
data class DetailWeather(
    val farmId: UUID,
    val address: String,
    val observedAt: LocalDateTime,
    val temperature: Int,
    val feelsLikeTemperature: Int?,
    val condition: WeatherCondition,
    val minTemperature: Int?,
    val maxTemperature: Int?,
    val humidity: Int?,
    val windSpeed: Double?,
    val precipitationProbability: Int?,
    val uvIndex: Int?,
    val forecast: List<DailyForecast>,
    val partial: PartialFailure
)
