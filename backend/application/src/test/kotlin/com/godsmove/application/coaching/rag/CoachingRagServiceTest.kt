package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.member.MemberRepository
import java.time.LocalDate
import java.util.UUID

class CoachingRagServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `answer returns insufficient evidence response when no documents are found`() {
        val vectorStore = FakeVectorStore(emptyList())
        val service = service(vectorStore)

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "  과습 위험이 있을까?  ", topK = 3)
        )

        assertThat(result.answer).contains("현재 자료만으로는 판단할 수 없습니다")
        assertThat(result.audit.status).isEqualTo(RagAuditStatus.WARN)
        assertThat(result.audit.warnings).containsExactly("no_retrieved_documents")
        assertThat(result.savedFeedbackId).isNull()
        assertThat(vectorStore.lastRequest?.query).isEqualTo("과습 위험이 있을까?")
        assertThat(vectorStore.lastRequest?.topK).isEqualTo(3)
    }

    @Test
    fun `answer rejects topK above configured maximum`() {
        val service = service(FakeVectorStore(emptyList()))

        assertThatThrownBy {
            service.answer(CoachingRagCommand(memberId = memberId, question = "과습 위험?", topK = 21))
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
    }

    @Test
    fun `answer rejects reversed period`() {
        val service = service(FakeVectorStore(emptyList()))

        assertThatThrownBy {
            service.answer(
                CoachingRagCommand(
                    memberId = memberId,
                    question = "과습 위험?",
                    periodStart = LocalDate.parse("2026-07-02"),
                    periodEnd = LocalDate.parse("2026-07-01")
                )
            )
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
    }

    private fun service(vectorStore: FakeVectorStore): CoachingRagService {
        return CoachingRagService(
            chatClient = NoopChatClient(),
            vectorStore = vectorStore,
            contextProvider = UnusedContextProvider(),
            filterBuilder = CoachingRetrievalFilterBuilder(),
            validator = CoachingStructuredOutputValidator(),
            persistencePolicy = CoachingFeedbackPersistencePolicy(),
            ragProperties = RagProperties()
        )
    }

    private class FakeVectorStore(
        private val documents: List<Document>
    ) : VectorStore {
        var lastRequest: SearchRequest? = null

        override fun add(documents: List<Document>) = Unit

        override fun delete(idList: List<String>) = Unit

        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> {
            lastRequest = request
            return documents
        }
    }

    private class NoopChatClient : ChatClient {
        override fun prompt(): ChatClient.ChatClientRequestSpec {
            error("ChatClient should not be called when retrieval is empty")
        }

        override fun prompt(content: String): ChatClient.ChatClientRequestSpec {
            error("ChatClient should not be called when retrieval is empty")
        }

        override fun prompt(prompt: org.springframework.ai.chat.prompt.Prompt): ChatClient.ChatClientRequestSpec {
            error("ChatClient should not be called when retrieval is empty")
        }

        override fun mutate(): ChatClient.Builder {
            error("ChatClient should not be called when retrieval is empty")
        }
    }

    private class UnusedContextProvider : CoachingContextProvider(
        memberRepository = mock(MemberRepository::class.java),
        farmRepository = mock(FarmRepository::class.java),
        cropRepository = mock(CropRepository::class.java),
        farmingRecordRepository = mock(FarmingRecordRepository::class.java)
    )
}
