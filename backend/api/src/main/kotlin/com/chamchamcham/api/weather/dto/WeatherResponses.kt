package com.chamchamcham.api.weather.dto

import com.chamchamcham.application.weather.AdviceLevel
import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.DailyWeather
import com.chamchamcham.application.weather.DetailWeather
import com.chamchamcham.application.weather.FarmingAdvice
import com.chamchamcham.application.weather.HomeWeather
import com.chamchamcham.application.weather.PartialFailure
import com.chamchamcham.application.weather.WeatherCondition
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object WeatherResponses {
    data class ConditionResponse(
        val code: String,
        val text: String
    ) {
        companion object {
            fun from(condition: WeatherCondition): ConditionResponse =
                ConditionResponse(code = condition.name, text = condition.text)
        }
    }

    data class PartialResponse(
        val degraded: Boolean,
        val missing: List<String>
    ) {
        companion object {
            fun from(partial: PartialFailure): PartialResponse =
                PartialResponse(degraded = partial.degraded, missing = partial.missing)
        }
    }

    data class ForecastResponse(
        val date: LocalDate,
        val dayOfWeek: String,
        val condition: ConditionResponse,
        val minTemperature: Int?,
        val maxTemperature: Int?
    ) {
        companion object {
            fun from(forecast: DailyForecast): ForecastResponse =
                ForecastResponse(
                    date = forecast.date,
                    // 로케일에 의존하는 getDisplayName 대신 enum 이름을 쓴다. 표시 문자열은
                    // 클라이언트가 자기 로케일로 만든다 — 요일은 date만 있으면 어디서든 뽑을 수
                    // 있어서, 기상청 고유값이라 코드로 내려줘야 하는 condition과는 사정이 다르다.
                    dayOfWeek = forecast.date.dayOfWeek.name,
                    condition = ConditionResponse.from(forecast.condition),
                    minTemperature = forecast.minTemperature,
                    maxTemperature = forecast.maxTemperature
                )
        }
    }

    data class HomeResponse(
        val farmId: UUID,
        val temperature: Int,
        val condition: ConditionResponse,
        val minTemperature: Int?,
        val maxTemperature: Int?,
        val observedAt: LocalDateTime,
        val partial: PartialResponse
    ) {
        companion object {
            fun from(result: HomeWeather): HomeResponse =
                HomeResponse(
                    farmId = result.farmId,
                    temperature = result.temperature,
                    condition = ConditionResponse.from(result.condition),
                    minTemperature = result.minTemperature,
                    maxTemperature = result.maxTemperature,
                    observedAt = result.observedAt,
                    partial = PartialResponse.from(result.partial)
                )
        }
    }

    data class CurrentResponse(
        val temperature: Int,
        val feelsLikeTemperature: Int?,
        val condition: ConditionResponse,
        val minTemperature: Int?,
        val maxTemperature: Int?,
        val humidity: Int?,
        val windSpeed: Double?,
        val precipitationProbability: Int?,
        val uvIndex: Int?
    ) {
        companion object {
            fun from(result: DetailWeather): CurrentResponse =
                CurrentResponse(
                    temperature = result.temperature,
                    feelsLikeTemperature = result.feelsLikeTemperature,
                    condition = ConditionResponse.from(result.condition),
                    minTemperature = result.minTemperature,
                    maxTemperature = result.maxTemperature,
                    humidity = result.humidity,
                    windSpeed = result.windSpeed,
                    precipitationProbability = result.precipitationProbability,
                    uvIndex = result.uvIndex
                )
        }
    }

    data class AdviceLevelResponse(val code: String, val text: String) {
        companion object {
            fun from(level: AdviceLevel): AdviceLevelResponse =
                AdviceLevelResponse(code = level.name, text = level.text)
        }
    }

    data class AdviceResponse(
        val workType: String,
        val workTypeLabel: String,
        val level: AdviceLevelResponse,
        val message: String
    ) {
        companion object {
            fun from(advice: FarmingAdvice): AdviceResponse =
                AdviceResponse(
                    workType = advice.workType.name,
                    workTypeLabel = advice.workType.label,
                    level = AdviceLevelResponse.from(advice.level),
                    message = advice.message
                )
        }
    }

    data class DetailResponse(
        val farmId: UUID,
        val address: String,
        val observedAt: LocalDateTime,
        val current: CurrentResponse,
        val forecast: List<ForecastResponse>,
        val advices: List<AdviceResponse>,
        val partial: PartialResponse
    ) {
        companion object {
            fun from(result: DetailWeather): DetailResponse =
                DetailResponse(
                    farmId = result.farmId,
                    address = result.address,
                    observedAt = result.observedAt,
                    current = CurrentResponse.from(result),
                    forecast = result.forecast.map(ForecastResponse::from),
                    advices = result.advices.map(AdviceResponse::from),
                    partial = PartialResponse.from(result.partial)
                )
        }
    }

    data class DailyResponse(
        val date: LocalDate,
        val condition: ConditionResponse,
        val minTemperature: Int,
        val maxTemperature: Int
    ) {
        companion object {
            fun from(result: DailyWeather): DailyResponse =
                DailyResponse(
                    date = result.date,
                    condition = ConditionResponse.from(result.condition),
                    minTemperature = result.minTemperature,
                    maxTemperature = result.maxTemperature
                )
        }
    }
}
