package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackLifecycleServiceTest {
    @Mock private lateinit var feedbackRepository: RecordFeedbackRepository
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초밭", roadAddress = "강원도 평창군")
    private val crop = Crop(UUID.randomUUID(), 1, "황기", CropUsePartCategory.ROOT_BARK)
    private val record = FarmingRecord(
        id = UUID.randomUUID(), member = member, farm = farm, crop = crop, workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0), weatherCondition = "맑음", weatherTemperature = 25,
        memo = "관수 기록", entryMode = "MANUAL", sourceRevision = 3,
    )

    private val service by lazy { RecordFeedbackLifecycleService(feedbackRepository, eventPublisher) }

    @Test
    fun `enqueue reuses feedback for the same record revision`() {
        val current = feedback()
        `when`(feedbackRepository.findByRecord_IdAndSourceRevision(record.id!!, record.sourceRevision)).thenReturn(current)

        val result = service.enqueue(record)

        assertThat(result).isSameAs(current)
        verify(feedbackRepository, never()).save(org.mockito.Mockito.any())
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `enqueue stales active feedback and publishes preparation for the new revision`() {
        val active = feedback(status = RecordFeedbackStatus.READY)
        val saved = feedback(id = UUID.randomUUID())
        `when`(feedbackRepository.findByRecord_IdAndSourceRevision(record.id!!, record.sourceRevision)).thenReturn(null)
        `when`(feedbackRepository.findAllByRecord_IdAndStatusIn(record.id!!, listOf(RecordFeedbackStatus.PENDING, RecordFeedbackStatus.READY)))
            .thenReturn(listOf(active))
        `when`(feedbackRepository.save(org.mockito.Mockito.any(RecordFeedback::class.java))).thenReturn(saved)

        service.enqueue(record)

        assertThat(active.status).isEqualTo(RecordFeedbackStatus.STALE)
        val eventCaptor = ArgumentCaptor.forClass(RecordFeedbackPreparationRequested::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.value.feedbackId).isEqualTo(saved.id)
        assertThat(eventCaptor.value.sourceRevision).isEqualTo(record.sourceRevision)
    }

    @Test
    fun `retry moves failed feedback back to pending and republishes preparation`() {
        val failed = feedback(id = UUID.randomUUID())
        failed.markFailed("RETRIEVAL_FAILED")
        `when`(feedbackRepository.save(failed)).thenReturn(failed)

        val result = service.retry(failed)

        assertThat(result.status).isEqualTo(RecordFeedbackStatus.PENDING)
        verify(eventPublisher).publishEvent(org.mockito.Mockito.any(RecordFeedbackPreparationRequested::class.java))
    }

    private fun feedback(
        id: UUID = UUID.randomUUID(),
        status: RecordFeedbackStatus = RecordFeedbackStatus.PENDING,
    ): RecordFeedback {
        val feedback = RecordFeedback(id = id, member = member, record = record, status = RecordFeedbackStatus.PENDING, sourceRevision = record.sourceRevision)
        if (status == RecordFeedbackStatus.READY) {
            feedback.markReady("관수 기록", "관수 기록이 구체적입니다.", actions(), emptyList(), "PASS", emptyList(), "chat", "embed")
        }
        return feedback
    }

    private fun actions() = listOf(
        com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackNextActionDraft(
            com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue.TODAY,
            com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory.IRRIGATION,
            "관수 기록",
            "오늘 토양을 확인하세요.",
        ),
        com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackNextActionDraft(
            com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue.THIS_WEEK,
            com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory.CULTIVATION,
            "배수 기록",
            "이번 주 배수로를 점검하세요.",
        ),
    )
}
