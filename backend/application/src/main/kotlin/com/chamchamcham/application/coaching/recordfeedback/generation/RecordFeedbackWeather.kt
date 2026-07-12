package com.chamchamcham.application.coaching.recordfeedback.generation

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class RecordFeedbackLiveWeather(
    val current: RecordFeedbackCurrentWeather,
    val forecastDays: List<RecordFeedbackForecastDay>,
    val source: String,
)

data class RecordFeedbackCurrentWeather(
    val temperatureC: Int,
    val skyCondition: String,
    val observedAt: LocalDateTime,
)

data class RecordFeedbackForecastDay(
    val date: LocalDate,
    val rainfallMm: BigDecimal?,
    val rainProbabilityPct: Int?,
    val maxTemperatureC: BigDecimal?,
    val minTemperatureC: BigDecimal?,
    val humidityPct: BigDecimal?,
    val windSpeedMs: BigDecimal?,
    val riskFlags: List<String>,
)
