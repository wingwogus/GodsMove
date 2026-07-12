package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.domain.coaching.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.RecordFeedbackActionDue

import com.chamchamcham.application.coaching.rag.common.RagProperties
import com.chamchamcham.application.coaching.rag.common.RagSourceType
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackFailureCode
import com.chamchamcham.application.coaching.recordfeedback.RecordFeedbackGenerationFailure
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

class RecordFeedbackGenerationServiceTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val context = readFixture("today-record-feedback-watering.json")

    @Test
    fun `generates only from context and official document evidence`() {
        val chatClient = FakeChatClient(validResult("doc-1", context.recordCitationId()))
        val vectorStore = FakeVectorStore(listOf(officialDocument("doc-1")))

        val generationResult = service(vectorStore = vectorStore, chatClient = chatClient)
            .generate(context, topK = 2)

        assertThat(generationResult.content.nextActions).hasSize(2)
        assertThat(generationResult.citations.map { it["id"] })
            .contains("doc-1", context.recordCitationId())
        assertThat(chatClient.requestSpec.advisorUseCount).isZero()
        assertThat(chatClient.requestSpec.userText).contains(context.recordCitationId())
        assertThat(vectorStore.requests).allSatisfy {
            assertThat(it.filterExpression?.toString().orEmpty())
                .contains("sourceType")
                .contains("TECH_DOCUMENT")
                .contains("cropName")
                .contains("참당귀")
                .contains("GENERAL")
        }
    }

    @Test
    fun `retries once when parsed output violates product validation`() {
        val invalidResult = validResult("doc-1", context.recordCitationId()).copy(
            nextActions = listOf(
                validAction(text = "짧음", refs = listOf("doc-1")),
                validAction(),
            ),
        )
        val chatClient = FakeChatClient(invalidResult, validResult("doc-1", context.recordCitationId()))

        service(documents = listOf(officialDocument("doc-1")), chatClient = chatClient)
            .generate(context)

        assertThat(chatClient.attempts).isEqualTo(2)
    }

    @Test
    fun `retries once when structured output parsing fails`() {
        val chatClient = FakeChatClient(
            RuntimeException("structured output parse failed"),
            validResult("doc-1", context.recordCitationId()),
        )

        service(documents = listOf(officialDocument("doc-1")), chatClient = chatClient)
            .generate(context)

        assertThat(chatClient.attempts).isEqualTo(2)
    }

    @Test
    fun `fails with structured output invalid after the single retry is exhausted`() {
        val invalidResult = validResult("doc-1", context.recordCitationId()).copy(
            goodPoint = validItem(text = "짧음", refs = listOf("doc-1")),
        )
        val chatClient = FakeChatClient(invalidResult, invalidResult)

        assertThatThrownBy {
            service(documents = listOf(officialDocument("doc-1")), chatClient = chatClient)
                .generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID)
        }
        assertThat(chatClient.attempts).isEqualTo(2)
    }

    @Test
    fun `fails with insufficient evidence when no official document is retrieved`() {
        assertThatThrownBy { service(documents = emptyList()).generate(context) }
            .isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
            }
    }

    @Test
    fun `context only retrieved documents are insufficient evidence`() {
        assertThatThrownBy {
            service(documents = listOf(contextOnlyDocument("record-doc"))).generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
        }
    }

    @Test
    fun `official document with blank id is insufficient evidence without llm call`() {
        val chatClient = FakeChatClient(validResult("doc-1", context.recordCitationId()))

        assertThatThrownBy {
            service(documents = listOf(officialDocument(id = " ")), chatClient = chatClient)
                .generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
        }
        assertThat(chatClient.attempts).isZero()
    }

    @Test
    fun `official document with blank text is insufficient evidence without llm call`() {
        val chatClient = FakeChatClient(validResult("doc-1", context.recordCitationId()))

        assertThatThrownBy {
            service(documents = listOf(officialDocument(text = " ")), chatClient = chatClient)
                .generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.INSUFFICIENT_EVIDENCE)
        }
        assertThat(chatClient.attempts).isZero()
    }

    @Test
    fun `returns server citation metadata after product output validation`() {
        val generated = service(documents = listOf(officialDocument("doc-1")))
            .generate(context)

        val documentCitation = generated.citations.first { citation -> citation["id"] == "doc-1" }
        assertThat(documentCitation["title"]).isEqualTo("농업기술길잡이 007 약용작물")
        assertThat(documentCitation["page"]).isEqualTo(123)
        assertThat(documentCitation["source"]).isEqualTo("guide.pdf")
        assertThat(documentCitation["sourceType"]).isEqualTo(RagSourceType.TECH_DOCUMENT.name)

        val recordCitation = generated.citations.first { citation -> citation["id"] == context.recordCitationId() }
        assertThat(recordCitation["title"]).isEqualTo("대상 영농기록")
        assertThat(recordCitation["sourceType"]).isEqualTo(RagSourceType.FARMING_RECORD.name)
    }

    @Test
    fun `maps vector store runtime failures to retrieval failed`() {
        assertThatThrownBy {
            service(vectorStore = FakeVectorStore(exception = IllegalStateException("vector unavailable")))
                .generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.RETRIEVAL_FAILED)
        }
    }

    @Test
    fun `maps chat runtime failures to chat unavailable without structured output retry`() {
        val chatClient = FakeChatClient(
            validResult("doc-1", context.recordCitationId()),
            callException = IllegalStateException("chat unavailable"),
        )

        assertThatThrownBy {
            service(documents = listOf(officialDocument("doc-1")), chatClient = chatClient)
                .generate(context)
        }.isInstanceOfSatisfying(RecordFeedbackGenerationFailure::class.java) {
            assertThat(it.code).isEqualTo(RecordFeedbackFailureCode.CHAT_UNAVAILABLE)
        }
        assertThat(chatClient.attempts).isZero()
    }

    private fun service(
        documents: List<Document>,
        chatClient: ChatClient = FakeChatClient(validResult("doc-1", context.recordCitationId())),
    ): RecordFeedbackGenerationService {
        return service(FakeVectorStore(documents), chatClient)
    }

    private fun service(
        vectorStore: FakeVectorStore,
        chatClient: ChatClient = FakeChatClient(validResult("doc-1", context.recordCitationId())),
    ): RecordFeedbackGenerationService {
        return RecordFeedbackGenerationService(
            chatClient = chatClient,
            vectorStore = vectorStore,
            queryPlanner = RecordFeedbackRetrievalQueryPlanner(),
            promptBuilder = RecordFeedbackPromptBuilder(),
            ragProperties = RagProperties(
                chat = RagProperties.Chat(model = "test-chat"),
                embedding = RagProperties.Embedding(model = "test-embedding"),
            ),
        )
    }

    private fun readFixture(name: String): RecordFeedbackContext {
        val resource = javaClass.classLoader.getResource("coaching/rag/$name")
            ?: error("Missing fixture: $name")
        return resource.openStream().use {
            objectMapper.readValue(it, RecordFeedbackContext::class.java)
        }
    }

    private fun officialDocument(
        id: String = "doc-1",
        text: String = "약용작물 관수 후 토양 수분과 배수 상태를 확인한다.",
    ): Document {
        val metadata = mapOf(
            "sourceType" to RagSourceType.TECH_DOCUMENT.name,
            "documentTitle" to "농업기술길잡이 007 약용작물",
            "page" to 123,
            "pdfPath" to "/data/rag/medicinal-plants/raw/pdfs/guide.pdf",
        )
        if (id.isBlank()) {
            return BlankIdDocument(text, metadata)
        }
        return Document.builder()
            .id(id)
            .text(text)
            .metadata(metadata)
            .build()
    }

    private fun contextOnlyDocument(id: String): Document {
        return Document.builder()
            .id(id)
            .text("대상 영농기록 context")
            .metadata("sourceType", RagSourceType.FARMING_RECORD.name)
            .metadata("documentTitle", "대상 영농기록")
            .build()
    }

    private fun validResult(
        documentCitationId: String,
        recordCitationId: String,
    ): RecordFeedbackContent {
        return RecordFeedbackContent(
            goodPoint = validItem(
                basis = "점적관수",
                text = "점적관수로 토양 상태를 확인한 점이 좋았어요.",
                refs = listOf(recordCitationId),
            ),
            nextActions = listOf(
                validAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.WEATHER,
                    basis = "비 예보",
                    text = "비 예보 전 배수로 막힘을 먼저 확인하세요.",
                    refs = listOf("weather:2026-07-04"),
                ),
                validAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = RecordFeedbackActionCategory.IRRIGATION,
                    basis = "토양 상태",
                    text = "다음 점검 때 토양 상태를 다시 살펴보세요.",
                    refs = listOf(recordCitationId, documentCitationId),
                ),
            ),
        )
    }

    private fun validItem(
        basis: String = "점적관수",
        text: String = "점적관수로 토양 상태를 확인한 점이 좋았어요.",
        refs: List<String> = listOf(context.recordCitationId()),
    ): RecordFeedbackGoodPoint {
        return RecordFeedbackGoodPoint(
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private fun validAction(
        due: RecordFeedbackActionDue = RecordFeedbackActionDue.NEXT_CHECK,
        category: RecordFeedbackActionCategory = RecordFeedbackActionCategory.IRRIGATION,
        basis: String = "토양 상태",
        text: String = "다음 점검 때 토양 상태를 다시 살펴보세요.",
        refs: List<String> = listOf(context.recordCitationId(), "doc-1"),
    ): RecordFeedbackAction {
        return RecordFeedbackAction(
            due = due,
            category = category,
            basis = basis,
            text = text,
            evidenceRefs = refs,
        )
    }

    private class FakeVectorStore(
        private val documents: List<Document> = emptyList(),
        private val exception: RuntimeException? = null,
    ) : VectorStore {
        val requests = mutableListOf<SearchRequest>()

        override fun add(documents: List<Document>) = Unit
        override fun delete(idList: List<String>) = Unit
        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> {
            requests += request
            exception?.let { throw it }
            return documents
        }
    }

    private class BlankIdDocument(
        text: String,
        metadata: Map<String, Any>,
    ) : Document("placeholder-id", text, metadata) {
        override fun getId(): String = " "
    }

    private class FakeChatClient(
        private vararg val responses: Any,
        val callException: RuntimeException? = null,
    ) : ChatClient {
        val requestSpec = FakeRequestSpec(this)
        var attempts = 0

        override fun prompt(): ChatClient.ChatClientRequestSpec = requestSpec
        override fun prompt(content: String): ChatClient.ChatClientRequestSpec = requestSpec
        override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec = requestSpec
        override fun mutate(): ChatClient.Builder = error("mutate is not used")

        fun nextResponse(): Any {
            val index = attempts.coerceAtMost(responses.lastIndex)
            attempts += 1
            return responses[index]
        }
    }

    private class FakeRequestSpec(
        private val chatClient: FakeChatClient,
    ) : ChatClient.ChatClientRequestSpec {
        var systemText: String = ""
        var userText: String = ""
        var advisorUseCount = 0

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
        override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>): ChatClient.ChatClientRequestSpec {
            advisorUseCount += 1
            return this
        }
        override fun advisors(vararg advisors: Advisor): ChatClient.ChatClientRequestSpec {
            advisorUseCount += advisors.size
            return this
        }
        override fun advisors(advisors: List<Advisor>): ChatClient.ChatClientRequestSpec {
            advisorUseCount += advisors.size
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
        override fun call(): ChatClient.CallResponseSpec {
            chatClient.callException?.let { throw it }
            return FakeCallResponseSpec(chatClient)
        }
        override fun stream(): ChatClient.StreamResponseSpec = error("stream is not used")
    }

    private class FakeCallResponseSpec(
        private val chatClient: FakeChatClient,
    ) : ChatClient.CallResponseSpec {
        override fun <T : Any> entity(type: Class<T>): T {
            return when (val response = chatClient.nextResponse()) {
                is RuntimeException -> throw response
                else -> type.cast(response)
            }
        }

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
