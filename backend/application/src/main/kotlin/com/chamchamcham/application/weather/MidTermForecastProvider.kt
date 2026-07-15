package com.chamchamcham.application.weather

import java.time.LocalDate

fun interface MidTermForecastProvider {
    fun fetchDayForecast(latitude: Double, longitude: Double, date: LocalDate): DailyForecast?
}
