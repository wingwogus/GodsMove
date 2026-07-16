package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure
import com.chamchamcham.application.coaching.recordfeedback.generation.CommonFeedbackDetail
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackAction
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContent
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationResult
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationService
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGoodPoint
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackRecordContext
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, OutputCaptureExtension::class)
class RecordFeedbackGenerationProcessorTest {
    @Mock private lateinit var feedbackRepository: RecordFeedbackRepository
    @Mock private lateinit var generationService: RecordFeedbackGenerationService

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초밭", roadAddress = "강원도 평창군")
    private val crop = Crop(UUID.randomUUID(), 1, "황기", CropUsePartCategory.ROOT_BARK)
    private val record = FarmingRecord(
        id = UUID.randomUUID(), member = member, farm = farm, crop = crop, workType = WorkType.PRUNING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0), weatherCondition = "맑음", weatherTemperature = 25,
        memo = "웃자란 가지를 정리했습니다.", entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
    )
    private lateinit var processor: RecordFeedbackGenerationProcessor

    @BeforeEach
    fun setUp() {
        processor = RecordFeedbackGenerationProcessor(feedbackRepository, generationService, objectMapper, NoOpTransactionManager())
    }

    @Test
    fun `processor stores validated content in typed fields and ordered action rows`() {
        val feedback = pendingFeedback()
        val event = event(feedback)
        stubFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenReturn(generationResult())

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(feedback.goodPointText).isEqualTo("가지 정리 시점이 적절합니다.")
        assertThat(feedback.nextActions().map { it.text }).containsExactly("새순 상태를 확인하세요.", "이번 주 병해충 흔적을 확인하세요.")
        assertThat(feedback.citations).containsExactly(mapOf("id" to "doc-1", "title" to "농업기술길잡이"))
        assertThat(feedback.auditWarnings).containsExactly("날씨 근거 없음")
    }

    @Test
    fun `processor stores expected generation failure`() {
        val feedback = pendingFeedback()
        val event = event(feedback)
        stubFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenThrow(
            RecordFeedbackGenerationFailure(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE),
        )

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("INSUFFICIENT_EVIDENCE")
    }

    @Test
    fun `processor logs safe validation diagnostics without raw generated content`(output: CapturedOutput) {
        val feedback = pendingFeedback()
        val event = event(feedback)
        stubFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenThrow(
            RecordFeedbackGenerationFailure(
                RecordFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID,
                IllegalStateException(
                    "invalid product output: next_action_1_text_length,next_action_1_text_english," +
                        "unknown_evidence:untrusted-generated-value",
                ),
            ),
        )

        processor.generate(event)

        val logs = output.out + output.err
        assertThat(logs)
            .contains("record feedback generation failed")
            .contains("STRUCTURED_OUTPUT_INVALID")
            .contains("next_action_1_text_length")
            .contains("next_action_1_text_english")
            .contains("unknown_evidence")
            .doesNotContain("untrusted-generated-value")
    }

    @Test
    fun `processor preserves stale feedback when generation finishes late`() {
        val feedback = pendingFeedback()
        val event = event(feedback)
        stubFeedback(event, feedback)
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenAnswer {
            feedback.markStale()
            generationResult()
        }

        assertDoesNotThrow { processor.generate(event) }

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.STALE)
        assertThat(feedback.goodPointText).isNull()
    }

    @Test
    fun `processor marks malformed snapshot as failed`() {
        val feedback = RecordFeedback(id = UUID.randomUUID(), member = member, record = record, status = RecordFeedbackStatus.PENDING, sourceRevision = 3)
        feedback.attachInputSnapshot(mapOf("schemaVersion" to "invalid"))
        val event = event(feedback)
        stubFeedback(event, feedback)

        processor.generate(event)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.FAILED)
        assertThat(feedback.failureCode).isEqualTo("INVALID_CONTEXT_SNAPSHOT")
    }

    private fun pendingFeedback(): RecordFeedback = RecordFeedback(
        id = UUID.randomUUID(), member = member, record = record, status = RecordFeedbackStatus.PENDING, sourceRevision = 3,
    ).also { it.attachInputSnapshot(objectMapper.convertValue(context(), SNAPSHOT_TYPE)) }

    private fun event(feedback: RecordFeedback) = RecordFeedbackGenerationRequested(
        feedbackId = feedback.id!!, memberId = member.id!!, recordId = record.id!!, sourceRevision = feedback.sourceRevision,
    )

    private fun stubFeedback(event: RecordFeedbackGenerationRequested, feedback: RecordFeedback) {
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(feedback)
    }

    private fun context() = RecordFeedbackContext(
        member = RecordFeedbackMemberContext(member.id!!, null, null),
        farm = RecordFeedbackFarmContext(farm.id!!, farm.name, farm.roadAddress, null, null),
        crop = RecordFeedbackCropContext(crop.id!!, crop.name, crop.usePartCategory),
        record = RecordFeedbackRecordContext(record.id!!, SOURCE_REVISION, record.workedAt, record.workType, CommonFeedbackDetail, record.weatherCondition, record.weatherTemperature, record.memo, 0),
        weather = null,
    )

    private fun generationResult() = RecordFeedbackGenerationResult(
        content = RecordFeedbackContent(
            goodPoint = RecordFeedbackGoodPoint("기록된 작업", "가지 정리 시점이 적절합니다.", listOf("doc-1")),
            nextActions = listOf(
                RecordFeedbackAction(RecordFeedbackActionDue.NEXT_CHECK, RecordFeedbackActionCategory.CULTIVATION, "새순 확인", "새순 상태를 확인하세요.", listOf("doc-1")),
                RecordFeedbackAction(RecordFeedbackActionDue.THIS_WEEK, RecordFeedbackActionCategory.PEST_DISEASE, "병해충 확인", "이번 주 병해충 흔적을 확인하세요.", listOf("doc-1")),
            ),
        ),
        citations = listOf(mapOf("id" to "doc-1", "title" to "농업기술길잡이")),
        auditWarnings = listOf("날씨 근거 없음"),
        modelInfo = RagModelInfo(embedding = "embedding-test", chat = "chat-test"),
    )

    private fun anyContext(): RecordFeedbackContext = org.mockito.Mockito.any(RecordFeedbackContext::class.java) ?: context()

    private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private companion object {
        const val SOURCE_REVISION = 3L
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
