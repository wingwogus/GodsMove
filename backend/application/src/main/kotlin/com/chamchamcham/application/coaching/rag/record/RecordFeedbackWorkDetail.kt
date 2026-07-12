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
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal

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
