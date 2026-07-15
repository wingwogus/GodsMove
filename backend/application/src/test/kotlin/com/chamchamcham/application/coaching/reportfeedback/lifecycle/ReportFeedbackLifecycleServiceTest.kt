package com.chamchamcham.application.coaching.reportfeedback.lifecycle

import com.chamchamcham.application.coaching.reportfeedback.generation.REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContext
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssembler
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextFingerprint
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackRecord
import com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackReport
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
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
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.FarmingCycleStartBasis
import com.chamchamcham.domain.report.WateringStatistics
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportFeedbackLifecycleServiceTest {
    private val memberId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val wateringFeedbackId = UUID.randomUUID()
    private val harvestFeedbackId = UUID.randomUUID()
    private val wateringFingerprint = "a".repeat(64)
    private val latestWateringFingerprint = "b".repeat(64)
    private val harvestFingerprint = "c".repeat(64)
    private lateinit var member: Member
    private lateinit var report: FarmingCycleReport

    @Mock private lateinit var feedbackRepository: ReportFeedbackRepository
    @Mock private lateinit var reportRepository: FarmingCycleReportRepository
    @Mock private lateinit var contextAssembler: ReportFeedbackContextAssembler
    @Mock private lateinit var contextFingerprint: ReportFeedbackContextFingerprint
    @Mock private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var service: ReportFeedbackLifecycleService

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
            id = UUID.randomUUID(), member = member, farm = farm, crop = crop,
            workType = WorkType.HARVEST, workedAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            weatherCondition = "맑음", weatherTemperature = 24, memo = "최종 수확", entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
        )
        report = FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = finalHarvest.workedAt.minusMonths(5),
                endsAt = finalHarvest.workedAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = CycleReportStatistics(
                    watering = WateringStatistics(recordCount = 1),
                ),
            ),
        ).also { setId(it, reportId) }
        service = ReportFeedbackLifecycleService(
            feedbackRepository,
            reportRepository,
            contextAssembler,
            contextFingerprint,
            eventPublisher,
        )
    }

    @Test
    fun `first reconcile creates ordered pending feedback with current fingerprints and events`() {
        val wateringContext = context(WorkType.WATERING)
        val harvestContext = context(WorkType.HARVEST)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId)).thenReturn(emptyList())
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(wateringContext)
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.HARVEST)).thenReturn(harvestContext)
        `when`(contextFingerprint.calculate(wateringContext)).thenReturn(wateringFingerprint)
        `when`(contextFingerprint.calculate(harvestContext)).thenReturn(harvestFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { invocation ->
            invocation.getArgument<List<ReportFeedback>>(0).also { feedbacks ->
                setId(feedbacks[0], wateringFeedbackId)
                setId(feedbacks[1], harvestFeedbackId)
            }
        }

        val feedbacks = service.reconcile(report, setOf(WorkType.HARVEST, WorkType.WATERING))

        assertThat(feedbacks.map(ReportFeedback::workType))
            .containsExactly(WorkType.WATERING, WorkType.HARVEST)
        assertThat(feedbacks.map(ReportFeedback::status))
            .containsOnly(ReportFeedbackStatus.PENDING)
        assertThat(feedbacks.map(ReportFeedback::sourceFingerprint))
            .containsExactly(wateringFingerprint, harvestFingerprint)
        val events = ArgumentCaptor.forClass(ReportFeedbackPreparationRequested::class.java)
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        assertThat(events.allValues.map(ReportFeedbackPreparationRequested::sourceFingerprint))
            .containsExactly(wateringFingerprint, harvestFingerprint)
        verify(feedbackRepository).findAllByReportAndMemberForUpdate(reportId, memberId)
        verify(feedbackRepository, never()).findAllByReport_IdAndMember_Id(reportId, memberId)
    }

    @Test
    fun `ready feedback becomes stale when its current context changes without publishing an event`() {
        val ready = readyFeedback(WorkType.WATERING, wateringFeedbackId, wateringFingerprint)
        val latestContext = context(WorkType.WATERING, sourceRevision = 2)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId)).thenReturn(listOf(ready))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(latestContext)
        `when`(contextFingerprint.calculate(latestContext)).thenReturn(latestWateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(ready.status).isEqualTo(ReportFeedbackStatus.STALE)
        assertThat(ready.sourceFingerprint).isEqualTo(wateringFingerprint)
        assertThat(ready.inputSnapshot).isNull()
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `unchanged legacy ready feedback backfills its snapshot fingerprint without changing status`() {
        val snapshot = linkedMapOf<String, Any?>(
            "report" to linkedMapOf("sourceRevision" to 1, "statistics" to mapOf("recordCount" to 1)),
            "records" to listOf(mapOf("id" to "watering-1")),
        )
        val legacyReady = legacyReadyFeedback(snapshot)
        val currentContext = context(WorkType.WATERING)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(legacyReady))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(wateringFingerprint)
        `when`(contextFingerprint.calculate(snapshot)).thenReturn(wateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(legacyReady.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(legacyReady.sourceFingerprint).isEqualTo(wateringFingerprint)
        assertThat(legacyReady.inputSnapshot).isEqualTo(snapshot)
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `legacy ready feedback survives numeric normalization in its persisted snapshot`() {
        val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
        val actualFingerprint = ReportFeedbackContextFingerprint(objectMapper)
        val currentContext = context(WorkType.WATERING, recordCount = BigDecimal("1E+3"))
        val snapshotBeforePersistence: Map<String, Any?> =
            objectMapper.convertValue(currentContext, SNAPSHOT_TYPE)
        val persistedSnapshot: Map<String, Any?> = objectMapper.readValue(
            objectMapper.writeValueAsBytes(snapshotBeforePersistence),
            SNAPSHOT_TYPE,
        )
        val legacyReady = legacyReadyFeedback(persistedSnapshot)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(legacyReady))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }
        val numericService = ReportFeedbackLifecycleService(
            feedbackRepository,
            reportRepository,
            contextAssembler,
            actualFingerprint,
            eventPublisher,
        )

        numericService.reconcile(report, setOf(WorkType.WATERING))

        assertThat(legacyReady.status).isEqualTo(ReportFeedbackStatus.READY)
        assertThat(legacyReady.sourceFingerprint).isEqualTo(actualFingerprint.calculate(currentContext))
        assertThat(legacyReady.inputSnapshot).isEqualTo(persistedSnapshot)
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `legacy feedback becomes stale when its snapshot fingerprint differs from current context`() {
        val snapshot = mapOf<String, Any?>("report" to mapOf("sourceRevision" to 1))
        val legacyReady = legacyReadyFeedback(snapshot)
        val currentContext = context(WorkType.WATERING, sourceRevision = 2)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(legacyReady))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(latestWateringFingerprint)
        `when`(contextFingerprint.calculate(snapshot)).thenReturn(wateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(legacyReady.status).isEqualTo(ReportFeedbackStatus.STALE)
        assertThat(legacyReady.sourceFingerprint).isNull()
        assertThat(legacyReady.inputSnapshot).isNull()
    }

    @Test
    fun `legacy feedback without an input snapshot becomes stale`() {
        val legacyReady = legacyReadyFeedback(null)
        val currentContext = context(WorkType.WATERING)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(legacyReady))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(wateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(legacyReady.status).isEqualTo(ReportFeedbackStatus.STALE)
    }

    @Test
    fun `legacy feedback with an unusable input snapshot becomes stale`() {
        val snapshot = mapOf<String, Any?>("unsupported" to Any())
        val legacyReady = legacyReadyFeedback(snapshot)
        val currentContext = context(WorkType.WATERING)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(legacyReady))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(wateringFingerprint)
        `when`(contextFingerprint.calculate(snapshot)).thenThrow(IllegalArgumentException("unsupported snapshot"))
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(legacyReady.status).isEqualTo(ReportFeedbackStatus.STALE)
    }

    @Test
    fun `new work type becomes a stale placeholder after feedback already exists`() {
        val ready = readyFeedback(WorkType.WATERING, wateringFeedbackId, wateringFingerprint)
        val currentContext = context(WorkType.WATERING)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId)).thenReturn(listOf(ready))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(wateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        val reconciled = service.reconcile(report, setOf(WorkType.WATERING, WorkType.FERTILIZING))

        val placeholder = reconciled.single { it.workType == WorkType.FERTILIZING }
        assertThat(placeholder.status).isEqualTo(ReportFeedbackStatus.STALE)
        assertThat(placeholder.sourceFingerprint).isNull()
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `removed work type becomes stale and remains stored without an event`() {
        val watering = readyFeedback(WorkType.WATERING, wateringFeedbackId, wateringFingerprint)
        val harvest = readyFeedback(WorkType.HARVEST, harvestFeedbackId, harvestFingerprint)
        val currentContext = context(WorkType.WATERING)
        `when`(feedbackRepository.findAllByReportAndMemberForUpdate(reportId, memberId))
            .thenReturn(listOf(harvest, watering))
        `when`(contextAssembler.assemble(memberId, reportId, WorkType.WATERING)).thenReturn(currentContext)
        `when`(contextFingerprint.calculate(currentContext)).thenReturn(wateringFingerprint)
        `when`(feedbackRepository.saveAll(Mockito.anyList<ReportFeedback>())).thenAnswer { it.getArgument(0) }

        val reconciled = service.reconcile(report, setOf(WorkType.WATERING))

        assertThat(reconciled).contains(harvest)
        assertThat(harvest.status).isEqualTo(ReportFeedbackStatus.STALE)
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `manual regeneration locks stale feedback and publishes preparation with the latest fingerprint`() {
        val stale = ReportFeedback.stalePlaceholder(member, report, WorkType.WATERING)
            .also { setId(it, wateringFeedbackId) }
        val latestContext = context(WorkType.WATERING, sourceRevision = 2)
        `when`(reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)).thenReturn(report)
        `when`(
            feedbackRepository.findByReportAndWorkTypeForUpdate(reportId, memberId, WorkType.WATERING),
        ).thenReturn(stale)
        `when`(contextAssembler.assemble(memberId, report, WorkType.WATERING)).thenReturn(latestContext)
        `when`(contextFingerprint.calculate(latestContext)).thenReturn(latestWateringFingerprint)

        val regenerated = service.regenerate(memberId, reportId, WorkType.WATERING)

        assertThat(regenerated.status).isEqualTo(ReportFeedbackStatus.PENDING)
        assertThat(regenerated.sourceFingerprint).isEqualTo(latestWateringFingerprint)
        assertThat(regenerated.inputSnapshot).isNull()
        val event = ReportFeedbackPreparationRequested(
            feedbackId = wateringFeedbackId,
            memberId = memberId,
            reportId = reportId,
            workType = WorkType.WATERING,
            sourceFingerprint = latestWateringFingerprint,
        )
        val order = Mockito.inOrder(reportRepository, feedbackRepository, contextAssembler, eventPublisher)
        order.verify(reportRepository).findByIdAndMemberIdForUpdate(reportId, memberId)
        order.verify(feedbackRepository)
            .findByReportAndWorkTypeForUpdate(reportId, memberId, WorkType.WATERING)
        order.verify(contextAssembler).assemble(memberId, report, WorkType.WATERING)
        order.verify(eventPublisher).publishEvent(event)
    }

    @Test
    fun `manual regeneration rejects ready feedback before assembling context`() {
        val ready = readyFeedback(WorkType.WATERING, wateringFeedbackId, wateringFingerprint)
        `when`(reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)).thenReturn(report)
        `when`(
            feedbackRepository.findByReportAndWorkTypeForUpdate(reportId, memberId, WorkType.WATERING),
        ).thenReturn(ready)

        assertThatThrownBy { service.regenerate(memberId, reportId, WorkType.WATERING) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_FEEDBACK_REGENERATION_NOT_ALLOWED)
        verifyNoInteractions(contextAssembler)
        verify(eventPublisher, never()).publishEvent(Mockito.any())
    }

    @Test
    fun `manual regeneration rejects a report outside member ownership before locking feedback`() {
        `when`(reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)).thenReturn(null)

        assertThatThrownBy { service.regenerate(memberId, reportId, WorkType.WATERING) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
        verifyNoInteractions(feedbackRepository, contextAssembler)
    }

    @Test
    fun `manual regeneration rejects a non-completed locked report before locking feedback`() {
        report.supersede()
        `when`(reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)).thenReturn(report)

        assertThatThrownBy { service.regenerate(memberId, reportId, WorkType.WATERING) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
        verifyNoInteractions(feedbackRepository, contextAssembler)
    }

    @Test
    fun `manual regeneration rejects a work type absent from locked report statistics`() {
        setStatistics(report, CycleReportStatistics.empty())
        `when`(reportRepository.findByIdAndMemberIdForUpdate(reportId, memberId)).thenReturn(report)

        assertThatThrownBy { service.regenerate(memberId, reportId, WorkType.WATERING) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
        verifyNoInteractions(feedbackRepository, contextAssembler)
    }

    private fun readyFeedback(
        workType: WorkType,
        id: UUID,
        sourceFingerprint: String,
    ) = ReportFeedback.pending(member, report, workType, sourceFingerprint).also {
        setId(it, id)
        it.attachInputSnapshot(mapOf("sourceRevision" to 1))
        it.markReady(
            summary = "${workType.label} 요약",
            items = emptyList(),
            citations = emptyList(),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )
    }

    private fun legacyReadyFeedback(snapshot: Map<String, Any?>?): ReportFeedback = ReportFeedback(
        id = wateringFeedbackId,
        member = member,
        report = report,
        workType = WorkType.WATERING,
        status = ReportFeedbackStatus.PENDING,
    ).also { feedback ->
        snapshot?.let(feedback::attachInputSnapshot)
        feedback.markReady(
            summary = "관수 작업 요약",
            items = emptyList(),
            citations = emptyList(),
            auditStatus = "PASS",
            auditWarnings = emptyList(),
            modelName = "test-chat",
            embeddingModel = "test-embedding",
        )
    }

    private fun context(
        workType: WorkType,
        sourceRevision: Long = 1,
        recordCount: Any = 1,
    ) = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = workType,
        report = ReportFeedbackReport(
            id = reportId,
            farmName = report.farm.name,
            cropName = report.crop.name,
            startsAt = report.startsAt,
            endsAt = requireNotNull(report.endsAt),
            sourceRevision = sourceRevision,
            statistics = mapOf("recordCount" to recordCount),
        ),
        records = listOf(
            ReportFeedbackRecord(
                id = UUID(0, workType.ordinal.toLong() + 1),
                workedAt = report.startsAt.plusDays(1),
                workType = workType,
                memo = "${workType.label} 기록",
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

    private fun setStatistics(target: FarmingCycleReport, statistics: CycleReportStatistics) {
        val field = target.javaClass.getDeclaredField("statistics")
        field.isAccessible = true
        field.set(target, statistics)
    }

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
