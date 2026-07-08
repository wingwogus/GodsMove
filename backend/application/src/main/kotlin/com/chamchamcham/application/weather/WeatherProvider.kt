package com.chamchamcham.application.weather

interface WeatherProvider {
    fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherSnapshot
}
