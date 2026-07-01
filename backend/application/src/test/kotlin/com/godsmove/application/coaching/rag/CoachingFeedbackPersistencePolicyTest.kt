package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CoachingFeedbackPersistencePolicyTest {
    private val policy = CoachingFeedbackPersistencePolicy()

    @Test
    fun `chat is not saved by default`() {
        val command = CoachingRagCommand(
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            mode = CoachingMode.CHAT,
            question = "지금 물 줘도 돼?"
        )

        assertThat(policy.shouldSave(command)).isFalse()
    }

    @Test
    fun `record auto and report manual are saved`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        assertThat(policy.shouldSave(CoachingRagCommand(memberId, CoachingMode.RECORD_AUTO, "자동 코칭"))).isTrue()
        assertThat(policy.shouldSave(CoachingRagCommand(memberId, CoachingMode.REPORT_MANUAL, "리포트 코칭"))).isTrue()
    }
}
