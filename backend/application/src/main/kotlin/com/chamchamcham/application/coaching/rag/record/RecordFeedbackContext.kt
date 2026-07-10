package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizerMaterialCategory
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PesticideCategory
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.ManagementType
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.LocalDate
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = PlantingFeedbackDetail::class, name = "PLANTING"),
    JsonSubTypes.Type(value = WateringFeedbackDetail::class, name = "WATERING"),
    JsonSubTypes.Type(value = FertilizingFeedbackDetail::class, name = "FERTILIZING"),
    JsonSubTypes.Type(value = PestControlFeedbackDetail::class, name = "PEST_CONTROL"),
    JsonSubTypes.Type(value = WeedingFeedbackDetail::class, name = "WEEDING"),
    JsonSubTypes.Type(value = HarvestFeedbackDetail::class, name = "HARVEST"),
    JsonSubTypes.Type(value = CommonFeedbackDetail::class, name = "COMMON"),
)
sealed interface RecordFeedbackWorkDetail

data class PlantingFeedbackDetail(
    val seedAmount: BigDecimal?,
    val seedAmountUnit: SeedAmountUnit?,
    val seedlingCount: Int?,
    val seedlingUnit: SeedlingUnit?,
    val propagationMethod: PropagationMethod,
) : RecordFeedbackWorkDetail

data class WateringFeedbackDetail(
    val irrigationAmount: IrrigationAmount?,
    val irrigationMethod: IrrigationMethod?,
) : RecordFeedbackWorkDetail

data class FertilizingFeedbackDetail(
    val materialCategory: FertilizerMaterialCategory,
    val amount: BigDecimal,
    val amountUnit: FertilizerAmountUnit,
    val applicationMethod: FertilizingMethod?,
) : RecordFeedbackWorkDetail

data class PestControlFeedbackDetail(
    val pesticideCategory: PesticideCategory,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: PesticideAmountUnit,
    val totalSprayAmount: BigDecimal,
    val totalSprayAmountUnit: SprayAmountUnit,
    val pestTarget: String?,
) : RecordFeedbackWorkDetail

data class WeedingFeedbackDetail(
    val weedingMethod: WeedingMethod?,
) : RecordFeedbackWorkDetail

data class HarvestFeedbackDetail(
    val harvestAmountKg: BigDecimal?,
    val amountUnknown: Boolean,
    val medicinalPart: CropUsePartCategory,
    val harvestSource: HarvestSource,
    val growthPeriod: Int,
    val growthPeriodUnit: GrowthPeriodUnit,
    val isFinalHarvest: Boolean,
) : RecordFeedbackWorkDetail

data object CommonFeedbackDetail : RecordFeedbackWorkDetail

data class RecordFeedbackLiveWeather(
    val current: RecordFeedbackCurrentWeather,
    val forecastDays: List<RecordFeedbackForecastDay>,
    val source: String,
)

data class RecordFeedbackCurrentWeather(
    val temperatureC: Int,
    val skyCondition: String,
    val observedAt: LocalDateTime,
)

data class RecordFeedbackForecastDay(
    val date: LocalDate,
    val rainfallMm: BigDecimal?,
    val rainProbabilityPct: Int?,
    val maxTemperatureC: BigDecimal?,
    val minTemperatureC: BigDecimal?,
    val humidityPct: BigDecimal?,
    val windSpeedMs: BigDecimal?,
    val riskFlags: List<String>,
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
