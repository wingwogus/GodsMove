package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.coaching.CoachingMode
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

const val TODAY_RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION = "record-feedback-context.v1"

data class TodayRecordFeedbackContext @JsonCreator constructor(
    @JsonProperty("schemaVersion") val schemaVersion: String,
    @JsonProperty("feedbackRequestId") val feedbackRequestId: String,
    @JsonProperty("mode") val mode: CoachingMode,
    @JsonProperty("member") val member: RecordFeedbackMemberContext,
    @JsonProperty("farm") val farm: RecordFeedbackFarmContext,
    @JsonProperty("crop") val crop: RecordFeedbackCropContext,
    @JsonProperty("cropCycle") val cropCycle: RecordFeedbackCropCycleContext?,
    @JsonProperty("targetRecord") val targetRecord: RecordFeedbackTargetRecordContext,
    @JsonProperty("weather") val weather: RecordFeedbackWeatherContext,
    @JsonProperty("recentRecords") val recentRecords: List<RecordFeedbackRecentRecordContext> = emptyList(),
    @JsonProperty("workTypeStats") val workTypeStats: RecordFeedbackWorkTypeStatsContext = RecordFeedbackWorkTypeStatsContext()
)

data class RecordFeedbackMemberContext @JsonCreator constructor(
    @JsonProperty("memberId") val memberId: String,
    @JsonProperty("experienceLevel") val experienceLevel: Int?,
    @JsonProperty("managementType") val managementType: String?
)

data class RecordFeedbackFarmContext @JsonCreator constructor(
    @JsonProperty("farmId") val farmId: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("address") val address: String,
    @JsonProperty("locationSource") val locationSource: String
)

data class RecordFeedbackCropContext @JsonCreator constructor(
    @JsonProperty("cropId") val cropId: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("usePartCategory") val usePartCategory: CropUsePartCategory
)

data class RecordFeedbackCropCycleContext @JsonCreator constructor(
    @JsonProperty("cycleId") val cycleId: String,
    @JsonProperty("startedRecordId") val startedRecordId: String,
    @JsonProperty("startedOn") val startedOn: LocalDate,
    @JsonProperty("daysAfterPlanting") val daysAfterPlanting: Int,
    @JsonProperty("startBasis") val startBasis: String
)

data class RecordFeedbackTargetRecordContext @JsonCreator constructor(
    @JsonProperty("recordId") val recordId: String,
    @JsonProperty("recordedOn") val recordedOn: LocalDate,
    @JsonProperty("workType") val workType: TodayRecordWorkType,
    @JsonProperty("fields") val fields: Map<String, Any?> = emptyMap(),
    @JsonProperty("memo") val memo: String,
    @JsonProperty("hasPhoto") val hasPhoto: Boolean = false,
    @JsonProperty("photoCount") val photoCount: Int = 0
) {
    fun fieldText(key: String): String? {
        return fields[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}

data class RecordFeedbackWeatherContext @JsonCreator constructor(
    @JsonProperty("recordDay") val recordDay: RecordFeedbackRecordDayWeather,
    @JsonProperty("recent7Days") val recent7Days: RecordFeedbackRecentWeatherSummary,
    @JsonProperty("source") val source: String
)

data class RecordFeedbackRecordDayWeather @JsonCreator constructor(
    @JsonProperty("avgTemperatureC") val avgTemperatureC: Double?,
    @JsonProperty("maxTemperatureC") val maxTemperatureC: Double?,
    @JsonProperty("minTemperatureC") val minTemperatureC: Double?,
    @JsonProperty("rainfallMm") val rainfallMm: Double?,
    @JsonProperty("humidityPct") val humidityPct: Double?
)

data class RecordFeedbackRecentWeatherSummary @JsonCreator constructor(
    @JsonProperty("rainfallMm") val rainfallMm: Double?,
    @JsonProperty("hotDaysCount") val hotDaysCount: Int?,
    @JsonProperty("dryDaysCount") val dryDaysCount: Int?
)

data class RecordFeedbackRecentRecordContext @JsonCreator constructor(
    @JsonProperty("recordId") val recordId: String,
    @JsonProperty("recordedOn") val recordedOn: LocalDate,
    @JsonProperty("workType") val workType: TodayRecordWorkType,
    @JsonProperty("memoSummary") val memoSummary: String
)

data class RecordFeedbackWorkTypeStatsContext @JsonCreator constructor(
    @JsonProperty("cycleCounts") val cycleCounts: Map<TodayRecordWorkType, Int> = emptyMap(),
    @JsonProperty("lastWorkedOnByType") val lastWorkedOnByType: Map<TodayRecordWorkType, LocalDate> = emptyMap(),
    @JsonProperty("recent30DayCounts") val recent30DayCounts: Map<TodayRecordWorkType, Int> = emptyMap()
)

enum class TodayRecordWorkType(
    val label: String
) {
    PLANTING("심기"),
    WATERING("물주기"),
    FERTILIZING("거름·비료"),
    PEST_CONTROL("병해충 방제"),
    WEEDING("제초"),
    PRUNING("가지·순 정리"),
    HARVESTING("수확"),
    PROCESSING("가공")
}

fun CropUsePartCategory.recordFeedbackLabel(): String {
    return when (this) {
        CropUsePartCategory.WHOLE_HERB -> "전초"
        CropUsePartCategory.ROOT_BARK -> "뿌리·껍질"
        CropUsePartCategory.RHIZOME -> "뿌리줄기"
        CropUsePartCategory.LEAF -> "잎"
        CropUsePartCategory.FLOWER -> "꽃"
        CropUsePartCategory.FRUIT -> "열매/과실"
        CropUsePartCategory.SEED -> "종자"
        CropUsePartCategory.STEM_BRANCH -> "줄기/가지"
        CropUsePartCategory.UNKNOWN -> "기타"
    }
}
