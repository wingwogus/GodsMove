package com.chamchamcham.application.weather

data class WeatherForecast(
    val precipitationProbability: Int?,
    val dailyForecasts: List<DailyForecast>
)
