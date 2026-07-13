package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.domain.coaching.ReportFeedback
import com.chamchamcham.domain.coaching.ReportFeedbackRepository
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
    private val feedbackId = UUID.randomUUID()
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
            weatherCondition = "맑음", weatherTemperature = 24, memo = "최종 수확", entryMode = "MANUAL",
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
    fun `enqueue creates pending feedback and publishes preparation request for a completed report`() {
        `when`(feedbackRepository.findByReport_Id(reportId)).thenReturn(null)
        `when`(feedbackRepository.save(Mockito.any(ReportFeedback::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ReportFeedback>(0).also { setId(it, feedbackId) }
        }

        val feedback = service.enqueue(report)

        assertThat(feedback.report).isSameAs(report)
        val event = ArgumentCaptor.forClass(ReportFeedbackPreparationRequested::class.java)
        verify(eventPublisher).publishEvent(event.capture())
        assertThat(event.value.memberId).isEqualTo(memberId)
        assertThat(event.value.reportId).isEqualTo(reportId)
    }

    @Test
    fun `enqueue returns existing feedback without another event`() {
        val existing = ReportFeedback.pending(member, report)
        `when`(feedbackRepository.findByReport_Id(reportId)).thenReturn(existing)

        assertThat(service.enqueue(report)).isSameAs(existing)

        verify(feedbackRepository, never()).save(Mockito.any())
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    private fun setId(target: Any, id: UUID) {
        val field = target.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(target, id)
    }
}
