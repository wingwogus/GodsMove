package com.chamchamcham.application.coaching.reportfeedback.generation

import com.chamchamcham.domain.farming.WorkType
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class ReportFeedbackContextFingerprintTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val fingerprint = ReportFeedbackContextFingerprint(objectMapper)

    @Test
    fun `map insertion order does not change the context fingerprint`() {
        val first = context(statistics = linkedMapOf("count" to 2, "amount" to 3))
        val second = context(statistics = linkedMapOf("amount" to 3, "count" to 2))

        assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second))
    }

    @Test
    fun `nested snapshot map insertion order does not change the fingerprint`() {
        val first = linkedMapOf<String, Any?>(
            "report" to linkedMapOf(
                "statistics" to linkedMapOf("count" to 2, "amount" to 3),
                "id" to "report-1",
            ),
            "records" to listOf(linkedMapOf("memo" to "점적관수", "id" to "record-1")),
        )
        val second = linkedMapOf<String, Any?>(
            "records" to listOf(linkedMapOf("id" to "record-1", "memo" to "점적관수")),
            "report" to linkedMapOf(
                "id" to "report-1",
                "statistics" to linkedMapOf("amount" to 3, "count" to 2),
            ),
        )

        assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second))
    }

    @Test
    fun `scientific plain trailing zero and integral numeric forms share a fingerprint`() {
        val fingerprints = listOf(
            BigDecimal("1E+3"),
            BigDecimal("1000"),
            BigDecimal("1000.0"),
            1000,
            1000L,
        ).map { amount ->
            fingerprint.calculate(mapOf("amount" to amount))
        }

        assertThat(fingerprints).containsOnly(fingerprints.first())
    }

    @Test
    fun `context matches its persisted untyped snapshot after numeric json round trip`() {
        val currentContext = context(
            statistics = mapOf(
                "recordCount" to 1,
                "amount" to BigDecimal("1E+3"),
                "ratio" to BigDecimal("1.2300"),
            ),
        )
        val snapshot: Map<String, Any?> = objectMapper.convertValue(currentContext, SNAPSHOT_TYPE)
        val persistedSnapshot: Map<String, Any?> = objectMapper.readValue(
            objectMapper.writeValueAsBytes(snapshot),
            SNAPSHOT_TYPE,
        )

        assertThat(fingerprint.calculate(currentContext))
            .isEqualTo(fingerprint.calculate(persistedSnapshot))
    }

    @Test
    fun `array order changes the context fingerprint`() {
        val firstRecord = record(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val secondRecord = record(UUID.fromString("00000000-0000-0000-0000-000000000002"))

        val first = context(records = listOf(firstRecord, secondRecord))
        val second = context(records = listOf(secondRecord, firstRecord))

        assertThat(fingerprint.calculate(first)).isNotEqualTo(fingerprint.calculate(second))
    }

    @Test
    fun `fingerprint is lowercase sha256 hex`() {
        assertThat(fingerprint.calculate(context()))
            .matches("^[0-9a-f]{64}$")
    }

    private fun context(
        statistics: Map<String, Any?> = mapOf("recordCount" to 2),
        records: List<ReportFeedbackRecord> = listOf(
            record(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        ),
    ) = ReportFeedbackContext(
        schemaVersion = REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION,
        workType = WorkType.WATERING,
        report = ReportFeedbackReport(
            id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
            farmName = "약초농장",
            cropName = "황기",
            startsAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            endsAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            sourceRevision = 7,
            statistics = statistics,
        ),
        records = records,
        previousReport = null,
        warnings = emptyList(),
    )

    private fun record(id: UUID) = ReportFeedbackRecord(
        id = id,
        workedAt = LocalDateTime.of(2026, 4, 1, 9, 0),
        workType = WorkType.WATERING,
        memo = "점적관수",
        details = linkedMapOf("method" to "DRIP", "amount" to "LOW"),
    )

    private companion object {
        val SNAPSHOT_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
