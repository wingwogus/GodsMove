package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RagIndexJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : RagIndexRepository {
    override fun retrieve(
        embedding: List<Double>,
        filters: RagRetrievalFilter,
        topK: Int
    ): List<RagEvidenceChunk> {
        val query = RagIndexSqlBuilder.buildRetrievalQuery(embedding, filters, topK)
        return try {
            jdbcTemplate.query(query.sql, rowMapper(), *query.args.toTypedArray())
        } catch (_: DataAccessException) {
            throw BusinessException(ErrorCode.RAG_INDEX_UNAVAILABLE)
        }
    }

    private fun rowMapper(): RowMapper<RagEvidenceChunk> {
        return RowMapper { rs, _ ->
            RagEvidenceChunk(
                id = UUID.fromString(rs.getString("id")),
                sourceType = RagSourceType.valueOf(rs.getString("source_type")),
                sourceId = rs.getString("source_id"),
                content = rs.getString("content"),
                label = rs.getString("label"),
                similarityScore = rs.getDouble("similarity_score")
            )
        }
    }
}
