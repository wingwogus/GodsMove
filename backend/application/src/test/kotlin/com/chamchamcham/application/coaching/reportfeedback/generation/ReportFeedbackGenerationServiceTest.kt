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
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import java.util.function.Consumer

class ReportFeedbackGenerationServiceTest {
    private val recordId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val previousReportId = UUID.randomUUID()

    @Test
    fun `evidence validation failure is retried with safe diagnostic codes`() {
        val client = FakeChatClient(
            validContent().copy(strengths = listOf(item(evidenceRefs = listOf("unknown")))),
            validContent(),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last()).contains("unknown_evidence")
    }

    @Test
    fun `section shape failures are retried with safe diagnostic codes`() {
        val invalid = validContent().copy(
            strengths = emptyList(),
        )
        val client = FakeChatClient(invalid, validContent())

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last())
            .contains("strength_count")
    }

    @Test
    fun `normalizes line breaks in public text before validation`() {
        val summary = "가".repeat(10) + "\n  " + "나".repeat(10)
        val comparison = "다".repeat(10) + "\r\n  " + "라".repeat(10)
        val strength = "마".repeat(10) + "\r  " + "바".repeat(10)
        val improvement = "사".repeat(10) + "\n\n  " + "아".repeat(10)
        val nextAction = "자".repeat(10) + " \n " + "차".repeat(10)
        val client = FakeChatClient(
            validContent().copy(
                summary = summary,
                comparisons = listOf(comparisonItem(text = comparison)),
                strengths = listOf(item(text = strength)),
                improvements = listOf(item(text = improvement)),
                nextActions = listOf(item(text = nextAction)),
            ),
        )

        val result = service(client).generate(context()).content

        assertThat(client.attempts).isEqualTo(1)
        assertThat(result.summary).isEqualTo("가".repeat(10) + " " + "나".repeat(10))
        assertThat(result.comparisons.single().text).isEqualTo("다".repeat(10) + " " + "라".repeat(10))
        assertThat(result.strengths.single().text).isEqualTo("마".repeat(10) + " " + "바".repeat(10))
        assertThat(result.improvements.single().text).isEqualTo("사".repeat(10) + " " + "아".repeat(10))
        assertThat(result.nextActions.single().text).isEqualTo("자".repeat(10) + " " + "차".repeat(10))
    }

    @Test
    fun `length failures are retried with safe diagnostic codes`() {
        val generatedText = "가".repeat(66)
        val invalid = validContent().copy(
            summary = generatedText,
            nextActions = listOf(item(text = generatedText)),
        )
        val client = FakeChatClient(invalid, validContent())

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last())
            .contains("summary_text_length", "next_action_text_length")
            .doesNotContain(generatedText)
    }

    @Test
    fun `minimum length failures are retried with safe diagnostic codes`() {
        val generatedText = "가".repeat(19)
        val invalid = validContent().copy(
            summary = "가".repeat(19),
            nextActions = listOf(item(text = generatedText)),
        )
        val client = FakeChatClient(invalid, validContent())

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last())
            .contains("summary_text_length", "next_action_text_length")
            .doesNotContain(generatedText)
    }

    @Test
    fun `English summary is accepted without retry`() {
        val generatedText = "WATERING 작업 기록의 전체 흐름과 관리 방향을 확인했어요."
        val client = FakeChatClient(
            validContent().copy(summary = generatedText),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(1)
    }

    @Test
    fun `English item is accepted without retry`() {
        val generatedText = "다음에는 DRIP 방식으로 물을 준 뒤 흙 속 수분과 젖은 깊이를 확인하세요."
        val client = FakeChatClient(
            validContent().copy(nextActions = listOf(item(text = generatedText))),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(1)
    }

    @Test
    fun `English comparison is accepted without retry`() {
        val generatedText = "WATERING 기록이 직전보다 늘어 작업 흐름의 변화를 확인했어요."
        val client = FakeChatClient(
            validContent().copy(comparisons = listOf(comparisonItem(text = generatedText))),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(1)
    }

    @Test
    fun `unavailable comparison is retried with a fixed diagnostic code only`() {
        val generatedText = "직전 재배보다 물 주기 기록이 한 번 늘었어요."
        val client = FakeChatClient(
            validContent().copy(
                comparisons = listOf(
                    comparisonItem(text = generatedText, evidenceRefs = listOf("report:$reportId")),
                ),
            ),
            validContent().copy(comparisons = emptyList()),
        )
        val unavailableContext = context().copy(previousReport = null, comparisons = emptyList())

        service(client).generate(unavailableContext)

        assertThat(client.attempts).isEqualTo(2)
        assertThat(client.requestSpec.userTexts.last())
            .contains("comparison_not_available")
            .doesNotContain(generatedText)
    }

    @Test
    fun `missing comparison report reference is retried with a fixed diagnostic code only`() {
        val client = FakeChatClient(
            validContent().copy(
                comparisons = listOf(
                    comparisonItem(evidenceRefs = listOf("report:$previousReportId")),
                ),
            ),
            validContent(),
        )

        service(client).generate(context())

        assertThat(client.attempts).isEqualTo(2)
        val retryDiagnostic = client.requestSpec.userTexts.last().substringAfter("직전 응답은")
        assertThat(retryDiagnostic)
            .contains("comparison_current_report_ref_required")
            .doesNotContain(reportId.toString(), previousReportId.toString())
    }

    @Test
    fun `English output remains valid on the first attempt`() {
        val content = validContent().copy(
            summary = "WATERING 작업 기록의 전체 흐름과 관리 방향을 확인했어요.",
        )
        val client = FakeChatClient(content, content)

        assertThat(service(client).generate(context()).content).isEqualTo(content)
        assertThat(client.attempts).isEqualTo(1)
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
    fun `two unknown evidence attempts fail only as structured output invalid`() {
        val invalid = validContent().copy(strengths = listOf(item(evidenceRefs = listOf("unknown"))))
        val client = FakeChatClient(invalid, invalid)

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
    fun `two parse failures expose only the safe parse diagnostic`() {
        val rawMessage = "private generated response"
        val client = FakeChatClient(RuntimeException(rawMessage), RuntimeException(rawMessage))

        assertThatThrownBy { service(client).generate(context()) }
            .isInstanceOfSatisfying(ReportFeedbackGenerationFailure::class.java) {
                assertThat(it.code).isEqualTo(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID)
                assertThat(it.cause?.message)
                    .isEqualTo("structured_output_parse_failed")
                    .doesNotContain(rawMessage)
            }
        assertThat(client.attempts).isEqualTo(2)
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
        assertThat(result.citations.map { it["id"] }).containsExactly(
            "report:$reportId",
            "report:$previousReportId",
            "record:$recordId",
        )
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
            id = reportId,
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
        previousReport = ReportFeedbackPreviousReport(
            id = previousReportId,
            startsAt = LocalDateTime.of(2025, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2025, 7, 1, 9, 0),
            statistics = mapOf("recordCount" to 1),
        ),
        comparisons = listOf(
            ReportFeedbackComparison(
                metricKey = "recordCount",
                metricLabel = "기록 횟수",
                currentValue = BigDecimal("2"),
                previousValue = BigDecimal("1"),
                difference = BigDecimal("1"),
                relativeChangePct = BigDecimal("100"),
                unit = "회",
            ),
        ),
        warnings = emptyList(),
    )

    private fun validContent() = ReportFeedbackContent(
        summary = "이번 물 주기 기록의 전체 흐름과 관리 방향을 확인했어요.",
        comparisons = listOf(comparisonItem()),
        strengths = listOf(
            item(text = "흙 상태를 살핀 뒤 물을 주어 필요한 곳부터 관리한 점이 좋았어요."),
        ),
        improvements = listOf(
            item(text = "물의 양이 알맞았는지 흙 속 수분까지 확인해 기록할 필요가 있어요."),
        ),
        nextActions = listOf(
            item(text = "다음에는 물을 준 뒤 흙 속까지 젖었는지 손으로 확인해 기록하세요."),
        ),
    )

    private fun item(
        text: String = "흙 상태를 살핀 뒤 물을 주어 필요한 곳부터 관리한 점이 좋았어요.",
        evidenceRefs: List<String> = listOf("record:$recordId"),
    ) = ReportFeedbackContentItem(
        basis = "관수 기록 1회",
        text = text,
        evidenceRefs = evidenceRefs,
    )

    private fun comparisonItem(
        text: String = "직전 재배보다 물 주기 기록이 한 번 늘어 흐름이 안정됐어요.",
        evidenceRefs: List<String> = listOf("report:$reportId", "report:$previousReportId"),
    ) = ReportFeedbackContentItem(
        basis = "직전보다 기록 1회 증가",
        text = text,
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
