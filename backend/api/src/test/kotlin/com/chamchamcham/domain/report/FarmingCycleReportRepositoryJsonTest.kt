package com.chamchamcham.domain.report

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:report-json-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    ],
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FarmingCycleReportRepositoryJsonTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val repository: FarmingCycleReportRepository,
) {
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var finalHarvest: FarmingRecord

    private val otherMemberId: UUID
        get() = requireNotNull(otherMember.id) { "Persisted member id is required" }
    private val workedAt = LocalDateTime.of(2026, 6, 15, 8, 30)

    @BeforeEach
    fun setUp() {
        member = persist(Member(email = "member@example.com", passwordHash = null))
        otherMember = persist(Member(email = "other@example.com", passwordHash = null))
        farm = persist(Farm(owner = member, name = "약초농장", roadAddress = "강원도 평창군"))
        crop = persist(
            Crop(externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        finalHarvest = persist(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = WorkType.HARVEST,
                workedAt = workedAt,
                weatherCondition = "맑음",
                weatherTemperature = 24,
                memo = "최종 수확",
                entryMode = EntryMode.MANUAL,
            ),
        )
    }

    @Test
    fun `typed statistics round trip through production Jackson JSON mapping`() {
        val original = completedReport(statisticsWithEveryWorkType())
        val id = requireNotNull(repository.saveAndFlush(original).id)
        entityManager.clear()

        val loaded = repository.findById(id).orElseThrow()

        assertThat(loaded.statistics).isEqualTo(original.statistics)
        assertThat(loaded.sourceRevision).isEqualTo(original.sourceRevision)
    }

    @Test
    fun `member scoped lookup does not expose another members report`() {
        val report = repository.saveAndFlush(completedReport())

        assertThat(repository.findByIdAndMember_Id(requireNotNull(report.id), otherMemberId))
            .isNull()
    }

    private fun completedReport(
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReport {
        return FarmingCycleReport.create(
            member = member,
            farm = farm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = workedAt.minusMonths(6),
                endsAt = workedAt,
                startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        ).also { setTimestamps(it, workedAt) }
    }

    private fun statisticsWithEveryWorkType(): CycleReportStatistics {
        return CycleReportStatistics(
            planting = PlantingStatistics(
                recordCount = 1,
                firstWorkedOn = LocalDate.of(2026, 1, 1),
                lastWorkedOn = LocalDate.of(2026, 1, 1),
                workedDayCount = 1,
                photoAttachedRecordCount = 1,
                propagationMethods = listOf(
                    PropagationStatistics(
                        code = "SEED",
                        label = "종자",
                        recordCount = 1,
                        recordRatePct = BigDecimal("100"),
                        totalQuantity = BigDecimal("10"),
                        quantityUnit = "g",
                        quantityCoverage = Coverage(1, 1),
                    ),
                ),
            ),
            watering = WateringStatistics(
                recordCount = 2,
                amountDistribution = listOf(distribution("ENOUGH", "충분", 2)),
                methodDistribution = listOf(distribution("SPRINKLE", "살수", 2)),
            ),
            fertilizing = FertilizingStatistics(
                recordCount = 3,
                totalAmountKg = BigDecimal("3"),
                averageAmountKg = BigDecimal("1"),
                amountCoverage = Coverage(3, 3),
                materialCategories = listOf(
                    MaterialCategoryStatistics(
                        code = "ORGANIC_FERTILIZER",
                        label = "유기질비료",
                        recordCount = 3,
                        recordRatePct = BigDecimal("100"),
                        amountKg = BigDecimal("3"),
                        amountRatePct = BigDecimal("100"),
                    ),
                ),
                methodDistribution = listOf(distribution("SOIL", "토양시비", 3)),
                categoryMethods = listOf(
                    CategoryMethodStatistics(
                        categoryCode = "ORGANIC_FERTILIZER",
                        categoryLabel = "유기질비료",
                        methodCode = "SOIL",
                        methodLabel = "토양시비",
                        recordCount = 3,
                        recordRatePct = BigDecimal("100"),
                    ),
                ),
            ),
            pestControl = PestControlStatistics(
                recordCount = 4,
                weatherDistribution = listOf(distribution("SUNNY", "맑음", 4)),
                categoryDistribution = listOf(distribution("FUNGICIDE", "살균제", 4)),
                pesticideAmounts = listOf(
                    AmountByUnit(unit = "ml", amount = BigDecimal("40"), coverage = Coverage(4, 4)),
                ),
                categoryAmounts = listOf(
                    CategoryAmountByUnit(
                        categoryCode = "FUNGICIDE",
                        categoryLabel = "살균제",
                        unit = "ml",
                        recordCount = 4,
                        amount = BigDecimal("40"),
                        coverage = Coverage(4, 4),
                    ),
                ),
                totalSprayAmountLiters = BigDecimal("20"),
                sprayAmountCoverage = Coverage(4, 4),
                targets = listOf(TargetCount(target = "진딧물", count = 4)),
            ),
            weeding = WeedingStatistics(
                recordCount = 5,
                methodDistribution = listOf(distribution("HAND", "손제초", 5)),
            ),
            pruning = CommonOnlyStatistics(recordCount = 6),
            harvest = HarvestStatistics(
                recordCount = 7,
                totalAmountKg = BigDecimal("70"),
                averageAmountKg = BigDecimal("10"),
                amountCoverage = Coverage(7, 7),
                firstHarvestedOn = LocalDate.of(2026, 6, 1),
                lastHarvestedOn = LocalDate.of(2026, 6, 15),
                medicinalParts = listOf(
                    HarvestPartStatistics(
                        code = "ROOT_BARK",
                        label = "뿌리·껍질",
                        recordCount = 7,
                        recordRatePct = BigDecimal("100"),
                        knownAmountKg = BigDecimal("70"),
                        amountRatePct = BigDecimal("100"),
                        amountCoverage = Coverage(7, 7),
                    ),
                ),
                finalGrowthPeriodMonths = 6,
                growthPeriodRangeMonths = GrowthPeriodRange(5, 7),
            ),
            etc = CommonOnlyStatistics(recordCount = 8),
        )
    }

    private fun distribution(code: String, label: String, count: Int): CountDistribution {
        return CountDistribution(
            code = code,
            label = label,
            count = count,
            ratePct = BigDecimal("100"),
        )
    }

    private fun <T : BaseTimeEntity> persist(entity: T): T {
        setTimestamps(entity, workedAt)
        return entityManager.persistAndFlush(entity)
    }

    private fun setTimestamps(entity: BaseTimeEntity, createdAt: LocalDateTime) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
        BaseTimeEntity::class.java.getDeclaredField("updatedAt").apply {
            isAccessible = true
            set(entity, createdAt)
        }
    }
}
