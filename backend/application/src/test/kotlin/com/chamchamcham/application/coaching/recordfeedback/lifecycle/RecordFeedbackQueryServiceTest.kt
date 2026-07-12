package com.chamchamcham.application.coaching.recordfeedback.lifecycle

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.coaching.RecordFeedback
import com.chamchamcham.domain.coaching.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.RecordFeedbackNextActionDraft
import com.chamchamcham.domain.coaching.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.RecordFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackQueryServiceTest {
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var feedbackRepository: RecordFeedbackRepository
    @Mock private lateinit var lifecycleService: RecordFeedbackLifecycleService

    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초밭", roadAddress = "강원도 평창군")
    private val crop = Crop(UUID.randomUUID(), 1, "황기", CropUsePartCategory.ROOT_BARK)
    private val record = FarmingRecord(
        id = UUID.randomUUID(), member = member, farm = farm, crop = crop, workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 11, 9, 0), weatherCondition = "맑음", weatherTemperature = 25,
        memo = "관수 기록", entryMode = "MANUAL", sourceRevision = 3,
    )
    private lateinit var service: RecordFeedbackQueryService

    @BeforeEach
    fun setUp() {
        service = RecordFeedbackQueryService(farmingRecordRepository, feedbackRepository, lifecycleService)
    }

    @Test
    fun `ready feedback maps stored columns and action rows to response content`() {
        stubOwnedRecord()
        val feedback = readyFeedback()
        `when`(feedbackRepository.findByRecord_IdAndSourceRevision(record.id!!, record.sourceRevision)).thenReturn(feedback)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(result.content?.goodPoint).isEqualTo("점적관수로 토양 수분을 확인한 점이 좋았어요.")
        assertThat(result.content?.nextActions?.map { it.text }).containsExactly(
            "오후에 토양 표면이 마르는지 한 번 더 확인하세요.",
            "이번 주 안에 배수로 주변의 막힌 흙을 정리하세요.",
        )
        assertThat(result.content?.nextActions?.map { it.category }).containsExactly(
            RecordFeedbackActionCategory.IRRIGATION,
            RecordFeedbackActionCategory.CULTIVATION,
        )
    }

    @Test
    fun `get falls back to stale feedback when current revision is absent`() {
        stubOwnedRecord()
        val stale = feedback(status = RecordFeedbackStatus.STALE, sourceRevision = 2)
        `when`(feedbackRepository.findByRecord_IdAndSourceRevision(record.id!!, record.sourceRevision)).thenReturn(null)
        `when`(feedbackRepository.findTopByRecord_IdAndStatusOrderByUpdatedAtDesc(record.id!!, RecordFeedbackStatus.STALE)).thenReturn(stale)

        val result = service.get(member.id!!, record.id!!)

        assertThat(result.feedbackId).isEqualTo(stale.id)
        assertThat(result.status).isEqualTo(RecordFeedbackStatus.STALE)
    }

    @Test
    fun `regenerate retries only failed current feedback`() {
        stubOwnedRecord()
        val failed = feedback(status = RecordFeedbackStatus.FAILED, failureCode = "CONTEXT_ASSEMBLY_FAILED")
        val retried = feedback()
        `when`(feedbackRepository.findByRecord_IdAndSourceRevision(record.id!!, record.sourceRevision)).thenReturn(failed)
        `when`(lifecycleService.retry(failed)).thenReturn(retried)

        val result = service.regenerate(member.id!!, record.id!!)

        verify(lifecycleService).retry(failed)
        assertThat(result.status).isEqualTo(RecordFeedbackStatus.PENDING)
    }

    @Test
    fun `get rejects a record not owned by the member`() {
        val otherMemberId = UUID.randomUUID()
        `when`(farmingRecordRepository.findContextSourceByIdAndMemberId(record.id!!, otherMemberId)).thenReturn(null)

        assertThatThrownBy { service.get(otherMemberId, record.id!!) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARMING_RECORD_NOT_FOUND)
    }

    private fun readyFeedback(): RecordFeedback = feedback().also {
        it.markReady(
            goodPointBasis = "점적관수",
            goodPointText = "점적관수로 토양 수분을 확인한 점이 좋았어요.",
            nextActions = listOf(
                RecordFeedbackNextActionDraft(RecordFeedbackActionDue.TODAY, RecordFeedbackActionCategory.IRRIGATION, "토양 표면", "오후에 토양 표면이 마르는지 한 번 더 확인하세요."),
                RecordFeedbackNextActionDraft(RecordFeedbackActionDue.THIS_WEEK, RecordFeedbackActionCategory.CULTIVATION, "배수로", "이번 주 안에 배수로 주변의 막힌 흙을 정리하세요."),
            ),
            citations = emptyList(), auditStatus = "PASS", auditWarnings = emptyList(), modelName = "chat", embeddingModel = "embed",
        )
    }

    private fun feedback(
        status: RecordFeedbackStatus = RecordFeedbackStatus.PENDING,
        sourceRevision: Long = record.sourceRevision,
        failureCode: String? = null,
    ): RecordFeedback {
        val feedback = RecordFeedback(id = UUID.randomUUID(), member = member, record = record, status = RecordFeedbackStatus.PENDING, sourceRevision = sourceRevision)
        when (status) {
            RecordFeedbackStatus.FAILED -> feedback.markFailed(requireNotNull(failureCode))
            RecordFeedbackStatus.STALE -> feedback.markStale()
            RecordFeedbackStatus.PENDING, RecordFeedbackStatus.READY -> Unit
        }
        ReflectionTestUtils.setField(feedback, "createdAt", LocalDateTime.of(2026, 7, 11, 10, 0))
        ReflectionTestUtils.setField(feedback, "updatedAt", LocalDateTime.of(2026, 7, 11, 10, 1))
        return feedback
    }

    private fun stubOwnedRecord() {
        `when`(farmingRecordRepository.findContextSourceByIdAndMemberId(record.id!!, member.id!!)).thenReturn(record)
    }
}
