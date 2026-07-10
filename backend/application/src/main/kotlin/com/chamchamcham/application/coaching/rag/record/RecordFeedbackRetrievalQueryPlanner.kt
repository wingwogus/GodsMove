package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

data class RecordFeedbackRetrievalQuery(
    val query: String,
    val reason: String
)

@Component
class RecordFeedbackRetrievalQueryPlanner {
    fun plan(context: RecordFeedbackContext): List<RecordFeedbackRetrievalQuery> {
        val cropName = context.crop.name.trim()
        val workTypeLabel = context.record.workType.label
        val queries = mutableListOf<RecordFeedbackRetrievalQuery>()

        queries += RecordFeedbackRetrievalQuery(
            query = "$cropName $workTypeLabel 재배 관리 약용작물",
            reason = "crop_work_type"
        )

        memoQuery(context)?.let { queries += it }

        weatherRiskQuery(context)?.let { queries += it }
        forecastWeatherRiskQuery(context)?.let { queries += it }
        pestControlQuery(context)?.let { queries += it }
        harvestQuery(context)?.let { queries += it }

        return queries
            .distinctBy { it.query }
            .take(6)
    }

    private fun memoQuery(context: RecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val memo = context.record.memo.trim()
        if (memo.isBlank()) {
            return null
        }
        return RecordFeedbackRetrievalQuery(
            query = "${context.crop.name.trim()} ${memo.take(120)}",
            reason = "memo_text"
        )
    }

    private fun weatherRiskQuery(context: RecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val weather = context.weather ?: return null
        val cropName = context.crop.name.trim()
        val rainfall = weather.forecastDays.mapNotNull { it.rainfallMm }.maxOrNull()
        val maxTemp = weather.forecastDays.mapNotNull { it.maxTemperatureC }.maxOrNull()
            ?: weather.current.temperatureC.toBigDecimal()

        if (rainfall != null && rainfall >= 30.toBigDecimal()) {
            return RecordFeedbackRetrievalQuery(
                query = "$cropName 강우 과습 배수 병해충",
                reason = "rain_wet_weather"
            )
        }

        if (maxTemp >= 30.toBigDecimal()) {
            return RecordFeedbackRetrievalQuery(
                query = "$cropName 고온 건조 관수 병해충",
                reason = "dry_hot_weather"
            )
        }

        return null
    }

    private fun forecastWeatherRiskQuery(context: RecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val cropName = context.crop.name.trim()
        val forecast = context.weather?.forecastDays ?: return null
        if (forecast.isEmpty()) {
            return null
        }

        val riskFlags = forecast.flatMap { it.riskFlags }.map { it.uppercase() }.toSet()
        val maxRainfall = forecast.mapNotNull { it.rainfallMm }.maxOrNull() ?: 0.toBigDecimal()
        val maxRainProbability = forecast.mapNotNull { it.rainProbabilityPct }.maxOrNull() ?: 0
        val maxHumidity = forecast.mapNotNull { it.humidityPct }.maxOrNull() ?: 0.toBigDecimal()
        val maxTemperature = forecast.mapNotNull { it.maxTemperatureC }.maxOrNull() ?: 0.toBigDecimal()
        val maxWindSpeed = forecast.mapNotNull { it.windSpeedMs }.maxOrNull() ?: 0.toBigDecimal()

        return when {
            riskFlags.any { it in RAIN_RISK_FLAGS } ||
                maxRainfall >= 20.toBigDecimal() ||
                maxRainProbability >= 70 ||
                maxHumidity >= 85.toBigDecimal() -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강우 예보 배수 과습 병해충",
                    reason = "forecast_rain_wet_weather"
                )
            }

            riskFlags.any { it in DRY_HOT_RISK_FLAGS } ||
                maxTemperature >= 30.toBigDecimal() -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 고온 건조 예보 관수 관리",
                    reason = "forecast_dry_hot_weather"
                )
            }

            riskFlags.any { it in WIND_RISK_FLAGS } || maxWindSpeed >= 8.toBigDecimal() -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강풍 예보 방제 작업 안전",
                    reason = "forecast_wind_weather"
                )
            }

            else -> null
        }
    }

    private fun pestControlQuery(context: RecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.record.workType != WorkType.PEST_CONTROL) {
            return null
        }
        val target = (context.record.detail as? PestControlFeedbackDetail)?.pestTarget
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return RecordFeedbackRetrievalQuery(
            query = "${context.crop.name.trim()} $target 방제",
            reason = "target_pest_or_disease"
        )
    }

    private fun harvestQuery(context: RecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.record.workType != WorkType.HARVEST) {
            return null
        }
        return RecordFeedbackRetrievalQuery(
            query = "약용작물 ${context.crop.usePartCategory.recordFeedbackLabel()} 수확 적기 ${context.crop.name.trim()}",
            reason = "harvest_use_part"
        )
    }

    private companion object {
        val RAIN_RISK_FLAGS = setOf("HEAVY_RAIN", "RAIN", "HIGH_HUMIDITY", "WET", "TYPHOON")
        val DRY_HOT_RISK_FLAGS = setOf("HOT", "HEAT", "DRY", "DROUGHT", "HIGH_TEMPERATURE")
        val WIND_RISK_FLAGS = setOf("STRONG_WIND", "WIND", "GALE")
    }
}
