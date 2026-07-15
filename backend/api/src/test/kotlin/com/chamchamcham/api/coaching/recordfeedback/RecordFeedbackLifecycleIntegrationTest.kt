package com.chamchamcham.api.coaching.recordfeedback

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackGenerationProcessor
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackGenerationRequested
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackLifecycleService
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackQueryService
import com.chamchamcham.application.coaching.common.RagModelInfo
import com.chamchamcham.application.coaching.recordfeedback.generation.CommonFeedbackDetail
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackAction
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContent
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationService
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGenerationResult
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackGoodPoint
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.recordfeedback.generation.RecordFeedbackRecordContext
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.report.FarmingCycleReportProjectionService
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionCategory
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackActionDue
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedback
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackStatus
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:record-feedback-lifecycle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
class RecordFeedbackLifecycleIntegrationTest @Autowired constructor(
    private val farmingRecordService: FarmingRecordService,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val recordFeedbackRepository: RecordFeedbackRepository,
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val generationProcessor: RecordFeedbackGenerationProcessor,
    private val queryService: RecordFeedbackQueryService,
    private val transactionManager: PlatformTransactionManager,
) {
    @MockBean
    private lateinit var projectionService: FarmingCycleReportProjectionService

    @MockBean
    private lateinit var generationService: RecordFeedbackGenerationService

    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    @BeforeEach
    fun setUp() {
        recordFeedbackRepository.deleteAll()
        wateringRecordRepository.deleteAllInBatch()
        farmingRecordRepository.deleteAllInBatch()
        memberCropRepository.deleteAllInBatch()
        farmRepository.deleteAllInBatch()
        cropRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()

        member = memberRepository.save(Member(email = "feedback-lifecycle@example.com", passwordHash = null))
        farm = farmRepository.save(Farm(owner = member, name = "코칭 검증 밭", roadAddress = "강원도 평창군"))
        crop = cropRepository.save(
            Crop(externalNo = 7101, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        memberCropRepository.save(MemberCrop(member = member, farm = farm, crop = crop))
        `when`(generationService.generate(anyContext(), org.mockito.Mockito.isNull())).thenReturn(generatedFeedback())
    }

    @Test
    fun `create commits a ready feedback with an entity derived input snapshot`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id

        val feedback = awaitReadyFeedback(recordId, 1)

        assertThat(feedback.sourceRevision).isEqualTo(1)
        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(feedback.inputSnapshot)
            .containsEntry("schemaVersion", "record-feedback-context.v2")
            .doesNotContainKeys("recentRecords", "workTypeStats", "cropCycle", "daysAfterPlanting")
        assertThat(feedback.goodPointText).isEqualTo("관수 기록이 구체적입니다.")
        assertThat(feedback.auditStatus).isEqualTo("PASS")
        assertThat(feedback.modelName).isEqualTo("chat-test")
    }

    @Test
    fun `automatic record feedback keeps one immutable snapshot and exposes only user output`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id

        val feedback = awaitReadyFeedback(recordId, 1)
        val feedbackId = requireNotNull(feedback.id)
        val snapshot = requireNotNull(feedback.inputSnapshot)
        val statusResult = queryService.get(requireNotNull(member.id), recordId)

        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(snapshot).containsEntry("schemaVersion", "record-feedback-context.v2")
        @Suppress("UNCHECKED_CAST")
        val recordSnapshot = snapshot["record"] as Map<String, Any?>
        assertThat(recordSnapshot)
            .containsEntry("recordId", recordId.toString())
            .containsEntry("sourceRevision", 1)
            .containsEntry("workType", "WATERING")
            .containsEntry("memo", "점적관수로 토양 수분을 보충했습니다.")
        assertThat(statusResult.content?.goodPoint).isEqualTo("관수 기록이 구체적입니다.")
        val nextActions = statusResult.content?.nextActions.orEmpty()
        assertThat(nextActions).hasSize(2)
        assertThat(nextActions.map { it.text }).containsExactly(
            "토양 수분을 다시 확인하세요.",
            "관수량 변화를 주간 기록에 남기세요.",
        )
        assertThat(nextActions.map { it.due }).containsExactly(
            RecordFeedbackActionDue.NEXT_CHECK,
            RecordFeedbackActionDue.THIS_WEEK,
        )
        assertThat(nextActions.map { it.category }).containsExactly(
            RecordFeedbackActionCategory.CULTIVATION,
            RecordFeedbackActionCategory.IRRIGATION,
        )

        generationProcessor.generate(
            RecordFeedbackGenerationRequested(
                feedbackId = feedbackId,
                memberId = requireNotNull(member.id),
                recordId = recordId,
                sourceRevision = 1,
            ),
        )

        val feedbacks = recordFeedbackRepository.findAll().filter { it.record.id == recordId }
        val reloaded = recordFeedbackRepository.findById(feedbackId).orElseThrow()
        assertThat(feedbacks).hasSize(1)
        assertThat(feedbacks.single().id).isEqualTo(feedbackId)
        assertThat(reloaded.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(reloaded.inputSnapshot).isEqualTo(snapshot)
        verify(generationService, times(1)).generate(anyContext(), org.mockito.Mockito.isNull())
    }

    @Test
    fun `update stales the prior revision and prepares a new ready snapshot`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id
        awaitReadyFeedback(recordId, 1)

        farmingRecordService.update(wateringUpdateCommand(recordId))
        val second = awaitReadyFeedback(recordId, 2)

        val first = recordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, 1)
            ?: error("first revision feedback must exist")

        assertThat(second.sourceRevision).isEqualTo(2)
        assertThat(first.status).isEqualTo(RecordFeedbackStatus.STALE)
        assertThat(second.status).isEqualTo(RecordFeedbackStatus.READY)
        assertThat(second.inputSnapshot).containsEntry("schemaVersion", "record-feedback-context.v2")
        assertThat(second.goodPointText).isEqualTo("관수 기록이 구체적입니다.")
    }

    @Test
    fun `delete stales active feedback without creating a replacement`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id
        awaitReadyFeedback(recordId, 1)

        farmingRecordService.delete(FarmingRecordCommand.Delete(memberId = requireNotNull(member.id), recordId = recordId))

        val record = farmingRecordRepository.findById(recordId).orElseThrow()
        val feedback = recordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, 1)
            ?: error("initial feedback must exist")

        assertThat(record.isDeleted).isTrue()
        assertThat(feedback.status).isEqualTo(RecordFeedbackStatus.STALE)
        assertThat(recordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, 2)).isNull()
    }

    @Test
    fun `concurrent first enqueue allocates distinct feedback revisions`() {
        val record = farmingRecordRepository.saveAndFlush(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = WorkType.WATERING,
                workedAt = LocalDateTime.of(2026, 7, 13, 8, 30),
                weatherCondition = "맑음",
                weatherTemperature = 23,
                memo = "동시 코칭 생성 검증 기록입니다.",
                entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
            ),
        )
        val recordId = requireNotNull(record.id)
        val lifecycleService = RecordFeedbackLifecycleService(
            recordFeedbackRepository,
            ApplicationEventPublisher { },
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = List(2) {
                executor.submit<Long> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "concurrent enqueue start timed out" }
                    checkNotNull(TransactionTemplate(transactionManager).execute {
                        val currentRecord = farmingRecordRepository.findById(recordId).orElseThrow()
                        lifecycleService.enqueue(currentRecord).sourceRevision
                    })
                }
            }

            check(ready.await(5, TimeUnit.SECONDS)) { "concurrent enqueue workers did not become ready" }
            start.countDown()

            assertThat(futures.map { it.get(5, TimeUnit.SECONDS) })
                .containsExactlyInAnyOrder(1L, 2L)
            assertThat(
                recordFeedbackRepository.findAll()
                    .filter { it.record.id == recordId }
                    .map { it.sourceRevision },
            ).containsExactlyInAnyOrder(1L, 2L)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun awaitReadyFeedback(recordId: UUID, revision: Long): RecordFeedback {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var lastStatus: RecordFeedbackStatus? = null
        while (System.nanoTime() < deadline) {
            val feedback = recordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, revision)
            lastStatus = feedback?.status
            if (feedback?.status == RecordFeedbackStatus.READY) return feedback
            if (feedback?.status == RecordFeedbackStatus.FAILED) {
                error("record feedback failed: recordId=$recordId revision=$revision code=${feedback.failureCode}")
            }
            Thread.sleep(25)
        }
        error("record feedback did not become READY: recordId=$recordId revision=$revision lastStatus=$lastStatus")
    }

    private fun wateringCreateCommand() = FarmingRecordCommand.Create(
        memberId = requireNotNull(member.id),
        farmId = requireNotNull(farm.id),
        cropId = requireNotNull(crop.id),
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 11, 8, 30),
        weatherCondition = "맑음",
        weatherTemperature = 24,
        memo = "점적관수로 토양 수분을 보충했습니다.",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.NORMAL,
            irrigationMethod = IrrigationMethod.DRIP,
        ),
    )

    private fun wateringUpdateCommand(recordId: UUID) = FarmingRecordCommand.Update(
        memberId = requireNotNull(member.id),
        recordId = recordId,
        farmId = requireNotNull(farm.id),
        cropId = requireNotNull(crop.id),
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 7, 12, 8, 30),
        weatherCondition = "흐림",
        weatherTemperature = 22,
        memo = "전날 강우를 반영해 관수량을 조절했습니다.",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.LOW,
            irrigationMethod = IrrigationMethod.DRIP,
        ),
    )

    private fun generatedFeedback() = RecordFeedbackGenerationResult(
        content = RecordFeedbackContent(
            goodPoint = RecordFeedbackGoodPoint(
                basis = "자동 생성 테스트 근거입니다.",
                text = "관수 기록이 구체적입니다.",
                evidenceRefs = listOf("doc-1"),
            ),
            nextActions = listOf(
                RecordFeedbackAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = RecordFeedbackActionCategory.CULTIVATION,
                    basis = "다음 점검이 필요합니다.",
                    text = "토양 수분을 다시 확인하세요.",
                    evidenceRefs = listOf("doc-1"),
                ),
                RecordFeedbackAction(
                    due = RecordFeedbackActionDue.THIS_WEEK,
                    category = RecordFeedbackActionCategory.IRRIGATION,
                    basis = "주간 관수량 추적이 필요합니다.",
                    text = "관수량 변화를 주간 기록에 남기세요.",
                    evidenceRefs = listOf("doc-1"),
                ),
            ),
        ),
        citations = listOf(mapOf("id" to "doc-1", "title" to "농업기술길잡이")),
        auditWarnings = emptyList(),
        modelInfo = RagModelInfo(
            embedding = "embedding-test",
            chat = "chat-test",
        ),
    )

    private fun anyContext(): RecordFeedbackContext {
        return org.mockito.Mockito.any(RecordFeedbackContext::class.java) ?: fallbackContext()
    }

    private fun fallbackContext() = RecordFeedbackContext(
        member = RecordFeedbackMemberContext(requireNotNull(member.id), null, null),
        farm = RecordFeedbackFarmContext(requireNotNull(farm.id), farm.name, farm.roadAddress, null, null),
        crop = RecordFeedbackCropContext(requireNotNull(crop.id), crop.name, crop.usePartCategory),
        record = RecordFeedbackRecordContext(
            recordId = UUID.randomUUID(),
            sourceRevision = 1,
            workedAt = LocalDateTime.of(2026, 7, 11, 8, 30),
            workType = WorkType.WATERING,
            detail = CommonFeedbackDetail,
            recordedWeatherCondition = "맑음",
            recordedTemperatureC = 24,
            memo = "점적관수로 토양 수분을 보충했습니다.",
            photoCount = 0,
        ),
        weather = null,
    )
}
