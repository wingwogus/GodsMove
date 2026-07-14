package com.chamchamcham.application.weather

import java.time.LocalDateTime

data class WeatherSnapshot(
    val temperature: Int,
    val skyCondition: String,
    val observedAt: LocalDateTime,
    val humidity: Int? = null,
    val windSpeed: Double? = null
)
