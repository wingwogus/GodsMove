package com.chamchamcham.application.report

import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class FarmingCyclePartitionerTest {
    private val partitioner = FarmingCyclePartitioner()

    @Test
    fun `final harvest closes its cycle and partial harvest does not`() {
        val records = listOf(
            record("01", day = 1, WorkType.FERTILIZING),
            harvest("02", day = 10, final = false),
            harvest("03", day = 12, final = true),
            record("04", day = 20, WorkType.WATERING),
        )

        val slices = partitioner.partition(records)

        assertThat(slices).hasSize(2)
        assertThat(slices[0].status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(slices[0].records.map { it.id }).containsExactly(
            id("01"), id("02"), id("03"),
        )
        assertThat(slices[1].status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(slices[1].startBasis)
            .isEqualTo(FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST)
    }

    @Test
    fun `work before planting and ETC stay inside the cycle`() {
        val records = listOf(
            record("01", day = 1, WorkType.ETC),
            record("02", day = 3, WorkType.PLANTING),
            harvest("03", day = 90, final = true),
        )

        assertThat(partitioner.partition(records).single().records.map { it.workType })
            .containsExactly(WorkType.ETC, WorkType.PLANTING, WorkType.HARVEST)
    }

    @Test
    fun `equal timestamps are deterministically ordered by id`() {
        val slices = partitioner.partition(
            listOf(
                harvest("02", day = 1, final = true),
                record("01", day = 1, WorkType.WATERING),
            ),
        )

        assertThat(slices.single().records.map { it.id }).containsExactly(id("01"), id("02"))
    }

    private fun harvest(number: String, day: Long, final: Boolean): CycleReportSourceRecord =
        record(
            number = number,
            day = day,
            workType = WorkType.HARVEST,
            harvest = HarvestReportSource(
                amountKg = null,
                medicinalPart = CategoryRef("ROOT_BARK", "뿌리·껍질"),
                growthPeriodMonths = 12,
                isFinalHarvest = final,
            ),
        )

    private fun record(
        number: String,
        day: Long,
        workType: WorkType,
        harvest: HarvestReportSource? = null,
    ): CycleReportSourceRecord =
        CycleReportSourceRecord(
            id = id(number),
            workedAt = LocalDateTime.of(2026, 1, 1, 9, 0).plusDays(day),
            workType = workType,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            hasPhoto = false,
            harvest = harvest,
        )

    private fun id(number: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-0000000000$number")
}
