package com.chamchamcham.domain.coaching.recordfeedback

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

class RecordFeedbackTest {
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
        entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
    )

    @Test
    fun `ready feedback stores a good point and ordered next actions`() {
        val feedback = RecordFeedback.pending(member, record, sourceRevision = 1)

        feedback.markReady(
            goodPointBasis = "관수 기록",
            goodPointText = "관수 기록이 구체적입니다.",
            nextActions = listOf(
                action("오늘 토양을 확인하세요."),
                action("이번 주 배수로를 점검하세요."),
            ),
            citations = listOf(mapOf("id" to "record:${record.id}")),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(feedback.goodPointBasis).isEqualTo("관수 기록")
        assertThat(feedback.goodPointText).isEqualTo("관수 기록이 구체적입니다.")
        assertThat(feedback.nextActions().map(RecordFeedbackNextAction::displayOrder)).containsExactly(0, 1)
        assertThat(feedback.nextActions().map(RecordFeedbackNextAction::text))
            .containsExactly("오늘 토양을 확인하세요.", "이번 주 배수로를 점검하세요.")
        assertThat(feedback.citations).containsExactly(mapOf("id" to "record:${record.id}"))
        assertThat(feedback.inputSnapshot).isNull()
    }

    @Test
    fun `ready feedback rejects action counts outside two to three`() {
        assertThatThrownBy {
            RecordFeedback.pending(member, record, 1).markReady(
                goodPointBasis = "관수 기록",
                goodPointText = "관수 기록이 구체적입니다.",
                nextActions = listOf(action("오늘 토양을 확인하세요.")),
                citations = emptyList(),
                auditStatus = "PASS",
                auditWarnings = emptyList(),
                modelName = "test-chat",
                embeddingModel = "test-embedding",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            RecordFeedback.pending(member, record, 1).markReady(
                goodPointBasis = "관수 기록",
                goodPointText = "관수 기록이 구체적입니다.",
                nextActions = List(4) { action("행동 ${it + 1}") },
                citations = emptyList(),
                auditStatus = "PASS",
                auditWarnings = emptyList(),
                modelName = "test-chat",
                embeddingModel = "test-embedding",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun action(text: String) = RecordFeedbackNextActionDraft(
        due = RecordFeedbackActionDue.THIS_WEEK,
        category = RecordFeedbackActionCategory.CULTIVATION,
        basis = "관수 기록",
        text = text,
    )
}
