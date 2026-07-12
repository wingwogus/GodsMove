package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.common.RagModelInfo
import com.chamchamcham.application.coaching.rag.record.CommonFeedbackDetail
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionCategory
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionDue
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackAction
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContent
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationResult
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationService
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGoodPoint
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackRecordContext
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
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackGenerationProcessorTest {
    @Mock private lateinit var feedbackRepository: CoachingFeedbackRepository
    @Mock private lateinit var generationService: RecordFeedbackGenerationService

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
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
    private lateinit var processor: RecordFeedbackGenerationProcessor

    @BeforeEach
    fun setUp() {
        processor = RecordFeedbackGenerationProcessor(
            feedbackRepository = feedbackRepository,
            generationService = generationService,
            objectMapper = objectMapper,
            transactionManager = NoOpTransactionManager(),
        )
    }

    @Test
    fun `processor ignores stale event and never calls generator`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback, sourceRevision = 2)
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        verifyNoInteractions(generationService)
    }

    @Test
    fun `processor persists ready result from the stored snapshot`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenReturn(generationResult())

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.READY)
        assertThat(feedback.structuredResult).containsKey("goodPoint")
        assertThat(feedback.citations).containsExactly(mapOf("id" to "doc-1", "title" to "농업기술길잡이"))
        assertThat(feedback.auditStatus).isEqualTo("WARN")
        assertThat(feedback.auditWarnings).containsExactly("날씨 근거 없음")
        assertThat(feedback.modelName).isEqualTo("chat-test")
        assertThat(feedback.embeddingModel).isEqualTo("embedding-test")

        val contextCaptor = ArgumentCaptor.forClass(RecordFeedbackContext::class.java)
        verify(generationService).generate(captureContext(contextCaptor), org.mockito.Mockito.isNull())
        assertThat(contextCaptor.value.record.recordId).isEqualTo(record.id)
        assertThat(contextCaptor.value.record.sourceRevision).isEqualTo(3)
    }

    @Test
    fun `processor stores insufficient evidence failure without touching record`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenThrow(
            RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE),
        )

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("INSUFFICIENT_EVIDENCE")
    }

    @Test
    fun `processor maps malformed snapshot to invalid context snapshot failure`() {
        val feedback = feedback(inputSnapshot = mapOf("schemaVersion" to "record-feedback-context/v1"))
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("INVALID_CONTEXT_SNAPSHOT")
        verifyNoInteractions(generationService)
    }

    @Test
    fun `processor marks an unexpected generation runtime failure as terminal`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull()))
            .thenThrow(IllegalStateException("unexpected generation failure"))

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("UNEXPECTED")
    }

    @Test
    fun `processor does not mark ready when feedback becomes stale during generation`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenAnswer {
            feedback.markStale()
            generationResult()
        }

        assertDoesNotThrow { processor.generate(event) }

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(feedback.structuredResult).isNull()
    }

    @Test
    fun `processor does not mark failed when feedback becomes stale during generation failure`() {
        val feedback = feedback(inputSnapshot = snapshot())
        val event = event(feedback)
        stubInitialAndFinalFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenAnswer {
            feedback.markStale()
            throw RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
        }

        assertDoesNotThrow { processor.generate(event) }

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(feedback.failureCode).isNull()
    }

    private fun feedback(
        status: CoachingFeedbackStatus = CoachingFeedbackStatus.PENDING,
        sourceRevision: Long = 3,
        inputSnapshot: Map<String, Any?>? = null,
    ): CoachingFeedback = CoachingFeedback(
        id = UUID.randomUUID(),
        member = member,
        feedbackType = FeedbackType.RECORD,
        status = status,
        record = record,
        sourceRevision = sourceRevision,
        inputSnapshot = inputSnapshot,
    )

    private fun event(
        feedback: CoachingFeedback,
        sourceRevision: Long = feedback.sourceRevision,
    ) = RecordFeedbackGenerationRequested(
        feedbackId = feedback.id!!,
        memberId = member.id!!,
        recordId = record.id!!,
        sourceRevision = sourceRevision,
    )

    private fun snapshot(): Map<String, Any?> = objectMapper.convertValue(context(), SNAPSHOT_TYPE)

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

    private fun generationResult() = RecordFeedbackGenerationResult(
        content = RecordFeedbackContent(
            goodPoint = RecordFeedbackGoodPoint(
                basis = "기록된 작업과 공식문서 근거가 일치합니다.",
                text = "가지 정리 시점이 적절합니다.",
                evidenceRefs = listOf("doc-1"),
            ),
            nextActions = listOf(
                RecordFeedbackAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = RecordFeedbackActionCategory.CULTIVATION,
                    basis = "다음 생육 확인이 필요합니다.",
                    text = "새순 상태를 확인하세요.",
                    evidenceRefs = listOf("doc-1"),
                ),
            ),
        ),
        citations = listOf(mapOf("id" to "doc-1", "title" to "농업기술길잡이")),
        auditWarnings = listOf("날씨 근거 없음"),
        modelInfo = RagModelInfo(
            embedding = "embedding-test",
            chat = "chat-test",
        ),
    )

    private fun anyContext(): RecordFeedbackContext {
        return org.mockito.Mockito.any(RecordFeedbackContext::class.java) ?: context()
    }

    private fun captureContext(captor: ArgumentCaptor<RecordFeedbackContext>): RecordFeedbackContext {
        return captor.capture() ?: context()
    }

    private fun stubInitialAndFinalFeedback(
        event: RecordFeedbackGenerationRequested,
        feedback: CoachingFeedback,
    ) {
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
    }

    private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private companion object {
        val SNAPSHOT_TYPE = object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {}
    }
}
