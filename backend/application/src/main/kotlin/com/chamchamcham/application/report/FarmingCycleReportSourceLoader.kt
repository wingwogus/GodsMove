package com.chamchamcham.application.report

import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FarmingCycleReportSourceRepository
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.FertilizingRecord
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PestControlRecord
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WateringRecord
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WeedingRecord
import com.chamchamcham.domain.farming.WorkType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Component
class FarmingCycleReportSourceLoader(
    private val repository: FarmingCycleReportSourceRepository,
) {
    @Transactional(readOnly = true)
    fun load(scope: ReportScope): List<CycleReportSourceRecord> {
        val snapshot = repository.load(
            memberId = scope.memberId,
            farmId = scope.farmId,
            cropId = scope.cropId,
        )
        return snapshot.records.map { record ->
            val recordId = requireNotNull(record.id) { "Persisted farming record id is required" }
            CycleReportSourceRecord(
                id = recordId,
                workedAt = record.workedAt,
                createdAt = record.createdAt,
                workType = record.workType,
                weatherCondition = record.weatherCondition,
                weatherTemperature = record.weatherTemperature,
                hasPhoto = snapshot.mediaRecordIds.contains(recordId),
                memo = record.memo,
                planting = when (record.workType) {
                    WorkType.PLANTING -> normalizePlanting(snapshot.plantingByRecordId.required(recordId))
                    else -> null
                },
                watering = when (record.workType) {
                    WorkType.WATERING -> snapshot.wateringByRecordId[recordId]?.let(::normalizeWatering)
                    else -> null
                },
                fertilizing = when (record.workType) {
                    WorkType.FERTILIZING -> normalizeFertilizing(snapshot.fertilizingByRecordId.required(recordId))
                    else -> null
                },
                pestControl = when (record.workType) {
                    WorkType.PEST_CONTROL -> normalizePestControl(snapshot.pestControlByRecordId.required(recordId))
                    else -> null
                },
                weeding = when (record.workType) {
                    WorkType.WEEDING -> snapshot.weedingByRecordId[recordId]?.let(::normalizeWeeding)
                    else -> null
                },
                harvest = when (record.workType) {
                    WorkType.HARVEST -> normalizeHarvest(snapshot.harvestByRecordId.required(recordId))
                    else -> null
                },
            )
        }
    }

    private fun <T> Map<UUID, T>.required(recordId: UUID): T =
        this[recordId] ?: throw IllegalStateException("Missing detail for record $recordId")

    private fun normalizePlanting(detail: PlantingRecord): PlantingReportSource {
        return if (detail.plantingMethod == PlantingMethod.SEED) {
            PlantingReportSource(
                plantingMethod = detail.plantingMethod.toRef(),
                propagationMethod = detail.propagationMethod?.toRef(),
                quantity = detail.seedAmount?.scale4(),
                quantityUnit = detail.seedAmountUnit?.name,
            )
        } else {
            PlantingReportSource(
                plantingMethod = detail.plantingMethod.toRef(),
                propagationMethod = detail.propagationMethod?.toRef(),
                quantity = detail.seedlingCount?.toBigDecimal()?.scale4(),
                quantityUnit = detail.seedlingCount?.let { "JU" },
            )
        }
    }

    private fun normalizeWatering(detail: WateringRecord): WateringReportSource =
        WateringReportSource(
            amount = detail.irrigationAmount?.toRef(),
            method = detail.irrigationMethod?.toRef(),
        )

    private fun normalizeFertilizing(detail: FertilizingRecord): FertilizingReportSource =
        FertilizingReportSource(
            materialName = detail.materialName,
            amount = detail.amount.scale4(),
            amountUnit = detail.amountUnit.name,
            amountKg = detail.amount.toKilogramsOrNull(detail.amountUnit),
            applicationMethod = detail.applicationMethod?.toRef(),
        )

    private fun normalizePestControl(detail: PestControlRecord): PestControlReportSource =
        PestControlReportSource(
            pesticideId = requireNotNull(detail.pesticide.id) { "Persisted pesticide id is required" },
            pesticideName = detail.pesticide.brandName,
            pesticideAmount = detail.pesticideAmount.scale4(),
            pesticideAmountUnit = detail.pesticideAmountUnit.name,
            totalSprayAmountLiters = when (detail.totalSprayAmountUnit) {
                SprayAmountUnit.ML -> detail.totalSprayAmount.divide(BigDecimal("1000")).scale4()
            },
            pestName = detail.pest?.name,
        )

    private fun normalizeWeeding(detail: WeedingRecord): WeedingReportSource =
        WeedingReportSource(method = detail.weedingMethod?.toRef())

    private fun normalizeHarvest(detail: HarvestRecord): HarvestReportSource =
        HarvestReportSource(
            amountKg = detail.harvestAmount?.scale4(),
            medicinalPart = detail.medicinalPart?.toRef(),
            harvestSource = detail.harvestSource.toRef(),
            growthPeriod = detail.growthPeriod,
            growthPeriodUnit = "MONTH",
            growthPeriodMonths = detail.growthPeriod,
            isLastHarvest = detail.isLastHarvest,
        )

    private fun BigDecimal.toKilogramsOrNull(unit: FertilizerAmountUnit): BigDecimal? =
        when (unit) {
            FertilizerAmountUnit.G -> divide(BigDecimal("1000"))
            FertilizerAmountUnit.ML -> null
        }

    private fun BigDecimal.scale4(): BigDecimal = setScale(4, RoundingMode.HALF_UP)

    private fun PlantingMethod.toRef(): CategoryRef = CategoryRef(name, label)
    private fun PropagationMethod.toRef(): CategoryRef = CategoryRef(name, label)
    private fun IrrigationAmount.toRef(): CategoryRef = CategoryRef(name, label)
    private fun IrrigationMethod.toRef(): CategoryRef = CategoryRef(name, label)
    private fun FertilizingMethod.toRef(): CategoryRef = CategoryRef(name, label)
    private fun WeedingMethod.toRef(): CategoryRef = CategoryRef(name, label)
    private fun CropUsePartCategory.toRef(): CategoryRef = CategoryRef(name, label)
    private fun HarvestSource.toRef(): CategoryRef = CategoryRef(name, label)
}
