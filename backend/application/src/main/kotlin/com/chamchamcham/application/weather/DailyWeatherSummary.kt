package com.chamchamcham.application.weather

import java.time.LocalDate

data class DailyWeatherSummary(
    val date: LocalDate,
    val skyCondition: String,
    val minTemperature: Int,
    val maxTemperature: Int
)
