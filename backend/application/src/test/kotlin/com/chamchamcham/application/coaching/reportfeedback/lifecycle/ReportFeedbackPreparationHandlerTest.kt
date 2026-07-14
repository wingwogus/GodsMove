package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackFailureCode
import com.chamchamcham.application.coaching.reportfeedback.ReportFeedbackGenerationFailure
import com.chamchamcham.application.coaching.reportfeedback.generation.REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContext
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssembler
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackRecord
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackReport
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedback
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
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
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackPreparationHandlerTest {
    private val memberId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val startsAt = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val endsAt = LocalDateTime.of(2026, 7, 1, 9, 0)

    @Mock private lateinit var feedbackRepository: ReportFeedbackRepository
    @Mock private lateinit var contextAssembler: ReportFeedbackContextAssembler
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private lateinit var member: Member
    private lateinit var report: FarmingCycleReport
    private lateinit var handler: ReportFeedbackPreparationHandler

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
        handler = ReportFeedbackPreparationHandler(
            feedbackRepository,
            contextAssembler,
            objectMapper,
            eventPublisher,
            NoOpTransactionManager(),
        )
    }

    @Test
    fun `only preparation handler is asynchronous`() {
        val preparationOn = ReportFeedbackPreparationHandler::class.java.getDeclaredMethod(
            "on",
            ReportFeedbackPreparationRequested::class.java,
        )
        val generationOn = ReportFeedbackGenerationHandler::class.java.getDeclaredMethod(
            "on",
            ReportFeedbackGenerationRequested::class.java,
        )

        assertThat(preparationOn.isAnnotationPresent(Async::class.java)).isTrue()
        assertThat(generationOn.isAnnotationPresent(Async::class.java)).isFalse()
        assertThat(generationOn.isAnnotationPresent(EventListener::class.java)).isTrue()
        assertThat(generationOn.isAnnotationPresent(TransactionalEventListener::class.java)).isFalse()
    }

    @Test
    fun `preparation publishes generation only after snapshot transaction resources are cleaned up`() {
        val feedback = pendingFeedback(WorkType.WATERING)
        val event = preparationEvent(WorkType.WATERING)
        val transactionManager = RecordingTransactionManager()
        val transactionAwareHandler = ReportFeedbackPreparationHandler(
            feedbackRepository,
            contextAssembler,
            objectMapper,
            eventPublisher,
            transactionManager,
        )
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(feedbackId, memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(context())
        doAnswer {
            assertThat(transactionManager.resourcesCleanedUp).isTrue()
            null
        }.`when`(eventPublisher).publishEvent(
            org.mockito.Mockito.any(ReportFeedbackGenerationRequested::class.java),
        )

        transactionAwareHandler.on(event)

        assertThat(transactionManager.resourcesCleanedUp).isTrue()
        verify(eventPublisher).publishEvent(org.mockito.Mockito.any(ReportFeedbackGenerationRequested::class.java))
    }

    @Test
    fun `preparation stores the requested work type snapshot and publishes the same generation target`() {
        val feedback = pendingFeedback(WorkType.WATERING)
        val event = preparationEvent(WorkType.WATERING)
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(feedback)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(feedbackId, memberId)).thenReturn(feedback)
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(context())

        handler.on(event)

        assertThat(feedback.inputSnapshot).isNotNull
        verify(contextAssembler).assemble(memberId, reportId, WorkType.WATERING)
        val published = ArgumentCaptor.forClass(ReportFeedbackGenerationRequested::class.java)
        verify(eventPublisher).publishEvent(published.capture())
        assertThat(published.value).isEqualTo(
            ReportFeedbackGenerationRequested(
                feedbackId = feedbackId,
                memberId = memberId,
                reportId = reportId,
                workType = WorkType.WATERING,
            ),
        )
    }

    @Test
    fun `preparation ignores an event whose report does not match the target row`() {
        val feedback = pendingFeedback(WorkType.WATERING)
        val event = preparationEvent(WorkType.WATERING, reportId = UUID.randomUUID())
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(feedback)

        handler.on(event)

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(feedback.inputSnapshot).isNull()
        verifyNoInteractions(contextAssembler)
        verify(feedbackRepository, never()).findByIdAndMemberIdForUpdate(feedbackId, memberId)
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `preparation ignores an event whose work type does not match the target row`() {
        val feedback = pendingFeedback(WorkType.WATERING)
        val event = preparationEvent(WorkType.FERTILIZING)
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(feedback)

        handler.on(event)

        assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(feedback.inputSnapshot).isNull()
        verifyNoInteractions(contextAssembler)
        verify(feedbackRepository, never()).findByIdAndMemberIdForUpdate(feedbackId, memberId)
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `invalid assembled context fails only the requested work type row`() {
        val target = pendingFeedback(WorkType.WATERING)
        val sibling = pendingFeedback(WorkType.FERTILIZING, UUID.randomUUID())
        val event = preparationEvent(WorkType.WATERING)
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(target)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(feedbackId, memberId)).thenReturn(target)
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenThrow(
            ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT),
        )

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(target.failureCode).isEqualTo(ReportFeedbackFailureCode.INVALID_CONTEXT.name)
        assertThat(sibling.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    @Test
    fun `unexpected assembly failure fails only the requested row with context assembly code`() {
        val target = pendingFeedback(WorkType.WATERING)
        val sibling = pendingFeedback(WorkType.FERTILIZING, UUID.randomUUID())
        val event = preparationEvent(WorkType.WATERING)
        `when`(feedbackRepository.findByIdAndMember_Id(feedbackId, memberId)).thenReturn(target)
        `when`(feedbackRepository.findByIdAndMemberIdForUpdate(feedbackId, memberId)).thenReturn(target)
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenThrow(
            IllegalStateException("source unavailable"),
        )

        handler.on(event)

        assertThat(target.status).isEqualTo(ReportFeedbackStatus.FAILED)
        assertThat(target.failureCode).isEqualTo(ReportFeedbackFailureCode.CONTEXT_ASSEMBLY_FAILED.name)
        assertThat(sibling.status).isEqualTo(ReportFeedbackStatus.PENDING)
        verify(eventPublisher, never()).publishEvent(org.mockito.Mockito.any())
    }

    private fun pendingFeedback(
        workType: WorkType,
        id: UUID = feedbackId,
    ) = ReportFeedback(
        id = id,
        member = member,
        report = report,
        workType = workType,
        status = ReportFeedbackStatus.PENDING,
    )

    private fun preparationEvent(
        workType: WorkType,
        reportId: UUID = this.reportId,
    ) = ReportFeedbackPreparationRequested(
        feedbackId = feedbackId,
        memberId = memberId,
        reportId = reportId,
        workType = workType,
    )

    private fun context() = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = WorkType.WATERING,
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
                id = recordId,
                workedAt = startsAt.plusDays(1),
                workType = WorkType.WATERING,
                memo = "점적관수를 했어요.",
                details = emptyMap(),
            ),
        ),
        previousReport = null,
        warnings = emptyList(),
    )

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

    private class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        var resourcesCleanedUp = false
            private set

        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
        override fun doCleanupAfterCompletion(transaction: Any) {
            resourcesCleanedUp = true
        }
    }
}
