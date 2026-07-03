package com.chamchamcham.application.coaching.rag.record

import com.chamchamcham.application.coaching.rag.CoachingActionDue
import com.chamchamcham.application.coaching.rag.CoachingCitationRef
import com.chamchamcham.application.coaching.rag.CoachingNextAction
import com.chamchamcham.application.coaching.rag.CoachingObservation
import com.chamchamcham.application.coaching.rag.CoachingPriority
import com.chamchamcham.application.coaching.rag.CoachingRecommendation
import com.chamchamcham.application.coaching.rag.CoachingRiskLevel
import com.chamchamcham.application.coaching.rag.CoachingStructuredOutputValidator
import com.chamchamcham.application.coaching.rag.CoachingStructuredResult
import com.chamchamcham.application.coaching.rag.RagProperties
import com.chamchamcham.application.coaching.rag.RagSourceType
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.nio.charset.Charset
import java.util.function.Consumer

class TodayRecordFeedbackServiceTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `generate rejects invalid schema before vector search`() {
        val vectorStore = FakeVectorStore(emptyList())
        val service = service(vectorStore = vectorStore)
        val context = readFixture("today-record-feedback-watering.json").copy(schemaVersion = "record-feedback-context.v2")

        assertThatThrownBy { service.generate(context) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
        assertThat(vectorStore.searchCalls).isEqualTo(0)
    }

    @Test
    fun `generate returns insufficient evidence when official docs are not retrieved`() {
        val result = service(vectorStore = FakeVectorStore(emptyList()))
            .generate(readFixture("today-record-feedback-watering.json"))

        assertThat(result.audit.warnings).contains("no_retrieved_documents")
        assertThat(result.result.riskLevel).isEqualTo(CoachingRiskLevel.UNKNOWN)
        assertThat(result.result.limitations).contains("검색된 공식문서 근거가 없습니다.")
    }

    @Test
    fun `generate retrieves official documents with planned queries and audits structured response`() {
        val vectorStore = FakeVectorStore(listOf(officialDocument("doc-1")))
        val chatClient = FakeChatClient(structuredResult("doc-1"))
        val result = service(vectorStore = vectorStore, chatClient = chatClient)
            .generate(readFixture("today-record-feedback-watering.json"), topK = 2)

        assertThat(result.audit.citations).containsExactly("doc-1")
        assertThat(result.model.chat).isEqualTo("test-chat")
        assertThat(vectorStore.requests.map { it.query }).contains("참당귀 물주기 재배 관리 약용작물")
        assertThat(vectorStore.requests).allSatisfy {
            assertThat(it.filterExpression?.toString().orEmpty())
                .contains("sourceType")
                .contains("TECH_DOCUMENT")
        }
        assertThat(chatClient.requestSpec.systemText).contains("약용작물 영농기록 피드백")
        assertThat(chatClient.requestSpec.userText).contains("오전 흙 표면이 말라 보여 점적 관수함.")
    }

    private fun service(
        vectorStore: FakeVectorStore,
        chatClient: ChatClient = FakeChatClient(structuredResult("doc-1"))
    ): TodayRecordFeedbackService {
        return TodayRecordFeedbackService(
            chatClient = chatClient,
            vectorStore = vectorStore,
            contextValidator = TodayRecordFeedbackContextValidator(),
            queryPlanner = RecordFeedbackRetrievalQueryPlanner(),
            promptBuilder = RecordFeedbackPromptBuilder(),
            outputValidator = CoachingStructuredOutputValidator(),
            ragProperties = RagProperties(
                chat = RagProperties.Chat(model = "test-chat"),
                embedding = RagProperties.Embedding(model = "test-embedding")
            )
        )
    }

