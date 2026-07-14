package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
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
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackLifecycleServiceTest {
    private val memberId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val wateringFeedbackId = UUID.randomUUID()
    private val harvestFeedbackId = UUID.randomUUID()
    private lateinit var member: Member
    private lateinit var report: FarmingCycleReport

    @Mock private lateinit var feedbackRepository: ReportFeedbackRepository
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var service: ReportFeedbackLifecycleService

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초농장", roadAddress = "강원도 평창군")
        val crop = Crop(
            id = UUID.randomUUID(),
            externalNo = 422,
            name = "황기",
            usePartCategory = CropUsePartCategory.ROOT_BARK,
        )
        val finalHarvest = FarmingRecord(
            id = UUID.randomUUID(), member = member, farm = farm, crop = crop,
            workType = WorkType.HARVEST, workedAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            weatherCondition = "맑음", weatherTemperature = 24, memo = "최종 수확", entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
        )
        report = FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = finalHarvest.workedAt.minusMonths(5),
                endsAt = finalHarvest.workedAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = CycleReportStatistics.empty(),
            ),
        ).also { setId(it, reportId) }
        service = ReportFeedbackLifecycleService(feedbackRepository, eventPublisher)
    }

    @Test
    fun `enqueue creates ordered pending feedback for every recorded work type`() {
        `when`(feedbackRepository.existsByReport_Id(reportId)).thenReturn(false)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { invocation ->
            invocation.getArgument<List<ReportFeedback>>(0).also { feedbacks ->
                setId(feedbacks[0], wateringFeedbackId)
                setId(feedbacks[1], harvestFeedbackId)
            }
        }

        val feedbacks = service.enqueue(
            report,
            setOf(WorkType.HARVEST, WorkType.WATERING),
        )

        assertThat(feedbacks.map(ReportFeedback::workType))
            .containsExactly(WorkType.WATERING, WorkType.HARVEST)
        assertThat(feedbacks).allMatch { it.report === report }
        val events = ArgumentCaptor.forClass(ReportFeedbackPreparationRequested::class.java)
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        assertThat(events.allValues.map(ReportFeedbackPreparationRequested::feedbackId))
            .containsExactly(wateringFeedbackId, harvestFeedbackId)
        assertThat(events.allValues.map(ReportFeedbackPreparationRequested::workType))
            .containsExactly(WorkType.WATERING, WorkType.HARVEST)
        assertThat(events.allValues).allMatch { it.memberId == memberId && it.reportId == reportId }
    }

    @Test
    fun `enqueue returns the existing work type collection without another event`() {
        val existing = listOf(ReportFeedback.pending(member, report, WorkType.WATERING))
        `when`(feedbackRepository.existsByReport_Id(reportId)).thenReturn(true)
        `when`(feedbackRepository.findAllByReport_IdAndMember_Id(reportId, memberId)).thenReturn(existing)

        assertThat(service.enqueue(report, setOf(WorkType.WATERING, WorkType.HARVEST)))
            .containsExactlyElementsOf(existing)

        verify(feedbackRepository, never()).saveAll(Mockito.anyList<ReportFeedback>())
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `retry moves failed feedback to pending and republishes preparation`() {
        val feedback = ReportFeedback(
            id = wateringFeedbackId,
            member = member,
            report = report,
            workType = WorkType.WATERING,
            status = ReportFeedbackStatus.PENDING,
        ).also {
            it.attachInputSnapshot(mapOf("schemaVersion" to 3))
            it.markFailed("STRUCTURED_OUTPUT_INVALID")
        }
        `when`(feedbackRepository.save(feedback)).thenReturn(feedback)

        val retried = service.retry(feedback)

        assertThat(retried.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(retried.inputSnapshot).isNull()
        verify(feedbackRepository).save(feedback)
        verify(eventPublisher).publishEvent(
            ReportFeedbackPreparationRequested(
                feedbackId = wateringFeedbackId,
                memberId = memberId,
                reportId = reportId,
                workType = WorkType.WATERING,
            ),
        )
    }

    private fun setId(target: Any, id: UUID) {
        val field = target.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(target, id)
    }
}
