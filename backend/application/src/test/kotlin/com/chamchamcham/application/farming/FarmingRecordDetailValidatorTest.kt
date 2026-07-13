package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WorkType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class FarmingRecordDetailValidatorTest {

    private val validator: FarmingRecordDetailValidator = DefaultFarmingRecordDetailValidator()

    private fun payloads(
        workType: WorkType,
        planting: FarmingRecordCommand.PlantingDetail? = null,
        watering: FarmingRecordCommand.WateringDetail? = null,
        fertilizing: FarmingRecordCommand.FertilizingDetail? = null,
        pestControl: FarmingRecordCommand.PestControlDetail? = null,
        weeding: FarmingRecordCommand.WeedingDetail? = null,
        harvest: FarmingRecordCommand.HarvestDetail? = null,
    ): List<FarmingRecordDetailPayload> {
        val memberId = UUID.randomUUID()
        val farmId = UUID.randomUUID()
        val cropId = UUID.randomUUID()
        val workedAt = LocalDateTime.of(2026, 6, 1, 9, 0)

        val create = FarmingRecordCommand.Create(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = workType,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            planting = planting,
            watering = watering,
            fertilizing = fertilizing,
            pestControl = pestControl,
            weeding = weeding,
            harvest = harvest,
        )
        val update = FarmingRecordCommand.Update(
            memberId = memberId,
            recordId = UUID.randomUUID(),
            farmId = farmId,
            cropId = cropId,
            workType = workType,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            planting = planting,
            watering = watering,
            fertilizing = fertilizing,
            pestControl = pestControl,
            weeding = weeding,
            harvest = harvest,
        )
        return listOf(create, update)
    }

    @Test
    fun `planting requires detail`() {
        payloads(workType = WorkType.PLANTING, planting = null).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `planting accepts detail with propagation method`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                propagationMethod = PropagationMethod.SEED,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `planting accepts seed group when propagation method is seed`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                propagationMethod = PropagationMethod.SEED,
                seedAmount = BigDecimal.TEN,
                seedAmountUnit = SeedAmountUnit.KG,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `planting rejects seedling group when propagation method is seed`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                propagationMethod = PropagationMethod.SEED,
                seedlingCount = 10,
                seedlingUnit = SeedlingUnit.JU,
            ),
        ).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
        }
    }

    @Test
    fun `planting accepts seedling group when propagation method is not seed`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                propagationMethod = PropagationMethod.CUTTING,
                seedlingCount = 10,
                seedlingUnit = SeedlingUnit.JU,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `planting rejects seed group when propagation method is not seed`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                propagationMethod = PropagationMethod.CUTTING,
                seedAmount = BigDecimal.TEN,
                seedAmountUnit = SeedAmountUnit.KG,
            ),
        ).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
        }
    }

    @Test
    fun `fertilizing requires detail`() {
        payloads(workType = WorkType.FERTILIZING, fertilizing = null).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `fertilizing accepts detail`() {
        payloads(
            workType = WorkType.FERTILIZING,
            fertilizing = FarmingRecordCommand.FertilizingDetail(
                materialName = "요소비료",
                amount = BigDecimal.TEN,
                amountUnit = FertilizerAmountUnit.KG,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `pest control requires detail`() {
        payloads(workType = WorkType.PEST_CONTROL, pestControl = null).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `pest control accepts detail`() {
        payloads(
            workType = WorkType.PEST_CONTROL,
            pestControl = FarmingRecordCommand.PestControlDetail(
                pesticideId = UUID.randomUUID(),
                pesticideAmount = BigDecimal.ONE,
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal.TEN,
                totalSprayAmountUnit = SprayAmountUnit.L,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `harvest requires detail`() {
        payloads(workType = WorkType.HARVEST, harvest = null).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `harvest accepts detail with known amount`() {
        payloads(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `harvest accepts unknown amount as null`() {
        payloads(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = null,
                amountUnknown = true,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `harvest rejects missing amount without unknown flag`() {
        payloads(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = null,
                amountUnknown = false,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        ).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
        }
    }

    @Test
    fun `harvest rejects amount together with unknown flag`() {
        payloads(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                amountUnknown = true,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        ).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
        }
    }

    @Test
    fun `watering weeding pruning and etc have no required detail`() {
        payloads(workType = WorkType.WATERING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
        payloads(workType = WorkType.WEEDING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
        payloads(workType = WorkType.PRUNING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
        payloads(workType = WorkType.ETC).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }
}
