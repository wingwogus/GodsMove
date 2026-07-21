package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component

@Component
class ReportFeedbackRetrievalQueryPlanner {
    fun plan(context: ReportFeedbackContext): List<String> {
        val cropName = context.report.cropName.trim()
        val workTypeLabel = context.workType.label
        val statistics = context.report.statistics
        return buildList {
            add("$cropName $workTypeLabel 재배 관리")
            add(periodQuery(cropName, workTypeLabel, context))
            if (statistics["averageIntervalDays"] is Number) {
                add("$cropName $workTypeLabel 간격")
            }
            addAll(detailQueries(cropName, context.workType, statistics))
        }.distinct().take(MAX_QUERIES)
    }

    private fun periodQuery(
        cropName: String,
        workTypeLabel: String,
        context: ReportFeedbackContext,
    ): String {
        val startMonth = context.report.startsAt.monthValue
        val endMonth = context.report.endsAt.monthValue
        val period = if (startMonth == endMonth) "${startMonth}월" else "${startMonth}월~${endMonth}월"
        return "$cropName $period $workTypeLabel 시기"
    }

    private fun detailQueries(
        cropName: String,
        workType: WorkType,
        statistics: Map<String, Any?>,
    ): List<String> = when (workType) {
        WorkType.PLANTING -> statistics.topCodes("propagationMethods")
            .mapNotNull { it.plantingQueryText() }
            .map { "$cropName $it 방법" }

        WorkType.WATERING -> statistics.topCodes("methodDistribution")
            .mapNotNull { it.irrigationQueryText() }
            .map { "$cropName $it 방법" }

        WorkType.FERTILIZING -> buildList {
            statistics.topLabels("materialDistribution")
                .forEach { add("$cropName $it 시비") }
            statistics.topCodes("methodDistribution")
                .mapNotNull { it.fertilizingQueryText() }
                .forEach { add("$cropName $it 방법") }
        }

        WorkType.PEST_CONTROL -> statistics.listOfMaps("targets")
            .mapNotNull { it["target"]?.toString()?.takeIf(String::isNotBlank) }
            .take(TOP_SIGNALS)
            .map { "$cropName $it 방제" }

        WorkType.WEEDING -> statistics.topCodes("methodDistribution")
            .mapNotNull { it.weedingQueryText() }
            .map { "$cropName $it" }

        WorkType.HARVEST -> statistics.topCodes("medicinalParts")
            .mapNotNull { it.cropPartQueryText() }
            .map { "$cropName $it 수확 시기" }

        WorkType.PRUNING, WorkType.ETC -> emptyList()
    }

    private fun Map<String, Any?>.topCodes(listKey: String): List<String> =
        topEntries(listKey).mapNotNull { it["code"]?.toString()?.takeIf(String::isNotBlank) }

    private fun Map<String, Any?>.topLabels(listKey: String): List<String> =
        topEntries(listKey).mapNotNull { it["label"]?.toString()?.takeIf(String::isNotBlank) }

    private fun Map<String, Any?>.topEntries(listKey: String): List<Map<String, Any?>> =
        listOfMaps(listKey)
            .sortedByDescending { it.count() ?: 0 }
            .take(TOP_SIGNALS)

    private fun Map<String, Any?>.count(): Int? =
        (this["count"] as? Number)?.toInt() ?: (this["recordCount"] as? Number)?.toInt()

    private fun Map<String, Any?>.listOfMaps(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.entries
                ?.mapNotNull { (mapKey, value) -> (mapKey as? String)?.let { it to value } }
                ?.toMap()
        }.orEmpty()

    private fun String.plantingQueryText(): String? = when (this) {
        "SEED" -> "파종"
        "SEEDLING" -> "정식"
        "CUTTING" -> "삽목"
        "GRAFTING" -> "접목"
        "LAYERING" -> "휘묻이"
        "DIVISION" -> "분주"
        "TISSUE_CULTURE" -> "조직배양 묘 정식"
        "PURCHASED" -> "모종 정식"
        else -> null
    }

    private fun String.irrigationQueryText(): String? = when (this) {
        "DRIP" -> "점적 관수"
        "SPRAYING" -> "살수 관수"
        else -> null
    }

    private fun String.fertilizingQueryText(): String? = when (this) {
        "SOIL" -> "토양 시비"
        "FOLIAR" -> "엽면 시비"
        else -> null
    }

    private fun String.weedingQueryText(): String? = when (this) {
        "HAND" -> "제초 관리"
        "MACHINE" -> "기계 제초"
        "MULCHING" -> "멀칭 제초"
        "HERBICIDE" -> "제초제 사용"
        else -> null
    }

    private fun String.cropPartQueryText(): String? = when (this) {
        "WHOLE_HERB" -> "전초"
        "ROOT_BARK" -> "뿌리"
        "RHIZOME" -> "근경"
        "LEAF" -> "잎"
        "FLOWER" -> "꽃"
        "FRUIT" -> "열매"
        "SEED" -> "종자"
        "STEM_BRANCH" -> "줄기"
        else -> null
    }

    private companion object {
        const val MAX_QUERIES = 5
        const val TOP_SIGNALS = 2
    }
}
