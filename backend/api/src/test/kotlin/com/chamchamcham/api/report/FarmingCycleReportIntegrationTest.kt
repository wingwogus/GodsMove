package com.chamchamcham.api.report

import com.chamchamcham.ApiApplication
import com.chamchamcham.application.farming.FarmingRecordCommand
import com.chamchamcham.application.farming.FarmingRecordService
import com.chamchamcham.application.report.FarmingCycleReportQueryService
import com.chamchamcham.application.report.FarmingCycleReportSearchCondition
import com.chamchamcham.domain.coaching.recordfeedback.RecordFeedbackRepository
import com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackRepository
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.report.CountDistribution
import com.chamchamcham.domain.report.FarmingCycleReportRepository
import com.chamchamcham.domain.report.FarmingCycleReportStatus
import com.chamchamcham.domain.report.GrowthPeriodRange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:farming-cycle-report-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
class FarmingCycleReportIntegrationTest @Autowired constructor(
    private val farmingRecordService: FarmingRecordService,
    private val queryService: FarmingCycleReportQueryService,
    private val recordFeedbackRepository: RecordFeedbackRepository,
    private val reportFeedbackRepository: ReportFeedbackRepository,
    private val reportRepository: FarmingCycleReportRepository,
    private val farmingRecordRepository: FarmingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val harvestRecordRepository: HarvestRecordRepository,
    private val farmingRecordMediaRepository: FarmingRecordMediaRepository,
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
) {
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val farmId: UUID
        get() = requireNotNull(farm.id) { "Persisted farm id is required" }
    private val cropId: UUID
        get() = requireNotNull(crop.id) { "Persisted crop id is required" }

    @BeforeEach
    fun setUp() {
        reportFeedbackRepository.deleteAllInBatch()
        reportRepository.deleteAllInBatch()
        recordFeedbackRepository.deleteAllInBatch()
        farmingRecordMediaRepository.deleteAllInBatch()
        wateringRecordRepository.deleteAllInBatch()
        harvestRecordRepository.deleteAllInBatch()
        farmingRecordRepository.deleteAllInBatch()
        memberCropRepository.deleteAllInBatch()
        farmRepository.deleteAllInBatch()
        cropRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()

        member = memberRepository.save(Member(email = "report-member@example.com", passwordHash = null))
        farm = farmRepository.save(Farm(owner = member, name = "통합검증농장", roadAddress = "강원도 평창군"))
        crop = cropRepository.save(
            Crop(externalNo = 7001, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        memberCropRepository.save(MemberCrop(member = member, farm = farm, crop = crop))
    }

    @Test
    fun `record CRUD rebuilds active and completed report end to end`() {
        val wateringId = farmingRecordService.create(wateringCommand()).id

        val active = reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
            memberId,
            farmId,
            cropId,
            FarmingCycleReportStatus.ACTIVE,
        )
        assertThat(active).isNotNull
        assertThat(active!!.statistics.watering.recordCount).isEqualTo(1)

        val finalHarvestId = farmingRecordService.create(
            harvestCommand(isLastHarvest = true, amountKg = "30"),
        ).id

        assertThat(
            reportRepository.findByMember_IdAndFarm_IdAndCrop_IdAndStatus(
                memberId,
                farmId,
                cropId,
                FarmingCycleReportStatus.ACTIVE,
            ),
        ).isNull()
        val completed = loadOnlyCompletedReport()
        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(completed.finalHarvestRecordId).isEqualTo(finalHarvestId)
        assertThat(completed.statistics.watering.recordCount).isEqualTo(1)
        assertThat(completed.statistics.harvest.totalAmountKg)
            .isEqualByComparingTo("30.0000")

        farmingRecordService.delete(FarmingRecordCommand.Delete(memberId, wateringId))

        val rebuilt = queryService.getDetail(
            memberId,
            completed.id,
        )
        assertThat(rebuilt.selected.statistics.watering.recordCount).isZero()
        assertThat(rebuilt.selected.sourceRevision)
            .isGreaterThan(completed.sourceRevision)
    }

    @Test
    fun `unknown optional harvest values still complete the cycle with required growth statistics`() {
        val finalHarvestId = farmingRecordService.create(
            harvestCommand(
                isLastHarvest = true,
                amountKg = null,
                amountUnknown = true,
                medicinalPart = null,
            ),
        ).id

        val completed = loadOnlyCompletedReport()

        assertThat(completed.status).isEqualTo(FarmingCycleReportStatus.COMPLETED)
        assertThat(completed.finalHarvestRecordId).isEqualTo(finalHarvestId)
        assertThat(completed.statisticsSchemaVersion).isEqualTo(3)
        assertThat(completed.statistics.harvest.recordCount).isEqualTo(1)
        assertThat(completed.statistics.harvest.totalAmountKg).isNull()
        assertThat(completed.statistics.harvest.amountCoverage.recordedCount).isZero()
        assertThat(completed.statistics.harvest.amountCoverage.targetCount).isEqualTo(1)
        assertThat(completed.statistics.harvest.medicinalParts).isEmpty()
        assertThat(completed.statistics.harvest.finalGrowthPeriodMonths).isEqualTo(4)
        assertThat(completed.statistics.harvest.growthPeriodRangeMonths).isEqualTo(GrowthPeriodRange(4, 4))
        assertThat(completed.statistics.harvest.growthPeriodDistribution).containsExactly(
            CountDistribution("4", "4개월", 1, BigDecimal("100.00")),
        )
    }

    private fun loadOnlyCompletedReport() = queryService.listCompleted(
        FarmingCycleReportSearchCondition(
            memberId = memberId,
            farmIds = setOf(farmId),
            cropIds = setOf(cropId),
            cursor = null,
            size = 20,
        ),
    ).items.single().let { queryService.getDetail(memberId, it.id).selected }

    private fun wateringCommand() = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.WATERING,
        workedAt = LocalDateTime.of(2026, 3, 1, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 17,
        memo = "관수",
        watering = FarmingRecordCommand.WateringDetail(
            irrigationAmount = IrrigationAmount.SUFFICIENT,
            irrigationMethod = IrrigationMethod.SPRAYING,
        ),
    )

    private fun harvestCommand(
        isLastHarvest: Boolean,
        amountKg: String?,
        amountUnknown: Boolean = false,
        medicinalPart: CropUsePartCategory? = CropUsePartCategory.ROOT_BARK,
        growthPeriod: Int = 4,
    ) = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = WorkType.HARVEST,
        workedAt = LocalDateTime.of(2026, 6, 30, 9, 0),
        weatherCondition = "흐림",
        weatherTemperature = 25,
        memo = "마지막 수확",
        harvest = FarmingRecordCommand.HarvestDetail(
            harvestAmount = amountKg?.let(::BigDecimal),
            amountUnknown = amountUnknown,
            medicinalPart = medicinalPart,
            harvestSource = HarvestSource.CULTIVATED,
            growthPeriod = growthPeriod,
            isLastHarvest = isLastHarvest,
        ),
    )
}
