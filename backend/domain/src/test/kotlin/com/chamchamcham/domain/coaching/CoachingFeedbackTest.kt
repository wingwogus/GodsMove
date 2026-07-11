package com.chamchamcham.domain.coaching

import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class CoachingFeedbackTest {
    private val member = Member(id = UUID.randomUUID(), email = "member@example.com", passwordHash = null)
    private val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초농장", roadAddress = "강원도 평창군")
    private val crop = Crop(
        id = UUID.randomUUID(),
        externalNo = 422,
        name = "황기",
        usePartCategory = CropUsePartCategory.ROOT_BARK,
    )
    private val record = FarmingRecord(
        id = UUID.randomUUID(),
        member = member,
        farm = farm,
        crop = crop,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 6, 15, 8, 30),
        weatherCondition = "맑음",
        weatherTemperature = 24,
        memo = "배수 확인",
        entryMode = "MANUAL",
    )

    @Test
    fun `pending record feedback validates target and transitions`() {
        val feedback = CoachingFeedback.pendingRecord(member, record, sourceRevision = 1)
        assertThat(feedback.feedbackType).isEqualTo(FeedbackType.RECORD)
        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        assertThat(feedback.inputSnapshot).isNull()

        feedback.attachInputSnapshot(mapOf("schemaVersion" to "record-feedback-context.v2"))
        assertThat(feedback.inputSnapshot).isNotNull()

        feedback.markStale()
        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.STALE)
    }

    @Test
    fun `failed feedback can retry same revision`() {
        val feedback = CoachingFeedback.pendingRecord(member, record, 1)
        feedback.markFailed("CONTEXT_ASSEMBLY_FAILED")
        feedback.retry()

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.PENDING)
        assertThat(feedback.failureCode).isNull()
        assertThat(feedback.inputSnapshot).isNull()
    }

    @Test
    fun `pending feedback becomes ready with immutable result metadata`() {
        val feedback = CoachingFeedback.pendingRecord(member, record, 1)
        val inputSnapshot = mapOf("schemaVersion" to "record-feedback-context.v2")

        feedback.attachInputSnapshot(inputSnapshot)
        feedback.markReady(
            structuredResult = mapOf("goodPoint" to mapOf("text" to "점적관수로 토양 상태를 확인한 점이 좋았어요.")),
            citations = listOf(mapOf("id" to "record:${record.id}")),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )

        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.READY)
        assertThat(feedback.structuredResult).containsKey("goodPoint")
        assertThat(feedback.citations).containsExactly(mapOf("id" to "record:${record.id}"))
        assertThat(feedback.auditStatus).isEqualTo("PASS")
        assertThat(feedback.auditWarnings).isEmpty()
        assertThat(feedback.failureCode).isNull()
        assertThat(feedback.modelName).isEqualTo("test-chat")
        assertThat(feedback.embeddingModel).isEqualTo("test-embedding")
        assertThat(feedback.inputSnapshot).isEqualTo(inputSnapshot)
        assertThatThrownBy { feedback.attachInputSnapshot(emptyMap()) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `pending record feedback rejects invalid lifecycle transitions`() {
        val feedback = CoachingFeedback.pendingRecord(member, record, 1)

        assertThatThrownBy { feedback.retry() }.isInstanceOf(IllegalStateException::class.java)

        feedback.markFailed("CONTEXT_ASSEMBLY_FAILED")

        assertThatThrownBy {
            feedback.attachInputSnapshot(mapOf("schemaVersion" to "record-feedback-context.v2"))
        }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy {
            feedback.markReady(
                structuredResult = mapOf("goodPoint" to mapOf("text" to "점적관수로 토양 상태를 확인한 점이 좋았어요.")),
                citations = listOf(mapOf("id" to "record:${record.id}")),
                auditStatus = "PASS",
                auditWarnings = emptyList(),
                modelName = "test-chat",
                embeddingModel = "test-embedding",
            )
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
