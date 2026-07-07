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

    private fun baseCommand(
        workType: WorkType,
        planting: FarmingRecordCommand.PlantingDetail? = null,
        watering: FarmingRecordCommand.WateringDetail? = null,
        fertilizing: FarmingRecordCommand.FertilizingDetail? = null,
        pestControl: FarmingRecordCommand.PestControlDetail? = null,
        weeding: FarmingRecordCommand.WeedingDetail? = null,
        harvest: FarmingRecordCommand.HarvestDetail? = null,
    ) = FarmingRecordCommand.Create(
        memberId = UUID.randomUUID(),
        farmId = UUID.randomUUID(),
        cropId = UUID.randomUUID(),
        workType = workType,
        workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        memo = "memo",
        planting = planting,
        watering = watering,
        fertilizing = fertilizing,
        pestControl = pestControl,
        weeding = weeding,
        harvest = harvest,
    )

    @Test
    fun `planting rejects purchased seed without purchase place`() {
        val command = baseCommand(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.PURCHASED,
                seedPurchasePlace = null,
            ),
        )

        val exception = assertThrows(BusinessException::class.java) { validator.validate(command) }

        assertEquals(ErrorCode.FARMING_RECORD_INVALID_DETAIL, exception.errorCode)
    }

    @Test
    fun `planting accepts purchased seed with purchase place`() {
        val command = baseCommand(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.PURCHASED,
                seedPurchasePlace = "종묘상",
            ),
        )

        assertDoesNotThrow { validator.validate(command) }
    }

    @Test
    fun `planting accepts self collected seed without purchase place`() {
        val command = baseCommand(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedSource = SeedSource.SELF_COLLECTED,
                seedPurchasePlace = null,
            ),
        )

        assertDoesNotThrow { validator.validate(command) }
    }

    @Test
    fun `fertilizing requires detail`() {
        val command = baseCommand(workType = WorkType.FERTILIZING, fertilizing = null)

        val exception = assertThrows(BusinessException::class.java) { validator.validate(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `fertilizing accepts detail`() {
        val command = baseCommand(
            workType = WorkType.FERTILIZING,
            fertilizing = FarmingRecordCommand.FertilizingDetail(
                materialName = "요소비료",
                amount = BigDecimal.TEN,
                amountUnit = FertilizerAmountUnit.KG,
            ),
        )

        assertDoesNotThrow { validator.validate(command) }
    }

    @Test
    fun `pest control requires detail`() {
        val command = baseCommand(workType = WorkType.PEST_CONTROL, pestControl = null)

        val exception = assertThrows(BusinessException::class.java) { validator.validate(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `pest control accepts detail`() {
        val command = baseCommand(
            workType = WorkType.PEST_CONTROL,
            pestControl = FarmingRecordCommand.PestControlDetail(
                pesticideName = "약제",
                pesticideAmount = BigDecimal.ONE,
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal.TEN,
                totalSprayAmountUnit = SprayAmountUnit.L,
            ),
        )

        assertDoesNotThrow { validator.validate(command) }
    }

    @Test
    fun `harvest requires detail`() {
        val command = baseCommand(workType = WorkType.HARVEST, harvest = null)

        val exception = assertThrows(BusinessException::class.java) { validator.validate(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `harvest accepts detail`() {
        val command = baseCommand(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
        )

        assertDoesNotThrow { validator.validate(command) }
    }

    @Test
    fun `watering weeding and pruning have no required detail`() {
        assertDoesNotThrow { validator.validate(baseCommand(workType = WorkType.WATERING)) }
        assertDoesNotThrow { validator.validate(baseCommand(workType = WorkType.WEEDING)) }
        assertDoesNotThrow { validator.validate(baseCommand(workType = WorkType.PRUNING)) }
    }
}
