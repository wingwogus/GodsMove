package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@DataJpaTest
@ActiveProfiles("test")
class PolicyProgramRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val policyProgramRepository: PolicyProgramRepository
) {
    @Test
    fun `title suggestions match keyword ignoring case`() {
        persistProgram(title = "청년 농업인 지원사업", externalId = "1")
        persistProgram(title = "귀농 정착 지원", externalId = "2")
        entityManager.flush()
        entityManager.clear()

        val result = policyProgramRepository.findTitleSuggestions("농업", PageRequest.of(0, 9))

        assertThat(result.map { it.title }).containsExactly("청년 농업인 지원사업")
    }

    @Test
    fun `title suggestions limit result size by pageable`() {
        (1..12).forEach { i -> persistProgram(title = "지원사업$i", externalId = "title-$i") }
        entityManager.flush()
        entityManager.clear()

        val result = policyProgramRepository.findTitleSuggestions("지원사업", PageRequest.of(0, 9))

        assertThat(result).hasSize(9)
    }

    @Test
    fun `agency suggestions match keyword ignoring case`() {
        persistProgram(title = "정책 A", externalId = "3", agencyName = "농림축산식품부")
        persistProgram(title = "정책 B", externalId = "4", agencyName = "해양수산부")
        entityManager.flush()
        entityManager.clear()

        val result = policyProgramRepository.findAgencySuggestions("농림", PageRequest.of(0, 9))

        assertThat(result.map { it.agencyName }).containsExactly("농림축산식품부")
    }

    @Test
    fun `agency suggestions limit result size by pageable`() {
        (1..12).forEach { i -> persistProgram(title = "정책$i", externalId = "agency-$i", agencyName = "공통기관") }
        entityManager.flush()
        entityManager.clear()

        val result = policyProgramRepository.findAgencySuggestions("공통기관", PageRequest.of(0, 9))

        assertThat(result).hasSize(9)
    }

    private fun persistProgram(
        title: String,
        externalId: String,
        agencyName: String = "농림축산식품부"
    ): PolicyProgram {
        val program = PolicyProgram(
            title = title,
            body = "정책 상세",
            region = "전국",
            targetManagementType = null
        )
        program.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = externalId,
            sourceYear = "2026",
            title = title,
            summary = "$title 요약",
            region = "전국",
            sourceUrl = null,
            agencyName = agencyName
        )
        return persist(program, LocalDateTime.of(2026, 7, 7, 9, 0))
    }

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
