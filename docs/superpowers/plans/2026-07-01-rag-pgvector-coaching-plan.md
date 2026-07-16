# RAG Pgvector Farming Coaching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an interactive farming coaching RAG API backed by a minimal pgvector chunk index, local Ollama embeddings, and OpenClaw chat generation.

**Architecture:** Keep GodsMove's existing module direction: `api -> application -> domain`. Put RAG orchestration, prompt/audit logic, provider ports, and pgvector repository in `application`; put HTTP provider adapters and controllers in `api`; keep existing domain tables as source of truth and add only `rag_index_chunk` as derived search data.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.4, Spring MVC, Spring Security, JdbcTemplate, PostgreSQL + pgvector, local Ollama `bge-m3`, OpenClaw chat completions, JUnit 5, Mockito, MockMvc.

---

## File Structure

Create:

- `backend/docs/db/rag-index-schema.sql`: migration-ready SQL for `vector` extension, `rag_index_chunk`, filter indexes, and HNSW cosine index.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt`: command types and `CoachingRagMode`.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt`: response model used by API mapping.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagEvidence.kt`: retrieved chunk and citation models.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilder.kt`: evidence formatting and prompt creation.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditor.kt`: citation extraction, audit, retry decision, and fallback answer.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`: RAG orchestration use case.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagClients.kt`: `EmbeddingClient`, `ChatCompletionClient`, and message DTOs.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexRepository.kt`: repository port and filter type.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilder.kt`: SQL text/value builder for pgvector retrieval.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexJdbcRepository.kt`: JdbcTemplate implementation.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagProperties.kt`: typed RAG config.
- `backend/api/src/main/kotlin/com/godsmove/api/coaching/controller/CoachingRagController.kt`: authenticated HTTP endpoint.
- `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`: request DTO.
- `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt`: response DTO.
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClient.kt`: Ollama embedding port implementation.
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClient.kt`: OpenClaw chat port implementation.

Modify:

- `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`: add RAG/provider error codes.
- `backend/api/src/main/resources/application-local.yml`: add non-secret RAG defaults.
- `backend/api/src/main/resources/application-dev.yml`: add RAG env-backed config.
- `backend/api/src/main/resources/application-prod.yml`: add RAG env-backed config.
- `backend/api/src/test/resources/application-test.yml`: add safe test RAG defaults.

Tests:

- `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditorTest.kt`
- `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilderTest.kt`
- `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilderTest.kt`
- `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRagServiceTest.kt`
- `backend/api/src/test/kotlin/com/godsmove/api/coaching/controller/CoachingRagControllerTest.kt`
- `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClientTest.kt`
- `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClientTest.kt`

## Task 1: Schema, Config, And Error Codes

**Files:**

- Create: `backend/docs/db/rag-index-schema.sql`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagProperties.kt`
- Modify: `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/main/resources/application-dev.yml`
- Modify: `backend/api/src/main/resources/application-prod.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`

- [ ] **Step 1: Add the pgvector schema SQL**

Create `backend/docs/db/rag-index-schema.sql`:

```sql
create extension if not exists vector;

create table if not exists rag_index_chunk (
  id uuid primary key default gen_random_uuid(),
  source_type varchar(32) not null check (source_type in ('TECH_DOCUMENT', 'FARMING_RECORD')),
  source_id varchar(128) not null,
  member_id uuid,
  farm_id uuid,
  crop_id uuid,
  work_type_id uuid,
  record_id uuid,
  worked_at timestamptz,
  chunk_index integer not null,
  content text not null,
  content_hash varchar(64) not null,
  embedding vector(1024) not null,
  embedding_model varchar(128) not null,
  metadata jsonb not null default '{}'::jsonb,
  indexed_at timestamptz not null default now(),
  unique (source_type, source_id, chunk_index, embedding_model)
);

create index if not exists rag_index_chunk_member_idx
  on rag_index_chunk (member_id);

create index if not exists rag_index_chunk_filter_idx
  on rag_index_chunk (source_type, member_id, farm_id, crop_id, work_type_id, record_id, worked_at);

create index if not exists rag_index_chunk_content_hash_idx
  on rag_index_chunk (content_hash);

create index if not exists rag_index_chunk_embedding_hnsw_idx
  on rag_index_chunk using hnsw (embedding vector_cosine_ops);
```

- [ ] **Step 2: Add RAG properties**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagProperties.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val embedding: Embedding = Embedding(),
    val chat: Chat = Chat(),
    val retrieval: Retrieval = Retrieval(),
    val timeoutMillis: Long = 30_000
) {
    data class Embedding(
        val model: String = "bge-m3",
        val dimension: Int = 1024
    )

    data class Chat(
        val model: String = "openclaw/agri-rag-coach"
    )

    data class Retrieval(
        val topKDefault: Int = 6,
        val topKMax: Int = 20,
        val lowSimilarityThreshold: Double = 0.55
    )
}
```

- [ ] **Step 3: Register configuration properties**

Modify `backend/api/src/main/kotlin/com/godsmove/ApiApplication.kt`:

```kotlin
package com.godsmove

