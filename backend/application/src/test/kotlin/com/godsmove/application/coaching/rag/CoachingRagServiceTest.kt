package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.ResponseEntity
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.converter.StructuredOutputConverter
import org.springframework.ai.document.Document
import org.springframework.ai.template.TemplateRenderer
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.UUID
import java.util.function.Consumer

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
    fun `answer returns validated structured result from fixed retrieved documents`() {
        val document = document("doc-1")
        val vectorStore = FakeVectorStore(listOf(document))
        val chatClient = FakeChatClient(result = structuredResult(citationId = "doc-1"))
        val service = service(vectorStore, chatClient = chatClient)

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?", topK = 2)
        )

        assertThat(result.audit.status).isEqualTo(RagAuditStatus.PASS)
        assertThat(result.audit.citations).containsExactly("doc-1")
        assertThat(result.result.summary).isEqualTo("배수 상태를 확인하세요.")
        assertThat(result.savedFeedbackId).isNull()
        assertThat(vectorStore.searchCalls).isEqualTo(1)
        assertThat(chatClient.requestSpec.advisorCount).isEqualTo(1)
    }

    @Test
    fun `answer preserves business exception from chat path`() {
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(exception = BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE))
        )

        assertThatThrownBy {
            service.answer(CoachingRagCommand(memberId = memberId, question = "과습 위험?"))
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_CHAT_UNAVAILABLE)
            }
    }

    @Test
    fun `answer maps generic structured output failure`() {
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(exception = IllegalStateException("parse failed"))
        )

        assertThatThrownBy {
            service.answer(CoachingRagCommand(memberId = memberId, question = "과습 위험?"))
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
            }
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

    private fun service(
        vectorStore: FakeVectorStore,
        chatClient: ChatClient = FakeChatClient()
    ): CoachingRagService {
        return CoachingRagService(
            chatClient = chatClient,
            vectorStore = vectorStore,
            contextProvider = FakeContextProvider(),
            filterBuilder = CoachingRetrievalFilterBuilder(),
            validator = CoachingStructuredOutputValidator(),
            persistencePolicy = CoachingFeedbackPersistencePolicy(),
            ragProperties = RagProperties()
        )
    }

    private fun structuredResult(citationId: String): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = "배수 상태를 확인하세요.",
            riskLevel = CoachingRiskLevel.LOW,
            confidence = 0.8,
            observations = listOf(CoachingObservation("관수", "배수 상태 확인", listOf(citationId))),
            diagnosis = "과습 위험은 낮습니다.",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.MEDIUM, "배수 상태 확인", "관수 기록 기반", null, listOf(citationId))
            ),
            nextActions = listOf(CoachingNextAction(CoachingActionDue.TODAY, "토양 상태 확인", listOf(citationId))),
            followUpQuestions = emptyList(),
            citations = listOf(CoachingCitationRef(citationId, "영농일지 관수", RagSourceType.FARMING_RECORD))
        )
    }

    private fun document(id: String): Document {
        return Document.builder()
            .id(id)
            .text("관수 후 배수 상태를 확인했다.")
            .metadata("sourceType", RagSourceType.FARMING_RECORD.name)
            .build()
    }

    private class FakeVectorStore(
        private val documents: List<Document>
    ) : VectorStore {
        var lastRequest: SearchRequest? = null
        var searchCalls = 0

        override fun add(documents: List<Document>) = Unit

        override fun delete(idList: List<String>) = Unit

        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> {
            searchCalls += 1
            lastRequest = request
            return documents
        }
    }

    private class FakeChatClient(
        private val result: CoachingStructuredResult = CoachingStructuredResult.insufficientEvidence("unused"),
        private val exception: RuntimeException? = null
    ) : ChatClient {
        val requestSpec = FakeRequestSpec(FakeCallResponseSpec(result, exception))

        override fun prompt(): ChatClient.ChatClientRequestSpec {
            return requestSpec
        }

        override fun prompt(content: String): ChatClient.ChatClientRequestSpec {
            return requestSpec
        }

        override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec {
            return requestSpec
        }

        override fun mutate(): ChatClient.Builder {
            error("mutate is not used")
        }
    }

    private class FakeRequestSpec(
        private val callResponseSpec: ChatClient.CallResponseSpec
    ) : ChatClient.ChatClientRequestSpec {
        var advisorCount = 0

        override fun mutate(): ChatClient.Builder = error("mutate is not used")

        override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>): ChatClient.ChatClientRequestSpec {
            return this
        }

        override fun advisors(vararg advisors: Advisor): ChatClient.ChatClientRequestSpec {
            advisorCount += advisors.size
            return this
        }

        override fun advisors(advisors: List<Advisor>): ChatClient.ChatClientRequestSpec {
            advisorCount += advisors.size
            return this
        }

        override fun messages(vararg messages: Message): ChatClient.ChatClientRequestSpec = this

        override fun messages(messages: List<Message>): ChatClient.ChatClientRequestSpec = this

        override fun <T : ChatOptions> options(chatOptions: T): ChatClient.ChatClientRequestSpec = this

        override fun toolNames(vararg toolNames: String): ChatClient.ChatClientRequestSpec = this

        override fun tools(vararg tools: Any): ChatClient.ChatClientRequestSpec = this

        override fun toolCallbacks(vararg toolCallbacks: ToolCallback): ChatClient.ChatClientRequestSpec = this

        override fun toolCallbacks(toolCallbacks: List<ToolCallback>): ChatClient.ChatClientRequestSpec = this

        override fun toolCallbacks(vararg toolCallbackProviders: ToolCallbackProvider): ChatClient.ChatClientRequestSpec = this

        override fun toolContext(toolContext: Map<String, Any>): ChatClient.ChatClientRequestSpec = this

        override fun system(text: String): ChatClient.ChatClientRequestSpec = this

        override fun system(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this

        override fun system(resource: Resource): ChatClient.ChatClientRequestSpec = this

        override fun system(systemSpecConsumer: Consumer<ChatClient.PromptSystemSpec>): ChatClient.ChatClientRequestSpec {
            return this
        }

        override fun user(text: String): ChatClient.ChatClientRequestSpec = this

        override fun user(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this

        override fun user(resource: Resource): ChatClient.ChatClientRequestSpec = this

        override fun user(userSpecConsumer: Consumer<ChatClient.PromptUserSpec>): ChatClient.ChatClientRequestSpec {
            return this
        }

        override fun templateRenderer(templateRenderer: TemplateRenderer): ChatClient.ChatClientRequestSpec = this

        override fun call(): ChatClient.CallResponseSpec = callResponseSpec

        override fun stream(): ChatClient.StreamResponseSpec = error("stream is not used")
    }

    private class FakeCallResponseSpec(
        private val result: CoachingStructuredResult,
        private val exception: RuntimeException?
    ) : ChatClient.CallResponseSpec {
        override fun <T : Any> entity(type: Class<T>): T {
            exception?.let { throw it }
            return type.cast(result)
        }

        override fun <T : Any> entity(type: ParameterizedTypeReference<T>): T = error("entity type reference is not used")

        override fun <T : Any> entity(structuredOutputConverter: StructuredOutputConverter<T>): T {
            error("entity converter is not used")
        }

        override fun chatClientResponse(): ChatClientResponse = error("chatClientResponse is not used")

        override fun chatResponse(): ChatResponse = error("chatResponse is not used")

        override fun content(): String = error("content is not used")

        override fun <T : Any> responseEntity(type: Class<T>): ResponseEntity<ChatResponse, T> {
            error("responseEntity class is not used")
        }

        override fun <T : Any> responseEntity(type: ParameterizedTypeReference<T>): ResponseEntity<ChatResponse, T> {
            error("responseEntity type reference is not used")
        }

        override fun <T : Any> responseEntity(
            structuredOutputConverter: StructuredOutputConverter<T>
        ): ResponseEntity<ChatResponse, T> {
            error("responseEntity converter is not used")
        }
    }

    private class FakeContextProvider : CoachingContextProvider(
        memberRepository = mock(MemberRepository::class.java),
        farmRepository = mock(FarmRepository::class.java),
        cropRepository = mock(CropRepository::class.java),
        farmingRecordRepository = mock(FarmingRecordRepository::class.java)
    ) {
        override fun build(command: CoachingRagCommand): CoachingContext {
            return CoachingContext("사용자 재배 context:")
        }
    }
}
