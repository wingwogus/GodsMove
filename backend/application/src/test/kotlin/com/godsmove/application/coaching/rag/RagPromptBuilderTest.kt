package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RagPromptBuilderTest {
    private val chunk = RagEvidenceChunk(
        id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
        sourceType = RagSourceType.FARMING_RECORD,
        sourceId = "00000000-0000-0000-0000-000000000201",
        content = "참당귀 관수 후 토양이 과습하지 않도록 배수 상태를 확인했다.",
        label = "영농일지 2026-06-30 관수",
        similarityScore = 0.82
    )

    @Test
    fun `buildPrompt includes citation ids and question`() {
        val messages = RagPromptBuilder().buildPrompt(
            question = "과습 위험이 있을까?",
            chunks = listOf(chunk)
        )

        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo("system")
        assertThat(messages[1].content).contains("과습 위험이 있을까?")
        assertThat(messages[1].content).contains("[chunk:00000000-0000-0000-0000-000000000101]")
        assertThat(messages[1].content).contains("참당귀 관수")
    }
}
