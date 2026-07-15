package com.chamchamcham.application.report

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingCycleReportSourceRepository
import com.chamchamcham.domain.farming.FarmingCycleReportSourceSnapshot
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.FertilizingRecord
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PestControlRecord
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WateringRecord
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WeedingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.Pesticide
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class FarmingCycleReportSourceLoaderTest {
    private val memberId = uuid("00000000-0000-0000-0000-000000000101")
    private val farmId = uuid("00000000-0000-0000-0000-000000000102")
    private val cropId = uuid("00000000-0000-0000-0000-000000000103")
    private val member = Member(id = memberId, email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
    private val crop = Crop(id = cropId, externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
    private val scope = ReportScope(memberId = memberId, farmId = farmId, cropId = cropId)

    @Test
    fun `loader normalizes merged fields without free text category fallback`() {
        val repository = FakeSourceRepository(fullSnapshot())
        val loader = FarmingCycleReportSourceLoader(repository)

        val records = loader.load(scope)

        assertThat(records.single { it.workType == WorkType.PLANTING }.planting!!.quantity)
            .isEqualByComparingTo("1250.0000")
        assertThat(records.single { it.workType == WorkType.PLANTING }.planting!!.quantityUnit)
            .isEqualTo("G")
        assertThat(records.single { it.workType == WorkType.WATERING }.watering!!.amount)
            .isEqualTo(CategoryRef("NORMAL", "보통"))
        val fertilizing = records.single { it.workType == WorkType.FERTILIZING }.fertilizing!!
        assertThat(fertilizing.materialName).isEqualTo("유기질비료")
        assertThat(fertilizing.amount).isEqualByComparingTo("250")
        assertThat(fertilizing.amountUnit).isEqualTo("ML")
        assertThat(fertilizing.amountKg).isNull()
        val pestControl = records.single { it.workType == WorkType.PEST_CONTROL }.pestControl!!
        assertThat(pestControl.pesticideName).isEqualTo("가가방")
        assertThat(pestControl.pestName).isEqualTo("진딧물")
        assertThat(records.single { it.workType == WorkType.PEST_CONTROL }
            .pestControl!!.pesticideAmountUnit)
            .isEqualTo("ML")
        assertThat(records.single { it.workType == WorkType.PEST_CONTROL }
            .pestControl!!.totalSprayAmountLiters)
            .isEqualByComparingTo("10.0000")
        assertThat(records.single { it.workType == WorkType.WEEDING }.weeding!!.method)
            .isEqualTo(CategoryRef("HAND", "손으로 뽑기"))
        assertThat(records.single { it.workType == WorkType.HARVEST }.harvest!!.amountKg)
            .isNull()
        assertThat(records.single { it.workType == WorkType.HARVEST }.harvest!!.growthPeriodMonths)
            .isEqualTo(24)
        assertThat(records.single { it.workType == WorkType.HARVEST }.harvest!!.isLastHarvest)
            .isTrue()
        assertThat(records.single { it.workType == WorkType.PRUNING }.planting).isNull()
        assertThat(records.single { it.workType == WorkType.ETC }.hasPhoto).isTrue()
        assertThat(records.single { it.workType == WorkType.PLANTING }.createdAt)
            .isEqualTo(LocalDateTime.of(2026, 7, 1, 8, 1))
    }

    @Test
    fun `loader preserves sub gram precision before report aggregation`() {
        val fertilizing = record("00000000-0000-0000-0000-000000000209", WorkType.FERTILIZING, hour = 9)
        val snapshot = emptySnapshot(records = listOf(fertilizing)).copy(
            fertilizingByRecordId = mapOf(
                requireNotNull(fertilizing.id) to FertilizingRecord(
                    record = fertilizing,
                    materialName = "유기질비료",
                    amount = BigDecimal("0.01"),
                    amountUnit = FertilizerAmountUnit.G,
                    applicationMethod = null,
                ),
            ),
        )

        val source = FarmingCycleReportSourceLoader(FakeSourceRepository(snapshot))
            .load(scope)
            .single()
            .fertilizing!!

        assertThat(source.amount).isEqualByComparingTo("0.0100")
        assertThat(source.amountKg).isEqualByComparingTo("0.00001")
    }

    @Test
    fun `loader fails when required detail is missing`() {
        val planting = record("00000000-0000-0000-0000-000000000201", WorkType.PLANTING, hour = 1)
        val repository = FakeSourceRepository(
            emptySnapshot(records = listOf(planting))
        )
        val loader = FarmingCycleReportSourceLoader(repository)

        assertThatThrownBy { loader.load(scope) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Missing detail for record ${planting.id}")
    }

    private fun fullSnapshot(): FarmingCycleReportSourceSnapshot {
        val planting = record("00000000-0000-0000-0000-000000000201", WorkType.PLANTING, hour = 1)
        val watering = record("00000000-0000-0000-0000-000000000202", WorkType.WATERING, hour = 2)
        val fertilizing = record("00000000-0000-0000-0000-000000000203", WorkType.FERTILIZING, hour = 3)
        val pestControl = record("00000000-0000-0000-0000-000000000204", WorkType.PEST_CONTROL, hour = 4)
        val weeding = record("00000000-0000-0000-0000-000000000205", WorkType.WEEDING, hour = 5)
        val pruning = record("00000000-0000-0000-0000-000000000206", WorkType.PRUNING, hour = 6)
        val harvest = record("00000000-0000-0000-0000-000000000207", WorkType.HARVEST, hour = 7)
        val etc = record("00000000-0000-0000-0000-000000000208", WorkType.ETC, hour = 8)

        return FarmingCycleReportSourceSnapshot(
            records = listOf(planting, watering, fertilizing, pestControl, weeding, pruning, harvest, etc),
            plantingByRecordId = mapOf(
                requireNotNull(planting.id) to PlantingRecord(
                    record = planting,
                    plantingMethod = PlantingMethod.SEED,
                    seedAmount = BigDecimal("1250.0000"),
                    seedAmountUnit = SeedAmountUnit.G,
                )
            ),
            wateringByRecordId = mapOf(
                requireNotNull(watering.id) to WateringRecord(
                    record = watering,
                    irrigationAmount = IrrigationAmount.NORMAL,
                    irrigationMethod = IrrigationMethod.DRIP,
                )
            ),
            fertilizingByRecordId = mapOf(
                requireNotNull(fertilizing.id) to FertilizingRecord(
                    record = fertilizing,
                    materialName = "유기질비료",
                    amount = BigDecimal("250.0000"),
                    amountUnit = FertilizerAmountUnit.ML,
                    applicationMethod = FertilizingMethod.SOIL,
                )
            ),
            pestControlByRecordId = mapOf(
                requireNotNull(pestControl.id) to PestControlRecord(
                    record = pestControl,
                    pesticide = Pesticide(
                        id = uuid("00000000-0000-0000-0000-000000000301"),
                        itemName = "만코제브 수화제",
                        brandName = "가가방",
                    ),
                    pesticideAmount = BigDecimal("30.0000"),
                    pesticideAmountUnit = PesticideAmountUnit.ML,
                    totalSprayAmount = BigDecimal("10000.0000"),
                    totalSprayAmountUnit = SprayAmountUnit.ML,
                    pest = Pest(
                        id = uuid("00000000-0000-0000-0000-000000000302"),
                        name = "진딧물",
                    ),
                )
            ),
            weedingByRecordId = mapOf(
                requireNotNull(weeding.id) to WeedingRecord(
                    record = weeding,
                    weedingMethod = WeedingMethod.HAND,
                )
            ),
            harvestByRecordId = mapOf(
                requireNotNull(harvest.id) to HarvestRecord(
                    record = harvest,
                    harvestAmount = null,
                    medicinalPart = CropUsePartCategory.ROOT_BARK,
                    harvestSource = HarvestSource.CULTIVATED,
                    growthPeriod = 24,
                    isLastHarvest = true,
                )
            ),
            mediaRecordIds = setOf(requireNotNull(etc.id)),
        )
    }

    private fun emptySnapshot(records: List<FarmingRecord>): FarmingCycleReportSourceSnapshot =
        FarmingCycleReportSourceSnapshot(
            records = records,
            plantingByRecordId = emptyMap(),
            wateringByRecordId = emptyMap(),
            fertilizingByRecordId = emptyMap(),
            pestControlByRecordId = emptyMap(),
            weedingByRecordId = emptyMap(),
            harvestByRecordId = emptyMap(),
            mediaRecordIds = emptySet(),
        )

    private fun record(id: String, workType: WorkType, hour: Long): FarmingRecord =
        FarmingRecord(
            id = uuid(id),
            member = member,
            farm = farm,
            crop = crop,
            workType = workType,
            workedAt = LocalDateTime.of(2026, 7, 1, 8, 0).plusHours(hour),
            weatherCondition = "맑음",
            weatherTemperature = 24,
            memo = "memo",
            entryMode = EntryMode.MANUAL,
        ).also { record ->
            com.chamchamcham.domain.common.BaseTimeEntity::class.java
                .getDeclaredField("createdAt")
                .apply { isAccessible = true }
                .set(record, LocalDateTime.of(2026, 7, 1, 8, 0).plusMinutes(hour))
        }

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private class FakeSourceRepository(
        private val snapshot: FarmingCycleReportSourceSnapshot,
    ) : FarmingCycleReportSourceRepository {
        override fun load(memberId: UUID, farmId: UUID, cropId: UUID): FarmingCycleReportSourceSnapshot = snapshot
    }
}