import com.godsmove.application.coaching.rag.RagProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RagProperties::class)
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
```

- [ ] **Step 4: Add RAG error codes**

Append these enum values before `UNAUTHORIZED` in `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`:

```kotlin
RAG_INVALID_REQUEST("RAG_001", "error.rag_invalid_request", 400),
RAG_EMBEDDING_UNAVAILABLE("RAG_002", "error.rag_embedding_unavailable", 503),
RAG_CHAT_UNAVAILABLE("RAG_003", "error.rag_chat_unavailable", 503),
RAG_INDEX_UNAVAILABLE("RAG_004", "error.rag_index_unavailable", 503),
RAG_EMBEDDING_DIMENSION_MISMATCH("RAG_005", "error.rag_embedding_dimension_mismatch", 500),
```

- [ ] **Step 5: Add YAML config defaults**

Add this block to `backend/api/src/main/resources/application-local.yml` and `backend/api/src/test/resources/application-test.yml`:

```yaml
rag:
  embedding:
    model: ${RAG_EMBEDDING_MODEL:bge-m3}
    dimension: ${RAG_EMBEDDING_DIMENSION:1024}
  chat:
    model: ${RAG_CHAT_MODEL:openclaw/agri-rag-coach}
  retrieval:
    top-k-default: ${RAG_TOP_K_DEFAULT:6}
    top-k-max: ${RAG_TOP_K_MAX:20}
    low-similarity-threshold: ${RAG_LOW_SIMILARITY_THRESHOLD:0.55}
  timeout-millis: ${RAG_TIMEOUT_MILLIS:30000}

