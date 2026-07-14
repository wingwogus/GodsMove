package com.chamchamcham.domain.report

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class FarmingCycleReportTest {
    private val workedAt = LocalDateTime.of(2026, 6, 15, 8, 30)
    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초농장", roadAddress = "강원도 평창군")
    private val crop = Crop(
        id = UUID.randomUUID(),
        externalNo = 422,
        name = "황기",
        usePartCategory = CropUsePartCategory.ROOT_BARK,
    )
    private val finalHarvest = FarmingRecord(
        id = UUID.randomUUID(),
        member = member,
        farm = farm,
        crop = crop,
        workType = WorkType.HARVEST,
        workedAt = workedAt,
        weatherCondition = "맑음",
        weatherTemperature = 24,
        memo = "최종 수확",
        entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
    )

    @Test
    fun `same projection keeps revision and changed typed statistics increment it`() {
        val initial = activeProjection(CycleReportStatistics.empty())
        val report = FarmingCycleReport.create(member, farm, crop, initial)
        val changed = initial.copy(
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1),
            ),
        )

        assertThat(report.sourceRevision).isEqualTo(1)
        assertThat(report.applyProjection(initial)).isFalse()
        assertThat(report.sourceRevision).isEqualTo(1)
        assertThat(report.applyProjection(changed)).isTrue()
        assertThat(report.sourceRevision).isEqualTo(2)
    }

    @Test
    fun `active rejects an end boundary and completed requires one`() {
        assertThatThrownBy {
            activeReport().applyProjection(
                activeProjection().copy(endsAt = workedAt, finalHarvestRecord = finalHarvest),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            activeReport().applyProjection(
                completedProjection().copy(finalHarvestRecord = null),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `supersede preserves statistics and increments once`() {
        val report = activeReport(
            CycleReportStatistics(watering = WateringStatistics(recordCount = 1)),
        )
        val revision = report.sourceRevision

        assertThat(report.supersede()).isTrue()
        assertThat(report.statistics.watering.recordCount).isEqualTo(1)
        assertThat(report.sourceRevision).isEqualTo(revision + 1)
        assertThat(report.supersede()).isFalse()
    }

    private fun activeReport(statistics: CycleReportStatistics = CycleReportStatistics.empty()): FarmingCycleReport {
        return FarmingCycleReport.create(member, farm, crop, activeProjection(statistics))
    }

    private fun activeProjection(
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReportProjection {
        return FarmingCycleReportProjection(
            status = FarmingCycleReportStatus.ACTIVE,
            startsAt = workedAt,
            endsAt = null,
            startBasis = FarmingCycleStartBasis.FIRST_RECORD,
            finalHarvestRecord = null,
            statisticsSchemaVersion = 1,
            statistics = statistics,
        )
    }

    private fun completedProjection(
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReportProjection {
        return FarmingCycleReportProjection(
            status = FarmingCycleReportStatus.COMPLETED,
            startsAt = workedAt.minusMonths(6),
            endsAt = workedAt,
            startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST,
            finalHarvestRecord = finalHarvest,
            statisticsSchemaVersion = 1,
            statistics = statistics,
        )
    }
}
