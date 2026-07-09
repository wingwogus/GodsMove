package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.SeedSource
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
    fun `planting rejects purchased seed without purchase place`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.PURCHASED,
                seedPurchasePlace = null,
            ),
        ).forEach { payload ->
            val exception = assertThrows(BusinessException::class.java) { validator.validate(payload) }
            assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
        }
    }

    @Test
    fun `planting accepts purchased seed with purchase place`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.PURCHASED,
                seedPurchasePlace = "종묘상",
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `planting accepts self collected seed without purchase place`() {
        payloads(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.SELF_COLLECTED,
                seedPurchasePlace = null,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
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
                pesticideName = "약제",
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
    fun `harvest accepts detail`() {
        payloads(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
        ).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }

    @Test
    fun `watering weeding and pruning have no required detail`() {
        payloads(workType = WorkType.WATERING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
        payloads(workType = WorkType.WEEDING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
        payloads(workType = WorkType.PRUNING).forEach { payload -> assertDoesNotThrow { validator.validate(payload) } }
    }
}
