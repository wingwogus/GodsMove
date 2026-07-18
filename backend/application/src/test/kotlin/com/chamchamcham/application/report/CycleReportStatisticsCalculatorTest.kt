package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.CommonOnlyStatistics
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.GrowthPeriodRange
import com.chamchamcham.domain.report.PlantingStatistics
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CycleReportStatisticsCalculatorTest {
    private val calculator = CycleReportStatisticsCalculator()

    @Test
    fun `every work type receives direct common fields`() {
        val result = calculator.calculate(allWorkTypeFixture())

        assertDirectCommonFields(result.planting, day = 1, photoAttached = false)
        assertDirectCommonFields(result.watering, day = 2, photoAttached = false)
        assertDirectCommonFields(result.fertilizing, day = 3, photoAttached = false)
        assertDirectCommonFields(result.pestControl, day = 4, photoAttached = false)
        assertDirectCommonFields(result.weeding, day = 5, photoAttached = false)
        assertDirectCommonFields(result.pruning, day = 6, photoAttached = false)
        assertDirectCommonFields(result.harvest, day = 7, photoAttached = true)
        assertDirectCommonFields(result.etc, day = 8, photoAttached = false)
        assertThat(result.harvest.photoAttachedRecordCount).isEqualTo(1)
    }

    @Test
    fun `missing work type remains an empty typed object`() {
        val result = calculator.calculate(listOf(watering(day = 1)))

        assertThat(result.planting).isEqualTo(PlantingStatistics.empty())
        assertThat(result.pruning).isEqualTo(CommonOnlyStatistics.empty())
        assertThat(result.etc).isEqualTo(CommonOnlyStatistics.empty())
    }

    @Test
    fun `planting separates seed grams and JU quantities`() {
        val result = calculator.calculate(
            listOf(seedPlanting("500"), seedPlanting("750"), cuttingPlanting(20)),
        ).planting

        assertThat(result.propagationMethods.map { it.code }).containsExactly("SEED", "CUTTING")
        assertThat(result.propagationMethods.first { it.code == "SEED" }.totalQuantity)
            .isEqualByComparingTo("1250.0000")
        assertThat(result.propagationMethods.first { it.code == "SEED" }.quantityUnit)
            .isEqualTo("G")
        assertThat(result.propagationMethods.first { it.code == "CUTTING" }.quantityUnit)
            .isEqualTo("JU")
        assertThat(result.plantingMethodDistribution.map { it.code to it.count })
            .containsExactly("SEED" to 2, "SEEDLING" to 1)
        assertThat(result.plantingMethodDistribution.map { it.code to it.ratePct })
            .containsExactly("SEED" to BigDecimal("66.67"), "SEEDLING" to BigDecimal("33.33"))
    }

    @Test
    fun `watering includes missing selections and has no adequacy fields`() {
        val result = calculator.calculate(
            listOf(watering("LOW", "DRIP"), watering(null, "MANUAL")),
        ).watering

        assertThat(result.amountDistribution.map { it.code to it.count })
            .containsExactlyInAnyOrder("LOW" to 1, "MISSING" to 1)
        assertThat(WateringStatistics::class.java.declaredFields.map { it.name })
            .doesNotContain("adequacy", "shortage", "excess")
    }

    @Test
    fun `fertilizing groups material names and nullable methods`() {
        val result = calculator.calculate(
            listOf(
                fertilizing("복합비료", "10000", "G", "10", "SOIL"),
                fertilizing("복합비료", "5000", "G", "5", null),
            ),
        ).fertilizing

        assertThat(result.totalAmountKg).isEqualByComparingTo("15.0000")
        assertThat(result.averageAmountKg).isEqualByComparingTo("7.50")
        assertThat(result.amountCoverage).isEqualTo(Coverage(2, 2))
        assertThat(result.materialCategories.single().amountKg)
            .isEqualByComparingTo("15.0000")
        assertThat(result.methodDistribution.map { it.code to it.count })
            .containsExactlyInAnyOrder("SOIL" to 1, "MISSING" to 1)
        assertThat(result.categoryMethods.map { Triple(it.categoryCode, it.methodCode, it.recordCount) })
            .containsExactly(Triple("복합비료", "MISSING", 1), Triple("복합비료", "SOIL", 1))
    }

    @Test
    fun `fertilizer mass statistics include G but exclude ML`() {
        val result = calculator.calculate(
            listOf(
                fertilizing("유기질비료", "500", "G", "0.5000", "SOIL"),
                fertilizing("액상비료", "250", "ML", null, "SOIL"),
            ),
        ).fertilizing

        assertThat(result.totalAmountKg).isEqualByComparingTo("0.5000")
        assertThat(result.amountCoverage).isEqualTo(Coverage(recordedCount = 1, targetCount = 2))
        assertThat(result.materialDistribution.map { it.label to it.count })
            .containsExactlyInAnyOrder("유기질비료" to 1, "액상비료" to 1)
    }

    @Test
    fun `fertilizer sums sub gram amounts before rounding kilograms`() {
        val result = calculator.calculate(
            listOf(
                fertilizing("유기질비료", "0.06", "G", "0.00006", "SOIL", day = 1),
                fertilizing("유기질비료", "0.06", "G", "0.00006", "SOIL", day = 2),
            ),
        ).fertilizing

        assertThat(result.totalAmountKg).isEqualByComparingTo("0.0001")
        assertThat(result.materialCategories.single().amountKg).isEqualByComparingTo("0.0001")
    }

    @Test
    fun `pesticide ML and G remain separate`() {
        val result = calculator.calculate(
            listOf(
                pest("가가방", "10", "ML", "100"),
                pest("가가방", "5", "G", "50"),
            ),
        ).pestControl

        assertThat(result.pesticideAmounts.map { it.unit to it.amount })
            .containsExactlyInAnyOrder(
                "ML" to BigDecimal("10.0000"),
                "G" to BigDecimal("5.0000"),
            )
        assertThat(result.categoryAmounts.map { Triple(it.categoryCode, it.unit, it.amount) })
            .containsExactly(
                Triple(id("91").toString(), "G", BigDecimal("5.0000")),
                Triple(id("91").toString(), "ML", BigDecimal("10.0000")),
            )
        assertThat(result.totalSprayAmountLiters).isEqualByComparingTo("150.0000")
    }

    @Test
    fun `pesticides with the same brand name remain separate products`() {
        val firstId = id("901")
        val secondId = id("902")
        val result = calculator.calculate(
            listOf(
                pest("같은이름", "10", "ML", "100", day = 1, pesticideId = firstId),
                pest("같은이름", "20", "ML", "100", day = 2, pesticideId = secondId),
            ),
        ).pestControl

        assertThat(result.categoryDistribution.map { it.code to it.count })
            .containsExactlyInAnyOrder(firstId.toString() to 1, secondId.toString() to 1)
        assertThat(result.categoryAmounts.map { it.categoryCode to it.recordCount })
            .containsExactlyInAnyOrder(firstId.toString() to 1, secondId.toString() to 1)
    }

    @Test
    fun `unknown harvest amount counts the record but not quantity`() {
        val result = calculator.calculate(
            listOf(
                harvest(null, "ROOT_BARK", 10, final = false),
                harvest("30", "LEAF", 12, final = true),
            ),
        ).harvest

        assertThat(result.recordCount).isEqualTo(2)
        assertThat(result.totalAmountKg).isEqualByComparingTo("30.0000")
        assertThat(result.averageAmountKg).isEqualByComparingTo("30.00")
        assertThat(result.amountCoverage).isEqualTo(Coverage(1, 2))
        assertThat(result.medicinalParts.sumOf { it.recordCount }).isEqualTo(2)
        assertThat(result.medicinalParts.first { it.code == "ROOT_BARK" }.knownAmountKg).isNull()
        assertThat(result.finalGrowthPeriodMonths).isEqualTo(12)
        assertThat(result.growthPeriodRangeMonths).isEqualTo(GrowthPeriodRange(10, 12))
    }

    @Test
    fun `harvest groups records by growth period months`() {
        val result = calculator.calculate(
            listOf(
                harvest("10", "ROOT_BARK", 24, final = false, day = 1),
                harvest("20", "ROOT_BARK", 24, final = false, day = 2),
                harvest("30", "ROOT_BARK", 36, final = true, day = 3),
            ),
        ).harvest

        assertThat(result.growthPeriodDistribution.map { Triple(it.code, it.label, it.count) })
            .containsExactly(
                Triple("24", "24개월", 2),
                Triple("36", "36개월", 1),
            )
        assertThat(result.growthPeriodDistribution.map { it.code to it.ratePct })
            .containsExactly("24" to BigDecimal("66.67"), "36" to BigDecimal("33.33"))
    }

    private fun assertDirectCommonFields(stats: Any, day: Int, photoAttached: Boolean) {
        assertThat(readField<Int>(stats, "recordCount")).isEqualTo(1)
        assertThat(readField<LocalDate>(stats, "firstWorkedOn")).isEqualTo(date(day))
        assertThat(readField<LocalDate>(stats, "lastWorkedOn")).isEqualTo(date(day))
        assertThat(readField<Int>(stats, "workedDayCount")).isEqualTo(1)
        assertThat(readField<BigDecimal?>(stats, "averageIntervalDays")).isNull()
        assertThat(readField<Int>(stats, "photoAttachedRecordCount"))
            .isEqualTo(if (photoAttached) 1 else 0)
        assertThat(readField<BigDecimal>(stats, "photoAttachmentRatePct"))
            .isEqualByComparingTo(if (photoAttached) "100.00" else "0.00")
        assertThat(readField<List<*>>(stats, "weatherDistribution").map { distribution ->
            val item = requireNotNull(distribution)
            readField<String>(item, "code") to readField<Int>(item, "count")
        }).containsExactly("맑음" to 1)
        assertThat(readField<BigDecimal>(stats, "averageTemperatureC")).isEqualByComparingTo("20.00")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readField(target: Any, name: String): T {
        val field = target::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun allWorkTypeFixture(): List<CycleReportSourceRecord> =
        listOf(
            seedPlanting("100", day = 1),
            watering(day = 2),
            fertilizing("복합비료", "10000", "G", "10", "SOIL", day = 3),
            pest("가가방", "5", "ML", "50", day = 4),
            weeding(day = 5),
            baseRecord("06", 6, WorkType.PRUNING),
            harvest("30", "ROOT_BARK", 12, final = true, day = 7, hasPhoto = true),
            baseRecord("08", 8, WorkType.ETC),
        )

    private fun seedPlanting(amount: String, day: Int = 1): CycleReportSourceRecord =
        baseRecord(
            number = "1$day",
            day = day,
            workType = WorkType.PLANTING,
            planting = PlantingReportSource(
                plantingMethod = CategoryRef("SEED", "씨앗 심기"),
                propagationMethod = null,
                quantity = BigDecimal(amount),
                quantityUnit = "G",
            ),
        )

    private fun cuttingPlanting(seedlingCount: Int, day: Int = 2): CycleReportSourceRecord =
        baseRecord(
            number = "2$day",
            day = day,
            workType = WorkType.PLANTING,
            planting = PlantingReportSource(
                plantingMethod = CategoryRef("SEEDLING", "모종 심기"),
                propagationMethod = CategoryRef("CUTTING", "삽목"),
                quantity = BigDecimal(seedlingCount),
                quantityUnit = "JU",
            ),
        )

    private fun watering(
        amount: String? = "LOW",
        method: String? = "DRIP",
        day: Int = 1,
    ): CycleReportSourceRecord =
        baseRecord(
            number = "3$day",
            day = day,
            workType = WorkType.WATERING,
            watering = WateringReportSource(
                amount = amount?.let { CategoryRef(it, wateringAmountLabel(it)) },
                method = method?.let { CategoryRef(it, wateringMethodLabel(it)) },
            ),
        )

    private fun fertilizing(
        materialName: String,
        amount: String,
        amountUnit: String,
        amountKg: String?,
        method: String?,
        day: Int = 1,
    ): CycleReportSourceRecord =
        baseRecord(
            number = "4$day",
            day = day,
            workType = WorkType.FERTILIZING,
            fertilizing = FertilizingReportSource(
                materialName = materialName,
                amount = BigDecimal(amount),
                amountUnit = amountUnit,
                amountKg = amountKg?.let(::BigDecimal),
                applicationMethod = method?.let { CategoryRef(it, fertilizingMethodLabel(it)) },
            ),
        )

    private fun pest(
        pesticideName: String,
        amount: String?,
        unit: String,
        sprayAmountLiters: String?,
        day: Int = 1,
        pesticideId: UUID = id("9$day"),
    ): CycleReportSourceRecord =
        baseRecord(
            number = "5$day",
            day = day,
            workType = WorkType.PEST_CONTROL,
            pestControl = PestControlReportSource(
                pesticideId = pesticideId,
                pesticideName = pesticideName,
                pesticideAmount = amount?.let(::BigDecimal),
                pesticideAmountUnit = unit,
                totalSprayAmountLiters = sprayAmountLiters?.let(::BigDecimal),
                pestName = "진딧물",
            ),
        )

    private fun weeding(day: Int = 1): CycleReportSourceRecord =
        baseRecord(
            number = "6$day",
            day = day,
            workType = WorkType.WEEDING,
            weeding = WeedingReportSource(method = CategoryRef("HAND", "손제초")),
        )

    private fun harvest(
        amountKg: String?,
        part: String,
        growthMonths: Int,
        final: Boolean,
        day: Int = growthMonths,
        hasPhoto: Boolean = false,
    ): CycleReportSourceRecord =
        baseRecord(
            number = "7$day",
            day = day,
            workType = WorkType.HARVEST,
            hasPhoto = hasPhoto,
            harvest = HarvestReportSource(
                amountKg = amountKg?.let(::BigDecimal),
                medicinalPart = CategoryRef(part, harvestPartLabel(part)),
                growthPeriod = growthMonths,
                growthPeriodUnit = "MONTH",
                growthPeriodMonths = growthMonths,
                isLastHarvest = final,
            ),
        )

    private fun baseRecord(
        number: String,
        day: Int,
        workType: WorkType,
        hasPhoto: Boolean = false,
        planting: PlantingReportSource? = null,
        watering: WateringReportSource? = null,
        fertilizing: FertilizingReportSource? = null,
        pestControl: PestControlReportSource? = null,
        weeding: WeedingReportSource? = null,
        harvest: HarvestReportSource? = null,
    ): CycleReportSourceRecord =
        CycleReportSourceRecord(
            id = id(number),
            workedAt = LocalDateTime.of(2026, 1, day, 9, 0),
            workType = workType,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            hasPhoto = hasPhoto,
            planting = planting,
            watering = watering,
            fertilizing = fertilizing,
            pestControl = pestControl,
            weeding = weeding,
            harvest = harvest,
        )

    private fun date(day: Int): LocalDate = LocalDate.of(2026, 1, day)

    private fun id(number: String): UUID {
        val suffix = number.padStart(12, '0').takeLast(12)
        return UUID.fromString("00000000-0000-0000-0000-$suffix")
    }

    private fun wateringAmountLabel(code: String): String =
        when (code) {
            "LOW" -> "적음"
            else -> code
        }

    private fun wateringMethodLabel(code: String): String =
        when (code) {
            "DRIP" -> "점적관수"
            "MANUAL" -> "수동"
            else -> code
        }

    private fun fertilizingMethodLabel(code: String): String =
        when (code) {
            "SOIL" -> "토양시비"
            else -> code
        }

    private fun harvestPartLabel(code: String): String =
        when (code) {
            "ROOT_BARK" -> "뿌리·껍질"
            "LEAF" -> "잎"
            else -> code
        }
}
