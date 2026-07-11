package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.CommonFeedbackDetail
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContextAssembler
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackRecordContext
import com.chamchamcham.application.coaching.rag.record.RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackPreparationServiceTest {
    @Mock private lateinit var feedbackRepository: CoachingFeedbackRepository
    @Mock private lateinit var contextAssembler: RecordFeedbackContextAssembler
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
        workType = WorkType.PRUNING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 25,
        memo = "웃자란 가지를 정리했습니다.",
        entryMode = "MANUAL",
        sourceRevision = 3,
    )
    private lateinit var service: RecordFeedbackPreparationService

    @BeforeEach
    fun setUp() {
        service = RecordFeedbackPreparationService(
            feedbackRepository = feedbackRepository,
            contextAssembler = contextAssembler,
            objectMapper = Jackson2ObjectMapperBuilder.json().build(),
            eventPublisher = eventPublisher,
        )
    }

    @Test
    fun `prepare stores the assembled context as an immutable input snapshot`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenReturn(context())

        service.prepare(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        assertThat(feedback.inputSnapshot)
            .containsEntry("schemaVersion", RECORD_FEEDBACK_CONTEXT_SCHEMA_VERSION)
            .containsKey("record")
        verify(contextAssembler).assemble(event.memberId, event.recordId)
    }

    @Test
    fun `preparation publishes generation request only after snapshot is attached`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenReturn(context())

        service.prepare(event)

        assertThat(feedback.inputSnapshot).isNotNull
        verify(eventPublisher).publishEvent(
            RecordFeedbackGenerationRequested(
                feedbackId = event.feedbackId,
                memberId = event.memberId,
                recordId = event.recordId,
                sourceRevision = event.sourceRevision,
            ),
        )
    }

    @Test
    fun `prepare marks pending feedback failed when context assembly fails`() {
        val feedback = feedback()
        val event = event(feedback)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(event.memberId, event.recordId)).thenThrow(IllegalStateException("unavailable"))

        service.prepare(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("CONTEXT_ASSEMBLY_FAILED")
        verifyNoMoreInteractions(eventPublisher)
    }

    @Test
    fun `prepare ignores an event for a different source revision`() {
        val feedback = feedback(sourceRevision = 4)
        val event = event(feedback, sourceRevision = 3)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)

        service.prepare(event)

        assertThat(feedback.inputSnapshot).isNull()
        verify(contextAssembler, never()).assemble(event.memberId, event.recordId)
    }

    private fun feedback(sourceRevision: Long = 3): CoachingFeedback = CoachingFeedback(
        id = UUID.randomUUID(),
        member = member,
        feedbackType = FeedbackType.RECORD,
        status = CoachingFeedbackStatus.PENDING,
        record = record,
        sourceRevision = sourceRevision,
    )

    private fun event(
        feedback: CoachingFeedback,
        sourceRevision: Long = feedback.sourceRevision,
    ) = RecordFeedbackPreparationRequested(
        feedbackId = feedback.id!!,
        memberId = member.id!!,
        recordId = record.id!!,
        sourceRevision = sourceRevision,
    )

    private fun context() = RecordFeedbackContext(
        member = RecordFeedbackMemberContext(member.id!!, null, null),
        farm = RecordFeedbackFarmContext(farm.id!!, farm.name, farm.roadAddress, null, null),
        crop = RecordFeedbackCropContext(crop.id!!, crop.name, crop.usePartCategory),
        record = RecordFeedbackRecordContext(
            recordId = record.id!!,
            sourceRevision = record.sourceRevision,
            workedAt = record.workedAt,
            workType = record.workType,
            detail = CommonFeedbackDetail,
            recordedWeatherCondition = record.weatherCondition,
            recordedTemperatureC = record.weatherTemperature,
            memo = record.memo,
            photoCount = 0,
        ),
        weather = null,
    )
}
