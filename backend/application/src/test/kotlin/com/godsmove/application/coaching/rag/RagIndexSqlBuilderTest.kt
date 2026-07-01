package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RagIndexSqlBuilderTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `vectorToSql rejects invalid vectors`() {
        assertThatThrownBy { RagIndexSqlBuilder.vectorToSql(emptyList()) }
            .hasMessageContaining("non-empty")
        assertThatThrownBy { RagIndexSqlBuilder.vectorToSql(listOf(Double.NaN)) }
            .hasMessageContaining("finite")
    }

    @Test
    fun `buildRetrievalQuery includes member access control and optional filters`() {
        val query = RagIndexSqlBuilder.buildRetrievalQuery(
            embedding = listOf(0.1, 0.2),
            filters = RagRetrievalFilter(
                memberId = memberId,
                cropId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                periodStart = LocalDate.parse("2026-06-01"),
                periodEnd = LocalDate.parse("2026-06-30")
            ),
            topK = 6
        )

        assertThat(query.sql).contains("source_type = 'TECH_DOCUMENT'")
        assertThat(query.sql).contains("source_type = 'FARMING_RECORD'")
        assertThat(query.sql).contains("member_id = ?")
        assertThat(query.sql).contains("crop_id = ?")
        assertThat(query.sql).contains("worked_at >=")
        assertThat(query.sql).contains("worked_at <")
        assertThat(query.args.first()).isEqualTo("[0.1,0.2]")
        assertThat(query.args).contains(memberId)
    }
}
