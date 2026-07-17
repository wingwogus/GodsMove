package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(PolicyProgramQueryRepositoryImpl::class)
class PolicyProgramQueryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val queryRepository: PolicyProgramQueryRepository
) {
    @Test
    fun `search matches keyword in title agency name or summary case insensitively`() {
        persistProgram("청년 농업인 지원")
        persistProgram("귀농 정착", agencyName = "청년농정과")
        persistProgram("스마트팜 확산", summary = "청년 대상 지원금")
        persistProgram("무관 정책", summary = "다른 내용")
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition(keyword = "청년"))

        assertThat(result.map { it.title })
            .containsExactlyInAnyOrder("청년 농업인 지원", "귀농 정착", "스마트팜 확산")
    }

    @Test
    fun `search excludes detail unsynced and non recommendable programs`() {
        persistProgram("정상 정책")
        persistProgram("상세 미동기화 정책", detailSynced = false)
        persistProgram("비활성 정책", recommendable = false)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition())

        assertThat(result.map { it.title }).containsExactly("정상 정책")
    }

    @Test
    fun `search without keyword returns all programs ordered by created at desc id desc`() {
        val oldest = persistProgram("가장 오래됨", createdAt = LocalDateTime.of(2026, 7, 5, 9, 0))
        val middle = persistProgram("중간", createdAt = LocalDateTime.of(2026, 7, 6, 9, 0))
        val newest = persistProgram("가장 최근", createdAt = LocalDateTime.of(2026, 7, 7, 9, 0))
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.search(condition())

        assertThat(result.map { requireNotNull(it.id) }).containsExactly(
            requireNotNull(newest.id),
            requireNotNull(middle.id),
            requireNotNull(oldest.id)
        )
    }

    @Test
    fun `search keeps all same-created-at rows across cursor pages`() {
        val sameCreatedAt = LocalDateTime.of(2026, 7, 6, 9, 0)
        persistProgram("이전 시각", createdAt = LocalDateTime.of(2026, 7, 5, 9, 0))
        persistProgram("동일 시각 A", createdAt = sameCreatedAt)
        persistProgram("동일 시각 B", createdAt = sameCreatedAt)
        persistProgram("동일 시각 C", createdAt = sameCreatedAt)
        entityManager.flush()
        entityManager.clear()

        val fullResult = queryRepository.search(condition())
        val firstPage = queryRepository.search(condition(size = 1))
        val cursorRow = firstPage.single()
        val secondPage = queryRepository.search(
            condition(
                cursorCreatedAt = cursorRow.createdAt,
                cursorId = requireNotNull(cursorRow.id)
            )
        )
        val pagedIds = (firstPage + secondPage).map { requireNotNull(it.id) }

        assertThat(pagedIds).containsExactlyElementsOf(fullResult.map { requireNotNull(it.id) })
        assertThat(pagedIds).doesNotHaveDuplicates()
    }

    @Test
    fun `search applies keyword filter together with cursor`() {
        // JPA auditing이 활성화된 컨텍스트에서는 createdAt이 persist 시각으로 덮어써지므로,
        // 의도한 최신순과 persist 순서를 항상 일치시켜야 한다(오래된 것부터 persist).
        persistProgram("청년 지원 과거", createdAt = LocalDateTime.of(2026, 7, 6, 9, 0))
        persistProgram("무관 정책", createdAt = LocalDateTime.of(2026, 7, 6, 12, 0))
        persistProgram("청년 지원 최근", createdAt = LocalDateTime.of(2026, 7, 7, 9, 0))
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.search(condition(keyword = "청년", size = 1))
        val cursorRow = firstPage.single()
        val secondPage = queryRepository.search(
            condition(
                keyword = "청년",
                cursorCreatedAt = cursorRow.createdAt,
                cursorId = requireNotNull(cursorRow.id)
            )
        )

        assertThat(firstPage.map { it.title }).containsExactly("청년 지원 최근")
        assertThat(secondPage.map { it.title }).containsExactly("청년 지원 과거")
    }

    @Test
    fun `count returns total matching programs ignoring size`() {
        persistProgram("청년 농업인 지원")
        persistProgram("귀농 정착", summary = "청년 정착 지원금")
        persistProgram("무관 정책", summary = "다른 내용")
        persistProgram("미동기화 청년 정책", detailSynced = false)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count("청년")

        assertThat(total).isEqualTo(2)
    }

    @Test
    fun `count without keyword returns all normal programs`() {
        persistProgram("정책 1")
        persistProgram("정책 2")
        persistProgram("비활성 정책", recommendable = false)
        entityManager.flush()
        entityManager.clear()

        val total = queryRepository.count(null)

        assertThat(total).isEqualTo(2)
    }

    private fun condition(
        keyword: String? = null,
        cursorCreatedAt: LocalDateTime? = null,
        cursorId: UUID? = null,
        size: Int = 20
    ): PolicyProgramQueryRepository.SearchCondition =
        PolicyProgramQueryRepository.SearchCondition(
            keyword = keyword,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            size = size
        )

    private fun persistProgram(
        title: String,
        agencyName: String = "농림축산식품부",
        summary: String = "$title 요약",
        detailSynced: Boolean = true,
        recommendable: Boolean = true,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 7, 7, 9, 0)
    ): PolicyProgram {
        val program = PolicyProgram(
            title = title,
            body = "정책 상세",
            region = "전국",
            targetManagementType = null
        ).apply {
            applyListFields(
                source = PolicySource.NONGUP_EZ,
                externalId = "external-$title",
                sourceYear = "2026",
                title = title,
                summary = summary,
                region = "전국",
                sourceUrl = "https://policy.example.com/$title",
                agencyName = agencyName
            )
            if (detailSynced) {
                applyDetailFields(
                    body = "정책 상세",
                    purpose = null,
                    eligibilityOriginal = null,
                    eligibilitySummary = "자격 확인",
                    benefitOriginal = null,
                    benefitSummary = "지원 확인",
                    applyStartsOn = null,
                    applyEndsOn = null,
                    applicationPeriodLabel = "접수기관문의",
                    applicationPeriodNotice = null,
                    applicationMethod = null,
                    requiredDocuments = null,
                    selectionCriteria = null,
                    departmentName = null,
                    onlineApplyAvailable = false,
                    applicationUrl = null,
                    targetTagsJson = "[]",
                    cropTagsJson = "[]",
                    regionTagsJson = "[]",
                    rawPayload = "{}",
                    recommendable = recommendable
                )
            }
        }
        setTimestamps(program, createdAt)
        return entityManager.persistAndFlush(program)
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
