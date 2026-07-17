package com.chamchamcham.domain.policy

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class PolicyProgramQueryRepositoryImpl(
    private val entityManager: EntityManager
) : PolicyProgramQueryRepository {
    override fun search(condition: PolicyProgramQueryRepository.SearchCondition): List<PolicyProgram> {
        val (where, params) = buildFilterPredicates(condition.keyword)

        if (condition.cursorCreatedAt != null && condition.cursorId != null) {
            where += "(p.createdAt < :cCreatedAt or (p.createdAt = :cCreatedAt and p.id < :cId))"
            params["cCreatedAt"] = condition.cursorCreatedAt
            params["cId"] = condition.cursorId
        }

        val query = entityManager.createQuery(
            """
            select p
            from PolicyProgram p
            where ${where.joinToString(" and ")}
            order by p.createdAt desc, p.id desc
            """.trimIndent(),
            PolicyProgram::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size

        return query.resultList
    }

    override fun count(keyword: String?): Long {
        val (where, params) = buildFilterPredicates(keyword)

        val query = entityManager.createQuery(
            """
            select count(p)
            from PolicyProgram p
            where ${where.joinToString(" and ")}
            """.trimIndent(),
            Long::class.javaObjectType
        )
        params.forEach(query::setParameter)
        return query.singleResult
    }

    private fun buildFilterPredicates(keyword: String?): Pair<MutableList<String>, MutableMap<String, Any>> {
        val where = mutableListOf("p.detailSynced = true", "p.recommendable = true")
        val params = linkedMapOf<String, Any>()

        keyword?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { trimmed ->
            where += "(lower(p.title) like :keyword or lower(p.agencyName) like :keyword or lower(p.summary) like :keyword)"
            params["keyword"] = "%$trimmed%"
        }

        return where to params
    }
}
