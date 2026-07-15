package com.chamchamcham.api.farm.dto

import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.DailyWeatherSummary
import com.chamchamcham.application.weather.FarmWeatherResult
import java.time.LocalDate
import java.time.LocalDateTime

object WeatherResponses {
    data class CurrentWeatherResponse(
        val temperature: Int,
        val weatherCondition: String,
        val observedAt: LocalDateTime,
        val address: String,
        val precipitationProbability: Int?,
        val forecast: List<ForecastDayResponse>,
        val humidity: Int?,
        val windSpeed: Double?,
        val uvIndex: Int?,
        val feelsLikeTemperature: Int?
    ) {
        companion object {
            fun from(result: FarmWeatherResult.CurrentDetail): CurrentWeatherResponse =
                CurrentWeatherResponse(
                    temperature = result.snapshot.temperature,
                    weatherCondition = result.snapshot.skyCondition,
                    observedAt = result.snapshot.observedAt,
                    address = result.roadAddress,
                    precipitationProbability = result.precipitationProbability,
                    forecast = result.forecast.map { ForecastDayResponse.from(it) },
                    humidity = result.snapshot.humidity,
                    windSpeed = result.snapshot.windSpeed,
                    uvIndex = result.uvIndex,
                    feelsLikeTemperature = result.snapshot.feelsLikeTemperature
                )
        }
    }

    data class ForecastDayResponse(
        val date: LocalDate,
        val weatherCondition: String?,
        val minTemperature: Int?,
        val maxTemperature: Int?
    ) {
        companion object {
            fun from(forecast: DailyForecast): ForecastDayResponse =
                ForecastDayResponse(
                    date = forecast.date,
                    weatherCondition = forecast.skyCondition,
                    minTemperature = forecast.minTemperature,
                    maxTemperature = forecast.maxTemperature
                )
        }
    }

    data class DailyWeatherResponse(
        val date: LocalDate,
        val weatherCondition: String,
        val minTemperature: Int,
        val maxTemperature: Int
    ) {
        companion object {
            fun from(summary: DailyWeatherSummary): DailyWeatherResponse =
                DailyWeatherResponse(
                    date = summary.date,
                    weatherCondition = summary.skyCondition,
                    minTemperature = summary.minTemperature,
                    maxTemperature = summary.maxTemperature
                )
        }
    }
}
