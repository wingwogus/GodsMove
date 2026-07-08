package com.chamchamcham.api.farming.dto

import com.chamchamcham.application.farming.FarmingRecordResult
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedSource
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WorkType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

object FarmingRecordResponses {
    data class RecordIdResponse(val id: UUID, val workType: WorkType) {
        companion object {
            fun from(result: FarmingRecordResult.RecordId): RecordIdResponse =
                RecordIdResponse(id = result.id, workType = result.workType)
        }
    }

    data class RecordSummaryResponse(
        val id: UUID,
        val cropId: UUID,
        val cropName: String,
        val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memoPreview: String,
        val thumbnailUrl: String?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.Summary): RecordSummaryResponse = RecordSummaryResponse(
                id = result.id,
                cropId = result.cropId,
                cropName = result.cropName,
                workType = result.workType,
                workedAt = result.workedAt,
                weatherCondition = result.weatherCondition,
                weatherTemperature = result.weatherTemperature,
                memoPreview = result.memoPreview,
                thumbnailUrl = result.thumbnailUrl,
            )
        }
    }

    data class RecordPageResponse(
        val items: List<RecordSummaryResponse>,
        val nextCursor: String?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.Page): RecordPageResponse = RecordPageResponse(
                items = result.items.map(RecordSummaryResponse::from),
                nextCursor = result.nextCursor,
            )
        }
    }

    data class RecordDetailResponse(
        val id: UUID,
        val farmId: UUID,
        val farmName: String,
        val cropId: UUID,
        val cropName: String,
        val workType: WorkType,
        val workedAt: LocalDateTime,
        val weatherCondition: String,
        val weatherTemperature: Int,
        val memo: String,
        val planting: PlantingDetailResponse?,
        val watering: WateringDetailResponse?,
        val fertilizing: FertilizingDetailResponse?,
        val pestControl: PestControlDetailResponse?,
        val weeding: WeedingDetailResponse?,
        val harvest: HarvestDetailResponse?,
        val imageUrls: List<String>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.Detail): RecordDetailResponse = RecordDetailResponse(
                id = result.id,
                farmId = result.farmId,
                farmName = result.farmName,
                cropId = result.cropId,
                cropName = result.cropName,
                workType = result.workType,
                workedAt = result.workedAt,
                weatherCondition = result.weatherCondition,
                weatherTemperature = result.weatherTemperature,
                memo = result.memo,
                planting = result.planting?.let(PlantingDetailResponse::from),
                watering = result.watering?.let(WateringDetailResponse::from),
                fertilizing = result.fertilizing?.let(FertilizingDetailResponse::from),
                pestControl = result.pestControl?.let(PestControlDetailResponse::from),
                weeding = result.weeding?.let(WeedingDetailResponse::from),
                harvest = result.harvest?.let(HarvestDetailResponse::from),
                imageUrls = result.imageUrls,
                createdAt = result.createdAt,
                updatedAt = result.updatedAt,
            )
        }
    }

    data class PlantingDetailResponse(
        val seedAmount: BigDecimal?,
        val seedAmountUnit: SeedAmountUnit?,
        val seedlingCount: Int?,
        val seedlingUnit: SeedlingUnit?,
        val seedSource: SeedSource?,
        val seedPurchasePlace: String?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.PlantingDetail): PlantingDetailResponse = PlantingDetailResponse(
                seedAmount = result.seedAmount,
                seedAmountUnit = result.seedAmountUnit,
                seedlingCount = result.seedlingCount,
                seedlingUnit = result.seedlingUnit,
                seedSource = result.seedSource,
                seedPurchasePlace = result.seedPurchasePlace,
            )
        }
    }

    data class WateringDetailResponse(
        val irrigationAmount: IrrigationAmount?,
        val irrigationMethod: IrrigationMethod?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.WateringDetail): WateringDetailResponse = WateringDetailResponse(
                irrigationAmount = result.irrigationAmount,
                irrigationMethod = result.irrigationMethod,
            )
        }
    }

    data class FertilizingDetailResponse(
        val materialName: String,
        val amount: BigDecimal,
        val amountUnit: FertilizerAmountUnit,
        val applicationMethod: FertilizingMethod?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.FertilizingDetail): FertilizingDetailResponse = FertilizingDetailResponse(
                materialName = result.materialName,
                amount = result.amount,
                amountUnit = result.amountUnit,
                applicationMethod = result.applicationMethod,
            )
        }
    }

    data class PestControlDetailResponse(
        val pesticideName: String,
        val pesticideAmount: BigDecimal,
        val pesticideAmountUnit: PesticideAmountUnit,
        val totalSprayAmount: BigDecimal,
        val totalSprayAmountUnit: SprayAmountUnit,
        val pestTarget: String?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.PestControlDetail): PestControlDetailResponse = PestControlDetailResponse(
                pesticideName = result.pesticideName,
                pesticideAmount = result.pesticideAmount,
                pesticideAmountUnit = result.pesticideAmountUnit,
                totalSprayAmount = result.totalSprayAmount,
                totalSprayAmountUnit = result.totalSprayAmountUnit,
                pestTarget = result.pestTarget,
            )
        }
    }

    data class WeedingDetailResponse(
        val weedingMethod: WeedingMethod?,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.WeedingDetail): WeedingDetailResponse =
                WeedingDetailResponse(weedingMethod = result.weedingMethod)
        }
    }

    data class HarvestDetailResponse(
        val harvestAmount: BigDecimal,
        val harvestAmountUnit: HarvestAmountUnit,
        val medicinalPart: CropUsePartCategory,
        val harvestSource: HarvestSource,
        val growthPeriod: Int,
        val growthPeriodUnit: GrowthPeriodUnit,
    ) {
        companion object {
            fun from(result: FarmingRecordResult.HarvestDetail): HarvestDetailResponse = HarvestDetailResponse(
                harvestAmount = result.harvestAmount,
                harvestAmountUnit = result.harvestAmountUnit,
                medicinalPart = result.medicinalPart,
                harvestSource = result.harvestSource,
                growthPeriod = result.growthPeriod,
                growthPeriodUnit = result.growthPeriodUnit,
            )
        }
    }
}
