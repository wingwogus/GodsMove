package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackLifecycleServiceTest {
    @Mock private lateinit var feedbackRepository: CoachingFeedbackRepository
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초밭", roadAddress = "강원도 평창군")
    private val crop = Crop(
        id = UUID.randomUUID(),
        externalNo = 1,
        name = "황기",
        usePartCategory = CropUsePartCategory.ROOT_BARK,
    )
    private val record = FarmingRecord(
        id = UUID.randomUUID(),
        member = member,
        farm = farm,
        crop = crop,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 25,
        memo = "토양 표면을 확인하고 점적 관수한 기록입니다.",
        entryMode = "MANUAL",
        sourceRevision = 3,
    )

    @Test
    fun `enqueue stales older active attempts and publishes once for a new revision`() {
        val oldPending = feedback(status = CoachingFeedbackStatus.PENDING, sourceRevision = 1)
        val oldReady = feedback(status = CoachingFeedbackStatus.READY, sourceRevision = 2)
        val saved = feedback(id = UUID.randomUUID(), status = CoachingFeedbackStatus.PENDING, sourceRevision = 3)
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(FeedbackType.RECORD, record.id!!, 3))
            .thenReturn(null)
        `when`(feedbackRepository.findAllByFeedbackTypeAndRecord_IdAndStatusIn(
            FeedbackType.RECORD,
            record.id!!,
            listOf(CoachingFeedbackStatus.PENDING, CoachingFeedbackStatus.READY),
        )).thenReturn(listOf(oldPending, oldReady))
        `when`(feedbackRepository.save(any(CoachingFeedback::class.java))).thenReturn(saved)
        val service = RecordFeedbackLifecycleService(feedbackRepository, eventPublisher)

        val result = service.enqueue(record)

        assertThat(result).isSameAs(saved)
        assertThat(oldPending.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(oldReady.status).isEqualTo(CoachingFeedbackStatus.STALE)
        val event = ArgumentCaptor.forClass(RecordFeedbackPreparationRequested::class.java)
        verify(eventPublisher).publishEvent(event.capture())
        assertThat(event.value).isEqualTo(
            RecordFeedbackPreparationRequested(saved.id!!, member.id!!, record.id!!, 3),
        )
    }

    @Test
    fun `enqueue returns current revision without staling or publishing again`() {
        val current = feedback(id = UUID.randomUUID(), status = CoachingFeedbackStatus.PENDING, sourceRevision = 3)
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(FeedbackType.RECORD, record.id!!, 3))
            .thenReturn(current)
        val service = RecordFeedbackLifecycleService(feedbackRepository, eventPublisher)

        assertThat(service.enqueue(record)).isSameAs(current)

        verify(feedbackRepository).findByFeedbackTypeAndRecord_IdAndSourceRevision(FeedbackType.RECORD, record.id!!, 3)
    }

    private fun feedback(
        id: UUID? = null,
        status: CoachingFeedbackStatus,
        sourceRevision: Long,
    ): CoachingFeedback = CoachingFeedback(
        id = id,
        member = member,
        feedbackType = FeedbackType.RECORD,
        status = status,
        record = record,
        sourceRevision = sourceRevision,
    )
}
