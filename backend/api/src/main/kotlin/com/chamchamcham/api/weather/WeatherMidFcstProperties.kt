package com.chamchamcham.api.weather

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "weather.mid-fcst")
data class WeatherMidFcstProperties(
    val baseUrl: String,
    val serviceKey: String,
    val connectTimeoutMillis: Int = 2000,
    val readTimeoutMillis: Int = 2000
)
