package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.coaching.recordfeedback.generation.CommonFeedbackDetail
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContextAssembler
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackRecordContext
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackPreparationServiceTest {
    @Mock private lateinit var feedbackRepository: RecordFeedbackRepository
    @Mock private lateinit var contextAssembler: RecordFeedbackContextAssembler
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초밭", roadAddress = "강원도 평창군")
    private val crop = Crop(UUID.randomUUID(), 1, "황기", CropUsePartCategory.ROOT_BARK)
    private val record = FarmingRecord(
        id = UUID.randomUUID(), member = member, farm = farm, crop = crop, workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0), weatherCondition = "맑음", weatherTemperature = 25,
        memo = "관수 기록", entryMode = "MANUAL", sourceRevision = 3,
    )

    @Test
    fun `prepare stores immutable snapshot and publishes generation request`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenReturn(context())
        val service = service()

        service.prepare(event)

        assertThat(feedback.inputSnapshot).isNotNull
        verify(eventPublisher).publishEvent(org.mockito.Mockito.any(RecordFeedbackGenerationRequested::class.java))
    }

    @Test
    fun `prepare publishes generation only after snapshot transaction resources are cleaned up`() {
        val feedback = feedback()
        val event = event(feedback)
        val transactionManager = RecordingTransactionManager()
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenReturn(context())
        doAnswer {
            assertThat(transactionManager.resourcesCleanedUp).isTrue()
            null
        }.`when`(eventPublisher).publishEvent(
            org.mockito.Mockito.any(RecordFeedbackGenerationRequested::class.java),
        )
        val service = RecordFeedbackPreparationService(
            feedbackRepository,
            contextAssembler,
            objectMapper,
            eventPublisher,
            transactionManager,
        )

        service.prepare(event)

        assertThat(transactionManager.resourcesCleanedUp).isTrue()
        verify(eventPublisher).publishEvent(org.mockito.Mockito.any(RecordFeedbackGenerationRequested::class.java))
    }

    @Test
    fun `prepare records context assembly failure`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenThrow(IllegalStateException("weather unavailable"))

        service().prepare(event)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("CONTEXT_ASSEMBLY_FAILED")
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `prepare keeps stale feedback stale when context assembly finishes late`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenAnswer {
            feedback.markStale()
            context()
        }

        service().prepare(event)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.STALE)
        assertThat(feedback.inputSnapshot).isNull()
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `prepare propagates fatal errors`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenThrow(AssertionError("fatal"))

        assertThrows(AssertionError::class.java) { service().prepare(event) }
    }

    private fun service() = RecordFeedbackPreparationService(
        feedbackRepository, contextAssembler, objectMapper, eventPublisher, NoOpTransactionManager(),
    )

    private fun feedback() = RecordFeedback(
        id = UUID.randomUUID(), member = member, record = record, status = RecordFeedbackStatus.PENDING, sourceRevision = record.sourceRevision,
    )

    private fun event(feedback: RecordFeedback) = RecordFeedbackPreparationRequested(
        feedbackId = feedback.id!!, memberId = member.id!!, recordId = record.id!!, sourceRevision = record.sourceRevision,
    )

    private fun context() = RecordFeedbackContext(
        member = RecordFeedbackMemberContext(member.id!!, null, null),
        farm = RecordFeedbackFarmContext(farm.id!!, farm.name, farm.roadAddress, null, null),
        crop = RecordFeedbackCropContext(crop.id!!, crop.name, crop.usePartCategory),
        record = RecordFeedbackRecordContext(record.id!!, record.sourceRevision, record.workedAt, record.workType, CommonFeedbackDetail, record.weatherCondition, record.weatherTemperature, record.memo, 0),
        weather = null,
    )

    private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        var resourcesCleanedUp = false
            private set

        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
        override fun doCleanupAfterCompletion(transaction: Any) {
            resourcesCleanedUp = true
        }
    }
}
