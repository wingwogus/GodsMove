package com.chamchamcham.application.coaching.rag.record

import org.springframework.stereotype.Component

data class RecordFeedbackRetrievalQuery(
    val query: String,
    val reason: String
)

@Component
class RecordFeedbackRetrievalQueryPlanner {
    fun plan(context: TodayRecordFeedbackContext): List<RecordFeedbackRetrievalQuery> {
        val cropName = context.crop.name.trim()
        val workTypeLabel = context.targetRecord.workType.label
        val queries = mutableListOf<RecordFeedbackRetrievalQuery>()

        queries += RecordFeedbackRetrievalQuery(
            query = "$cropName $workTypeLabel 재배 관리 약용작물",
            reason = "crop_work_type"
        )

        memoQuery(context)?.let { queries += it }

        context.cropCycle?.let { cycle ->
            queries += RecordFeedbackRetrievalQuery(
                query = "$cropName ${cycle.daysAfterPlanting}일차 생육 관리",
                reason = "days_after_planting"
            )
        }

        weatherRiskQuery(context)?.let { queries += it }
        forecastWeatherRiskQuery(context)?.let { queries += it }
        pestControlQuery(context)?.let { queries += it }
        harvestQuery(context)?.let { queries += it }

        return queries
            .distinctBy { it.query }
            .take(6)
    }

    private fun memoQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val memo = context.targetRecord.memo.trim()
        if (memo.isBlank()) {
            return null
        }
        return RecordFeedbackRetrievalQuery(
            query = "${context.crop.name.trim()} ${memo.take(120)}",
            reason = "memo_text"
        )
    }

    private fun weatherRiskQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val cropName = context.crop.name.trim()
        val recent = context.weather.recent7Days
        val recordDay = context.weather.recordDay
        val rainfall = recent.rainfallMm ?: recordDay.rainfallMm
        val dryDays = recent.dryDaysCount ?: 0
        val hotDays = recent.hotDaysCount ?: 0
        val maxTemp = recordDay.maxTemperatureC ?: 0.0

        if (rainfall != null && rainfall >= 30.0) {
            return RecordFeedbackRetrievalQuery(
                query = "$cropName 강우 과습 배수 병해충",
                reason = "rain_wet_weather"
            )
        }

        val lowRecentRainfall = recent.rainfallMm != null && recent.rainfallMm <= 5.0
        if (lowRecentRainfall || dryDays >= 4 || hotDays >= 2 || maxTemp >= 30.0) {
            return RecordFeedbackRetrievalQuery(
                query = "$cropName 고온 건조 관수 병해충",
                reason = "dry_hot_weather"
            )
        }

        return null
    }

    private fun forecastWeatherRiskQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val cropName = context.crop.name.trim()
        val forecast = context.weather.forecast7Days
        if (forecast.isEmpty()) {
            return null
        }

        val riskFlags = forecast.flatMap { it.riskFlags }.map { it.uppercase() }.toSet()
        val maxRainfall = forecast.mapNotNull { it.rainfallMm }.maxOrNull() ?: 0.0
        val maxRainProbability = forecast.mapNotNull { it.rainProbabilityPct }.maxOrNull() ?: 0
        val maxHumidity = forecast.mapNotNull { it.humidityPct }.maxOrNull() ?: 0.0
        val maxTemperature = forecast.mapNotNull { it.maxTemperatureC }.maxOrNull() ?: 0.0
        val maxWindSpeed = forecast.mapNotNull { it.windSpeedMs }.maxOrNull() ?: 0.0

        return when {
            riskFlags.any { it in RAIN_RISK_FLAGS } ||
                maxRainfall >= 20.0 ||
                maxRainProbability >= 70 ||
                maxHumidity >= 85.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강우 예보 배수 과습 병해충",
                    reason = "forecast_rain_wet_weather"
                )
            }

            riskFlags.any { it in DRY_HOT_RISK_FLAGS } ||
                maxTemperature >= 30.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 고온 건조 예보 관수 관리",
                    reason = "forecast_dry_hot_weather"
                )
            }

            riskFlags.any { it in WIND_RISK_FLAGS } || maxWindSpeed >= 8.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강풍 예보 방제 작업 안전",
                    reason = "forecast_wind_weather"
                )
            }

            else -> null
        }
    }

    private fun pestControlQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.targetRecord.workType != TodayRecordWorkType.PEST_CONTROL) {
            return null
        }
        val target = context.targetRecord.fieldText("targetPestOrDisease") ?: return null
        return RecordFeedbackRetrievalQuery(
            query = "${context.crop.name.trim()} $target 방제",
            reason = "target_pest_or_disease"
        )
    }

    private fun harvestQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        if (context.targetRecord.workType != TodayRecordWorkType.HARVESTING) {
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
