package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.report.CategoryRef
import com.chamchamcham.application.report.CycleReportSourceRecord
import com.chamchamcham.application.report.FarmingCycleReportSourceLoader
import com.chamchamcham.application.report.ReportScope
import com.chamchamcham.application.report.WateringReportSource
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackContextAssemblerTest {
    private val memberId = UUID.randomUUID()
    private val farmId = UUID.randomUUID()
    private val cropId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val previousId = UUID.randomUUID()
    private val start = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val end = LocalDateTime.of(2026, 7, 1, 9, 0)

    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var sourceLoader: FarmingCycleReportSourceLoader

    @Test
    fun `assemble keeps only target period records and includes previous completed report when present`() {
        val report = completedReport(reportId, start, end)
        val previous = completedReport(previousId, start.minusYears(1), end.minusYears(1))
        val inRangeId = UUID.randomUUID()
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(
            reportRepository.findTopByMember_IdAndFarm_IdAndCrop_IdAndStatusAndEndsAtBeforeAndIdNotOrderByEndsAtDescIdDesc(
                memberId,
                farmId,
                cropId,
                FarmingCycleReportStatus.COMPLETED,
                end,
                reportId,
            ),
        ).thenReturn(previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(
            listOf(record(UUID.randomUUID(), start.minusDays(1)), record(inRangeId, start.plusDays(1)), record(UUID.randomUUID(), end.plusDays(1))),
        )

        val context = ReportFeedbackContextAssembler(reportRepository, sourceLoader).assemble(memberId, reportId)

        assertThat(context.records.map(ReportFeedbackRecord::id)).containsExactly(inRangeId)
        assertThat(context.previousReport?.id).isEqualTo(previousId)
        assertThat(context.records.single().memo).isEqualTo("점적관수로 수분을 보충했습니다.")
        assertThat(context.records.single().details)
            .containsEntry("weatherCondition", "맑음")
            .containsEntry("weatherTemperature", 24)
            .containsEntry("hasPhoto", false)
            .containsKey("watering")
    }

    private fun completedReport(id: UUID, startsAt: LocalDateTime, endsAt: LocalDateTime): FarmingCycleReport {
        val member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        val farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "강원도 평창군")
        val crop = Crop(id = cropId, externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
        val harvest = FarmingRecord(
            id = UUID.randomUUID(), member = member, farm = farm, crop = crop, workType = WorkType.HARVEST,
            workedAt = endsAt, weatherCondition = "맑음", weatherTemperature = 24, memo = "최종 수확", entryMode = "MANUAL",
        )
        return FarmingCycleReport.create(
            member, farm, crop,
            FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED, startsAt = startsAt, endsAt = endsAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD, finalHarvestRecord = harvest,
                statisticsSchemaVersion = 1, statistics = CycleReportStatistics.empty(),
            ),
        ).also { setId(it, id) }
    }

    private fun record(id: UUID, workedAt: LocalDateTime) = CycleReportSourceRecord(
        id = id, workedAt = workedAt, workType = WorkType.WATERING, weatherCondition = "맑음", weatherTemperature = 24,
        hasPhoto = false, memo = "점적관수로 수분을 보충했습니다.",
        watering = WateringReportSource(CategoryRef("LOW", "적음"), CategoryRef("HOSE", "호스")),
    )

    private fun setId(target: Any, id: UUID) {
        val field = target.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(target, id)
    }
}
