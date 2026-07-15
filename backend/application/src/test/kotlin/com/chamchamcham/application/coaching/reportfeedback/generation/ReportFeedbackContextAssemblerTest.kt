package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.report.CategoryRef
import com.chamchamcham.application.report.CycleReportSourceRecord
import com.chamchamcham.application.report.FarmingCyclePartitioner
import com.chamchamcham.application.report.FarmingCycleReportSourceLoader
import com.chamchamcham.application.report.HarvestReportSource
import com.chamchamcham.application.report.ReportScope
import com.chamchamcham.application.report.WateringReportSource
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.Coverage
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportQueryRepository
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.WateringStatistics
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackContextAssemblerTest {
    private val memberId = UUID.randomUUID()
    private val farmId = UUID.randomUUID()
    private val cropId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()
    private val previousFinalHarvestId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetWateringId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val targetFertilizingId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val targetFinalHarvestId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val startsAt = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val endsAt = LocalDateTime.of(2026, 7, 1, 9, 0)

    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var reportQueryRepository: FarmingCycleReportQueryRepository
    @Mock private lateinit var sourceLoader: FarmingCycleReportSourceLoader

    @Test
    fun `assemble selects the final harvest slice and keeps only the requested work type`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(
                watering = WateringStatistics(
                    recordCount = 4,
                    workedDayCount = 3,
                    averageIntervalDays = BigDecimal("2.5"),
                    photoAttachmentRatePct = BigDecimal("75"),
                ),
            ),
        ).also { setSourceRevision(it, 7) }
        val previous = completedReport(
            id = previousReportId,
            finalHarvestId = UUID.randomUUID(),
            startsAt = startsAt.minusYears(1),
            endsAt = endsAt.minusYears(1),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(
                    recordCount = 3,
                    workedDayCount = 2,
                    averageIntervalDays = BigDecimal("4"),
                    photoAttachmentRatePct = BigDecimal("50"),
                ),
            ),
        ).also { setSourceRevision(it, 3) }
        stubReports(report, previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val context = assembler().assemble(memberId, reportId, WorkType.WATERING)

        assertThat(context.schemaVersion).isEqualTo(3)
        assertThat(context.workType).isEqualTo(WorkType.WATERING)
        assertThat(context.records.map(ReportFeedbackRecord::id)).containsExactly(targetWateringId)
        assertThat(context.records).allMatch { it.workType == WorkType.WATERING }
        assertThat(context.report.statistics)
            .containsEntry("recordCount", 4)
            .containsEntry("averageIntervalDays", BigDecimal("2.5"))
            .doesNotContainKeys("watering", "fertilizing", "harvest")
        assertThat(context.report.sourceRevision).isEqualTo(7)
        assertThat(context.previousReport?.id).isEqualTo(previousReportId)
        assertThat(context.previousReport?.sourceRevision).isEqualTo(3)
        assertThat(context.previousReport?.statistics).containsEntry("recordCount", 3)
        assertThat(context.comparisons.map(ReportFeedbackComparison::metricKey)).containsExactly(
            "recordCount",
            "workedDayCount",
            "averageIntervalDays",
            "photoAttachmentRatePct",
        )
        assertThat(context.comparisons.first().difference).isEqualByComparingTo("1")
    }

    @Test
    fun `assemble omits previous comparison when the prior report has no matching work records`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 1)),
        )
        val previous = completedReport(
            id = previousReportId,
            finalHarvestId = UUID.randomUUID(),
            startsAt = startsAt.minusYears(1),
            endsAt = endsAt.minusYears(1),
            statistics = CycleReportStatistics.empty(),
        )
        stubReports(report, previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val context = assembler().assemble(memberId, reportId, WorkType.WATERING)

        assertThat(context.previousReport).isNull()
        assertThat(context.comparisons).isEmpty()
        assertThat(context.warnings).containsExactly("previous_work_type_unavailable")
    }

    @Test
    fun `assemble leaves comparisons empty when there is no previous report`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 1)),
        )
        stubReports(report, null)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val context = assembler().assemble(memberId, reportId, WorkType.WATERING)

        assertThat(context.previousReport).isNull()
        assertThat(context.comparisons).isEmpty()
        assertThat(context.warnings).containsExactly("previous_report_unavailable")
    }

    @Test
    fun `assemble calculates selected typed quantity before map conversion and preserves coverage`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(
                fertilizing = FertilizingStatistics(
                    recordCount = 2,
                    workedDayCount = 2,
                    totalAmountKg = BigDecimal("1.5000"),
                    amountCoverage = Coverage(1, 2),
                ),
            ),
        )
        val previous = completedReport(
            id = previousReportId,
            finalHarvestId = UUID.randomUUID(),
            startsAt = startsAt.minusYears(1),
            endsAt = endsAt.minusYears(1),
            statistics = CycleReportStatistics(
                fertilizing = FertilizingStatistics(
                    recordCount = 1,
                    workedDayCount = 1,
                    totalAmountKg = BigDecimal("1.0000"),
                    amountCoverage = Coverage(1, 1),
                ),
            ),
        )
        stubReports(report, previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val comparison = assembler().assemble(memberId, reportId, WorkType.FERTILIZING)
            .comparisons.single { it.metricKey == "fertilizing.totalAmountKg" }

        assertThat(comparison.difference).isEqualByComparingTo("0.5")
        assertThat(comparison.currentCoverage).isEqualTo(Coverage(1, 2))
        assertThat(comparison.previousCoverage).isEqualTo(Coverage(1, 1))
    }

    @Test
    fun `assemble uses the canonical final harvest boundary when reports have the same end time`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 1)),
        )
        val previous = completedReport(
            id = previousReportId,
            finalHarvestId = previousFinalHarvestId,
            startsAt = startsAt.minusYears(1),
            endsAt = endsAt,
            statistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 2)),
        )
        stubReports(report, previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val context = assembler().assemble(memberId, reportId, WorkType.WATERING)

        assertThat(context.previousReport?.id).isEqualTo(previousReportId)
        verify(reportQueryRepository).findPreviousCompleted(
            memberId,
            farmId,
            cropId,
            endsAt,
            targetFinalHarvestId,
        )
    }

    @Test
    fun `assemble rejects a work type absent from the target slice`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics.empty(),
        )
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        assertThatThrownBy { assembler().assemble(memberId, reportId, WorkType.PRUNING) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.INVALID_CONTEXT)
            }
    }

    private fun assembler() = ReportFeedbackContextAssembler(
        reportRepository = reportRepository,
        reportQueryRepository = reportQueryRepository,
        sourceLoader = sourceLoader,
        partitioner = FarmingCyclePartitioner(),
        objectMapper = jacksonObjectMapper(),
        comparisonCalculator = ReportFeedbackComparisonCalculator(),
    )

    private fun stubReports(report: FarmingCycleReport, previous: FarmingCycleReport?) {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(
            reportQueryRepository.findPreviousCompleted(
                memberId,
                farmId,
                cropId,
                requireNotNull(report.endsAt),
                requireNotNull(report.finalHarvestRecord?.id),
            ),
        ).thenReturn(previous)
    }

    private fun cycleRecords(): List<CycleReportSourceRecord> = listOf(
        harvestSource(previousFinalHarvestId, startsAt, isFinal = true),
        CycleReportSourceRecord(
            id = targetWateringId,
            workedAt = startsAt,
            workType = WorkType.WATERING,
            weatherCondition = "맑음",
            weatherTemperature = 24,
            hasPhoto = false,
            memo = "점적관수로 수분을 보충했어요.",
            watering = WateringReportSource(CategoryRef("LOW", "적음"), CategoryRef("DRIP", "점적")),
        ),
        CycleReportSourceRecord(
            id = targetFertilizingId,
            workedAt = startsAt.plusDays(1),
            workType = WorkType.FERTILIZING,
            weatherCondition = "맑음",
            weatherTemperature = 25,
            hasPhoto = false,
            memo = "밑거름을 줬어요.",
        ),
        harvestSource(targetFinalHarvestId, endsAt, isFinal = true),
    )

    private fun harvestSource(id: UUID, workedAt: LocalDateTime, isFinal: Boolean) = CycleReportSourceRecord(
        id = id,
        workedAt = workedAt,
        workType = WorkType.HARVEST,
        weatherCondition = "맑음",
        weatherTemperature = 24,
        hasPhoto = false,
        memo = "마지막 수확이에요.",
        harvest = HarvestReportSource(
            amountKg = BigDecimal("5.0"),
            medicinalPart = CategoryRef("ROOT_BARK", "뿌리/근피"),
            growthPeriodMonths = 12,
            isLastHarvest = isFinal,
        ),
    )

    private fun completedReport(
        id: UUID,
        finalHarvestId: UUID,
        startsAt: LocalDateTime,
        endsAt: LocalDateTime,
        statistics: CycleReportStatistics,
    ): FarmingCycleReport {
        val member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        val farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "강원도 평창군")
        val crop = Crop(id = cropId, externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
        val harvest = FarmingRecord(
            id = finalHarvestId,
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.HARVEST,
            workedAt = endsAt,
            weatherCondition = "맑음",
            weatherTemperature = 24,
            memo = "최종 수확",
            entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
        )
        return FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = startsAt,
                endsAt = endsAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = harvest,
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        ).also { setId(it, id) }
    }

    private fun setId(target: Any, id: UUID) {
        val field = target.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(target, id)
    }

    private fun setSourceRevision(target: FarmingCycleReport, revision: Long) {
        val field = target.javaClass.getDeclaredField("sourceRevision")
        field.isAccessible = true
        field.set(target, revision)
    }
}
