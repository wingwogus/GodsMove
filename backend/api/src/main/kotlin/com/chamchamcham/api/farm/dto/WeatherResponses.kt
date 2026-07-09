package com.chamchamcham.api.farm.dto

import com.chamchamcham.application.weather.WeatherSnapshot
import java.time.LocalDateTime

object WeatherResponses {
    data class CurrentWeatherResponse(
        val temperature: Int,
        val weatherCondition: String,
        val observedAt: LocalDateTime
    ) {
        companion object {
            fun from(snapshot: WeatherSnapshot): CurrentWeatherResponse =
                CurrentWeatherResponse(
                    temperature = snapshot.temperature,
                    weatherCondition = snapshot.skyCondition,
                    observedAt = snapshot.observedAt
                )
        }
    }
}
