package com.godsmove.application.coaching.rag.seed

import com.godsmove.application.coaching.rag.FarmingRecordDocumentFactory
import com.godsmove.application.coaching.rag.RagProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import org.springframework.jdbc.core.JdbcTemplate
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.OffsetDateTime

class DevRagSeedServiceTest {
    @Test
    fun `seed writes relational timestamps as local date times`() {
        val jdbcTemplate = RecordingJdbcTemplate()
        val service = DevRagSeedService(
            jdbcTemplate = jdbcTemplate,
            ragProperties = RagProperties(),
            vectorStore = NoopVectorStore(),
            farmingRecordDocumentFactory = FarmingRecordDocumentFactory()
        )

        service.seed(
            DevRagSeedCommand(
                pdfPath = null,
                resetIndex = false,
                includePdf = false,
                includeFarmingRecords = false,
                maxPdfChunks = 1
            )
        )

        val allArgs = jdbcTemplate.updates.flatMap { it.args }
        assertThat(allArgs).noneMatch { it is OffsetDateTime }

        val workedAtArgs = jdbcTemplate.updates
            .filter { it.sql.contains("insert into farming_record") }
            .map { it.args[5] }

        assertThat(workedAtArgs).containsExactly(
            LocalDateTime.of(2026, 6, 18, 8, 30),
            LocalDateTime.of(2026, 6, 22, 7, 50),
            LocalDateTime.of(2026, 6, 25, 16, 10),
            LocalDateTime.of(2026, 6, 28, 17, 20),
            LocalDateTime.of(2026, 6, 30, 9, 0)
        )
    }

    @Test
    fun `seed rejects oversized pdf before text extraction`() {
        val pdf = Files.createTempFile("godsmove-rag-seed-", ".pdf")
        RandomAccessFile(pdf.toFile(), "rw").use { file ->
            file.setLength(DevRagSeedService.MAX_SEED_PDF_BYTES + 1)
        }

        try {
            val service = DevRagSeedService(
                jdbcTemplate = RecordingJdbcTemplate(),
                ragProperties = RagProperties(),
                vectorStore = NoopVectorStore(),
                farmingRecordDocumentFactory = FarmingRecordDocumentFactory()
            )

            assertThatThrownBy {
                service.seed(
                    DevRagSeedCommand(
                        pdfPath = pdf.toString(),
                        resetIndex = false,
                        includePdf = true,
                        includeFarmingRecords = false,
                        maxPdfChunks = 1
                    )
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("PDF file is too large for local seed")
        } finally {
            Files.deleteIfExists(pdf)
        }
    }

    private data class RecordedUpdate(
        val sql: String,
        val args: List<Any?>
    )

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val updates = mutableListOf<RecordedUpdate>()

        override fun update(sql: String, vararg args: Any?): Int {
            updates += RecordedUpdate(sql, args.toList())
            return 1
        }
    }

    private class NoopVectorStore : VectorStore {
        override fun add(documents: List<Document>) = Unit

        override fun delete(idList: List<String>) = Unit

        override fun delete(filterExpression: Filter.Expression) = Unit

        override fun similaritySearch(request: SearchRequest): List<Document> = emptyList()
    }
}
