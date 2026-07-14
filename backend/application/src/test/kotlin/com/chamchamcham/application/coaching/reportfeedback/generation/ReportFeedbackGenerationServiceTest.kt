package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.application.coaching.common.RagProperties
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.WorkType
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
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.util.UUID
import java.util.function.Consumer

class ReportFeedbackGenerationServiceTest {
    private val recordId = UUID.randomUUID()

    @Test
    fun `validation failure is retried with safe diagnostic codes`() {
        val client = FakeChatClient(invalidToneContent(), validContent())

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last()).contains("summary_text_tone")
    }

    @Test
    fun `language failure is retried with a fixed diagnostic code only`() {
        val generatedText = "WATERING 관수 흐름을 확인했어요."
        val client = FakeChatClient(
            validContent().copy(summary = generatedText),
            validContent(),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last())
            .contains("summary_text_language")
            .doesNotContain(generatedText, "WATERING 관수")
    }

    @Test
    fun `two language failures end as structured output invalid`() {
        val invalid = validContent().copy(summary = "WATERING 관수 흐름을 확인했어요.")
        val client = FakeChatClient(invalid, invalid)

        assertThatThrownBy { service(client).generate(context()) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID)
            }
        assertThat(client.attempts).isEqualTo(2)
    }

    @Test
    fun `unknown evidence value is not echoed into retry prompt`() {
        val privateValue = "private-model-value"
        val client = FakeChatClient(
            validContent().copy(
                strengths = listOf(item(evidenceRefs = listOf(privateValue))),
            ),
            validContent(),
        )

        service(client).generate(context())

        assertThat(client.requestSpec.userTexts.last())
            .contains("unknown_evidence")
            .doesNotContain(privateValue)
    }

    @Test
    fun `two invalid attempts fail only as structured output invalid`() {
        val client = FakeChatClient(invalidToneContent(), invalidToneContent())

        assertThatThrownBy { service(client).generate(context()) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID)
            }
        assertThat(client.attempts).isEqualTo(2)
    }

    @Test
    fun `entity time chat failure is not retried as invalid structured output`() {
        val client = FakeChatClient(BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE))

        assertThatThrownBy { service(client).generate(context()) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.CHAT_UNAVAILABLE)
            }
        assertThat(client.attempts).isEqualTo(1)
    }

    @Test
    fun `structured output parse failure still receives one retry`() {
        val client = FakeChatClient(RuntimeException("parse failed"), validContent())

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last()).contains("structured_output_parse_failed")
    }

    @Test
    fun `mixed work type records are rejected before retrieval`() {
        val vectorStore = FakeVectorStore()
        val mixed = context().copy(
            records = context().records + context().records.single().copy(workType = WorkType.FERTILIZING),
        )

        assertThatThrownBy { service(FakeChatClient(validContent()), vectorStore).generate(mixed) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.INVALID_CONTEXT)
            }
        assertThat(vectorStore.requests).isEmpty()
    }

    @Test
    fun `record-backed output is valid without a retrieved technical document`() {
        val result = service(FakeChatClient(validContent())).generate(context())

        assertThat(result.content.strengths).hasSize(1)
        assertThat(result.citations.map { it["id"] }).containsExactly("record:$recordId")
    }

    private fun service(
        client: FakeChatClient,
        vectorStore: FakeVectorStore = FakeVectorStore(),
    ) = ReportFeedbackGenerationService(
        chatClient = client,
        vectorStore = vectorStore,
        queryPlanner = ReportFeedbackRetrievalQueryPlanner(),
        promptBuilder = ReportFeedbackPromptBuilder(),
        ragProperties = RagProperties(
            chat = RagProperties.Chat(model = "test-chat"),
            embedding = RagProperties.Embedding(model = "test-embedding"),
        ),
    )

    private fun context() = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = WorkType.WATERING,
        report = ReportFeedbackReport(
            id = UUID.randomUUID(),
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            statistics = mapOf("recordCount" to 1),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = recordId,
                workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                workType = WorkType.WATERING,
                memo = "점적관수를 했어요.",
                details = emptyMap(),
            ),
        ),
        previousReport = null,
        warnings = emptyList(),
    )

    private fun validContent() = ReportFeedbackContent(
        summary = "이번 물 주기 기록의 흐름을 확인했어요.",
        strengths = listOf(item()),
        improvements = emptyList(),
        nextActions = emptyList(),
    )

    private fun invalidToneContent() = validContent().copy(
        summary = "이번 물 주기 기록의 흐름을 확인했습니다.",
    )

    private fun item(
        evidenceRefs: List<String> = listOf("record:$recordId"),
    ) = ReportFeedbackContentItem(
        basis = "관수 기록 1회",
        text = "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.",
        evidenceRefs = evidenceRefs,
    )

    private class FakeVectorStore : VectorStore {
        val requests = mutableListOf<SearchRequest>()

        override fun add(documents: List<Document>) = Unit
        override fun delete(idList: List<String>) = Unit
        override fun delete(filterExpression: Filter.Expression) = Unit
        override fun similaritySearch(request: SearchRequest): List<Document> {
            requests += request
            return emptyList()
        }
    }

    private class FakeChatClient(
        private vararg val responses: Any,
    ) : ChatClient {
        val requestSpec = FakeRequestSpec(this)
        var attempts = 0

        override fun prompt(): ChatClient.ChatClientRequestSpec = requestSpec
        override fun prompt(content: String): ChatClient.ChatClientRequestSpec = requestSpec
        override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec = requestSpec
        override fun mutate(): ChatClient.Builder = error("mutate is not used")

        fun nextResponse(): Any {
            val response = responses[attempts.coerceAtMost(responses.lastIndex)]
            attempts += 1
            return response
        }
    }

    private class FakeRequestSpec(
        private val chatClient: FakeChatClient,
    ) : ChatClient.ChatClientRequestSpec {
        val userTexts = mutableListOf<String>()

        override fun mutate(): ChatClient.Builder = error("mutate is not used")
        override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>) = this
        override fun advisors(vararg advisors: Advisor) = this
        override fun advisors(advisors: List<Advisor>) = this
        override fun messages(vararg messages: Message) = this
        override fun messages(messages: List<Message>) = this
        override fun <T : ChatOptions> options(chatOptions: T) = this
        override fun toolNames(vararg toolNames: String) = this
        override fun tools(vararg tools: Any) = this
        override fun toolCallbacks(vararg toolCallbacks: ToolCallback) = this
        override fun toolCallbacks(toolCallbacks: List<ToolCallback>) = this
        override fun toolCallbacks(vararg toolCallbackProviders: ToolCallbackProvider) = this
        override fun toolContext(toolContext: Map<String, Any>) = this
        override fun system(text: String) = this
        override fun system(resource: Resource, charset: Charset) = this
        override fun system(resource: Resource) = this
        override fun system(systemSpecConsumer: Consumer<ChatClient.PromptSystemSpec>) = this
        override fun user(text: String): ChatClient.ChatClientRequestSpec {
            userTexts += text
            return this
        }
        override fun user(resource: Resource, charset: Charset) = this
        override fun user(resource: Resource) = this
        override fun user(userSpecConsumer: Consumer<ChatClient.PromptUserSpec>) = this
        override fun templateRenderer(templateRenderer: TemplateRenderer) = this
        override fun call(): ChatClient.CallResponseSpec = FakeCallResponseSpec(chatClient)
        override fun stream(): ChatClient.StreamResponseSpec = error("stream is not used")
    }

    private class FakeCallResponseSpec(
        private val chatClient: FakeChatClient,
    ) : ChatClient.CallResponseSpec {
        override fun <T : Any> entity(type: Class<T>): T = when (val response = chatClient.nextResponse()) {
            is RuntimeException -> throw response
            else -> type.cast(response)
        }
        override fun <T : Any> entity(type: ParameterizedTypeReference<T>): T = error("not used")
        override fun <T : Any> entity(structuredOutputConverter: StructuredOutputConverter<T>): T = error("not used")
        override fun chatClientResponse(): ChatClientResponse = error("not used")
        override fun chatResponse(): ChatResponse = error("not used")
        override fun content(): String = error("not used")
        override fun <T : Any> responseEntity(type: Class<T>): ResponseEntity<ChatResponse, T> = error("not used")
        override fun <T : Any> responseEntity(type: ParameterizedTypeReference<T>): ResponseEntity<ChatResponse, T> = error("not used")
        override fun <T : Any> responseEntity(structuredOutputConverter: StructuredOutputConverter<T>): ResponseEntity<ChatResponse, T> = error("not used")
    }
}
