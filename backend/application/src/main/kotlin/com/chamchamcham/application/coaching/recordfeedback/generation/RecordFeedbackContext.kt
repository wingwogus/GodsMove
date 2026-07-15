package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.ManagementType
import java.time.LocalDateTime
import java.util.UUID

const val RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION = "record-feedback-context.v2"

data class RecordFeedbackContext(
    val schemaVersion: String = RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION,
    val member: RecordFeedbackMemberContext,
    val farm: RecordFeedbackFarmContext,
    val crop: RecordFeedbackCropContext,
    val record: RecordFeedbackRecordContext,
    val weather: RecordFeedbackLiveWeather?,
    val warnings: List<String> = emptyList(),
)

data class RecordFeedbackMemberContext(
    val memberId: UUID,
    val experienceLevel: Int?,
    val managementType: ManagementType?,
)

data class RecordFeedbackFarmContext(
    val farmId: UUID,
    val name: String,
    val roadAddress: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class RecordFeedbackCropContext(
    val cropId: UUID,
    val name: String,
    val usePartCategory: CropUsePartCategory,
)

data class RecordFeedbackRecordContext(
    val recordId: UUID,
    val sourceRevision: Long,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val detail: RecordFeedbackWorkDetail,
    val recordedWeatherCondition: String,
    val recordedTemperatureC: Int,
    val memo: String,
    val photoCount: Int,
)

fun RecordFeedbackContext.recordCitationId(): String {
    return "record:${record.recordId}"
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
