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
            "sourceYear" to condition.sourceYear
        )

        condition.benefitSummary?.let { benefitSummary ->
            where += "p.benefitSummary = :benefitSummary"
            params["benefitSummary"] = benefitSummary
        }

        condition.cursor?.let { cursor ->
            when (condition.sort) {
                PolicyRecommendationSort.RECOMMENDED -> {
                    where += """
                        (
                            r.score < :cursorScore
                            or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) > :cursorApplyEndsOn)
                            or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) = :cursorApplyEndsOn and r.id > :cursorId)
                        )
                    """.trimIndent()
                    params["cursorScore"] = requireNotNull(cursor.score) {
                        "Recommended policy cursor requires score"
                    }
                    params["cursorApplyEndsOn"] = cursor.applyEndsOn ?: maxDate
                }

                PolicyRecommendationSort.LATEST -> {
                    if (cursor.applyStartsOn == null) {
                        where += "p.applyStartsOn is null and r.id > :cursorId"
                    } else {
                        where += """
                            (
                                p.applyStartsOn < :cursorApplyStartsOn
                                or (p.applyStartsOn = :cursorApplyStartsOn and r.id > :cursorId)
                                or p.applyStartsOn is null
                            )
                        """.trimIndent()
                        params["cursorApplyStartsOn"] = cursor.applyStartsOn
                    }
                }
            }
            params["cursorId"] = cursor.id
        }

        val orderBy = when (condition.sort) {
            PolicyRecommendationSort.RECOMMENDED -> {
                params["maxDate"] = maxDate
                "r.score desc, coalesce(p.applyEndsOn, :maxDate) asc, r.id asc"
            }

            PolicyRecommendationSort.LATEST ->
                "case when p.applyStartsOn is null then 1 else 0 end asc, p.applyStartsOn desc, r.id asc"
        }

        val query = entityManager.createQuery(
            """
            select r
            from PolicyRecommendation r
            join fetch r.policyProgram p
            where ${where.joinToString(" and ")}
            order by $orderBy
            """.trimIndent(),
            PolicyRecommendation::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size

        return PolicyRecommendationQueryRepository.SearchResult(query.resultList)
    }
}
