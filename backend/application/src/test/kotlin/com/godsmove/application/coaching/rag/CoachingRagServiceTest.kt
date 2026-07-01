package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.UUID

class CoachingRagServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val chunkId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Test
    fun `answer returns cited response when model follows citation rule`() {
        val service = service(
            chunks = listOf(chunk()),
            answer = "과습 위험은 배수 상태 확인으로 판단해야 합니다. [chunk:$chunkId]"
        )

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(result.answer).contains("과습 위험")
        assertThat(result.audit.status).isEqualTo(RagAuditStatus.PASS)
        assertThat(result.citations).hasSize(1)
        assertThat(result.citations[0].chunkId).isEqualTo(chunkId.toString())
    }

    @Test
    fun `answer retries and falls back when citations are missing`() {
        val chat = FakeChatCompletionClient(
            answers = ArrayDeque(listOf("근거 없는 답변입니다.", "다시 근거 없는 답변입니다."))
        )
        val service = service(chunks = listOf(chunk()), chat = chat)

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(chat.calls).isEqualTo(2)
        assertThat(result.answer).contains("모델 답변이 citation 규칙을 지키지 않아")
        assertThat(result.audit.status).isEqualTo(RagAuditStatus.WARN)
        assertThat(result.audit.warnings).contains("citation_retry_failed_used_extractive_fallback")
    }

    @Test
    fun `answer returns insufficient evidence response when no chunks are found`() {
        val service = service(chunks = emptyList(), answer = "")

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(result.answer).contains("현재 자료만으로는 판단할 수 없습니다")
        assertThat(result.audit.warnings).contains("no_retrieved_chunks")
    }

    private fun service(
        chunks: List<RagEvidenceChunk>,
        answer: String = "답변 [chunk:$chunkId]",
        chat: FakeChatCompletionClient = FakeChatCompletionClient(ArrayDeque(listOf(answer)))
    ): CoachingRagService {
        return CoachingRagService(
            embeddingClient = FakeEmbeddingClient(),
            chatCompletionClient = chat,
            ragIndexRepository = FakeRagIndexRepository(chunks),
            promptBuilder = RagPromptBuilder(),
            citationAuditor = RagCitationAuditor(),
            ragProperties = RagProperties()
        )
    }

    private fun chunk() = RagEvidenceChunk(
        id = chunkId,
        sourceType = RagSourceType.FARMING_RECORD,
        sourceId = "00000000-0000-0000-0000-000000000201",
        content = "관수 후 배수 상태를 확인했다.",
        label = "영농일지 관수",
        similarityScore = 0.81
    )

    private class FakeEmbeddingClient : EmbeddingClient {
        override fun embed(input: String, model: String): List<Double> = List(1024) { 0.1 }
    }

    private class FakeChatCompletionClient(
        private val answers: ArrayDeque<String>
    ) : ChatCompletionClient {
        var calls = 0

        override fun complete(messages: List<ChatMessage>, model: String): String {
            calls += 1
            return answers.removeFirst()
        }
    }

    private class FakeRagIndexRepository(
        private val chunks: List<RagEvidenceChunk>
    ) : RagIndexRepository {
        override fun retrieve(
            embedding: List<Double>,
            filters: RagRetrievalFilter,
            topK: Int
        ): List<RagEvidenceChunk> = chunks
    }
}
