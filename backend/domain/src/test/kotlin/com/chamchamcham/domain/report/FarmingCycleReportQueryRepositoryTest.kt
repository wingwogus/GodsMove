package com.chamchamcham.domain.report

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.format.FormatMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@DataJpaTest
@ActiveProfiles("test")
@Import(FarmingCycleReportQueryRepositoryImpl::class)
@TestPropertySource(
    properties = [
        "spring.jpa.properties.hibernate.type.json_format_mapper=com.chamchamcham.domain.report.TestCycleReportJsonFormatMapper",
    ],
)
class FarmingCycleReportQueryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val repository: FarmingCycleReportQueryRepository,
    private val reportRepository: FarmingCycleReportRepository,
) {
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val farmId: UUID
        get() = requireNotNull(farm.id) { "Persisted farm id is required" }
    private val cropId: UUID
        get() = requireNotNull(crop.id) { "Persisted crop id is required" }

    private val baseTime = LocalDateTime.of(2026, 1, 1, 9, 0)

    @BeforeEach
    fun setUp() {
        member = persist(Member(email = "member@example.com", passwordHash = null), baseTime)
        otherMember = persist(Member(email = "other@example.com", passwordHash = null), baseTime)
        farm = persist(Farm(owner = member, name = "약초농장", roadAddress = "서울시 강남구"), baseTime)
        crop = persist(
            Crop(externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
            baseTime,
        )
    }

    @Test
    fun `completed page excludes active superseded and other member reports`() {
        persistCompleted(endedAt = day(30))
        persistCompleted(endedAt = day(20))
        persistActive()
        persistSuperseded()
        persistCompleted(owner = otherMember)
        entityManager.flush()
        entityManager.clear()

        val result = repository.searchCompleted(condition(size = 10))

        assertThat(result.rows.map { it.endsAt })
            .containsExactly(day(30), day(20))
    }

    @Test
    fun `report can be locked only by its id and owning member`() {
        val report = persistCompleted(endedAt = day(30))
        entityManager.flush()
        entityManager.clear()

        val locked = reportRepository.findByIdAndMemberIdForUpdate(requireNotNull(report.id), memberId)
        val outsideOwner = reportRepository.findByIdAndMemberIdForUpdate(
            requireNotNull(report.id),
            requireNotNull(otherMember.id),
        )

        assertThat(locked?.id).isEqualTo(report.id)
        assertThat(outsideOwner).isNull()
    }

    @Test
    fun `completed list accepts member wide farm only crop only and combined filters`() {
        val otherFarm = persist(
            Farm(owner = member, name = "다른 약초농장", roadAddress = "서울시 마포구"),
            baseTime,
        )
        val otherCrop = persist(
            Crop(externalNo = 423, name = "감초", usePartCategory = CropUsePartCategory.ROOT_BARK),
            baseTime,
        )
        val newestAcrossScopes = persistCompleted(farm = farm, crop = crop, endedAt = day(40))
        val olderAcrossScopes = persistCompleted(farm = otherFarm, crop = otherCrop, endedAt = day(30))
        entityManager.flush()
        entityManager.clear()

        val all = repository.searchCompleted(condition(farmId = null, cropId = null, size = 20))
        assertThat(all.rows.map { it.id })
            .containsExactly(newestAcrossScopes.id, olderAcrossScopes.id)

        val byFarm = repository.searchCompleted(condition(farmId = farmId, cropId = null, size = 20))
        assertThat(byFarm.rows.map { it.id }).containsExactly(newestAcrossScopes.id)
        assertThat(byFarm.rows).allMatch { it.farm.id == farmId }

        val byCrop = repository.searchCompleted(condition(farmId = null, cropId = cropId, size = 20))
        assertThat(byCrop.rows.map { it.id }).containsExactly(newestAcrossScopes.id)
        assertThat(byCrop.rows).allMatch { it.crop.id == cropId }

        val combined = repository.searchCompleted(condition(farmId = farmId, cropId = cropId, size = 20))
        assertThat(combined.rows.map { it.id }).containsExactly(newestAcrossScopes.id)
    }

    @Test
    fun `completed cursor resolves equal end times with final harvest id`() {
        repeat(3) {
            persistCompleted(endedAt = day(30))
        }
        entityManager.flush()
        entityManager.clear()

        val ordered = repository.searchCompleted(condition(size = 3)).rows
        val cursorReport = ordered[1]
        val expectedReport = ordered[2]

        val result = repository.searchCompleted(
            condition(
                cursor = FarmingCycleReportQueryRepository.Cursor(
                    endsAt = day(30),
                    finalHarvestRecordId = requireNotNull(cursorReport.finalHarvestRecord?.id),
                ),
                size = 10,
            ),
        )

        assertThat(result.rows.map { it.id })
            .containsExactly(expectedReport.id)
    }

    @Test
    fun `previous completed report uses the same boundary ordering`() {
        repeat(2) {
            persistCompleted(endedAt = day(30))
        }
        entityManager.flush()
        entityManager.clear()

        val ordered = repository.searchCompleted(condition(size = 2)).rows
        val selected = ordered[0]
        val previous = ordered[1]

        assertThat(
            repository.findPreviousCompleted(
                memberId = memberId,
                farmId = farmId,
                cropId = cropId,
                endsAt = requireNotNull(selected.endsAt),
                finalHarvestRecordId = requireNotNull(selected.finalHarvestRecord?.id),
            )?.id,
        ).isEqualTo(previous.id)
    }

    @Test
    fun `work item projection includes active and completed positive statistics and orders report cards`() {
        val otherFarm = persist(
            Farm(owner = member, name = "다른 약초농장", roadAddress = "서울시 마포구"),
            baseTime,
        )
        val otherCrop = persist(
            Crop(externalNo = 423, name = "감초", usePartCategory = CropUsePartCategory.ROOT_BARK),
            baseTime,
        )
        val latest = persistCompleted(
            endedAt = day(40),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 2, lastWorkedOn = day(38).toLocalDate()),
                harvest = HarvestStatistics(recordCount = 1, lastWorkedOn = day(40).toLocalDate()),
            ),
        )
        val sameEndOtherScope = persistCompleted(
            farm = otherFarm,
            crop = otherCrop,
            endedAt = day(40),
            statistics = CycleReportStatistics(
                planting = PlantingStatistics(recordCount = 3, lastWorkedOn = day(15).toLocalDate()),
                etc = CommonOnlyStatistics(recordCount = 0, lastWorkedOn = day(16).toLocalDate()),
            ),
        )
        persistCompleted(
            owner = otherMember,
            endedAt = day(50),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 9, lastWorkedOn = day(50).toLocalDate()),
            ),
        )
        val active = persistActive(
            startsAt = day(10),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 4, lastWorkedOn = day(20).toLocalDate()),
            ),
        )
        persistSuperseded(endedAt = day(60))
        entityManager.flush()
        entityManager.clear()

        val all = repository.searchWorkItems(
            workCondition(farmId = null, cropId = null, size = 20),
        ).rows

        assertThat(all.map { it.reportId to it.workType })
            .containsExactly(
                requireNotNull(latest.id) to WorkType.HARVEST,
                requireNotNull(latest.id) to WorkType.WATERING,
                requireNotNull(active.id) to WorkType.WATERING,
                requireNotNull(sameEndOtherScope.id) to WorkType.PLANTING,
            )
        val activeItem = all.first { it.reportId == active.id }
        assertThat(activeItem.status).isEqualTo(FarmingCycleReportStatus.ACTIVE)
        assertThat(activeItem.endsAt).isNull()
        assertThat(activeItem.finalHarvestRecordId).isNull()
        assertThat(all.map { it.recordCount }).containsExactlyInAnyOrder(2, 1, 3, 4)
        assertThat(all.map { it.lastWorkedOn })
            .containsExactlyInAnyOrder(
                day(38).toLocalDate(),
                day(40).toLocalDate(),
                day(15).toLocalDate(),
                day(20).toLocalDate(),
            )

        assertThat(repository.searchWorkItems(workCondition(farmId = farmId, cropId = null)).rows.map { it.reportId })
            .containsOnly(latest.id, active.id)
        assertThat(repository.searchWorkItems(workCondition(farmId = null, cropId = cropId)).rows.map { it.reportId })
            .containsOnly(latest.id, active.id)
        assertThat(
            repository.searchWorkItems(
                workCondition(farmId = null, cropId = null, workType = WorkType.PLANTING),
            ).rows.map { it.reportId },
        ).containsExactly(sameEndOtherScope.id)
    }

    @Test
    fun `work items without a last worked date are ordered last`() {
        val recentWithoutWorkedOn = persistCompleted(
            endedAt = day(70),
            statistics = CycleReportStatistics(
                etc = CommonOnlyStatistics(recordCount = 1, lastWorkedOn = null),
            ),
        )
        val olderWithWorkedOn = persistCompleted(
            endedAt = day(30),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(20).toLocalDate()),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val items = repository.searchWorkItems(workCondition(size = 20)).rows

        assertThat(items.map { it.reportId to it.workType }).containsExactly(
            requireNotNull(olderWithWorkedOn.id) to WorkType.WATERING,
            requireNotNull(recentWithoutWorkedOn.id) to WorkType.ETC,
        )
    }

    @Test
    fun `work items on the same date keep active status first`() {
        val tiedActive = persistActive(
            startsAt = day(5),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(25).toLocalDate()),
            ),
        )
        val tiedCompleted = persistCompleted(
            endedAt = day(30),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(25).toLocalDate()),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val items = repository.searchWorkItems(workCondition(size = 20)).rows

        assertThat(items.map { it.reportId }).containsExactly(
            requireNotNull(tiedActive.id),
            requireNotNull(tiedCompleted.id),
        )
    }

    @Test
    fun `work item cursor crosses active work types and completed reports without overlap`() {
        val active = persistActive(
            startsAt = day(40),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 2, lastWorkedOn = day(41).toLocalDate()),
                harvest = HarvestStatistics(recordCount = 1, lastWorkedOn = day(42).toLocalDate()),
            ),
        )
        val completed = persistCompleted(
            endedAt = day(30),
            statistics = CycleReportStatistics(
                watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(20).toLocalDate()),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val firstPage = repository.searchWorkItems(workCondition(size = 1)).rows
        val first = firstPage.single()
        val secondPage = repository.searchWorkItems(
            workCondition(
                cursor = FarmingCycleReportQueryRepository.WorkItemCursor(
                    lastWorkedOn = first.lastWorkedOn,
                    status = first.status,
                    sortAt = first.sortAt,
                    reportId = first.reportId,
                    workType = first.workType,
                ),
                size = 20,
            ),
        ).rows

        assertThat(first.reportId).isEqualTo(active.id)
        assertThat(first.workType).isEqualTo(WorkType.HARVEST)
        assertThat(secondPage.map { it.reportId to it.workType })
            .containsExactly(
                requireNotNull(active.id) to WorkType.WATERING,
                requireNotNull(completed.id) to WorkType.WATERING,
            )
        assertThat((firstPage + secondPage).map { it.reportId to it.workType }).doesNotHaveDuplicates()
    }

    private fun persistActive(
        startsAt: LocalDateTime = day(50),
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReport =
        persistReport(
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.ACTIVE,
                startsAt = startsAt,
                endsAt = null,
                startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST,
                finalHarvestRecord = null,
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        )

    private fun persistCompleted(
        owner: Member = member,
        farm: Farm? = null,
        crop: Crop = this.crop,
        endedAt: LocalDateTime = day(10),
        statistics: CycleReportStatistics = CycleReportStatistics.empty(),
    ): FarmingCycleReport {
        val reportFarm = farm ?: if (owner === member) this.farm else persist(
            Farm(owner = owner, name = "다른 농장", roadAddress = "서울시 서초구"),
            baseTime,
        )
        val finalHarvest = persistFinalHarvest(
            owner = owner,
            farm = reportFarm,
            crop = crop,
            workedAt = endedAt,
        )
        return persistReport(
            owner = owner,
            farm = reportFarm,
            crop = crop,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = endedAt.minusDays(10),
                endsAt = endedAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = statistics,
            ),
        )
    }

    private fun persistSuperseded(
        endedAt: LocalDateTime = day(10),
    ): FarmingCycleReport {
        val report = persistCompleted(endedAt = endedAt)
        report.supersede()
        return report
    }

    private fun persistReport(
        owner: Member = member,
        farm: Farm? = null,
        crop: Crop = this.crop,
        projection: FarmingCycleReportProjection,
    ): FarmingCycleReport {
        val reportFarm = farm ?: if (owner === member) this.farm else persist(
            Farm(owner = owner, name = "다른 농장", roadAddress = "서울시 서초구"),
            baseTime,
        )
        return persist(FarmingCycleReport.create(owner, reportFarm, crop, projection), baseTime)
    }

    private fun persistFinalHarvest(
        owner: Member,
        farm: Farm? = null,
        crop: Crop = this.crop,
        workedAt: LocalDateTime,
    ): FarmingRecord {
        val recordFarm = farm ?: if (owner === member) this.farm else persist(
            Farm(owner = owner, name = "다른 수확 농장", roadAddress = "서울시 송파구"),
            baseTime,
        )
        return persist(
            FarmingRecord(
                member = owner,
                farm = recordFarm,
                crop = crop,
                workType = WorkType.HARVEST,
                workedAt = workedAt,
                weatherCondition = "맑음",
                weatherTemperature = 20,
                memo = "최종 수확",
                entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
            ),
            baseTime,
        )
    }

    private fun condition(
        farmId: UUID? = this.farmId,
        cropId: UUID? = this.cropId,
        cursor: FarmingCycleReportQueryRepository.Cursor? = null,
        size: Int = 20,
    ): FarmingCycleReportQueryRepository.SearchCondition =
        FarmingCycleReportQueryRepository.SearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            cursor = cursor,
            size = size,
        )

    private fun workCondition(
        farmId: UUID? = this.farmId,
        cropId: UUID? = this.cropId,
        workType: WorkType? = null,
        cursor: FarmingCycleReportQueryRepository.WorkItemCursor? = null,
        size: Int = 20,
    ): FarmingCycleReportQueryRepository.WorkItemSearchCondition =
        FarmingCycleReportQueryRepository.WorkItemSearchCondition(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workType = workType,
            cursor = cursor,
            size = size,
        )

    private fun day(day: Long): LocalDateTime = baseTime.plusDays(day)

    private fun <T : BaseTimeEntity> persist(entity: T, createdAt: LocalDateTime): T {
        setTimestamps(entity, createdAt)
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

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class FarmingCycleReportQueryRepositoryTestApplication

class TestCycleReportJsonFormatMapper : FormatMapper {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> fromString(
        charSequence: CharSequence,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): T {
        if (javaType.javaTypeClass == CycleReportStatistics::class.java) {
            val key = charSequence.toString().substringAfter("\"key\":\"").substringBefore('"')
            return requireNotNull(statisticsByKey[key]) { "Missing test statistics for key $key" } as T
        }
        return javaType.fromString(charSequence)
    }

    override fun <T : Any?> toString(
        value: T,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): String {
        if (value is CycleReportStatistics) {
            val key = UUID.randomUUID().toString()
            statisticsByKey[key] = value
            return "{\"key\":\"$key\"}"
        }
        return javaType.toString(value)
    }

    companion object {
        private val statisticsByKey = ConcurrentHashMap<String, CycleReportStatistics>()
    }
}