ollama:
  base-url: ${OLLAMA_BASE_URL:http://localhost:11434}

openclaw:
  base-url: ${OPENCLAW_BASE_URL:http://127.0.0.1:18789}
  api-key: ${OPENCLAW_API_KEY:}
  agent-id: ${OPENCLAW_AGENT_ID:agri-rag-coach}
```

Add this block to `backend/api/src/main/resources/application-dev.yml` and `backend/api/src/main/resources/application-prod.yml`:

```yaml
rag:
  embedding:
    model: ${RAG_EMBEDDING_MODEL:bge-m3}
    dimension: ${RAG_EMBEDDING_DIMENSION:1024}
  chat:
    model: ${RAG_CHAT_MODEL:openclaw/agri-rag-coach}
  retrieval:
    top-k-default: ${RAG_TOP_K_DEFAULT:6}
    top-k-max: ${RAG_TOP_K_MAX:20}
    low-similarity-threshold: ${RAG_LOW_SIMILARITY_THRESHOLD:0.55}
  timeout-millis: ${RAG_TIMEOUT_MILLIS:30000}

ollama:
  base-url: ${OLLAMA_BASE_URL}

openclaw:
  base-url: ${OPENCLAW_BASE_URL}
  api-key: ${OPENCLAW_API_KEY}
  agent-id: ${OPENCLAW_AGENT_ID:agri-rag-coach}
```

- [ ] **Step 6: Run compile check for config wiring**

Run:

```bash
cd backend
./gradlew :api:compileKotlin :application:compileKotlin
```

Expected: compilation succeeds after all files in this task are present.

## Task 2: RAG Core Models, Prompt, Audit, And Fallback

**Files:**

- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagEvidence.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagClients.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilder.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditor.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilderTest.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditorTest.kt`

- [ ] **Step 1: Write prompt builder tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilderTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RagPromptBuilderTest {
    private val chunk = RagEvidenceChunk(
        id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
        sourceType = RagSourceType.FARMING_RECORD,
        sourceId = "00000000-0000-0000-0000-000000000201",
        content = "참당귀 관수 후 토양이 과습하지 않도록 배수 상태를 확인했다.",
        label = "영농일지 2026-06-30 관수",
        similarityScore = 0.82
    )

    @Test
    fun `buildPrompt includes citation ids and question`() {
        val messages = RagPromptBuilder().buildPrompt(
            question = "과습 위험이 있을까?",
            chunks = listOf(chunk)
        )

        assertThat(messages).hasSize(2)
        assertThat(messages[0].role).isEqualTo("system")
        assertThat(messages[1].content).contains("과습 위험이 있을까?")
        assertThat(messages[1].content).contains("[chunk:00000000-0000-0000-0000-000000000101]")
        assertThat(messages[1].content).contains("참당귀 관수")
    }
}
```

- [ ] **Step 2: Write citation auditor tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditorTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RagCitationAuditorTest {
    private val knownChunkId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val lowChunkId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val chunks = listOf(
        RagEvidenceChunk(
            id = knownChunkId,
            sourceType = RagSourceType.TECH_DOCUMENT,
            sourceId = "tech-page-12",
            content = "과습은 뿌리 생육을 저해한다.",
            label = "기술문서 p.12",
            similarityScore = 0.8
        ),
        RagEvidenceChunk(
            id = lowChunkId,
            sourceType = RagSourceType.TECH_DOCUMENT,
            sourceId = "tech-page-13",
            content = "배수 상태를 확인한다.",
            label = "기술문서 p.13",
            similarityScore = 0.4
        )
    )

    @Test
    fun `audit passes when all citations are known`() {
        val audit = RagCitationAuditor().audit(
            answerText = "배수 확인이 필요합니다. [chunk:$knownChunkId]",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.PASS)
        assertThat(audit.warnings).isEmpty()
        assertThat(audit.citations).containsExactly(knownChunkId.toString())
    }

    @Test
    fun `audit fails for missing citations`() {
        val audit = RagCitationAuditor().audit(
            answerText = "배수 확인이 필요합니다.",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("no_citations")
    }

    @Test
    fun `audit warns for low similarity citations`() {
        val audit = RagCitationAuditor().audit(
            answerText = "관련성은 낮지만 참고할 수 있습니다. [chunk:$lowChunkId]",
            retrievedChunks = chunks,
            lowSimilarityThreshold = 0.55
        )

        assertThat(audit.status).isEqualTo(RagAuditStatus.WARN)
        assertThat(audit.warnings).contains("low_similarity:$lowChunkId")
    }

    @Test
    fun `extractive fallback cites retrieved chunks`() {
        val answer = RagCitationAuditor().buildExtractiveFallbackAnswer(
            question = "과습 위험이 있을까?",
            retrievedChunks = chunks
        )

        assertThat(answer).contains("[chunk:$knownChunkId]")
        assertThat(answer).contains("모델 답변이 citation 규칙을 지키지 않아")
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests '*RagPromptBuilderTest' --tests '*RagCitationAuditorTest'
```

Expected: tests fail because RAG core classes do not exist.

- [ ] **Step 4: Add core models and ports**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import java.time.LocalDate
import java.util.UUID

enum class CoachingRagMode {
    CHAT,
    RECORD_AUTO,
    REPORT_MANUAL
}

data class CoachingRagCommand(
    val memberId: UUID,
    val mode: CoachingRagMode = CoachingRagMode.CHAT,
    val question: String,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workTypeId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val topK: Int? = null
)
```

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagEvidence.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import java.util.UUID

enum class RagSourceType {
    TECH_DOCUMENT,
    FARMING_RECORD
}

data class RagEvidenceChunk(
    val id: UUID,
    val sourceType: RagSourceType,
    val sourceId: String,
    val content: String,
    val label: String,
    val similarityScore: Double
)

data class RagCitation(
    val chunkId: String,
    val sourceType: RagSourceType,
    val sourceId: String,
    val label: String,
    val similarityScore: Double
)
```

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt`:

```kotlin
package com.godsmove.application.coaching.rag

enum class RagAuditStatus {
    PASS,
    WARN,
    FAIL
}

data class RagAuditResult(
    val status: RagAuditStatus,
    val warnings: List<String>,
    val citations: List<String>
)

data class RagModelInfo(
    val embedding: String,
    val chat: String
)

data class CoachingRagResult(
    val answer: String,
    val citations: List<RagCitation>,
    val audit: RagAuditResult,
    val model: RagModelInfo
)
```

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagClients.kt`:

```kotlin
package com.godsmove.application.coaching.rag

data class ChatMessage(
    val role: String,
    val content: String
)

interface EmbeddingClient {
    fun embed(input: String, model: String): List<Double>
}

interface ChatCompletionClient {
    fun complete(messages: List<ChatMessage>, model: String): String
}
```

- [ ] **Step 5: Add prompt builder and citation auditor implementation**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagPromptBuilder.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class RagPromptBuilder {
    fun buildPrompt(question: String, chunks: List<RagEvidenceChunk>): List<ChatMessage> {
        val evidence = formatEvidence(chunks)
        return listOf(
            ChatMessage(
                role = "system",
                content = "너는 농업 영농 코칭 보조자다. 반드시 제공된 근거 chunk만 사용해 한국어로 답한다. 모든 근거 문장 끝에 [chunk:<id>] citation을 붙인다. 근거가 부족하면 현재 자료만으로는 판단할 수 없다고 말한다."
            ),
            ChatMessage(
                role = "user",
                content = "질문: $question\n\n근거:\n$evidence"
            )
        )
    }

    fun buildCitationRetryPrompt(question: String, chunks: List<RagEvidenceChunk>): List<ChatMessage> {
        val allowed = chunks.joinToString(", ") { "[chunk:${it.id}]" }
        return listOf(
            ChatMessage(
                role = "system",
                content = "이전 답변은 citation 감사에 실패했다. 허용된 chunk id만 사용하고 모든 bullet 끝에 정확한 citation을 붙인다."
            ),
            ChatMessage(
                role = "user",
                content = "질문: $question\n\n허용된 citation: $allowed\n\n근거:\n${formatEvidence(chunks)}"
            )
        )
    }

    private fun formatEvidence(chunks: List<RagEvidenceChunk>): String {
        return chunks.joinToString("\n\n") { chunk ->
            "[chunk:${chunk.id}] ${chunk.label}\n${chunk.content}"
        }
    }
}
```

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagCitationAuditor.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class RagCitationAuditor {
    private val citationPattern = Regex("\\[chunk:([^\\]]+)]")

    fun audit(
        answerText: String,
        retrievedChunks: List<RagEvidenceChunk>,
        lowSimilarityThreshold: Double
    ): RagAuditResult {
        val warnings = mutableListOf<String>()
        val citations = extractCitations(answerText)
        val retrievedById = retrievedChunks.associateBy { it.id.toString() }

        if (retrievedChunks.isEmpty()) warnings += "no_retrieved_chunks"
        if (answerText.isBlank()) warnings += "empty_answer"
        if (answerText.isNotBlank() && citations.isEmpty()) warnings += "no_citations"

        citations.forEach { citation ->
            val chunk = retrievedById[citation]
            if (chunk == null) {
                warnings += "unknown_citation:$citation"
            } else if (chunk.similarityScore < lowSimilarityThreshold) {
                warnings += "low_similarity:$citation"
            }
        }

        val failure = warnings.any {
            it == "no_retrieved_chunks" ||
                it == "empty_answer" ||
                it == "no_citations" ||
                it.startsWith("unknown_citation:")
        }

        return RagAuditResult(
            status = if (failure) RagAuditStatus.FAIL else if (warnings.isNotEmpty()) RagAuditStatus.WARN else RagAuditStatus.PASS,
            warnings = warnings.distinct(),
            citations = citations
        )
    }

    fun shouldRetry(audit: RagAuditResult): Boolean {
        return audit.warnings.any { it == "no_citations" || it.startsWith("unknown_citation:") }
    }

    fun buildExtractiveFallbackAnswer(
        question: String,
        retrievedChunks: List<RagEvidenceChunk>,
        maxChunks: Int = 4
    ): String {
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(question)
            .map { it.value }
            .filter { it.length >= 2 }
            .toSet()

        val bullets = retrievedChunks.take(maxChunks).map { chunk ->
            "- ${chunk.label}: ${snippet(chunk.content, tokens)} [chunk:${chunk.id}]"
        }

        return listOf(
            "모델 답변이 citation 규칙을 지키지 않아, 검색된 근거에서 확인 가능한 내용만 추출했습니다.",
            "",
            *bullets.toTypedArray()
        ).joinToString("\n")
    }

    private fun extractCitations(text: String): List<String> {
        return citationPattern.findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun snippet(content: String, tokens: Set<String>, maxLength: Int = 220): String {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= maxLength) return normalized
        val index = tokens.mapNotNull { token ->
            normalized.indexOf(token, ignoreCase = true).takeIf { it >= 0 }
        }.minOrNull() ?: 0
        val start = (index - 70).coerceAtLeast(0)
        val prefix = if (start > 0) "..." else ""
        return prefix + normalized.substring(start, (start + maxLength).coerceAtMost(normalized.length)).trim()
    }
}
```

- [ ] **Step 6: Run core tests**

Run:

```bash
cd backend
./gradlew :application:test --tests '*RagPromptBuilderTest' --tests '*RagCitationAuditorTest'
```

Expected: tests pass.

## Task 3: Retrieval SQL Builder And JDBC Repository

**Files:**

- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexRepository.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilder.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexJdbcRepository.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilderTest.kt`

- [ ] **Step 1: Write SQL builder tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilderTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run SQL builder test to verify it fails**

Run:

```bash
cd backend
./gradlew :application:test --tests '*RagIndexSqlBuilderTest'
```

Expected: test fails because SQL builder classes do not exist.

- [ ] **Step 3: Add repository port and SQL builder**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexRepository.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import java.time.LocalDate
import java.util.UUID

data class RagRetrievalFilter(
    val memberId: UUID,
    val farmId: UUID? = null,
    val cropId: UUID? = null,
    val workTypeId: UUID? = null,
    val recordId: UUID? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null
)

interface RagIndexRepository {
    fun retrieve(
        embedding: List<Double>,
        filters: RagRetrievalFilter,
        topK: Int
    ): List<RagEvidenceChunk>
}
```

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilder.kt`:

```kotlin
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
        val args = mutableListOf<Any>(vectorToSql(embedding), filters.memberId)
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

        args += topK

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
            args = args.dropLast(1) + args.first() + args.last()
        )
    }
}
```

- [ ] **Step 4: Add JdbcTemplate repository**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexJdbcRepository.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
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
        return jdbcTemplate.query(query.sql, rowMapper(), *query.args.toTypedArray())
    }

    private fun rowMapper() = { rs: ResultSet, _: Int ->
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
```