    private fun readFixture(name: String): TodayRecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, TodayRecordFeedbackContext::class.java)
        }
    }

    private fun officialDocument(id: String): Document {
        return Document.builder()
            .id(id)
            .text("약용작물 관수 후 토양 수분과 배수 상태를 확인한다.")
            .metadata("sourceType", RagSourceType.TECH_DOCUMENT.name)
            .metadata("documentTitle", "농업기술길잡이 007 약용작물")
            .metadata("page", 123)
            .build()
    }

    private fun structuredResult(citationId: String): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = "관수 판단은 최근 건조 조건을 반영했습니다.",
            riskLevel = CoachingRiskLevel.LOW,
            confidence = 0.74,
            observations = listOf(CoachingObservation("건조 조건", "최근 7일 강수량이 적습니다.", listOf(citationId))),
            diagnosis = "현재 기록만으로 과습 위험은 높지 않습니다.",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.MEDIUM, "다음 관수 전 토양 수분 확인", "건조 조건", null, listOf(citationId))
            ),
            nextActions = listOf(CoachingNextAction(CoachingActionDue.NEXT_CHECK, "잎 처짐과 토양 상태 기록", listOf(citationId))),
            followUpQuestions = listOf("배수가 잘 되지 않는 구역이 있나요?"),
            citations = listOf(CoachingCitationRef(citationId, "농업기술길잡이 007 약용작물", RagSourceType.TECH_DOCUMENT))
        )
    }

    private class FakeVectorStore(
        private val documents: List<Document>
    ) : VectorStore {
        val requests = mutableListOf<SearchRequest>()
        val searchCalls: Int
            get() = requests.size

        override fun add(documents: List<Document>) = Unit

        override fun delete(idList: List<String>) = Unit

        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> {
            requests += request
            return documents
        }
    }

    private class FakeChatClient(
        private val result: CoachingStructuredResult
    ) : ChatClient {
        val requestSpec = FakeRequestSpec(FakeCallResponseSpec(result))

        override fun prompt(): ChatClient.ChatClientRequestSpec = requestSpec

        override fun prompt(content: String): ChatClient.ChatClientRequestSpec = requestSpec

        override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec = requestSpec

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
    }

    private class FakeRequestSpec(
        private val callResponseSpec: ChatClient.CallResponseSpec
    ) : ChatClient.ChatClientRequestSpec {
        var systemText: String = ""
        var userText: String = ""

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
        override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>): ChatClient.ChatClientRequestSpec = this
        override fun advisors(vararg advisors: Advisor): ChatClient.ChatClientRequestSpec = this
        override fun advisors(advisors: List<Advisor>): ChatClient.ChatClientRequestSpec = this
        override fun messages(vararg messages: Message): ChatClient.ChatClientRequestSpec = this
        override fun messages(messages: List<Message>): ChatClient.ChatClientRequestSpec = this
        override fun <T : ChatOptions> options(chatOptions: T): ChatClient.ChatClientRequestSpec = this
        override fun toolNames(vararg toolNames: String): ChatClient.ChatClientRequestSpec = this
        override fun tools(vararg tools: Any): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(vararg toolCallbacks: ToolCallback): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(toolCallbacks: List<ToolCallback>): ChatClient.ChatClientRequestSpec = this
        override fun toolCallbacks(vararg toolCallbackProviders: ToolCallbackProvider): ChatClient.ChatClientRequestSpec = this
        override fun toolContext(toolContext: Map<String, Any>): ChatClient.ChatClientRequestSpec = this

        override fun system(text: String): ChatClient.ChatClientRequestSpec {
            systemText = text
            return this
        }

        override fun system(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this
        override fun system(resource: Resource): ChatClient.ChatClientRequestSpec = this
        override fun system(systemSpecConsumer: Consumer<ChatClient.PromptSystemSpec>): ChatClient.ChatClientRequestSpec = this

        override fun user(text: String): ChatClient.ChatClientRequestSpec {
            userText = text
            return this
        }

        override fun user(resource: Resource, charset: Charset): ChatClient.ChatClientRequestSpec = this
        override fun user(resource: Resource): ChatClient.ChatClientRequestSpec = this
        override fun user(userSpecConsumer: Consumer<ChatClient.PromptUserSpec>): ChatClient.ChatClientRequestSpec = this
        override fun templateRenderer(templateRenderer: TemplateRenderer): ChatClient.ChatClientRequestSpec = this
        override fun call(): ChatClient.CallResponseSpec = callResponseSpec
        override fun stream(): ChatClient.StreamResponseSpec = error("stream is not used")
    }

    private class FakeCallResponseSpec(
        private val result: CoachingStructuredResult
    ) : ChatClient.CallResponseSpec {
        override fun <T : Any> entity(type: Class<T>): T = type.cast(result)
        override fun <T : Any> entity(type: ParameterizedTypeReference<T>): T = error("entity type reference is not used")
        override fun <T : Any> entity(structuredOutputConverter: StructuredOutputConverter<T>): T = error("entity converter is not used")
        override fun chatClientResponse(): ChatClientResponse = error("chatClientResponse is not used")
        override fun chatResponse(): ChatResponse = error("chatResponse is not used")
        override fun content(): String = error("content is not used")
        override fun <T : Any> responseEntity(type: Class<T>): ResponseEntity<ChatResponse, T> = error("responseEntity class is not used")
        override fun <T : Any> responseEntity(type: ParameterizedTypeReference<T>): ResponseEntity<ChatResponse, T> = error("responseEntity type reference is not used")
        override fun <T : Any> responseEntity(structuredOutputConverter: StructuredOutputConverter<T>): ResponseEntity<ChatResponse, T> = error("responseEntity converter is not used")
    }
}
