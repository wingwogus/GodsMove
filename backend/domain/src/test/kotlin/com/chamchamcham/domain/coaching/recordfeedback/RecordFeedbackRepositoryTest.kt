package com.chamchamcham.domain.coaching.recordfeedback

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.format.FormatMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:record-feedback-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "spring.jpa.properties.hibernate.type.json_format_mapper=com.chamchamcham.domain.coaching.recordfeedback.TestRecordFeedbackJsonFormatMapper",
    ],
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecordFeedbackRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val repository: RecordFeedbackRepository,
) {
    private val workedAt = LocalDateTime.of(2026, 7, 1, 9, 0)
    private lateinit var member: Member
    private lateinit var record: FarmingRecord

    @BeforeEach
    fun setUp() {
        member = persist(Member(email = "member@example.com", passwordHash = null))
        val farm = persist(Farm(owner = member, name = "약초농장", roadAddress = "강원도 평창군"))
        val crop = persist(
            Crop(externalNo = 422, name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK),
        )
        record = persist(
            FarmingRecord(
                member = member,
                farm = farm,
                crop = crop,
                workType = WorkType.WATERING,
                workedAt = workedAt,
                weatherCondition = "맑음",
                weatherTemperature = 24,
                memo = "물을 줬습니다.",
                entryMode = EntryMode.MANUAL,
            ),
        )
    }

    @Test
    fun `the same source revision cannot be duplicated for one record`() {
        repository.saveAndFlush(pendingFeedback(1))
        repository.save(pendingFeedback(1))

        assertThatThrownBy { repository.flush() }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `feedback rows are locked and returned newest first`() {
        repository.saveAllAndFlush(listOf(pendingFeedback(1), pendingFeedback(2)))
        entityManager.clear()

        val feedbacks = repository.findAllByRecord_IdOrderBySourceRevisionDesc(requireNotNull(record.id))

        assertThat(feedbacks.map { it.sourceRevision }).containsExactly(2L, 1L)
    }

    @Test
    fun `feedback revision allocation exposes a parent record lock`() {
        val lockedRecord = repository.findRecordByIdForFeedbackUpdate(requireNotNull(record.id))

        assertThat(lockedRecord?.id).isEqualTo(record.id)
    }

    private fun pendingFeedback(sourceRevision: Long): RecordFeedback =
        RecordFeedback.pending(member, record, sourceRevision).also(::setTimestamps)

    private fun <T : BaseTimeEntity> persist(entity: T): T {
        setTimestamps(entity)
        return entityManager.persistAndFlush(entity)
    }

    private fun setTimestamps(entity: BaseTimeEntity) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, workedAt)
        }
        BaseTimeEntity::class.java.getDeclaredField("updatedAt").apply {
            isAccessible = true
            set(entity, workedAt)
        }
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["com.chamchamcham.domain"])
@EnableJpaRepositories(basePackages = ["com.chamchamcham.domain"])
private class RecordFeedbackRepositoryTestApplication

class TestRecordFeedbackJsonFormatMapper : FormatMapper {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> fromString(
        charSequence: CharSequence,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): T = when {
        Map::class.java.isAssignableFrom(javaType.javaTypeClass) -> emptyMap<String, Any?>() as T
        List::class.java.isAssignableFrom(javaType.javaTypeClass) -> emptyList<Any?>() as T
        else -> javaType.fromString(charSequence)
    }

    override fun <T : Any?> toString(
        value: T,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): String = when (value) {
        is Map<*, *> -> "{}"
        is List<*> -> "[]"
        else -> javaType.toString(value)
    }
}
