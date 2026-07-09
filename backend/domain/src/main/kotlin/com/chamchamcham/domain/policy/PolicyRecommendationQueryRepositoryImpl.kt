package com.chamchamcham.domain.policy

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PolicyRecommendationQueryRepositoryImpl(
    private val entityManager: EntityManager
) : PolicyRecommendationQueryRepository {
    override fun findPage(
        condition: PolicyRecommendationQueryRepository.SearchCondition
    ): PolicyRecommendationQueryRepository.SearchResult {
        val maxDate = LocalDate.of(9999, 12, 31)
        val where = mutableListOf(
            "r.member.id = :memberId",
            "p.source = :source",
            "p.sourceYear = :sourceYear"
        )
        val params = linkedMapOf<String, Any>(
            "memberId" to condition.memberId,
            "source" to condition.source,
            "sourceYear" to condition.sourceYear,
            "maxDate" to maxDate
        )

        condition.benefitSummary?.let { benefitSummary ->
            where += "p.benefitSummary = :benefitSummary"
            params["benefitSummary"] = benefitSummary
        }

        condition.cursor?.let { cursor ->
            where += """
                (
                    r.score < :cursorScore
                    or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) > :cursorApplyEndsOn)
                    or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) = :cursorApplyEndsOn and r.id > :cursorId)
                )
            """.trimIndent()
            params["cursorScore"] = cursor.score
            params["cursorApplyEndsOn"] = cursor.applyEndsOn ?: maxDate
            params["cursorId"] = cursor.id
        }

        val query = entityManager.createQuery(
            """
            select r
            from PolicyRecommendation r
            join fetch r.policyProgram p
            where ${where.joinToString(" and ")}
            order by r.score desc, coalesce(p.applyEndsOn, :maxDate) asc, r.id asc
            """.trimIndent(),
            PolicyRecommendation::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size

        return PolicyRecommendationQueryRepository.SearchResult(query.resultList)
    }
}
