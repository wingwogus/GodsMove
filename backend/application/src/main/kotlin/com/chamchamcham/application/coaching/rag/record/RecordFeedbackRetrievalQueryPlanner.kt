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

        context.cropCycle?.let { cycle ->
            queries += RecordFeedbackRetrievalQuery(
                query = "$cropName ${cycle.daysAfterPlanting}일차 생육 관리",
                reason = "days_after_planting"
            )
        }

        weatherRiskQuery(context)?.let { queries += it }
        pestControlQuery(context)?.let { queries += it }
        harvestQuery(context)?.let { queries += it }

        return queries
            .distinctBy { it.query }
            .take(4)
    }

    private fun weatherRiskQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
        val cropName = context.crop.name.trim()
        val recent = context.weather.recent7Days
        val recordDay = context.weather.recordDay
        val rainfall = recent.rainfallMm ?: recordDay.rainfallMm
        val dryDays = recent.dryDaysCount ?: 0
        val hotDays = recent.hotDaysCount ?: 0
        val maxTemp = recordDay.maxTemperatureC ?: 0.0

        return when {
            (rainfall != null && rainfall <= 5.0) || dryDays >= 4 || hotDays >= 2 || maxTemp >= 30.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 고온 건조 관수 병해충",
                    reason = "dry_hot_weather"
                )
            }

            rainfall != null && rainfall >= 30.0 -> {
                RecordFeedbackRetrievalQuery(
                    query = "$cropName 강우 과습 배수 병해충",
                    reason = "rain_wet_weather"
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
}