- [ ] **Step 5: Run repository tests**

Run:

```bash
cd backend
./gradlew :application:test --tests '*RagIndexSqlBuilderTest'
```

Expected: tests pass.

## Task 4: CoachingRagService Orchestration

**Files:**

- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRagServiceTest.kt`

- [ ] **Step 1: Write service tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRagServiceTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CoachingRagServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val chunkId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Test
    fun `answer returns cited response when model follows citation rule`() {
        val service = service(
            chunks = listOf(chunk()),
            answer = "과습 위험은 배수 상태 확인으로 판단해야 합니다. [chunk:$chunkId]"
        )

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(result.answer).contains("과습 위험")
        assertThat(result.audit.status).isEqualTo(RagAuditStatus.PASS)
        assertThat(result.citations).hasSize(1)
        assertThat(result.citations[0].chunkId).isEqualTo(chunkId.toString())
    }

    @Test
    fun `answer retries and falls back when citations are missing`() {
        val chat = FakeChatCompletionClient(
            answers = ArrayDeque(listOf("근거 없는 답변입니다.", "다시 근거 없는 답변입니다."))
        )
        val service = service(chunks = listOf(chunk()), chat = chat)

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(chat.calls).isEqualTo(2)
        assertThat(result.answer).contains("모델 답변이 citation 규칙을 지키지 않아")
        assertThat(result.audit.status).isEqualTo(RagAuditStatus.WARN)
    }

    @Test
    fun `answer returns insufficient evidence response when no chunks are found`() {
        val service = service(chunks = emptyList(), answer = "")

        val result = service.answer(
            CoachingRagCommand(memberId = memberId, question = "과습 위험이 있을까?")
        )

        assertThat(result.answer).contains("현재 자료만으로는 판단할 수 없습니다")
        assertThat(result.audit.warnings).contains("no_retrieved_chunks")
    }

    private fun service(
        chunks: List<RagEvidenceChunk>,
        answer: String = "답변 [chunk:$chunkId]",
        chat: FakeChatCompletionClient = FakeChatCompletionClient(ArrayDeque(listOf(answer)))
    ): CoachingRagService {
        return CoachingRagService(
            embeddingClient = FakeEmbeddingClient(),
            chatCompletionClient = chat,
            ragIndexRepository = FakeRagIndexRepository(chunks),
            promptBuilder = RagPromptBuilder(),
            citationAuditor = RagCitationAuditor(),
            ragProperties = RagProperties()
        )
    }

    private fun chunk() = RagEvidenceChunk(
        id = chunkId,
        sourceType = RagSourceType.FARMING_RECORD,
        sourceId = "00000000-0000-0000-0000-000000000201",
        content = "관수 후 배수 상태를 확인했다.",
        label = "영농일지 관수",
        similarityScore = 0.81
    )

    private class FakeEmbeddingClient : EmbeddingClient {
        override fun embed(input: String, model: String): List<Double> = listOf(0.1, 0.2, 0.3)
    }

    private class FakeChatCompletionClient(
        private val answers: ArrayDeque<String>
    ) : ChatCompletionClient {
        var calls = 0
        override fun complete(messages: List<ChatMessage>, model: String): String {
            calls += 1
            return answers.removeFirst()
        }
    }

    private class FakeRagIndexRepository(
        private val chunks: List<RagEvidenceChunk>
    ) : RagIndexRepository {
        override fun retrieve(
            embedding: List<Double>,
            filters: RagRetrievalFilter,
            topK: Int
        ): List<RagEvidenceChunk> = chunks
    }
}
```

