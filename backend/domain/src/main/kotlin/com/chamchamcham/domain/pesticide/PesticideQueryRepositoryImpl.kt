package com.chamchamcham.domain.pesticide

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class PesticideQueryRepositoryImpl(
    private val entityManager: EntityManager
) : PesticideQueryRepository {
    override fun search(condition: PesticideQueryRepository.SearchCondition): List<Pesticide> {
        val where = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        condition.keyword?.takeIf(String::isNotBlank)?.let {
            where += "(p.brandName like concat('%', :keyword, '%') or p.itemName like concat('%', :keyword, '%'))"
            params["keyword"] = it
        }
        condition.cursor?.let { cursor ->
            where += "(p.brandName > :cursorBrandName or (p.brandName = :cursorBrandName and p.id > :cursorId))"
            params["cursorBrandName"] = cursor.brandName
            params["cursorId"] = cursor.id
        }

        val whereClause = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val query = entityManager.createQuery(
            """
            select p
            from Pesticide p
            $whereClause
            order by p.brandName asc, p.id asc
            """.trimIndent(),
            Pesticide::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size
        return query.resultList
    }
}
