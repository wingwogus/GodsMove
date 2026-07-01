package com.godsmove.application.coaching.rag

import java.time.ZoneOffset

data class RagSqlQuery(
    val sql: String,
    val args: List<Any>
)

object RagIndexSqlBuilder {
    fun vectorToSql(vector: List<Double>): String {
        require(vector.isNotEmpty()) { "embedding must be a non-empty numeric array" }
        require(vector.all { it.isFinite() }) { "embedding must contain only finite numbers" }
        return vector.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    fun buildRetrievalQuery(
        embedding: List<Double>,
        filters: RagRetrievalFilter,
        topK: Int
    ): RagSqlQuery {
        val vector = vectorToSql(embedding)
        val args = mutableListOf<Any>(vector, filters.memberId)
        val farmingConditions = mutableListOf("source_type = 'FARMING_RECORD'", "member_id = ?")

        filters.farmId?.let {
            farmingConditions += "farm_id = ?"
            args += it
        }
        filters.cropId?.let {
            farmingConditions += "crop_id = ?"
            args += it
        }
        filters.workTypeId?.let {
            farmingConditions += "work_type_id = ?"
            args += it
        }
        filters.recordId?.let {
            farmingConditions += "record_id = ?"
            args += it
        }
        filters.periodStart?.let {
            farmingConditions += "worked_at >= ?"
            args += it.atStartOfDay().toInstant(ZoneOffset.UTC)
        }
        filters.periodEnd?.let {
            farmingConditions += "worked_at < ?"
            args += it.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        }

        return RagSqlQuery(
            sql = """
                select
                  id,
                  source_type,
                  source_id,
                  content,
                  coalesce(metadata ->> 'label', source_id) as label,
                  1 - (embedding <=> ?::vector) as similarity_score
                from rag_index_chunk
                where source_type = 'TECH_DOCUMENT'
                   or (${farmingConditions.joinToString(" and ")})
                order by embedding <=> ?::vector
                limit ?
            """.trimIndent(),
            args = args + vector + topK
        )
    }
}
