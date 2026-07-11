package com.chamchamcham.api.coaching

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.coaching.rag.common.RagModelInfo
import com.chamchamcham.application.coaching.rag.record.CommonFeedbackDetail
import com.chamchamcham.application.coaching.rag.record.GeneratedRecordFeedback
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionCategory
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackActionDue
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackCoachingResult
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackCropContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackFarmContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackGenerationService
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackItem
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackMemberContext
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackNextAction
import com.chamchamcham.application.coaching.rag.record.RecordFeedbackRecordContext
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.report.FarmingCycleReportProjectionService
import com.chamchamcham.domain.coaching.CoachingFeedbackRepository
import com.chamchamcham.domain.coaching.CoachingFeedbackStatus
import com.chamchamcham.domain.coaching.FeedbackType
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
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
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID
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
    private val coachingFeedbackRepository: CoachingFeedbackRepository,
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
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
        coachingFeedbackRepository.deleteAllInBatch()
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

        val record = farmingRecordRepository.findById(recordId).orElseThrow()
        val feedback = coachingFeedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            recordId,
            1,
        ) ?: error("record feedback must be created")

        assertThat(record.sourceRevision).isEqualTo(1)
        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.READY)
        assertThat(feedback.inputSnapshot)
            .containsEntry("schemaVersion", "record-feedback-context.v2")
            .doesNotContainKeys("recentRecords", "workTypeStats", "cropCycle", "daysAfterPlanting")
        assertThat(feedback.structuredResult).containsKey("goodPoint")
        assertThat(feedback.auditStatus).isEqualTo("PASS")
        assertThat(feedback.modelName).isEqualTo("chat-test")
    }

    @Test
    fun `update stales the prior revision and prepares a new ready snapshot`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id

        farmingRecordService.update(wateringUpdateCommand(recordId))

        val record = farmingRecordRepository.findById(recordId).orElseThrow()
        val first = coachingFeedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            recordId,
            1,
        ) ?: error("first revision feedback must exist")
        val second = coachingFeedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            recordId,
            2,
        ) ?: error("second revision feedback must exist")

        assertThat(record.sourceRevision).isEqualTo(2)
        assertThat(first.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(second.status).isEqualTo(CoachingFeedbackStatus.READY)
        assertThat(second.inputSnapshot).containsEntry("schemaVersion", "record-feedback-context.v2")
        assertThat(second.structuredResult).containsKey("goodPoint")
    }

    @Test
    fun `delete stales active feedback without creating a replacement`() {
        val recordId = farmingRecordService.create(wateringCreateCommand()).id

        farmingRecordService.delete(FarmingRecordCommand.Delete(memberId = requireNotNull(member.id), recordId = recordId))

        val record = farmingRecordRepository.findById(recordId).orElseThrow()
        val feedback = coachingFeedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
            FeedbackType.RECORD,
            recordId,
            1,
        ) ?: error("initial feedback must exist")

        assertThat(record.isDeleted).isTrue()
        assertThat(record.sourceRevision).isEqualTo(2)
        assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.STALE)
        assertThat(
            coachingFeedbackRepository.findByFeedbackTypeAndRecord_IdAndSourceRevision(
                FeedbackType.RECORD,
                recordId,
                2,
            ),
        ).isNull()
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

    private fun generatedFeedback() = GeneratedRecordFeedback(
        result = RecordFeedbackCoachingResult(
            goodPoint = RecordFeedbackItem(
                basis = "자동 생성 테스트 근거입니다.",
                text = "관수 기록이 구체적입니다.",
                evidenceRefs = listOf("doc-1"),
            ),
            nextActions = listOf(
                RecordFeedbackNextAction(
                    due = RecordFeedbackActionDue.NEXT_CHECK,
                    category = RecordFeedbackActionCategory.CULTIVATION,
                    basis = "다음 점검이 필요합니다.",
                    text = "토양 수분을 다시 확인하세요.",
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
