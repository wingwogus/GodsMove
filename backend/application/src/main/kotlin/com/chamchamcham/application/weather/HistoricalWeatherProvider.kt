package com.chamchamcham.application.weather

import java.time.LocalDate

interface HistoricalWeatherProvider {
    fun fetchDailySummary(latitude: Double, longitude: Double, date: LocalDate): DailyWeatherSummary?
}