- [ ] **Step 2: Run service tests to verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests '*CoachingRagServiceTest'
```

Expected: tests fail because `CoachingRagService` does not exist.

- [ ] **Step 3: Add service implementation**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.stereotype.Service

@Service
class CoachingRagService(
    private val embeddingClient: EmbeddingClient,
    private val chatCompletionClient: ChatCompletionClient,
    private val ragIndexRepository: RagIndexRepository,
    private val promptBuilder: RagPromptBuilder,
    private val citationAuditor: RagCitationAuditor,
    private val ragProperties: RagProperties
) {
    fun answer(command: CoachingRagCommand): CoachingRagResult {
        val normalizedQuestion = normalizeQuestion(command.question)
        val topK = normalizeTopK(command.topK)
        validatePeriod(command)

        val embedding = embeddingClient.embed(normalizedQuestion, ragProperties.embedding.model)
        if (embedding.size != ragProperties.embedding.dimension) {
            throw BusinessException(ErrorCode.RAG_EMBEDDING_DIMENSION_MISMATCH)
        }

        val chunks = ragIndexRepository.retrieve(
            embedding = embedding,
            filters = command.toRetrievalFilter(),
            topK = topK
        )

        if (chunks.isEmpty()) {
            val audit = RagAuditResult(
                status = RagAuditStatus.WARN,
                warnings = listOf("no_retrieved_chunks"),
                citations = emptyList()
            )
            return CoachingRagResult(
                answer = "현재 자료만으로는 판단할 수 없습니다. 영농일지나 기술문서 색인 상태를 확인해주세요.",
                citations = emptyList(),
                audit = audit,
                model = modelInfo()
            )
        }

        var answerText = chatCompletionClient.complete(
            promptBuilder.buildPrompt(normalizedQuestion, chunks),
            ragProperties.chat.model
        )
        var audit = citationAuditor.audit(
            answerText = answerText,
            retrievedChunks = chunks,
            lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
        )

        if (citationAuditor.shouldRetry(audit)) {
            answerText = chatCompletionClient.complete(
                promptBuilder.buildCitationRetryPrompt(normalizedQuestion, chunks),
                ragProperties.chat.model
            )
            audit = citationAuditor.audit(
                answerText = answerText,
                retrievedChunks = chunks,
                lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
            )
        }

        if (citationAuditor.shouldRetry(audit)) {
            answerText = citationAuditor.buildExtractiveFallbackAnswer(normalizedQuestion, chunks)
            val fallbackAudit = citationAuditor.audit(
                answerText = answerText,
                retrievedChunks = chunks,
                lowSimilarityThreshold = ragProperties.retrieval.lowSimilarityThreshold
            )
            audit = fallbackAudit.copy(
                status = RagAuditStatus.WARN,
                warnings = (fallbackAudit.warnings + "citation_retry_failed_used_extractive_fallback").distinct()
            )
        }

        return CoachingRagResult(
            answer = answerText,
            citations = toCitations(audit, chunks),
            audit = audit,
            model = modelInfo()
        )
    }

    private fun normalizeQuestion(question: String): String {
        val normalized = question.trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return normalized
    }

    private fun normalizeTopK(topK: Int?): Int {
        val value = topK ?: ragProperties.retrieval.topKDefault
        if (value !in 1..ragProperties.retrieval.topKMax) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
        return value
    }

    private fun validatePeriod(command: CoachingRagCommand) {
        if (command.periodStart != null && command.periodEnd != null && command.periodStart.isAfter(command.periodEnd)) {
            throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
        }
    }

    private fun CoachingRagCommand.toRetrievalFilter(): RagRetrievalFilter {
        return RagRetrievalFilter(
            memberId = memberId,
            farmId = farmId,
            cropId = cropId,
            workTypeId = workTypeId,
            recordId = recordId,
            periodStart = periodStart,
            periodEnd = periodEnd
        )
    }

    private fun toCitations(audit: RagAuditResult, chunks: List<RagEvidenceChunk>): List<RagCitation> {
        val chunkById = chunks.associateBy { it.id.toString() }
        return audit.citations.distinct().mapNotNull { citation ->
            chunkById[citation]?.let { chunk ->
                RagCitation(
                    chunkId = citation,
                    sourceType = chunk.sourceType,
                    sourceId = chunk.sourceId,
                    label = chunk.label,
                    similarityScore = chunk.similarityScore
                )
            }
        }
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
```

