package com.chamchamcham.application.weather

import java.time.LocalDate

object FarmWeatherResult {
    data class CurrentDetail(
        val snapshot: WeatherSnapshot,
        val roadAddress: String,
        val precipitationProbability: Int?,
        val forecast: List<DailyForecast>,
        val uvIndex: Int?,
        val minTemperature: Int?,
        val maxTemperature: Int?
    )
}

data class DailyForecast(
    val date: LocalDate,
    val minTemperature: Int?,
    val maxTemperature: Int?,
    val skyCondition: String?
)
