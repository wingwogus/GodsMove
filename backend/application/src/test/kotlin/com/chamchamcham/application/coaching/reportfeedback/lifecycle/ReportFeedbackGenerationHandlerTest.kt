package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.coaching.reportfeedback.generation.REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContent
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContentItem
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContext
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationResult
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationService
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackRecord
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackReport
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackItemSection
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.report.CycleReportStatistics
import com.chamchamcham.domain.report.FarmingCycleReport
import com.chamchamcham.domain.report.FarmingCycleReportProjection
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, OutputCaptureExtension::class)
class ReportFeedbackGenerationHandlerTest {
    private val memberId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()
    private val sourceFingerprint = "a".repeat(64)
    private val startsAt = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val endsAt = LocalDateTime.of(2026, 7, 1, 9, 0)

    @Mock private lateinit var feedbackRepository: ReportFeedbackRepository
    @Mock private lateinit var generationService: ReportFeedbackGenerationService

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private lateinit var member: Member
    private lateinit var report: FarmingCycleReport
    private lateinit var handler: ReportFeedbackGenerationHandler

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        val farm = Farm(id = UUID.randomUUID(), owner = member, name = "약초농장", roadAddress = "강원도 평창군")
        val crop = Crop(
            id = UUID.randomUUID(),
            externalNo = 422,
            name = "황기",
            usePartCategory = CropUsePartCategory.ROOT_BARK,
        )
        val finalHarvest = FarmingRecord(
            id = UUID.randomUUID(),
            member = member,
            farm = farm,
            crop = crop,
            workType = WorkType.HARVEST,
            workedAt = endsAt,
            weatherCondition = "맑음",
            weatherTemperature = 24,
            memo = "최종 수확",
            entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
        )
        report = FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = startsAt,
                endsAt = endsAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = CycleReportStatistics.empty(),
            ),
        ).also { setId(it, reportId) }
        handler = ReportFeedbackGenerationHandler(
            feedbackRepository,
            generationService,
            objectMapper,
            NoOpTransactionManager(),
        )
    }

    @Test
    fun `generation readies only the requested row when the valid result has no items`() {
        val target = pendingFeedback(WorkType.WATERING)
        val sibling = pendingFeedback(WorkType.FERTILIZING, UUID.randomUUID(), context(WorkType.FERTILIZING))
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        lateinit var generated: ReportFeedbackContext
        `when`(generationService.generate(anyContext())).thenAnswer { invocation ->
            generated = invocation.getArgument(0)
            emptyItemResult()
        }

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(target.summary).isEqualTo("이번 물 주기 기록의 흐름을 확인했어요.")
        assertThat(target.items()).isEmpty()
        assertThat(sibling.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(generated.workType).isEqualTo(WorkType.WATERING)
        assertThat(generated.records).allMatch { it.workType == WorkType.WATERING }
    }

    @Test
    fun `generation persists comparison rows before the other sections`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenReturn(
            ReportFeedbackGenerationResult(
                content = ReportFeedbackContent(
                    summary = "이번 물 주기 기록의 흐름을 확인했어요.",
                    comparisons = listOf(
                        contentItem("직전보다 기록 1회 증가", "직전 재배보다 물 주기 기록이 한 번 늘었어요."),
                    ),
                    strengths = listOf(
                        contentItem("현재 기록", "물 준 기록을 남겨 흐름을 확인하기 좋았어요."),
                    ),
                    improvements = emptyList(),
                    nextActions = emptyList(),
                ),
                citations = emptyList(),
                auditWarnings = emptyList(),
                modelInfo = RagModelInfo(embedding = "embedding-test", chat = "chat-test"),
            ),
        )

        handler.on(event)

        assertThat(target.items().map { it.section }).containsExactly(
            ReportFeedbackItemSection.COMPARISON,
            ReportFeedbackItemSection.STRENGTH,
        )
        assertThat(target.items().map { it.displayOrder }).containsExactly(0, 1)
        assertThat(target.items().map { it.text }).containsExactly(
            "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
            "물 준 기록을 남겨 흐름을 확인하기 좋았어요.",
        )
    }

    @Test
    fun `generation ignores an event whose report does not match the target row`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING, reportId = UUID.randomUUID())
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(target)

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(generationService, never()).generate(anyContext())
        verify(feedbackRepository, never()).findByIdAndMemberIdForUpdate(feedbackId, memberId)
    }

    @Test
    fun `generation ignores an event whose work type does not match the target row`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.FERTILIZING)
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(target)

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(generationService, never()).generate(anyContext())
        verify(feedbackRepository, never()).findByIdAndMemberIdForUpdate(feedbackId, memberId)
    }

    @Test
    fun `generation ignores an event whose fingerprint does not match the target row`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING, sourceFingerprint = "b".repeat(64))
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(target)

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(generationService, never()).generate(anyContext())
        verify(feedbackRepository, never()).findByIdAndMemberIdForUpdate(feedbackId, memberId)
    }

    @Test
    fun `generation result is discarded when the snapshot changes before the final write`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenAnswer {
            target.attachInputSnapshot(snapshot(context().copy(warnings = listOf("snapshot replaced"))))
            emptyItemResult()
        }

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(target.summary).isNull()
    }

    @Test
    fun `generation result is discarded when the source fingerprint changes before the final write`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenAnswer {
            ReflectionTestUtils.setField(target, "sourceFingerprint", "b".repeat(64))
            emptyItemResult()
        }

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(target.summary).isNull()
    }

    @Test
    fun `expected generation failure fails only the requested work type row`() {
        val target = pendingFeedback(WorkType.WATERING)
        val sibling = pendingFeedback(WorkType.FERTILIZING, UUID.randomUUID(), context(WorkType.FERTILIZING))
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenThrow(
            ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE),
        )

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(target.failureCode).isEqualTo(ReportFeedbackFailureCode.CHAT_UNAVAILABLE.name)
        assertThat(sibling.status).isEqualTo(ReportFeedbackStatus.PENDING)
    }

    @Test
    fun `generation failure is discarded when the source fingerprint changes before the final write`() {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenAnswer {
            ReflectionTestUtils.setField(target, "sourceFingerprint", "b".repeat(64))
            throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE)
        }

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(target.failureCode).isNull()
    }

    @Test
    fun `generation failure logs safe diagnostics without raw evidence values`(output: CapturedOutput) {
        val target = pendingFeedback(WorkType.WATERING)
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)
        `when`(generationService.generate(anyContext())).thenThrow(
            ReportFeedbackGenerationFailure(
                ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID,
                IllegalStateException(
                    "comparison_not_available,strength_count,next_action_text_paragraph," +
                        "summary_text_length,comparison_text_length," +
                        "unknown_evidence:untrusted-generated-value",
                ),
            ),
        )

        handler.on(event)

        val logs = output.out + output.err
        assertThat(logs)
            .contains("report feedback generation failed")
            .contains("STRUCTURED_OUTPUT_INVALID")
            .contains("comparison_not_available")
            .contains("strength_count")
            .contains("next_action_text_paragraph")
            .contains("summary_text_length")
            .contains("comparison_text_length")
            .contains("unknown_evidence")
            .doesNotContain("untrusted-generated-value")
    }

    @Test
    fun `malformed target snapshot fails only that row`() {
        val target = ReportFeedback(
            id = feedbackId,
            member = member,
            report = report,
            workType = WorkType.WATERING,
            status = ReportFeedbackStatus.PENDING,
            sourceFingerprint = sourceFingerprint,
        ).also { it.attachInputSnapshot(mapOf("schemaVersion" to "invalid")) }
        val sibling = pendingFeedback(WorkType.FERTILIZING, UUID.randomUUID(), context(WorkType.FERTILIZING))
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(target.failureCode).isEqualTo(ReportFeedbackFailureCode.INVALID_CONTEXT_SNAPSHOT.name)
        assertThat(sibling.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(generationService, never()).generate(anyContext())
    }

    @Test
    fun `well formed snapshot for a different work type fails as invalid context`() {
        val target = pendingFeedback(
            workType = WorkType.WATERING,
            context = context(WorkType.HARVEST),
        )
        val event = generationEvent(WorkType.WATERING)
        stubTarget(event, target)

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(target.failureCode).isEqualTo(ReportFeedbackFailureCode.INVALID_CONTEXT.name)
        verify(generationService, never()).generate(anyContext())
    }

    private fun pendingFeedback(
        workType: WorkType,
        id: UUID = feedbackId,
        context: ReportFeedbackContext = context(workType),
    ) = ReportFeedback(
        id = id,
        member = member,
        report = report,
        workType = workType,
        status = ReportFeedbackStatus.PENDING,
        sourceFingerprint = sourceFingerprint,
    ).also { it.attachInputSnapshot(snapshot(context)) }

    private fun generationEvent(
        workType: WorkType,
        reportId: UUID = this.reportId,
        sourceFingerprint: String = this.sourceFingerprint,
    ) = ReportFeedbackGenerationRequested(
        feedbackId = feedbackId,
        memberId = memberId,
        reportId = reportId,
        workType = workType,
        sourceFingerprint = sourceFingerprint,
    )

    private fun stubTarget(event: ReportFeedbackGenerationRequested, target: ReportFeedback) {
        `when`(feedbackRepository.findByIdAndMember_Id(event.feedbackId, event.memberId)).thenReturn(target)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(event.feedbackId, event.memberId)).thenReturn(target)
    }

    private fun context(workType: WorkType = WorkType.WATERING) = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = workType,
        report = ReportFeedbackReport(
            id = reportId,
            farmName = report.farm.name,
            cropName = report.crop.name,
            startsAt = startsAt,
            endsAt = endsAt,
            statistics = mapOf("recordCount" to 1),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = UUID.randomUUID(),
                workedAt = startsAt.plusDays(1),
                workType = workType,
                memo = "${workType.name} 작업을 기록했어요.",
                details = emptyMap(),
            ),
        ),
        previousReport = null,
        warnings = emptyList(),
    )

    private fun emptyItemResult() = ReportFeedbackGenerationResult(
        content = ReportFeedbackContent(
            summary = "이번 물 주기 기록의 흐름을 확인했어요.",
            strengths = emptyList(),
            improvements = emptyList(),
            nextActions = emptyList(),
        ),
        citations = emptyList(),
        auditWarnings = emptyList(),
        modelInfo = RagModelInfo(embedding = "embedding-test", chat = "chat-test"),
    )

    private fun contentItem(basis: String, text: String) = ReportFeedbackContentItem(
        basis = basis,
        text = text,
        evidenceRefs = emptyList(),
    )

    private fun snapshot(context: ReportFeedbackContext): Map<String, Any?> =
        objectMapper.convertValue(context, SNAPSHOT_TYPE)

    private fun anyContext(): ReportFeedbackContext =
        org.mockito.Mockito.any(ReportFeedbackContext::class.java) ?: context()

    private fun setId(target: Any, id: UUID) {
        val field = target.javaClass.getDeclaredField("id")
        field.isAccessible = true
        field.set(target, id)
    }

    private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
