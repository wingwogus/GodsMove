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
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
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
    @Mock private lateinit var sourceLoader: FarmingCycleReportSourceLoader

    @Test
    fun `assemble selects the final harvest slice and keeps only the requested work type`() {
        val report = completedReport(
            id = reportId,
            finalHarvestId = targetFinalHarvestId,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1, averageIntervalDays = BigDecimal("3.5")),
            ),
        )
        val previous = completedReport(
            id = previousReportId,
            finalHarvestId = UUID.randomUUID(),
            startsAt = startsAt.minusYears(1),
            endsAt = endsAt.minusYears(1),
            statistics = CycleReportStatistics(watering = WateringStatistics(recordCount = 2)),
        )
        stubReports(report, previous)
        `when`(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(cycleRecords())

        val context = assembler().assemble(memberId, reportId, WorkType.WATERING)

        assertThat(context.schemaVersion).isEqualTo(2)
        assertThat(context.workType).isEqualTo(WorkType.WATERING)
        assertThat(context.records.map(ReportFeedbackRecord::id)).containsExactly(targetWateringId)
        assertThat(context.records).allMatch { it.workType == WorkType.WATERING }
        assertThat(context.report.statistics)
            .containsEntry("recordCount", 1)
            .containsEntry("averageIntervalDays", BigDecimal("3.5"))
            .doesNotContainKeys("watering", "fertilizing", "harvest")
        assertThat(context.previousReport?.id).isEqualTo(previousReportId)
        assertThat(context.previousReport?.statistics).containsEntry("recordCount", 2)
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

        assertThat(assembler().assemble(memberId, reportId, WorkType.WATERING).previousReport).isNull()
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
        sourceLoader = sourceLoader,
        partitioner = FarmingCyclePartitioner(),
        objectMapper = jacksonObjectMapper(),
    )

    private fun stubReports(report: FarmingCycleReport, previous: FarmingCycleReport?) {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
        `when`(
            reportRepository.findTopByMember_IdAndFarm_IdAndCrop_IdAndStatusAndEndsAtBeforeAndIdNotOrderByEndsAtDescIdDesc(
                memberId,
                farmId,
                cropId,
                FarmingCycleReportStatus.COMPLETED,
                endsAt,
                reportId,
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
            isFinalHarvest = isFinal,
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
            entryMode = "MANUAL",
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
}