- [ ] **Step 4: Run application RAG tests**

Run:

```bash
cd backend
./gradlew :application:test --tests '*Rag*Test' --tests '*CoachingRagServiceTest'
```

Expected: application RAG tests pass.

## Task 5: Ollama And OpenClaw Provider Adapters

**Files:**

- Create: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClient.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClient.kt`
- Test: `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClientTest.kt`
- Test: `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClientTest.kt`

- [ ] **Step 1: Write provider adapter tests**

Create `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClientTest.kt`:

```kotlin
package com.godsmove.api.rag.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OllamaEmbeddingClientTest {
    @Test
    fun `embed parses Ollama embeddings response`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OllamaEmbeddingClient(builder, "http://ollama.test")

        server.expect(requestTo("http://ollama.test/api/embed"))
            .andExpect(content().json("""{"model":"bge-m3","input":"관수"}"""))
            .andRespond(withSuccess("""{"embeddings":[[0.1,0.2,0.3]]}""", MediaType.APPLICATION_JSON))

        assertThat(client.embed("관수", "bge-m3")).containsExactly(0.1, 0.2, 0.3)
        server.verify()
    }
}
```

Create `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClientTest.kt`:

```kotlin
package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.ChatMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenClawChatCompletionClientTest {
    @Test
    fun `complete sends OpenClaw agent header and parses content`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OpenClawChatCompletionClient(
            restClientBuilder = builder,
            baseUrl = "http://openclaw.test",
            apiKey = "secret",
            agentId = "agri-rag-coach"
        )

        server.expect(requestTo("http://openclaw.test/v1/chat/completions"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
            .andExpect(header("x-openclaw-agent-id", "agri-rag-coach"))
            .andRespond(withSuccess("""{"choices":[{"message":{"content":"답변 [chunk:c1]"}}]}""", MediaType.APPLICATION_JSON))

        val answer = client.complete(listOf(ChatMessage("user", "질문")), "openclaw/agri-rag-coach")

        assertThat(answer).isEqualTo("답변 [chunk:c1]")
        server.verify()
    }
}
```

- [ ] **Step 2: Run provider tests to verify they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests '*OllamaEmbeddingClientTest' --tests '*OpenClawChatCompletionClientTest'
```

Expected: tests fail because provider adapter classes do not exist.

- [ ] **Step 3: Add Ollama embedding client**

Create `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClient.kt`:

```kotlin
package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.EmbeddingClient
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OllamaEmbeddingClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${ollama.base-url}")
    baseUrl: String
) : EmbeddingClient {
    private val restClient = restClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun embed(input: String, model: String): List<Double> {
        return try {
            val response = restClient.post()
                .uri("/api/embed")
                .body(OllamaEmbedRequest(model = model, input = input))
                .retrieve()
                .body(OllamaEmbedResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)

            response.embeddings?.firstOrNull()
                ?: response.embedding
                ?: throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: Exception) {
            throw BusinessException(ErrorCode.RAG_EMBEDDING_UNAVAILABLE)
        }
    }
}

data class OllamaEmbedRequest(
    val model: String,
    val input: String
)

data class OllamaEmbedResponse(
    val embeddings: List<List<Double>>? = null,
    val embedding: List<Double>? = null
)
```

- [ ] **Step 4: Add OpenClaw chat client**

Create `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClient.kt`:

```kotlin
package com.godsmove.api.rag.client

import com.godsmove.application.coaching.rag.ChatCompletionClient
import com.godsmove.application.coaching.rag.ChatMessage
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OpenClawChatCompletionClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${openclaw.base-url}")
    baseUrl: String,
    @Value("\${openclaw.api-key}")
    private val apiKey: String,
    @Value("\${openclaw.agent-id}")
    private val agentId: String
) : ChatCompletionClient {
    private val restClient = restClientBuilder.baseUrl(baseUrl.trimEnd('/')).build()

    override fun complete(messages: List<ChatMessage>, model: String): String {
        if (apiKey.isBlank() || agentId.isBlank()) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }

        return try {
            val response = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .header("x-openclaw-agent-id", agentId)
                .body(OpenClawChatRequest(model = model, messages = messages, stream = false))
                .retrieve()
                .body(OpenClawChatResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)

            response.choices.firstOrNull()?.message?.content
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: Exception) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }
    }
}

data class OpenClawChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean
)

data class OpenClawChatResponse(
    val choices: List<OpenClawChoice> = emptyList()
)

data class OpenClawChoice(
    val message: OpenClawMessage? = null
)

data class OpenClawMessage(
    val content: String? = null
)
```

- [ ] **Step 5: Run provider tests**

Run:

```bash
cd backend
./gradlew :api:test --tests '*OllamaEmbeddingClientTest' --tests '*OpenClawChatCompletionClientTest'
```

Expected: provider tests pass.

## Task 6: HTTP API And DTO Mapping

**Files:**

- Create: `backend/api/src/main/kotlin/com/godsmove/api/coaching/controller/CoachingRagController.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt`
- Test: `backend/api/src/test/kotlin/com/godsmove/api/coaching/controller/CoachingRagControllerTest.kt`

- [ ] **Step 1: Write controller tests**

Create `backend/api/src/test/kotlin/com/godsmove/api/coaching/controller/CoachingRagControllerTest.kt`:

```kotlin
package com.godsmove.api.coaching.controller

import com.godsmove.api.exception.GlobalExceptionHandler
import com.godsmove.application.coaching.rag.CoachingRagResult
import com.godsmove.application.coaching.rag.CoachingRagService
import com.godsmove.application.coaching.rag.RagAuditResult
import com.godsmove.application.coaching.rag.RagAuditStatus
import com.godsmove.application.coaching.rag.RagModelInfo
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CoachingRagController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class CoachingRagControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var coachingRagService: CoachingRagService

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    fun `query returns RAG answer`() {
        `when`(coachingRagService.answer(any()))
            .thenReturn(
                CoachingRagResult(
                    answer = "답변",
                    citations = emptyList(),
                    audit = RagAuditResult(RagAuditStatus.PASS, emptyList(), emptyList()),
                    model = RagModelInfo("bge-m3", "openclaw/agri-rag-coach")
                )
            )

        mockMvc.perform(
            post("/api/v1/coaching/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"과습 위험이 있을까?","topK":6}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.answer", equalTo("답변")))
            .andExpect(jsonPath("$.data.model.embedding", equalTo("bge-m3")))
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    fun `query rejects blank question`() {
        mockMvc.perform(
            post("/api/v1/coaching/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests '*CoachingRagControllerTest'
```

Expected: tests fail because controller and DTOs do not exist.

- [ ] **Step 3: Add request DTO**

Create `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`:

```kotlin
package com.godsmove.api.coaching.dto

import com.godsmove.application.coaching.rag.CoachingRagCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate
import java.util.UUID

object CoachingRagRequests {
    data class QueryRequest(
        @field:NotBlank(message = "질문을 입력해주세요")
        val question: String,
        val farmId: UUID? = null,
        val cropId: UUID? = null,
        val workTypeId: UUID? = null,
        val recordId: UUID? = null,
        val periodStart: LocalDate? = null,
        val periodEnd: LocalDate? = null,
        @field:Min(value = 1, message = "topK는 1 이상이어야 합니다")
        @field:Max(value = 20, message = "topK는 20 이하여야 합니다")
        val topK: Int? = null
    ) {
        fun toCommand(memberId: UUID): CoachingRagCommand {
            return CoachingRagCommand(
                memberId = memberId,
                question = question,
                farmId = farmId,
                cropId = cropId,
                workTypeId = workTypeId,
                recordId = recordId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                topK = topK
            )
        }
    }
}
```

- [ ] **Step 4: Add response DTO**

Create `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt`:

```kotlin
package com.godsmove.api.coaching.dto

import com.godsmove.application.coaching.rag.CoachingRagResult
import com.godsmove.application.coaching.rag.RagAuditStatus
import com.godsmove.application.coaching.rag.RagSourceType

object CoachingRagResponses {
    data class QueryResponse(
        val answer: String,
        val citations: List<CitationResponse>,
        val audit: AuditResponse,
        val model: ModelResponse
    ) {
        companion object {
            fun from(result: CoachingRagResult): QueryResponse {
                return QueryResponse(
                    answer = result.answer,
                    citations = result.citations.map {
                        CitationResponse(
                            chunkId = it.chunkId,
                            sourceType = it.sourceType,
                            sourceId = it.sourceId,
                            label = it.label,
                            similarityScore = it.similarityScore
                        )
                    },
                    audit = AuditResponse(result.audit.status, result.audit.warnings),
                    model = ModelResponse(result.model.embedding, result.model.chat)
                )
            }
        }
    }

    data class CitationResponse(
        val chunkId: String,
        val sourceType: RagSourceType,
        val sourceId: String,
        val label: String,
        val similarityScore: Double
    )

    data class AuditResponse(
        val status: RagAuditStatus,
        val warnings: List<String>
    )

    data class ModelResponse(
        val embedding: String,
        val chat: String
    )
}
```

- [ ] **Step 5: Add controller**

Create `backend/api/src/main/kotlin/com/godsmove/api/coaching/controller/CoachingRagController.kt`:

```kotlin
package com.godsmove.api.coaching.controller

import com.godsmove.api.coaching.dto.CoachingRagRequests
import com.godsmove.api.coaching.dto.CoachingRagResponses
import com.godsmove.api.common.ApiResponse
import com.godsmove.application.coaching.rag.CoachingRagService
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/coaching/rag")
class CoachingRagController(
    private val coachingRagService: CoachingRagService
) {
    @PostMapping("/query")
    fun query(
        @AuthenticationPrincipal memberId: String,
        @Valid @RequestBody request: CoachingRagRequests.QueryRequest
    ): ResponseEntity<ApiResponse<CoachingRagResponses.QueryResponse>> {
        val parsedMemberId = parseMemberId(memberId)
        val result = coachingRagService.answer(request.toCommand(parsedMemberId))
        return ResponseEntity.ok(ApiResponse.ok(CoachingRagResponses.QueryResponse.from(result)))
    }

    private fun parseMemberId(memberId: String): UUID {
        return try {
            UUID.fromString(memberId)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
```

- [ ] **Step 6: Run controller tests**

Run:

```bash
cd backend
./gradlew :api:test --tests '*CoachingRagControllerTest'
```

Expected: controller tests pass.

## Task 7: Full Verification And Commit

**Files:**

- All files from Tasks 1-6.

- [ ] **Step 1: Run focused RAG test suite**

Run:

```bash
cd backend
./gradlew :application:test --tests '*Rag*Test' --tests '*CoachingRagServiceTest' :api:test --tests '*CoachingRagControllerTest' --tests '*OllamaEmbeddingClientTest' --tests '*OpenClawChatCompletionClientTest'
```

Expected: all focused tests pass.

- [ ] **Step 2: Run backend tests**

Run:

```bash
cd backend
./gradlew test
```

Expected: all backend tests pass. If H2 cannot execute pgvector SQL, no test should require H2 to create or query `vector` columns in this MVP.

- [ ] **Step 3: Confirm no accidental query history tables were added**

Run:

```bash
rg -n "rag_query|rag_answer|rag_query_source" backend docs/superpowers/specs docs/superpowers/plans
```

Expected: only historical PoC/spec discussion references may appear; no implementation SQL or Kotlin class should create query/answer history tables.

- [ ] **Step 4: Confirm secrets are not committed**

Run:

```bash
rg -n "OPENCLAW_API_KEY: [^}$]|Bearer secret|api-key: [A-Za-z0-9]" backend
```

Expected: no committed real secret. Test-only literal `"secret"` is acceptable only in test source.

- [ ] **Step 5: Review git diff**

Run:

```bash
git diff --stat
git diff -- backend/docs/db/rag-index-schema.sql backend/application/src/main/kotlin/com/godsmove/application/coaching/rag backend/api/src/main/kotlin/com/godsmove/api/coaching backend/api/src/main/kotlin/com/godsmove/api/rag/client
```

Expected: diff is limited to RAG schema, application RAG, provider adapters, API DTO/controller, config, and tests.

- [ ] **Step 6: Commit implementation**

Run:

```bash
git add backend/docs/db/rag-index-schema.sql \
  backend/application/src/main/kotlin/com/godsmove/application/coaching/rag \
  backend/application/src/test/kotlin/com/godsmove/application/coaching/rag \
  backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt \
  backend/api/src/main/kotlin/com/godsmove/api/coaching \
  backend/api/src/main/kotlin/com/godsmove/api/rag/client \
  backend/api/src/test/kotlin/com/godsmove/api/coaching \
  backend/api/src/test/kotlin/com/godsmove/api/rag/client \
  backend/api/src/main/resources/application-local.yml \
  backend/api/src/main/resources/application-dev.yml \
  backend/api/src/main/resources/application-prod.yml \
  backend/api/src/test/resources/application-test.yml
git commit \
  -m "feat(rag): 영농 코칭 RAG API 추가" \
  -m "대화식 영농 코칭을 먼저 제공하되 자동 영농일지 코칭과 리포트 코칭이 같은 retrieval, prompt, audit, provider 경계를 재사용할 수 있게 구성했다." \
  -m "Constraint: pgvector schema is kept as explicit SQL until migration tooling is introduced" \
  -m "Constraint: Chat generation uses OpenClaw while embeddings use local Ollama bge-m3" \
  -m "Rejected: Persist query and answer logs in MVP | product scope only needs immediate interactive responses" \
  -m "Rejected: Add vector columns to farming_record | derived search chunks should not pollute source-of-truth domain tables" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: Keep future RECORD_AUTO and REPORT_MANUAL flows on the same CoachingRagService engine" \
  -m "Tested: ./gradlew test" \
  -m "Not-tested: Real PostgreSQL pgvector query and real provider calls"
```

Expected: implementation commit is created without staging unrelated `AGENTS.md` changes.
