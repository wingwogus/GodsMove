package com.chamchamcham.domain.farming

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
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
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(FarmingRecordQueryRepositoryImpl::class)
class FarmingRecordQueryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val queryRepository: FarmingRecordQueryRepository
) {
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var farm: Farm
    private lateinit var hwanggiCrop: Crop
    private lateinit var ginsengCrop: Crop

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }
    private val hwanggiCropId: UUID
        get() = requireNotNull(hwanggiCrop.id) { "Persisted crop id is required" }

    private val now = LocalDateTime.of(2026, 6, 12, 7, 0)

    @BeforeEach
    fun setUp() {
        member = persist(Member(email = "member@example.com", passwordHash = null), now)
        otherMember = persist(Member(email = "other@example.com", passwordHash = null), now)
        farm = persist(Farm(owner = member, name = "약초농장", roadAddress = "서울시 강남구"), now)
        hwanggiCrop = persist(crop(name = "황기", externalNo = 422), now)
        ginsengCrop = persist(crop(name = "인삼", externalNo = 107), now)
    }

    @Test
    fun `search returns own active records latest first with cursor`() {
        persistRecord(workedAt = LocalDateTime.of(2026, 6, 12, 10, 0))
        persistRecord(workedAt = LocalDateTime.of(2026, 6, 12, 9, 0))
        persistRecord(workedAt = LocalDateTime.of(2026, 6, 12, 8, 0))
        persistRecord(workedAt = LocalDateTime.of(2026, 6, 12, 11, 0), isDeleted = true)
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.search(condition(size = 2))
        val secondPage = queryRepository.search(
            condition(
                size = 2,
                cursor = FarmingRecordQueryRepository.Cursor(
                    workedAt = firstPage.rows.last().record.workedAt,
                    id = requireNotNull(firstPage.rows.last().record.id)
                )
            )
        )

        assertThat(firstPage.rows.map { it.record.workedAt }).containsExactly(
            LocalDateTime.of(2026, 6, 12, 10, 0),
            LocalDateTime.of(2026, 6, 12, 9, 0)
        )
        assertThat(secondPage.rows.map { it.record.workedAt }).containsExactly(LocalDateTime.of(2026, 6, 12, 8, 0))
    }

    @Test
    fun `search filters by crop`() {
        persistRecord(crop = hwanggiCrop)
        persistRecord(crop = ginsengCrop)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(cropId = hwanggiCropId))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.crop.id).isEqualTo(hwanggiCropId)
    }

    @Test
    fun `search filters by work type`() {
        persistRecord(workType = WorkType.PLANTING)
        persistRecord(workType = WorkType.HARVEST)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(workType = WorkType.HARVEST))

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.workType).isEqualTo(WorkType.HARVEST)
    }

    @Test
    fun `search filters by worked at range`() {
        persistRecord(workedAt = LocalDateTime.of(2026, 5, 1, 9, 0))
        persistRecord(workedAt = LocalDateTime.of(2026, 6, 15, 9, 0))
        persistRecord(workedAt = LocalDateTime.of(2026, 7, 1, 9, 0))
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(
            condition(
                workedAtFrom = LocalDateTime.of(2026, 6, 1, 0, 0),
                workedAtTo = LocalDateTime.of(2026, 7, 1, 0, 0)
            )
        )

        assertThat(result.rows.map { it.record.workedAt }).containsExactly(LocalDateTime.of(2026, 6, 15, 9, 0))
    }

    @Test
    fun `search excludes other members records`() {
        persistRecord(owner = member)
        persistRecord(owner = otherMember)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition())

        assertThat(result.rows).hasSize(1)
        assertThat(result.rows.single().record.member.id).isEqualTo(memberId)
    }

    @Test
    fun `search returns thumbnail from first media by display order`() {
        val record = persistRecord()
        persistMedia(record, displayOrder = 1, url = "https://example.test/2.jpg")
        persistMedia(record, displayOrder = 0, url = "https://example.test/1.jpg")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition())

        assertThat(result.rows.single().thumbnailUrl).isEqualTo("https://example.test/1.jpg")
    }

    private fun persistRecord(
        owner: Member = member,
        crop: Crop = hwanggiCrop,
        workType: WorkType = WorkType.PRUNING,
        workedAt: LocalDateTime = LocalDateTime.of(2026, 6, 12, 9, 0),
        isDeleted: Boolean = false,
    ): FarmingRecord {
        val record = FarmingRecord(
            member = owner,
            farm = farm,
            crop = crop,
            workType = workType,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 20,
            memo = "memo",
            entryMode = "MANUAL",
            isDeleted = isDeleted,
        )
        return persist(record, now)
    }

    private fun persistMedia(record: FarmingRecord, displayOrder: Int, url: String) {
        val media = persist(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = UploadedMediaUsageType.FARMING_RECORD,
                fileUrl = url,
                cloudinaryPublicId = "farming/${UUID.randomUUID()}",
                status = UploadedMediaStatus.ATTACHED,
            ),
            now
        )
        persist(FarmingRecordMedia(record = record, uploadedMedia = media, displayOrder = displayOrder), now)
    }

    private fun crop(name: String, externalNo: Int): Crop =
        Crop(externalNo = externalNo, name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)

    private fun condition(
        cropId: UUID? = null,
        workType: WorkType? = null,
        workedAtFrom: LocalDateTime? = null,
        workedAtTo: LocalDateTime? = null,
        cursor: FarmingRecordQueryRepository.Cursor? = null,
        size: Int = 20,
    ): FarmingRecordQueryRepository.SearchCondition =
        FarmingRecordQueryRepository.SearchCondition(
            memberId = memberId,
            cropId = cropId,
            workType = workType,
            workedAtFrom = workedAtFrom,
            workedAtTo = workedAtTo,
            cursor = cursor,
            size = size,
        )

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
private class FarmingRecordQueryRepositoryTestApplication
