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
import java.time.LocalDateTime
import java.util.UUID

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
    fun `latest completed report uses completed ordering only`() {
        val older = persistCompleted(endedAt = day(20))
        val latest = persistCompleted(endedAt = day(30))
        persistActive()
        persistSuperseded(endedAt = day(40))
        entityManager.flush()
        entityManager.clear()

        val result = repository.findLatestCompleted(memberId, farmId, cropId)

        assertThat(result?.id).isEqualTo(latest.id)
        assertThat(result?.id).isNotEqualTo(older.id)
    }

    private fun persistActive(): FarmingCycleReport =
        persistReport(
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.ACTIVE,
                startsAt = day(50),
                endsAt = null,
                startBasis = FarmingCycleStartBasis.AFTER_PREVIOUS_FINAL_HARVEST,
                finalHarvestRecord = null,
                statisticsSchemaVersion = 1,
                statistics = CycleReportStatistics.empty(),
            ),
        )

    private fun persistCompleted(
        owner: Member = member,
        endedAt: LocalDateTime = day(10),
    ): FarmingCycleReport {
        val finalHarvest = persistFinalHarvest(owner = owner, workedAt = endedAt)
        return persistReport(
            owner = owner,
            projection = FarmingCycleReportProjection(
                status = FarmingCycleReportStatus.COMPLETED,
                startsAt = endedAt.minusDays(10),
                endsAt = endedAt,
                startBasis = FarmingCycleStartBasis.FIRST_RECORD,
                finalHarvestRecord = finalHarvest,
                statisticsSchemaVersion = 1,
                statistics = CycleReportStatistics.empty(),
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
        projection: FarmingCycleReportProjection,
    ): FarmingCycleReport {
        val reportFarm = if (owner === member) farm else persist(
            Farm(owner = owner, name = "다른 농장", roadAddress = "서울시 서초구"),
            baseTime,
        )
        return persist(FarmingCycleReport.create(owner, reportFarm, crop, projection), baseTime)
    }

    private fun persistFinalHarvest(
        owner: Member,
        workedAt: LocalDateTime,
    ): FarmingRecord {
        val recordFarm = if (owner === member) farm else persist(
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
            return CycleReportStatistics.empty() as T
        }
        return javaType.fromString(charSequence)
    }

    override fun <T : Any?> toString(
        value: T,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): String {
        if (value is CycleReportStatistics) {
            return "{}"
        }
        return javaType.toString(value)
    }
}
