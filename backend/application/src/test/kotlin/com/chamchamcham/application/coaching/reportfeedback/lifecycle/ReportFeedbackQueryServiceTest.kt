package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemDraft
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
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
import com.chamchamcham.domain.report.FertilizingStatistics
import com.chamchamcham.domain.report.HarvestStatistics
import com.chamchamcham.domain.report.WateringStatistics
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackQueryServiceTest {
    private val memberId = uuid("000000000001")
    private val reportId = uuid("000000000101")
    private val wateringFeedbackId = uuid("000000000201")
    private val fertilizingFeedbackId = uuid("000000000202")
    private val harvestFeedbackId = uuid("000000000203")
    private val createdAt = LocalDateTime.of(2026, 7, 14, 10, 0)
    private val updatedAt = LocalDateTime.of(2026, 7, 14, 10, 1)

    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var feedbackRepository: ReportFeedbackRepository
    @Mock private lateinit var lifecycleService: ReportFeedbackLifecycleService

    private lateinit var service: ReportFeedbackQueryService
    private lateinit var member: Member
    private lateinit var completedReport: FarmingCycleReport

    @BeforeEach
    fun setUp() {
        service = ReportFeedbackQueryService(reportRepository, feedbackRepository, lifecycleService)
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        completedReport = report(
            FarmingCycleReportStatus.COMPLETED,
            statisticsFor(WorkType.WATERING, WorkType.FERTILIZING, WorkType.HARVEST),
        )
    }

    @Test
    fun `completed report returns work type feedbacks by latest work date with ready content only`() {
        val watering = readyFeedback()
        val fertilizing = pendingFeedback()
        val harvest = failedFeedback()
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(completedReport)
        `when`(feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId))
            .thenReturn(listOf(harvest, watering, fertilizing))

        val result = service.get(memberId, reportId)

        assertThat(result.reportId).isEqualTo(reportId)
        assertThat(result.feedbacks.map { it.workType }).containsExactly(
            WorkType.HARVEST,
            WorkType.WATERING,
            WorkType.FERTILIZING,
        )

        val ready = result.feedbacks.first { it.workType == WorkType.WATERING }
        assertThat(ready.feedbackId).isEqualTo(wateringFeedbackId)
        assertThat(ready.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(ready.inputPrepared).isTrue()
        assertThat(ready.failureCode).isNull()
        assertThat(ready.content?.summary).isEqualTo("관수 작업 요약")
        assertThat(ready.content?.comparisons?.map { it.text })
            .containsExactly("직전 재배보다 물 주기 기록이 한 번 늘었어요.")
        assertThat(ready.content?.strengths?.map { it.text }).containsExactly("수분 상태를 꾸준히 확인했어요.")
        assertThat(ready.content?.improvements?.map { it.text }).containsExactly("관수 간격도 함께 비교해 보세요.")
        assertThat(ready.content?.nextActions?.map { it.text }).containsExactly("내일 토양 수분을 다시 확인하세요.")
        assertThat(ready.createdAt).isEqualTo(createdAt)
        assertThat(ready.updatedAt).isEqualTo(updatedAt)

        val pending = result.feedbacks.first { it.workType == WorkType.FERTILIZING }
        assertThat(pending.feedbackId).isEqualTo(fertilizingFeedbackId)
        assertThat(pending.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(pending.inputPrepared).isFalse()
        assertThat(pending.failureCode).isNull()
        assertThat(pending.content).isNull()

        val failed = result.feedbacks.first { it.workType == WorkType.HARVEST }
        assertThat(failed.feedbackId).isEqualTo(harvestFeedbackId)
        assertThat(failed.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(failed.inputPrepared).isTrue()
        assertThat(failed.failureCode).isEqualTo("STRUCTURED_OUTPUT_INVALID")
        assertThat(failed.content).isNull()
    }

    @Test
    fun `completed report without feedback rows returns an empty collection`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(completedReport)
        `when`(feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId)).thenReturn(emptyList())

        val result = service.get(memberId, reportId)

        assertThat(result.reportId).isEqualTo(reportId)
        assertThat(result.feedbacks).isEmpty()
    }

    @Test
    fun `stale feedback hides persisted content and preparation details`() {
        val stale = readyFeedback().also(ReportFeedback::markStale)
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(completedReport)
        `when`(feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId)).thenReturn(listOf(stale))

        val result = service.get(memberId, reportId).feedbacks.single()

        assertThat(result.status).isEqualTo(ReportFeedbackStatus.STALE)
        assertThat(result.inputPrepared).isFalse()
        assertThat(result.failureCode).isNull()
        assertThat(result.content).isNull()
    }

    @Test
    fun `feedback for a removed work type is excluded from the current report response`() {
        val wateringOnlyReport = report(
            FarmingCycleReportStatus.COMPLETED,
            statisticsFor(WorkType.WATERING),
        )
        val watering = readyFeedback()
        val removedHarvest = failedFeedback().also(ReportFeedback::markStale)
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(wateringOnlyReport)
        `when`(feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId))
            .thenReturn(listOf(removedHarvest, watering))

        val result = service.get(memberId, reportId)

        assertThat(result.feedbacks.map { it.workType }).containsExactly(WorkType.WATERING)
    }

    @Test
    fun `active report is not exposed as report feedback`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId))
            .thenReturn(report(FarmingCycleReportStatus.ACTIVE))

        assertReportNotFound()
        verifyNoInteractions(feedbackRepository)
    }

    @Test
    fun `superseded report is not exposed as report feedback`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId))
            .thenReturn(report(FarmingCycleReportStatus.SUPERSEDED))

        assertReportNotFound()
        verifyNoInteractions(feedbackRepository)
    }

    @Test
    fun `report outside the member scope is not exposed`() {
        `when`(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(null)

        assertReportNotFound()
        verifyNoInteractions(feedbackRepository)
    }

    @Test
    fun `regenerate delegates stale feedback to the locked lifecycle`() {
        val stale = readyFeedback().also(ReportFeedback::markStale)
        `when`(lifecycleService.regenerate(memberId, reportId, WorkType.WATERING)).thenAnswer {
            stale.retry("d".repeat(64))
            stale
        }

        val result = service.regenerate(memberId, reportId, WorkType.WATERING)

        assertThat(result.workType).isEqualTo(WorkType.WATERING)
        assertThat(result.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(result.inputPrepared).isFalse()
        assertThat(result.failureCode).isNull()
        verify(reportRepository, never()).findByIdAndMember_Id(reportId, memberId)
    }

    @Test
    fun `regenerate propagates locked lifecycle validation without preloading the report`() {
        `when`(lifecycleService.regenerate(memberId, reportId, WorkType.HARVEST))
            .thenThrow(BusinessException(ErrorCode.REPORT_FEEDBACK_NOT_FOUND))

        assertThatThrownBy { service.regenerate(memberId, reportId, WorkType.HARVEST) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
        verify(reportRepository, never()).findByIdAndMember_Id(reportId, memberId)
    }

    private fun assertReportNotFound() {
        assertThatThrownBy { service.get(memberId, reportId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
    }

    private fun readyFeedback(): ReportFeedback = feedback(
        id = wateringFeedbackId,
        workType = WorkType.WATERING,
    ).also {
        it.attachInputSnapshot(mapOf("schemaVersion" to 2))
        it.markReady(
            summary = "관수 작업 요약",
            items = listOf(
                ReportFeedbackItemDraft(
                    ReportFeedbackItemSection.COMPARISON,
                    "직전보다 기록 1회 증가",
                    "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
                ),
                ReportFeedbackItemDraft(ReportFeedbackItemSection.STRENGTH, "기록 근거", "수분 상태를 꾸준히 확인했어요."),
                ReportFeedbackItemDraft(ReportFeedbackItemSection.IMPROVEMENT, "통계 근거", "관수 간격도 함께 비교해 보세요."),
                ReportFeedbackItemDraft(ReportFeedbackItemSection.NEXT_ACTION, "기록 근거", "내일 토양 수분을 다시 확인하세요."),
            ),
            citations = listOf(mapOf("kind" to "record", "id" to "internal-only")),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "chat-model",
            embeddingModel = "embedding-model",
        )
    }

    private fun pendingFeedback(): ReportFeedback = feedback(
        id = fertilizingFeedbackId,
        workType = WorkType.FERTILIZING,
    )

    private fun failedFeedback(): ReportFeedback = feedback(
        id = harvestFeedbackId,
        workType = WorkType.HARVEST,
    ).also {
        it.attachInputSnapshot(mapOf("schemaVersion" to 2))
        it.markFailed("STRUCTURED_OUTPUT_INVALID")
    }

    private fun feedback(id: UUID, workType: WorkType): ReportFeedback =
        ReportFeedback(
            id = id,
            member = member,
            report = completedReport,
            workType = workType,
            status = ReportFeedbackStatus.PENDING,
            sourceFingerprint = "${workType.ordinal + 1}".repeat(64).take(64),
        ).also {
            ReflectionTestUtils.setField(it, "createdAt", createdAt)
            ReflectionTestUtils.setField(it, "updatedAt", updatedAt)
        }

    private fun report(
        status: FarmingCycleReportStatus,
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReport {
        val farm = Farm(
            id = uuid("000000000301"),
            owner = member,
            name = "약초농장",
            roadAddress = "강원도 평창군",
        )
        val crop = Crop(
            id = uuid("000000000401"),
            externalNo = 422,
            name = "황기",
            usePartCategory = CropUsePartCategory.ROOT_BARK,
        )
        val finalHarvest = FarmingRecord(
            id = uuid("000000000501"),
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.HARVEST,
            workedAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            weatherCondition = "맑음",
            weatherTemperature = 24,
            memo = "최종 수확",
            entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
        )
        val sourceStatus = if (status == FarmingCycleReportStatus.ACTIVE) {
            FarmingCycleReportStatus.ACTIVE
        } else {
            FarmingCycleReportStatus.COMPLETED
        }
        val report = FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = sourceStatus,
                startsAt = LocalDateTime.of(2026, 2, 1, 9, 0),
                endsAt = if (sourceStatus == FarmingCycleReportStatus.COMPLETED) finalHarvest.workedAt else null,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = if (sourceStatus == FarmingCycleReportStatus.COMPLETED) finalHarvest else null,
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        )
        ReflectionTestUtils.setField(report, "id", reportId)
        if (status == FarmingCycleReportStatus.SUPERSEDED) {
            report.supersede()
        }
        return report
    }

    private fun statisticsFor(vararg workTypes: WorkType) = CycleReportStatistics(
        watering = WateringStatistics(
            recordCount = WorkType.WATERING.countIn(workTypes),
            lastWorkedOn = LocalDate.of(2026, 6, 2),
        ),
        fertilizing = FertilizingStatistics(
            recordCount = WorkType.FERTILIZING.countIn(workTypes),
            lastWorkedOn = LocalDate.of(2026, 6, 1),
        ),
        harvest = HarvestStatistics(
            recordCount = WorkType.HARVEST.countIn(workTypes),
            lastWorkedOn = LocalDate.of(2026, 6, 3),
        ),
    )

    private fun WorkType.countIn(workTypes: Array<out WorkType>): Int =
        if (this in workTypes) 1 else 0

    private fun uuid(tail: String): UUID =
        UUID.fromString("00000000-0000-0000-0000-$tail")
}
