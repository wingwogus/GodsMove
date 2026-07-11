package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest(
    properties = ["spring.jpa.properties.hibernate.generate_statistics=true"]
)
@ActiveProfiles("test")
@Import(PolicyRecommendationQueryRepositoryImpl::class)
class PolicyRecommendationQueryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val queryRepository: PolicyRecommendationQueryRepository
) {
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private val sourceYear = "2026"
    private val otherSourceYear = "2025"

    private val memberId: UUID
        get() = requireNotNull(member.id) { "Persisted member id is required" }

    @BeforeEach
    fun setUp() {
        val now = LocalDateTime.of(2026, 7, 7, 9, 0)
        member = persist(Member(email = "policy-member@example.com", passwordHash = null), now)
        otherMember = persist(Member(email = "other-policy-member@example.com", passwordHash = null), now)
    }

    @Test
    fun `find page avoids policy program selects proportional to card count`() {
        persistRecommendation("정책 A", score = "0.9500", applyEndsOn = null)
        persistRecommendation("정책 B", score = "0.9000", applyEndsOn = null)
        persistRecommendation("정책 C", score = "0.8500", applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val statistics = entityManager.entityManager.entityManagerFactory
            .unwrap(SessionFactory::class.java)
            .statistics
        statistics.clear()

        val result = queryRepository.findPage(condition(size = 10))
        val cardFields = result.rows.map { row ->
            listOf(
                row.policyProgram.title,
                row.policyProgram.eligibilitySummary,
                row.policyProgram.benefitSummary,
                row.policyProgram.agencyName
            )
        }

        assertThat(cardFields).hasSize(3)
        assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(2L)
    }

    @Test
    fun `find page returns member recommendations ordered by score deadline and id with cursor`() {
        persistRecommendation("높은 점수", score = "0.9500", applyEndsOn = LocalDate.of(2026, 7, 20))
        persistRecommendation("동점 빠른 마감", score = "0.9000", applyEndsOn = LocalDate.of(2026, 7, 10))
        persistRecommendation("동점 늦은 마감", score = "0.9000", applyEndsOn = LocalDate.of(2026, 8, 1))
        persistRecommendation("마감 없음", score = "0.8000", applyEndsOn = null)
        persistRecommendation("다른 회원", member = otherMember, score = "0.9900", applyEndsOn = LocalDate.of(2026, 7, 1))
        persistRecommendation("다른 기준연도", sourceYear = otherSourceYear, score = "0.9900", applyEndsOn = LocalDate.of(2026, 7, 1))
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.findPage(condition(size = 2))
        val cursorRow = firstPage.rows.last()
        val secondPage = queryRepository.findPage(
            condition(
                size = 10,
                cursor = PolicyRecommendationQueryRepository.Cursor(
                    score = cursorRow.score,
                    applyStartsOn = null,
                    applyEndsOn = cursorRow.policyProgram.applyEndsOn,
                    id = requireNotNull(cursorRow.id)
                )
            )
        )

        assertThat(firstPage.rows.map { it.policyProgram.title }).containsExactly("높은 점수", "동점 빠른 마감")
        assertThat(secondPage.rows.map { it.policyProgram.title }).containsExactly("동점 늦은 마감", "마감 없음")
    }

    @Test
    fun `find page keeps real maximum deadline before null across recommended cursor pages`() {
        val maxDate = LocalDate.of(9999, 12, 31)
        persistRecommendation("경계 후보 A", score = "0.9000", applyEndsOn = null)
        persistRecommendation("경계 후보 B", score = "0.9000", applyEndsOn = null)
        persistRecommendation("경계 후보 C", score = "0.9000", applyEndsOn = null)

        val rowsById = queryRepository.findPage(condition()).rows
        val nullDeadlineRows = rowsById.dropLast(1)
        val datedRow = rowsById.last().also { row ->
            row.policyProgram.applyEndsOn = maxDate
        }
        val datedTitle = datedRow.policyProgram.title
        val nullDeadlineTitles = nullDeadlineRows.map { it.policyProgram.title }
        val datedCursor = PolicyRecommendationQueryRepository.Cursor(
            score = datedRow.score,
            applyStartsOn = null,
            applyEndsOn = maxDate,
            id = requireNotNull(datedRow.id)
        )
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.findPage(condition(size = 1))
        val pageAfterDated = queryRepository.findPage(condition(cursor = datedCursor, size = 1))
        val firstNullRow = pageAfterDated.rows.single()
        val pageAfterFirstNull = queryRepository.findPage(
            condition(
                cursor = PolicyRecommendationQueryRepository.Cursor(
                    score = firstNullRow.score,
                    applyStartsOn = null,
                    applyEndsOn = null,
                    id = requireNotNull(firstNullRow.id)
                )
            )
        )
        val titlesAcrossBoundary =
            firstPage.rows.map { it.policyProgram.title } +
                pageAfterDated.rows.map { it.policyProgram.title } +
                pageAfterFirstNull.rows.map { it.policyProgram.title }

        assertThat(titlesAcrossBoundary).containsExactly(datedTitle, *nullDeadlineTitles.toTypedArray())
    }

    @Test
    fun `find page filters recommendations by benefit summary`() {
        persistRecommendation("융자 지원", benefitSummary = "융자/금융", score = "0.9000", applyEndsOn = null)
        persistRecommendation("직불 지원", benefitSummary = "직불/수당", score = "0.9500", applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.findPage(condition(benefitSummary = "융자/금융"))

        assertThat(result.rows.map { it.policyProgram.title }).containsExactly("융자 지원")
    }

    @Test
    fun `find page orders latest policies by start date with null last and cursor`() {
        persistRecommendation("최근", score = "0.7000", applyStartsOn = LocalDate.of(2026, 7, 1), applyEndsOn = null)
        persistRecommendation("중간", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
        persistRecommendation("과거", score = "0.9500", applyStartsOn = LocalDate.of(2026, 5, 1), applyEndsOn = null)
        persistRecommendation("시작일 없음", score = "0.9900", applyStartsOn = null, applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val firstPage = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST, size = 2))
        val cursorRow = firstPage.rows.last()
        val secondPage = queryRepository.findPage(
            condition(
                sort = PolicyRecommendationSort.LATEST,
                cursor = PolicyRecommendationQueryRepository.Cursor(
                    score = null,
                    applyStartsOn = cursorRow.policyProgram.applyStartsOn,
                    applyEndsOn = null,
                    id = requireNotNull(cursorRow.id)
                )
            )
        )

        assertThat(firstPage.rows.map { it.policyProgram.title }).containsExactly("최근", "중간")
        assertThat(secondPage.rows.map { it.policyProgram.title }).containsExactly("과거", "시작일 없음")
    }

    @Test
    fun `find page orders equal latest dates by id`() {
        persistRecommendation("동일일 A", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
        persistRecommendation("동일일 B", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val result = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST))

        assertThat(result.rows.map { requireNotNull(it.id).toString() }).isSorted
    }

    @Test
    fun `find page keeps all same-date latest rows across cursor pages`() {
        val sameDate = LocalDate.of(2026, 6, 1)
        persistRecommendation("동일일 A", score = "0.9000", applyStartsOn = sameDate, applyEndsOn = null)
        persistRecommendation("동일일 B", score = "0.9000", applyStartsOn = sameDate, applyEndsOn = null)
        persistRecommendation("동일일 C", score = "0.9000", applyStartsOn = sameDate, applyEndsOn = null)
        persistRecommendation("이전 날짜", score = "0.9000", applyStartsOn = LocalDate.of(2026, 5, 1), applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val fullResult = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST))
        val firstPage = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST, size = 1))
        val cursorRow = firstPage.rows.single()
        val secondPage = queryRepository.findPage(
            condition(
                sort = PolicyRecommendationSort.LATEST,
                cursor = latestCursor(cursorRow)
            )
        )
        val pagedIds = (firstPage.rows + secondPage.rows).map { requireNotNull(it.id) }

        assertThat(cursorRow.policyProgram.applyStartsOn).isEqualTo(sameDate)
        assertThat(pagedIds).containsExactlyElementsOf(fullResult.rows.map { requireNotNull(it.id) })
        assertThat(pagedIds).doesNotHaveDuplicates()
    }

    @Test
    fun `find page keeps all null-start latest rows across cursor pages`() {
        persistRecommendation("날짜 있음", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
        persistRecommendation("시작일 없음 A", score = "0.9000", applyStartsOn = null, applyEndsOn = null)
        persistRecommendation("시작일 없음 B", score = "0.9000", applyStartsOn = null, applyEndsOn = null)
        persistRecommendation("시작일 없음 C", score = "0.9000", applyStartsOn = null, applyEndsOn = null)
        entityManager.flush()
        entityManager.clear()

        val fullResult = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST))
        val firstPage = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST, size = 2))
        val cursorRow = firstPage.rows.last()
        val secondPage = queryRepository.findPage(
            condition(
                sort = PolicyRecommendationSort.LATEST,
                cursor = latestCursor(cursorRow)
            )
        )
        val pagedIds = (firstPage.rows + secondPage.rows).map { requireNotNull(it.id) }

        assertThat(cursorRow.policyProgram.applyStartsOn).isNull()
        assertThat(pagedIds).containsExactlyElementsOf(fullResult.rows.map { requireNotNull(it.id) })
        assertThat(pagedIds).doesNotHaveDuplicates()
    }

    private fun persistRecommendation(
        title: String,
        member: Member = this.member,
        sourceYear: String = this.sourceYear,
        benefitSummary: String = "지원 확인",
        score: String,
        applyStartsOn: LocalDate? = null,
        applyEndsOn: LocalDate?
    ): PolicyRecommendation {
        val program = persist(
            PolicyProgram(
                title = title,
                body = "정책 상세",
                region = "전국",
                targetManagementType = null,
                applyEndsOn = applyEndsOn
            ).apply {
                applyListFields(
                    source = PolicySource.NONGUP_EZ,
                    externalId = "external-$title",
                    sourceYear = sourceYear,
                    title = title,
                    summary = "$title 요약",
                    region = "전국",
                    sourceUrl = null,
                    agencyName = "농림축산식품부"
                )
                applyDetailFields(
                    body = "정책 상세",
                    purpose = null,
                    eligibilityOriginal = null,
                    eligibilitySummary = "자격 확인",
                    benefitOriginal = null,
                    benefitSummary = benefitSummary,
                    applyStartsOn = applyStartsOn,
                    applyEndsOn = applyEndsOn,
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
                    recommendable = true
                )
            },
            LocalDateTime.of(2026, 7, 7, 9, 10)
        )
        return persist(
            PolicyRecommendation(
                member = member,
                policyProgram = program,
                score = BigDecimal(score),
                reason = "추천 사유"
            ),
            LocalDateTime.of(2026, 7, 7, 9, 20)
        )
    }

    private fun condition(
        benefitSummary: String? = null,
        sort: PolicyRecommendationSort = PolicyRecommendationSort.RECOMMENDED,
        cursor: PolicyRecommendationQueryRepository.Cursor? = null,
        size: Int = 20
    ): PolicyRecommendationQueryRepository.SearchCondition =
        PolicyRecommendationQueryRepository.SearchCondition(
            memberId = memberId,
            source = PolicySource.NONGUP_EZ,
            sourceYear = sourceYear,
            benefitSummary = benefitSummary,
            sort = sort,
            cursor = cursor,
            size = size
        )

    private fun latestCursor(
        row: PolicyRecommendation
    ): PolicyRecommendationQueryRepository.Cursor =
        PolicyRecommendationQueryRepository.Cursor(
            score = null,
            applyStartsOn = row.policyProgram.applyStartsOn,
            applyEndsOn = null,
            id = requireNotNull(row.id)
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
private class PolicyRecommendationQueryRepositoryTestApplication
