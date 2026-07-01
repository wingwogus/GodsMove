package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.coaching.CoachingFeedback
import com.godsmove.domain.coaching.CoachingFeedbackRepository
import com.godsmove.domain.coaching.CoachingMode
import com.godsmove.domain.crop.Crop
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.Farm
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecord
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.farming.WorkType
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
import java.time.LocalDate
import java.util.Optional
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
    fun `answer saves durable mode feedback with structured payload`() {
        val document = document("doc-1")
        val vectorStore = FakeVectorStore(listOf(document))
        val feedbackRepository = mock(CoachingFeedbackRepository::class.java)
        val memberRepository = mock(MemberRepository::class.java)
        val farmingRecordRepository = mock(FarmingRecordRepository::class.java)
        val member = member()
        val farm = farm(member = member)
        val crop = crop()
        val recordId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val record = farmingRecord(recordId = recordId, member = member, farm = farm, crop = crop)
        val savedId = UUID.fromString("00000000-0000-0000-0000-000000000201")

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(record)
        `when`(feedbackRepository.save(any(CoachingFeedback::class.java))).thenAnswer {
            savedFeedback(savedId, it.getArgument(0))
        }
        val service = service(
            vectorStore = vectorStore,
            chatClient = FakeChatClient(result = structuredResult(citationId = "doc-1")),
            feedbackRepository = feedbackRepository,
            memberRepository = memberRepository,
            farmingRecordRepository = farmingRecordRepository
        )

        val result = service.answer(
            CoachingRagCommand(
                memberId = memberId,
                mode = CoachingMode.REPORT_MANUAL,
                question = "리포트",
                recordId = recordId
            )
        )

        val captor = ArgumentCaptor.forClass(CoachingFeedback::class.java)
        verify(feedbackRepository).save(captor.capture())
        val feedback = captor.value
        assertThat(result.savedFeedbackId).isEqualTo(savedId)
        assertThat(feedback.member).isEqualTo(member)
        assertThat(feedback.record).isEqualTo(record)
        assertThat(feedback.farm).isEqualTo(farm)
        assertThat(feedback.crop).isEqualTo(crop)
        assertThat(feedback.question).isEqualTo("리포트")
        assertThat(feedback.summary).isEqualTo("배수 상태를 확인하세요.")
        assertThat(feedback.riskLevel).isEqualTo("LOW")
        assertThat(feedback.structuredResult["question"]).isEqualTo("리포트")
        assertThat(feedback.citations).containsExactly(
            mapOf("chunkId" to "doc-1", "label" to "영농일지 관수", "sourceType" to "FARMING_RECORD")
        )
        assertThat(feedback.auditStatus).isEqualTo("PASS")
    }

    @Test
    fun `answer rejects foreign farm during durable feedback save`() {
        val farmId = UUID.fromString("00000000-0000-0000-0000-000000000301")
        val memberRepository = mock(MemberRepository::class.java)
        val farmRepository = mock(FarmRepository::class.java)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(null)
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(result = structuredResult(citationId = "doc-1")),
            memberRepository = memberRepository,
            farmRepository = farmRepository
        )

        assertThatThrownBy {
            service.answer(
                CoachingRagCommand(
                    memberId = memberId,
                    mode = CoachingMode.REPORT_MANUAL,
                    question = "리포트",
                    farmId = farmId
                )
            )
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
    }

    @Test
    fun `answer rejects foreign record during durable feedback save`() {
        val recordId = UUID.fromString("00000000-0000-0000-0000-000000000401")
        val memberRepository = mock(MemberRepository::class.java)
        val farmingRecordRepository = mock(FarmingRecordRepository::class.java)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))
        `when`(farmingRecordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(null)
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(result = structuredResult(citationId = "doc-1")),
            memberRepository = memberRepository,
            farmingRecordRepository = farmingRecordRepository
        )

        assertThatThrownBy {
            service.answer(
                CoachingRagCommand(
                    memberId = memberId,
                    mode = CoachingMode.REPORT_MANUAL,
                    question = "리포트",
                    recordId = recordId
                )
            )
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
    }

    @Test
    fun `answer does not save durable feedback when audit fails`() {
        val feedbackRepository = mock(CoachingFeedbackRepository::class.java)
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(result = structuredResult(citationId = "unknown-doc")),
            feedbackRepository = feedbackRepository
        )

        val result = service.answer(
            CoachingRagCommand(
                memberId = memberId,
                mode = CoachingMode.REPORT_MANUAL,
                question = "리포트"
            )
        )

        assertThat(result.audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(result.savedFeedbackId).isNull()
        verify(feedbackRepository, never()).save(any(CoachingFeedback::class.java))
    }

    @Test
    fun `answer rejects record auto durable feedback without record id`() {
        val feedbackRepository = mock(CoachingFeedbackRepository::class.java)
        val service = service(
            vectorStore = FakeVectorStore(listOf(document("doc-1"))),
            chatClient = FakeChatClient(result = structuredResult(citationId = "doc-1")),
            feedbackRepository = feedbackRepository
        )

        assertThatThrownBy {
            service.answer(
                CoachingRagCommand(
                    memberId = memberId,
                    mode = CoachingMode.RECORD_AUTO,
                    question = "자동 코칭"
                )
            )
        }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_INVALID_REQUEST)
            }
        verify(feedbackRepository, never()).save(any(CoachingFeedback::class.java))
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
        chatClient: ChatClient = FakeChatClient(),
        feedbackRepository: CoachingFeedbackRepository = mock(CoachingFeedbackRepository::class.java),
        memberRepository: MemberRepository = mock(MemberRepository::class.java),
        farmRepository: FarmRepository = mock(FarmRepository::class.java),
        cropRepository: CropRepository = mock(CropRepository::class.java),
        farmingRecordRepository: FarmingRecordRepository = mock(FarmingRecordRepository::class.java)
    ): CoachingRagService {
        return CoachingRagService(
            chatClient = chatClient,
            vectorStore = vectorStore,
            contextProvider = FakeContextProvider(),
            filterBuilder = CoachingRetrievalFilterBuilder(),
            validator = CoachingStructuredOutputValidator(),
            persistencePolicy = CoachingFeedbackPersistencePolicy(),
            feedbackRepository = feedbackRepository,
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository,
            feedbackMapper = CoachingFeedbackMapper(),
            ragProperties = RagProperties()
        )
    }

    private fun member(): Member {
        return Member(
            id = memberId,
            email = "member@example.com",
            passwordHash = "hash"
        )
    }

    private fun farm(
        id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000102"),
        member: Member
    ): Farm {
        return Farm(
            id = id,
            owner = member,
            name = "테스트 농장",
            region = "경기",
            city = "수원",
            street = "테스트로 1"
        )
    }

    private fun crop(id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000103")): Crop {
        return Crop(
            id = id,
            name = "상추",
            category = "채소",
            lifecycleType = "ANNUAL",
            defaultUnit = "kg"
        )
    }

    private fun farmingRecord(
        recordId: UUID,
        member: Member,
        farm: Farm,
        crop: Crop
    ): FarmingRecord {
        return FarmingRecord(
            id = recordId,
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType(id = UUID.fromString("00000000-0000-0000-0000-000000000104"), name = "관수"),
            workedAt = LocalDateTime.parse("2026-07-01T09:00:00"),
            memo = "배수 확인",
            entryMode = "MANUAL"
        )
    }

    private fun savedFeedback(id: UUID, feedback: CoachingFeedback): CoachingFeedback {
        return CoachingFeedback(
            id = id,
            member = feedback.member,
            coachingMode = feedback.coachingMode,
            record = feedback.record,
            farm = feedback.farm,
            crop = feedback.crop,
            question = feedback.question,
            periodStartsOn = feedback.periodStartsOn,
            periodEndsOn = feedback.periodEndsOn,
            summary = feedback.summary,
            riskLevel = feedback.riskLevel,
            confidenceScore = feedback.confidenceScore,
            structuredResult = feedback.structuredResult,
            citations = feedback.citations,
            auditStatus = feedback.auditStatus,
            auditWarnings = feedback.auditWarnings,
            modelName = feedback.modelName,
            embeddingModel = feedback.embeddingModel
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
