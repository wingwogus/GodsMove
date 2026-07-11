package com.chamchamcham.application.coaching.feedback

import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionCategory
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionDue
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.CoachingFeedback
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackQueryServiceTest {
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var feedbackRepository: CoachingFeedbackRepository
    @Mock private lateinit var lifecycleService: RecordFeedbackLifecycleService

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
        memo = "토양 상태를 확인하고 관수한 영농기록입니다.",
        entryMode = "MANUAL",
        sourceRevision = 3,
    )
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private lateinit var service: RecordFeedbackQueryService

    @BeforeEach
    fun setUp() {
        service = RecordFeedbackQueryService(farmingRecordRepository, feedbackRepository, lifecycleService, objectMapper)
    }

    @Test
    fun `get returns the owned current revision feedback without exposing its snapshot`() {
        stubOwnedRecord()
        val feedback = feedback(status = CoachingFeedbackStatus.PENDING, inputSnapshot = mapOf("schemaVersion" to "v2"))
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(feedback)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.feedbackId).isEqualTo(feedback.id)
        assertThat(result.recordId).isEqualTo(record.id)
        assertThat(result.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        assertThat(result.sourceRevision).isEqualTo(3)
        assertThat(result.inputPrepared).isTrue()
        assertThat(result.failureCode).isNull()
        assertThat(result.feedback).isNull()
    }

    @Test
    fun `get returns a failed feedback for the owned current revision`() {
        stubOwnedRecord()
        val feedback = feedback(
            status = CoachingFeedbackStatus.FAILED,
            failureCode = "CONTEXT_ASSEMBLY_FAILED",
        )
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(feedback)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.status).isEqualTo(CoachingFeedbackStatus.FAILED)
        assertThat(result.inputPrepared).isFalse()
        assertThat(result.failureCode).isEqualTo("CONTEXT_ASSEMBLY_FAILED")
        assertThat(result.feedback).isNull()
    }

    @Test
    fun `ready feedback returns only user facing text fields`() {
        stubOwnedRecord()
        val feedback = feedback(
            status = CoachingFeedbackStatus.READY,
            structuredResult = structuredResult(),
        )
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(feedback)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.feedback?.goodPoint?.text).contains("점적관수")
        assertThat(result.feedback?.nextActions).hasSize(2)
        assertThat(result.feedback?.nextActions?.map { it.text }).containsExactly(
            "오후에 토양 표면이 마르는지 한 번 더 확인하세요.",
            "이번 주 안에 배수로 주변의 막힌 흙을 정리하세요.",
        )
        assertThat(result.feedback?.nextActions?.map { it.due }).containsExactly(
            RecordFeedbackActionDue.TODAY,
            RecordFeedbackActionDue.THIS_WEEK,
        )
        assertThat(result.feedback?.nextActions?.map { it.category }).containsExactly(
            RecordFeedbackActionCategory.IRRIGATION,
            RecordFeedbackActionCategory.CULTIVATION,
        )
    }

    @Test
    fun `ready feedback with malformed structured result is an internal consistency error`() {
        stubOwnedRecord()
        val feedback = feedback(
            status = CoachingFeedbackStatus.READY,
            structuredResult = mapOf(
                "goodPoint" to mapOf(
                    "basis" to "record_memo",
                    "text" to "점적관수로 토양 수분을 확인한 점이 좋았어요.",
                    "evidenceRefs" to listOf("record.memo"),
                ),
            ),
        )
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(feedback)

        assertThatThrownBy { service.get(member.id!!, record.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `get falls back to latest stale feedback only when current revision is absent`() {
        stubOwnedRecord()
        val stale = feedback(status = CoachingFeedbackStatus.STALE, sourceRevision = 2)
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(null)
        `when`(feedbackRepository.findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
            FeedbackType.RECORD,
            record.id!!,
            CoachingFeedbackStatus.STALE,
        )).thenReturn(stale)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.feedbackId).isEqualTo(stale.id)
        assertThat(result.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(result.sourceRevision).isEqualTo(2)
    }

    @Test
    fun `get rejects another member or deleted record as not found`() {
        val otherMemberId = UUID.randomUUID()
        `when`(farmingRecordRepository.findContextSourceByIdAndMemberId(record.id!!, otherMemberId)).thenReturn(null)

        assertThatThrownBy { service.get(otherMemberId, record.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARMING_RECORD_NOT_FOUND)
    }

    @Test
    fun `get reports not found when the owned record has no feedback`() {
        stubOwnedRecord()
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(null)
        `when`(feedbackRepository.findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
            FeedbackType.RECORD,
            record.id!!,
            CoachingFeedbackStatus.STALE,
        )).thenReturn(null)

        assertThatThrownBy { service.get(member.id!!, record.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RECORD_FEEDBACK_NOT_FOUND)
    }

    @Test
    fun `regenerate retries only a failed feedback for the current revision`() {
        stubOwnedRecord()
        val failed = feedback(status = CoachingFeedbackStatus.FAILED, failureCode = "CONTEXT_ASSEMBLY_FAILED")
        val retried = feedback(status = CoachingFeedbackStatus.PENDING)
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(failed)
        `when`(lifecycleService.retry(failed)).thenReturn(retried)

        val result = service.regenerate(member.id!!, record.id!!)

        verify(lifecycleService).retry(failed)
        assertThat(result.feedbackId).isEqualTo(retried.id)
        assertThat(result.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        assertThat(result.failureCode).isNull()
    }

    @Test
    fun `regenerate rejects pending ready and stale feedback`() {
        stubOwnedRecord()
        listOf(CoachingFeedbackStatus.PENDING, CoachingFeedbackStatus.READY, CoachingFeedbackStatus.STALE).forEach { status ->
            val feedback = feedback(status = status)
            `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
                FeedbackType.RECORD,
                record.id!!,
                record.sourceRevision,
            )).thenReturn(feedback)

            assertThatThrownBy { service.regenerate(member.id!!, record.id!!) }
                .isInstanceOf(BusinessException::class.java)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED)
        }

        verifyNoInteractions(lifecycleService)
    }

    @Test
    fun `regenerate rejects stale-only history without creating current revision feedback`() {
        stubOwnedRecord()
        val stale = feedback(status = CoachingFeedbackStatus.STALE, sourceRevision = 2)
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(null)
        `when`(feedbackRepository.findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
            FeedbackType.RECORD,
            record.id!!,
            CoachingFeedbackStatus.STALE,
        )).thenReturn(stale)

        assertThatThrownBy { service.regenerate(member.id!!, record.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RECORD_FEEDBACK_REGENERATION_NOT_ALLOWED)

        verifyNoInteractions(lifecycleService)
    }

    @Test
    fun `regenerate reports not found when the owned record has no feedback history`() {
        stubOwnedRecord()
        `when`(feedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            record.id!!,
            record.sourceRevision,
        )).thenReturn(null)
        `when`(feedbackRepository.findTopByFeedbackTypeAndRecord_IdAndStatusOrderByUpdatedAtDesc(
            FeedbackType.RECORD,
            record.id!!,
            CoachingFeedbackStatus.STALE,
        )).thenReturn(null)

        assertThatThrownBy { service.regenerate(member.id!!, record.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RECORD_FEEDBACK_NOT_FOUND)

        verifyNoInteractions(lifecycleService)
    }

    private fun feedback(
        status: CoachingFeedbackStatus,
        sourceRevision: Long = record.sourceRevision,
        inputSnapshot: Map<String, Any?>? = null,
        structuredResult: Map<String, Any?>? = null,
        failureCode: String? = null,
    ): CoachingFeedback = CoachingFeedback(
        id = UUID.randomUUID(),
        member = member,
        feedbackType = FeedbackType.RECORD,
        status = status,
        record = record,
        sourceRevision = sourceRevision,
        inputSnapshot = inputSnapshot,
        structuredResult = structuredResult,
        failureCode = failureCode,
    ).also {
        ReflectionTestUtils.setField(it, "createdAt", LocalDateTime.of(2026, 7, 11, 10, 0))
        ReflectionTestUtils.setField(it, "updatedAt", LocalDateTime.of(2026, 7, 11, 10, 1))
    }

    private fun structuredResult(): Map<String, Any?> = mapOf(
        "goodPoint" to mapOf(
            "basis" to "record_memo",
            "text" to "점적관수로 토양 수분을 확인한 점이 좋았어요.",
            "evidenceRefs" to listOf("record.memo"),
        ),
        "nextActions" to listOf(
            mapOf(
                "due" to "TODAY",
                "category" to "IRRIGATION",
                "basis" to "weather",
                "text" to "오후에 토양 표면이 마르는지 한 번 더 확인하세요.",
                "evidenceRefs" to listOf("weather.forecast"),
            ),
            mapOf(
                "due" to "THIS_WEEK",
                "category" to "CULTIVATION",
                "basis" to "record_memo",
                "text" to "이번 주 안에 배수로 주변의 막힌 흙을 정리하세요.",
                "evidenceRefs" to listOf("record.memo"),
            ),
        ),
        "citations" to listOf(mapOf("title" to "비공개 근거")),
        "audit" to mapOf("status" to "PASS"),
        "model" to mapOf("chat" to "gpt-test"),
        "inputSnapshot" to mapOf("memo" to "토양 상태를 확인하고 관수한 영농기록입니다."),
    )

    private fun stubOwnedRecord() {
        `when`(farmingRecordRepository.findContextSourceByIdAndMemberId(record.id!!, member.id!!)).thenReturn(record)
    }
}
