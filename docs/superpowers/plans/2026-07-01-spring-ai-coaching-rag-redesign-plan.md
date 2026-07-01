# Spring AI Coaching RAG Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom coaching RAG plumbing with Spring AI `ChatClient`, `VectorStore`, OpenClaw `ChatModel` adapter, structured JSON output, and mode-specific `coaching_feedback` persistence.

**Architecture:** Keep the existing module direction: `api -> application -> domain`. Put Spring AI orchestration, structured output validation, context assembly, and persistence policy in `application`; keep JPA entities and repositories in `domain`; put provider adapters, HTTP DTOs, and Spring AI auto-configuration in `api`.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.4, Spring AI 1.1.8, Spring MVC, Spring Security, Spring Data JPA, Spring AI `ChatClient`, Spring AI PgVector `VectorStore`, Spring AI Ollama embedding model, PostgreSQL pgvector, OpenClaw chat completions, JUnit 5, Mockito, MockMvc.

---

## Scope Check

This plan implements one cohesive subsystem: Spring AI-based farming coaching RAG. It includes dependencies, provider adapter, structured output, retrieval, indexing, persistence, and API response changes because each piece is needed for a working vertical slice.

Spring AI 2.0.0 currently targets Spring Boot 4.x. This backend is on Spring Boot 3.5.4, so the implementation uses the Spring AI 1.1.x stable line and pins `spring-ai-bom:1.1.8`.

Official references used while writing this plan:

- Spring AI dependency management: https://docs.spring.io/spring-ai/reference/getting-started.html
- Spring AI 1.1.8 reference: https://docs.spring.io/spring-ai/reference/1.1/index.html
- ChatClient structured entity output: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Structured output converter: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html
- Retrieval augmentation advisor: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
- PgVector metadata filters: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- Ollama embedding model: https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html

## File Structure

Create:

- `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingMode.kt`: durable coaching mode enum.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResult.kt`: structured model output and validation enums.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingContextProvider.kt`: safe member/farm/crop/record context provider.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidator.kt`: range and citation validation.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackPersistencePolicy.kt`: mode-specific save decision.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactory.kt`: converts records to Spring AI documents.
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilder.kt`: builds Spring AI metadata filter expressions.
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatModel.kt`: Spring AI `ChatModel` adapter for OpenClaw.
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatDtos.kt`: OpenClaw HTTP request/response DTOs.
- `backend/api/src/main/kotlin/com/godsmove/config/OpenClawProperties.kt`: OpenClaw config properties.
- `backend/api/src/main/kotlin/com/godsmove/config/SpringAiRagConfig.kt`: ChatClient and retrieval advisor beans.
- Test files listed in each task.

Modify:

- `backend/application/build.gradle.kts`
- `backend/api/build.gradle.kts`
- `backend/api/src/main/resources/application-local.yml`
- `backend/api/src/main/resources/application-dev.yml`
- `backend/api/src/main/resources/application-prod.yml`
- `backend/api/src/test/resources/application-test.yml`
- `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`
- `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedback.kt`
- `backend/domain/src/main/kotlin/com/godsmove/domain/farming/FarmingRecordFieldValueRepository.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/seed/DevRagSeedService.kt`
- `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`
- `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt`
- `backend/api/src/main/kotlin/com/godsmove/ApiApplication.kt`
- `frontend/dev-rag-test.html`

Delete after replacements pass tests:

- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagClients.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexRepository.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexJdbcRepository.kt`
- `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilder.kt`
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClient.kt`
- `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClient.kt`
- Direct tests for the deleted classes.

## Task 1: Spring AI Dependencies And Configuration

**Files:**

- Modify: `backend/application/build.gradle.kts`
- Modify: `backend/api/build.gradle.kts`
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/main/resources/application-dev.yml`
- Modify: `backend/api/src/main/resources/application-prod.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`
- Modify: `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`

- [ ] **Step 1: Add Spring AI dependency management to application**

In `backend/application/build.gradle.kts`, add the Spring AI BOM and Spring AI core dependency inside `dependencies`:

```kotlin
implementation(platform("org.springframework.ai:spring-ai-bom:1.1.8"))
implementation("org.springframework.ai:spring-ai-core")
```

- [ ] **Step 2: Add Spring AI provider/vector dependencies to api**

In `backend/api/build.gradle.kts`, add these dependencies inside `dependencies`:

```kotlin
implementation(platform("org.springframework.ai:spring-ai-bom:1.1.8"))
implementation("org.springframework.ai:spring-ai-starter-model-ollama")
implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
implementation("org.springframework.boot:spring-boot-starter-jdbc")
```

- [ ] **Step 3: Replace legacy `ollama` RAG config with Spring AI properties in local/test**

In `backend/api/src/main/resources/application-local.yml` and `backend/api/src/test/resources/application-test.yml`, keep the existing `rag` and `openclaw` blocks, remove the top-level `ollama:` block, and add:

```yaml
spring:
  ai:
    model:
      embedding: ollama
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      embedding:
        model: ${RAG_EMBEDDING_MODEL:bge-m3}
    vectorstore:
      pgvector:
        initialize-schema: true
        schema-name: public
        table-name: vector_store
        dimensions: ${RAG_EMBEDDING_DIMENSION:1024}
        distance-type: COSINE_DISTANCE
        index-type: HNSW
```

If these files already have a top-level `spring:` block, merge the `ai:` subtree under the existing `spring:` key instead of creating a duplicate key.

- [ ] **Step 4: Add Spring AI properties in dev/prod**

In `backend/api/src/main/resources/application-dev.yml` and `backend/api/src/main/resources/application-prod.yml`, keep `rag` and `openclaw`, remove the top-level `ollama:` block, and add:

```yaml
spring:
  ai:
    model:
      embedding: ollama
    ollama:
      base-url: ${OLLAMA_BASE_URL}
      embedding:
        model: ${RAG_EMBEDDING_MODEL:bge-m3}
    vectorstore:
      pgvector:
        initialize-schema: false
        schema-name: public
        table-name: vector_store
        dimensions: ${RAG_EMBEDDING_DIMENSION:1024}
        distance-type: COSINE_DISTANCE
        index-type: HNSW
```

- [ ] **Step 5: Add structured output error code**

In `backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt`, add this value after `RAG_EMBEDDING_DIMENSION_MISMATCH`:

```kotlin
RAG_STRUCTURED_OUTPUT_INVALID("RAG_006", "error.rag_structured_output_invalid", 502),
```

- [ ] **Step 6: Compile dependency wiring**

Run:

```bash
cd backend
./gradlew :application:compileKotlin :api:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit dependency/config changes**

Run:

```bash
git add backend/application/build.gradle.kts backend/api/build.gradle.kts backend/api/src/main/resources/application-local.yml backend/api/src/main/resources/application-dev.yml backend/api/src/main/resources/application-prod.yml backend/api/src/test/resources/application-test.yml backend/application/src/main/kotlin/com/godsmove/application/exception/ErrorCode.kt
git commit -m "chore(rag): Spring AI 의존성 추가" -m "Spring AI 1.1.x를 Boot 3.5 라인에 맞춰 도입하고 Ollama embedding 및 PgVectorStore 설정을 표준 프로퍼티로 옮긴다." -m "Constraint: Spring AI 2.0 targets Spring Boot 4.x" -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:compileKotlin :api:compileKotlin" -m "Not-tested: real Ollama or pgvector runtime"
```

## Task 2: Structured Coaching Models And API Shape

**Files:**

- Modify: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt`
- Create: `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingMode.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResult.kt`
- Modify: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt`
- Modify: `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResultTest.kt`

- [ ] **Step 1: Write structured result tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResultTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredResultTest {
    @Test
    fun `insufficient evidence result uses unknown risk and low confidence`() {
        val result = CoachingStructuredResult.insufficientEvidence("자료가 부족합니다.")

        assertThat(result.summary).isEqualTo("자료가 부족합니다.")
        assertThat(result.riskLevel).isEqualTo(CoachingRiskLevel.UNKNOWN)
        assertThat(result.confidence).isEqualTo(0.0)
        assertThat(result.recommendations).isEmpty()
        assertThat(result.followUpQuestions).isNotEmpty()
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingStructuredResultTest
```

Expected: compilation fails because `CoachingStructuredResult` does not exist.

- [ ] **Step 3: Add structured output model**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResult.kt`:

```kotlin
package com.godsmove.application.coaching.rag

enum class CoachingRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

enum class CoachingPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class CoachingActionDue {
    IMMEDIATE,
    TODAY,
    THIS_WEEK,
    NEXT_CHECK
}

data class CoachingStructuredResult(
    val summary: String,
    val riskLevel: CoachingRiskLevel,
    val confidence: Double,
    val observations: List<CoachingObservation>,
    val diagnosis: String,
    val recommendations: List<CoachingRecommendation>,
    val nextActions: List<CoachingNextAction>,
    val followUpQuestions: List<String>,
    val citations: List<CoachingCitationRef>
) {
    companion object {
        fun insufficientEvidence(message: String): CoachingStructuredResult {
            return CoachingStructuredResult(
                summary = message,
                riskLevel = CoachingRiskLevel.UNKNOWN,
                confidence = 0.0,
                observations = emptyList(),
                diagnosis = message,
                recommendations = emptyList(),
                nextActions = emptyList(),
                followUpQuestions = listOf("최근 영농일지나 작물 상태 정보를 추가로 입력해주세요."),
                citations = emptyList()
            )
        }
    }
}

data class CoachingObservation(
    val title: String,
    val detail: String,
    val citationIds: List<String>
)

data class CoachingRecommendation(
    val priority: CoachingPriority,
    val action: String,
    val reason: String,
    val caution: String? = null,
    val citationIds: List<String>
)

data class CoachingNextAction(
    val due: CoachingActionDue,
    val action: String,
    val citationIds: List<String>
)

data class CoachingCitationRef(
    val chunkId: String,
    val label: String,
    val sourceType: RagSourceType
)
```

- [ ] **Step 4: Add durable coaching mode enum**

Create `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingMode.kt`:

```kotlin
package com.godsmove.domain.coaching

enum class CoachingMode {
    CHAT,
    RECORD_AUTO,
    REPORT_MANUAL
}
```

- [ ] **Step 5: Update command mode**

Replace `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt` with:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import java.time.LocalDate
import java.util.UUID

data class CoachingRagCommand(
    val memberId: UUID,
    val mode: CoachingMode = CoachingMode.CHAT,
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

- [ ] **Step 6: Update application result model**

Replace `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt` with:

```kotlin
package com.godsmove.application.coaching.rag

import java.util.UUID

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
    val result: CoachingStructuredResult,
    val audit: RagAuditResult,
    val model: RagModelInfo,
    val savedFeedbackId: UUID? = null
)
```

- [ ] **Step 7: Update request DTO for mode**

In `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt`, add `mode` to `QueryRequest` and map it:

```kotlin
val mode: CoachingMode = CoachingMode.CHAT,
```

Add the import:

```kotlin
import com.godsmove.domain.coaching.CoachingMode
```

Set the command field:

```kotlin
mode = mode,
```

- [ ] **Step 8: Replace response DTO with structured shape**

Replace `backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt` with:

```kotlin
package com.godsmove.api.coaching.dto

import com.godsmove.application.coaching.rag.CoachingActionDue
import com.godsmove.application.coaching.rag.CoachingPriority
import com.godsmove.application.coaching.rag.CoachingRagResult
import com.godsmove.application.coaching.rag.CoachingRiskLevel
import com.godsmove.application.coaching.rag.RagAuditStatus
import com.godsmove.application.coaching.rag.RagSourceType
import java.util.UUID

object CoachingRagResponses {
    data class QueryResponse(
        val result: StructuredResultResponse,
        val audit: AuditResponse,
        val model: ModelResponse,
        val savedFeedbackId: UUID?
    ) {
        companion object {
            fun from(result: CoachingRagResult): QueryResponse {
                return QueryResponse(
                    result = StructuredResultResponse.from(result.result),
                    audit = AuditResponse(result.audit.status, result.audit.warnings),
                    model = ModelResponse(result.model.embedding, result.model.chat),
                    savedFeedbackId = result.savedFeedbackId
                )
            }
        }
    }

    data class StructuredResultResponse(
        val summary: String,
        val riskLevel: CoachingRiskLevel,
        val confidence: Double,
        val observations: List<ObservationResponse>,
        val diagnosis: String,
        val recommendations: List<RecommendationResponse>,
        val nextActions: List<NextActionResponse>,
        val followUpQuestions: List<String>,
        val citations: List<CitationResponse>
    ) {
        companion object {
            fun from(result: com.godsmove.application.coaching.rag.CoachingStructuredResult): StructuredResultResponse {
                return StructuredResultResponse(
                    summary = result.summary,
                    riskLevel = result.riskLevel,
                    confidence = result.confidence,
                    observations = result.observations.map { ObservationResponse(it.title, it.detail, it.citationIds) },
                    diagnosis = result.diagnosis,
                    recommendations = result.recommendations.map {
                        RecommendationResponse(it.priority, it.action, it.reason, it.caution, it.citationIds)
                    },
                    nextActions = result.nextActions.map { NextActionResponse(it.due, it.action, it.citationIds) },
                    followUpQuestions = result.followUpQuestions,
                    citations = result.citations.map { CitationResponse(it.chunkId, it.label, it.sourceType) }
                )
            }
        }
    }

    data class ObservationResponse(
        val title: String,
        val detail: String,
        val citationIds: List<String>
    )

    data class RecommendationResponse(
        val priority: CoachingPriority,
        val action: String,
        val reason: String,
        val caution: String?,
        val citationIds: List<String>
    )

    data class NextActionResponse(
        val due: CoachingActionDue,
        val action: String,
        val citationIds: List<String>
    )

    data class CitationResponse(
        val chunkId: String,
        val label: String,
        val sourceType: RagSourceType
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

- [ ] **Step 9: Run structured model test**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingStructuredResultTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit structured model changes**

Run after tests pass:

```bash
git add backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingMode.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagCommand.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResult.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagResult.kt backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagRequests.kt backend/api/src/main/kotlin/com/godsmove/api/coaching/dto/CoachingRagResponses.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredResultTest.kt
git commit -m "feat(rag): 구조화 코칭 응답 모델 추가" -m "OpenClaw 응답을 자연어 문자열이 아니라 위험도, 진단, 권장 작업, 출처를 포함한 구조화 결과로 다루기 위한 application/API 모델을 추가한다." -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingStructuredResultTest" -m "Not-tested: controller response serialization"
```

## Task 3: Redesign CoachingFeedback Persistence Model

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedback.kt`
- Modify: `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedbackRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/godsmove/domain/coaching/CoachingFeedbackMappingTest.kt`

- [ ] **Step 1: Replace CoachingFeedback entity**

Replace `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedback.kt` with:

```kotlin
package com.godsmove.domain.coaching

import com.godsmove.domain.common.BaseTimeEntity
import com.godsmove.domain.crop.Crop
import com.godsmove.domain.farm.Farm
import com.godsmove.domain.farming.FarmingRecord
import com.godsmove.domain.member.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "coaching_feedback")
class CoachingFeedback(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Enumerated(EnumType.STRING)
    @Column(name = "coaching_mode", nullable = false, length = 32)
    val coachingMode: CoachingMode,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    val record: FarmingRecord? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    val farm: Farm? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    val crop: Crop? = null,

    @Column(nullable = false, columnDefinition = "text")
    val question: String,

    @Column(name = "period_starts_on")
    val periodStartsOn: LocalDate? = null,

    @Column(name = "period_ends_on")
    val periodEndsOn: LocalDate? = null,

    @Column(nullable = false, columnDefinition = "text")
    val summary: String,

    @Column(name = "risk_level", nullable = false, length = 32)
    val riskLevel: String,

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    val confidenceScore: BigDecimal,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_result", nullable = false, columnDefinition = "jsonb")
    val structuredResult: Map<String, Any?>,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val citations: List<Map<String, Any?>>,

    @Column(name = "audit_status", nullable = false, length = 32)
    val auditStatus: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_warnings", nullable = false, columnDefinition = "jsonb")
    val auditWarnings: List<String>,

    @Column(name = "model_name", nullable = false, length = 128)
    val modelName: String,

    @Column(name = "embedding_model", nullable = false, length = 128)
    val embeddingModel: String,
) : BaseTimeEntity()
```

- [ ] **Step 2: Add repository queries for mode-specific history**

Replace `backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedbackRepository.kt` with:

```kotlin
package com.godsmove.domain.coaching

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachingFeedbackRepository : JpaRepository<CoachingFeedback, UUID> {
    fun findTop20ByMemberIdOrderByCreatedAtDesc(memberId: UUID): List<CoachingFeedback>

    fun findByRecordIdOrderByCreatedAtDesc(recordId: UUID): List<CoachingFeedback>
}
```

- [ ] **Step 3: Write persistence policy test**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackPersistencePolicyTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CoachingFeedbackPersistencePolicyTest {
    private val policy = CoachingFeedbackPersistencePolicy()

    @Test
    fun `chat is not saved by default`() {
        val command = CoachingRagCommand(
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            mode = CoachingMode.CHAT,
            question = "지금 물 줘도 돼?"
        )

        assertThat(policy.shouldSave(command)).isFalse()
    }

    @Test
    fun `record auto and report manual are saved`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        assertThat(policy.shouldSave(CoachingRagCommand(memberId, CoachingMode.RECORD_AUTO, "자동 코칭"))).isTrue()
        assertThat(policy.shouldSave(CoachingRagCommand(memberId, CoachingMode.REPORT_MANUAL, "리포트 코칭"))).isTrue()
    }
}
```

- [ ] **Step 4: Add persistence policy**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackPersistencePolicy.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.springframework.stereotype.Component

@Component
class CoachingFeedbackPersistencePolicy {
    fun shouldSave(command: CoachingRagCommand): Boolean {
        return when (command.mode) {
            CoachingMode.CHAT -> false
            CoachingMode.RECORD_AUTO,
            CoachingMode.REPORT_MANUAL -> true
        }
    }
}
```

- [ ] **Step 5: Run domain/application tests**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingFeedbackPersistencePolicyTest :domain:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit persistence model**

Run:

```bash
git add backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedback.kt backend/domain/src/main/kotlin/com/godsmove/domain/coaching/CoachingFeedbackRepository.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackPersistencePolicy.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackPersistencePolicyTest.kt
git commit -m "feat(rag): 코칭 결과 저장 모델 재설계" -m "구조화 AI 결과를 jsonb로 저장하고 CHAT은 미저장, RECORD_AUTO와 REPORT_MANUAL은 저장하는 정책을 명시한다." -m "Constraint: Development schema can be rebuilt" -m "Confidence: medium" -m "Scope-risk: broad" -m "Tested: ./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingFeedbackPersistencePolicyTest :domain:compileKotlin" -m "Not-tested: PostgreSQL jsonb persistence"
```

## Task 4: OpenClaw Spring AI ChatModel Adapter

**Files:**

- Create: `backend/api/src/main/kotlin/com/godsmove/config/OpenClawProperties.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatDtos.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatModel.kt`
- Create: `backend/api/src/main/kotlin/com/godsmove/config/SpringAiRagConfig.kt`
- Modify: `backend/api/src/main/kotlin/com/godsmove/ApiApplication.kt`
- Test: `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatModelTest.kt`

- [ ] **Step 1: Add OpenClaw properties**

Create `backend/api/src/main/kotlin/com/godsmove/config/OpenClawProperties.kt`:

```kotlin
package com.godsmove.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openclaw")
data class OpenClawProperties(
    val baseUrl: String = "http://127.0.0.1:18789",
    val apiKey: String = "",
    val agentId: String = "agri-rag-coach",
    val model: String = "openclaw/agri-rag-coach"
)
```

- [ ] **Step 2: Register OpenClaw properties**

In `backend/api/src/main/kotlin/com/godsmove/ApiApplication.kt`, update `@EnableConfigurationProperties`:

```kotlin
@EnableConfigurationProperties(RagProperties::class, OpenClawProperties::class)
```

Add the import:

```kotlin
import com.godsmove.config.OpenClawProperties
```

- [ ] **Step 3: Add OpenClaw DTOs**

Create `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatDtos.kt`:

```kotlin
package com.godsmove.api.rag.client

data class OpenClawCompletionRequest(
    val model: String,
    val messages: List<OpenClawCompletionMessage>,
    val stream: Boolean = false
)

data class OpenClawCompletionMessage(
    val role: String,
    val content: String
)

data class OpenClawCompletionResponse(
    val choices: List<OpenClawCompletionChoice> = emptyList()
)

data class OpenClawCompletionChoice(
    val message: OpenClawCompletionMessage? = null
)
```

- [ ] **Step 4: Write OpenClaw adapter HTTP test**

Create `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatModelTest.kt`:

```kotlin
package com.godsmove.api.rag.client

import com.godsmove.config.OpenClawProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class OpenClawChatModelTest {
    @Test
    fun `call sends auth and agent headers to OpenClaw`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val model = OpenClawChatModel(
            restClientBuilder = builder,
            properties = OpenClawProperties(
                baseUrl = "http://openclaw.test",
                apiKey = "test-key",
                agentId = "agent-1",
                model = "openclaw/agri-rag-coach"
            )
        )

        server.expect(requestTo("http://openclaw.test/v1/chat/completions"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(header("x-openclaw-agent-id", "agent-1"))
            .andExpect(content().json("""{"model":"openclaw/agri-rag-coach","messages":[{"role":"user","content":"안녕"}],"stream":false}"""))
            .andRespond(withSuccess("""{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"ok\"}"}}]}""", MediaType.APPLICATION_JSON))

        val response = model.call(Prompt(UserMessage("안녕")))

        assertThat(response.result.output.text).contains("summary")
        server.verify()
    }
}
```

- [ ] **Step 5: Implement OpenClawChatModel**

Create `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatModel.kt`:

```kotlin
package com.godsmove.api.rag.client

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.config.OpenClawProperties
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class OpenClawChatModel(
    restClientBuilder: RestClient.Builder,
    private val properties: OpenClawProperties
) : ChatModel {
    private val restClient = restClientBuilder.baseUrl(properties.baseUrl.trimEnd('/')).build()

    override fun call(prompt: Prompt): ChatResponse {
        if (properties.apiKey.isBlank() || properties.agentId.isBlank()) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }

        return try {
            val response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .header("x-openclaw-agent-id", properties.agentId)
                .body(OpenClawCompletionRequest(properties.model, prompt.instructions.map(Message::toOpenClawMessage)))
                .retrieve()
                .body(OpenClawCompletionResponse::class.java)
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)

            val content = response.choices.firstOrNull()?.message?.content
                ?: throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
            ChatResponse(listOf(Generation(AssistantMessage(content))))
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RestClientException) {
            throw BusinessException(ErrorCode.RAG_CHAT_UNAVAILABLE)
        }
    }

    private fun Message.toOpenClawMessage(): OpenClawCompletionMessage {
        return OpenClawCompletionMessage(role = messageType.value, content = text)
    }
}
```

- [ ] **Step 6: Add ChatClient bean**

Create `backend/api/src/main/kotlin/com/godsmove/config/SpringAiRagConfig.kt`:

```kotlin
package com.godsmove.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringAiRagConfig {
    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.create(chatModel)
    }
}
```

- [ ] **Step 7: Run adapter test**

Run:

```bash
cd backend
./gradlew :api:test --tests com.godsmove.api.rag.client.OpenClawChatModelTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit OpenClaw adapter**

Run:

```bash
git add backend/api/src/main/kotlin/com/godsmove/config/OpenClawProperties.kt backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatDtos.kt backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatModel.kt backend/api/src/main/kotlin/com/godsmove/config/SpringAiRagConfig.kt backend/api/src/main/kotlin/com/godsmove/ApiApplication.kt backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatModelTest.kt
git commit -m "feat(rag): OpenClaw ChatModel 어댑터 추가" -m "OpenClaw를 Spring AI ChatClient 경로 안에서 사용할 수 있도록 ChatModel 어댑터와 설정을 추가한다." -m "Constraint: OpenClaw requires x-openclaw-agent-id" -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :api:test --tests com.godsmove.api.rag.client.OpenClawChatModelTest" -m "Not-tested: real OpenClaw server"
```

## Task 5: Coaching Context, Document Factory, And Metadata Filters

**Files:**

- Modify: `backend/domain/src/main/kotlin/com/godsmove/domain/farming/FarmingRecordFieldValueRepository.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingContextProvider.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactory.kt`
- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilder.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilderTest.kt`

- [ ] **Step 1: Add field-value lookup method**

Replace `backend/domain/src/main/kotlin/com/godsmove/domain/farming/FarmingRecordFieldValueRepository.kt` with:

```kotlin
package com.godsmove.domain.farming

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FarmingRecordFieldValueRepository : JpaRepository<FarmingRecordFieldValue, UUID> {
    fun findByRecord_Id(recordId: UUID): List<FarmingRecordFieldValue>
}
```

- [ ] **Step 2: Write document factory test**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactoryTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class FarmingRecordDocumentFactoryTest {
    @Test
    fun `build creates document content and metadata for farming record`() {
        val factory = FarmingRecordDocumentFactory()
        val record = IndexedFarmingRecord(
            recordId = UUID.fromString("40000000-0000-0000-0000-000000000001"),
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
            farmId = UUID.fromString("10000000-0000-0000-0000-000000000042"),
            cropId = UUID.fromString("20000000-0000-0000-0000-000000000042"),
            workTypeId = UUID.fromString("30000000-0000-0000-0000-000000000002"),
            memberName = "박민서",
            memberRegion = "강원특별자치도 평창군 진부면",
            farmName = "하늘들 약초농장",
            cropName = "참당귀",
            workTypeName = "관수",
            workedAt = LocalDateTime.of(2026, 6, 22, 7, 50),
            memo = "점적 관수를 진행했다.",
            fieldValues = listOf("관수 시간: 55분")
        )

        val document = factory.build(record)

        assertThat(document.text).contains("농업인: 박민서")
        assertThat(document.text).contains("영농일지: 점적 관수를 진행했다.")
        assertThat(document.metadata["label"]).isEqualTo("관수 2026-06-22")
        assertThat(document.metadata["memberId"]).isEqualTo(record.memberId.toString())
        assertThat(document.metadata["sourceType"]).isEqualTo("FARMING_RECORD")
    }
}
```

- [ ] **Step 3: Add document factory**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactory.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.ai.document.Document
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class IndexedFarmingRecord(
    val recordId: UUID,
    val memberId: UUID,
    val farmId: UUID,
    val cropId: UUID,
    val workTypeId: UUID,
    val memberName: String?,
    val memberRegion: String?,
    val farmName: String,
    val cropName: String,
    val workTypeName: String,
    val workedAt: LocalDateTime,
    val memo: String?,
    val fieldValues: List<String>
)

@Component
class FarmingRecordDocumentFactory {
    fun build(record: IndexedFarmingRecord): Document {
        val workedOn = record.workedAt.toLocalDate()
        val content = buildString {
            appendLine("농업인: ${record.memberName ?: "미입력"}")
            appendLine("지역: ${record.memberRegion ?: "미입력"}")
            appendLine("농장: ${record.farmName}")
            appendLine("작물: ${record.cropName}")
            appendLine("작업일시: ${record.workedAt}")
            appendLine("작업유형: ${record.workTypeName}")
            appendLine("영농일지: ${record.memo ?: "기록 없음"}")
            if (record.fieldValues.isNotEmpty()) {
                appendLine("세부 항목:")
                record.fieldValues.forEach { appendLine("- $it") }
            }
        }.trim()

        return Document(
            record.recordId.toString(),
            content,
            mapOf(
                "label" to "${record.workTypeName} $workedOn",
                "sourceType" to RagSourceType.FARMING_RECORD.name,
                "sourceId" to record.recordId.toString(),
                "memberId" to record.memberId.toString(),
                "farmId" to record.farmId.toString(),
                "cropId" to record.cropId.toString(),
                "workTypeId" to record.workTypeId.toString(),
                "recordId" to record.recordId.toString(),
                "workedAt" to workedOn.toString(),
                "workedAtEpochDay" to workedOn.toEpochDay(),
                "workedAtEpochSecond" to record.workedAt.toEpochSecond(ZoneOffset.UTC)
            )
        )
    }
}
```

- [ ] **Step 4: Write metadata filter builder test**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilderTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class CoachingRetrievalFilterBuilderTest {
    private val builder = CoachingRetrievalFilterBuilder()

    @Test
    fun `build includes shared docs and current member records`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val cropId = UUID.fromString("20000000-0000-0000-0000-000000000042")

        val filter = builder.build(
            CoachingRagCommand(
                memberId = memberId,
                mode = CoachingMode.CHAT,
                question = "과습 위험?",
                cropId = cropId,
                periodStart = LocalDate.parse("2026-06-01"),
                periodEnd = LocalDate.parse("2026-06-30")
            )
        )

        assertThat(filter).contains("sourceType == 'TECH_DOCUMENT'")
        assertThat(filter).contains("memberId == '$memberId'")
        assertThat(filter).contains("cropId == '$cropId'")
        assertThat(filter).contains("workedAtEpochDay >= 20605")
        assertThat(filter).contains("workedAtEpochDay <= 20634")
    }
}
```

- [ ] **Step 5: Add metadata filter builder**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilder.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class CoachingRetrievalFilterBuilder {
    fun build(command: CoachingRagCommand): String {
        val farmingConditions = mutableListOf(
            "sourceType == '${RagSourceType.FARMING_RECORD.name}'",
            "memberId == '${command.memberId}'"
        )

        command.farmId?.let { farmingConditions += "farmId == '$it'" }
        command.cropId?.let { farmingConditions += "cropId == '$it'" }
        command.workTypeId?.let { farmingConditions += "workTypeId == '$it'" }
        command.recordId?.let { farmingConditions += "recordId == '$it'" }
        command.periodStart?.let { farmingConditions += "workedAtEpochDay >= ${it.toEpochDay()}" }
        command.periodEnd?.let { farmingConditions += "workedAtEpochDay <= ${it.toEpochDay()}" }

        return "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' || (${farmingConditions.joinToString(" && ")})"
    }
}
```

- [ ] **Step 6: Add context provider skeleton**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingContextProvider.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.crop.CropRepository
import com.godsmove.domain.farm.FarmRepository
import com.godsmove.domain.farming.FarmingRecordRepository
import com.godsmove.domain.member.MemberRepository
import org.springframework.stereotype.Component

data class CoachingContext(
    val text: String
)

@Component
class CoachingContextProvider(
    private val memberRepository: MemberRepository,
    private val farmRepository: FarmRepository,
    private val cropRepository: CropRepository,
    private val farmingRecordRepository: FarmingRecordRepository
) {
    fun build(command: CoachingRagCommand): CoachingContext {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val farm = command.farmId?.let { farmRepository.findById(it).orElse(null) }
        val crop = command.cropId?.let { cropRepository.findById(it).orElse(null) }
        val record = command.recordId?.let { farmingRecordRepository.findById(it).orElse(null) }

        return CoachingContext(
            text = buildString {
                appendLine("사용자 재배 context:")
                appendLine("- 지역: ${member.region ?: "미입력"}")
                appendLine("- 영농 경력: ${member.experienceLevel ?: "미입력"}")
                appendLine("- 경영체 등록: ${member.managementType}")
                farm?.let { appendLine("- 농장: ${it.name} (${it.region} ${it.city})") }
                crop?.let { appendLine("- 작물: ${it.name} / ${it.category} / 기본 단위 ${it.defaultUnit}") }
                record?.let { appendLine("- 기준 영농일지: ${it.workedAt} ${it.workType.name}") }
                command.periodStart?.let { appendLine("- 기간 시작: $it") }
                command.periodEnd?.let { appendLine("- 기간 종료: $it") }
            }.trim()
        )
    }
}
```

- [ ] **Step 7: Run document/filter tests**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.FarmingRecordDocumentFactoryTest --tests com.godsmove.application.coaching.rag.CoachingRetrievalFilterBuilderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit context and metadata filtering**

Run:

```bash
git add backend/domain/src/main/kotlin/com/godsmove/domain/farming/FarmingRecordFieldValueRepository.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingContextProvider.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactory.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilder.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/FarmingRecordDocumentFactoryTest.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRetrievalFilterBuilderTest.kt
git commit -m "feat(rag): 코칭 문서 메타데이터 필터 구성" -m "영농일지를 Spring AI Document로 변환하고 회원 격리, 작물, 농장, 작업일, 작업유형 필터를 metadata expression으로 구성한다." -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:test --tests com.godsmove.application.coaching.rag.FarmingRecordDocumentFactoryTest --tests com.godsmove.application.coaching.rag.CoachingRetrievalFilterBuilderTest" -m "Not-tested: actual PgVector metadata filter execution"
```

## Task 6: Structured Output Validation And Spring AI Service Orchestration

**Files:**

- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingRagServiceTest.kt`

- [ ] **Step 1: Write structured output validator tests**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredOutputValidatorTest {
    private val validator = CoachingStructuredOutputValidator()

    @Test
    fun `invalid confidence fails audit`() {
        val result = CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.MEDIUM,
            confidence = 1.4,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )

        val audit = validator.validate(result, emptySet())

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("invalid_confidence")
    }

    @Test
    fun `unknown citation fails audit`() {
        val result = CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.LOW,
            confidence = 0.7,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = listOf(
                CoachingRecommendation(CoachingPriority.HIGH, "관수 중단", "과습", null, listOf("missing"))
            ),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )

        val audit = validator.validate(result, setOf("known"))

        assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
        assertThat(audit.warnings).contains("unknown_citation:missing")
    }
}
```

- [ ] **Step 2: Add structured output validator**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidator.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component

@Component
class CoachingStructuredOutputValidator {
    fun validate(result: CoachingStructuredResult, allowedCitationIds: Set<String>): RagAuditResult {
        val warnings = mutableListOf<String>()

        if (result.confidence !in 0.0..1.0) warnings += "invalid_confidence"

        val citationIds = buildList {
            result.observations.forEach { addAll(it.citationIds) }
            result.recommendations.forEach { addAll(it.citationIds) }
            result.nextActions.forEach { addAll(it.citationIds) }
            result.citations.forEach { add(it.chunkId) }
        }

        result.recommendations
            .filter { it.citationIds.isEmpty() && result.riskLevel != CoachingRiskLevel.UNKNOWN }
            .forEach { warnings += "recommendation_without_citation:${it.action}" }

        result.nextActions
            .filter { it.citationIds.isEmpty() && result.riskLevel != CoachingRiskLevel.UNKNOWN }
            .forEach { warnings += "next_action_without_citation:${it.action}" }

        citationIds.distinct().forEach { citationId ->
            if (citationId !in allowedCitationIds) warnings += "unknown_citation:$citationId"
        }

        val failure = warnings.any {
            it == "invalid_confidence" ||
                it.startsWith("unknown_citation:") ||
                it.startsWith("recommendation_without_citation:") ||
                it.startsWith("next_action_without_citation:")
        }

        return RagAuditResult(
            status = if (failure) RagAuditStatus.FAIL else if (warnings.isEmpty()) RagAuditStatus.PASS else RagAuditStatus.WARN,
            warnings = warnings.distinct(),
            citations = citationIds.distinct()
        )
    }
}
```

- [ ] **Step 3: Run validator tests**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingStructuredOutputValidatorTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Replace CoachingRagService orchestration**

Replace `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt` with:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CoachingRagService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextProvider: CoachingContextProvider,
    private val filterBuilder: CoachingRetrievalFilterBuilder,
    private val validator: CoachingStructuredOutputValidator,
    private val persistencePolicy: CoachingFeedbackPersistencePolicy,
    private val ragProperties: RagProperties
) {
    @Transactional
    fun answer(command: CoachingRagCommand): CoachingRagResult {
        val normalizedQuestion = normalizeQuestion(command.question)
        val topK = normalizeTopK(command.topK)
        validatePeriod(command)

        val filterExpression = filterBuilder.build(command)
        val retrievedDocuments = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(normalizedQuestion)
                .topK(topK)
                .filterExpression(filterExpression)
                .build()
        )

        if (retrievedDocuments.isEmpty()) {
            val result = CoachingStructuredResult.insufficientEvidence("현재 자료만으로는 판단할 수 없습니다. 영농일지나 기술문서 색인 상태를 확인해주세요.")
            return CoachingRagResult(
                result = result,
                audit = RagAuditResult(RagAuditStatus.WARN, listOf("no_retrieved_documents"), emptyList()),
                model = modelInfo()
            )
        }

        val context = contextProvider.build(command)
        val advisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(
                VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .topK(topK)
                    .build()
            )
            .build()

        val result = try {
            chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt(normalizedQuestion, context))
                .advisors(advisor)
                .advisors { spec -> spec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression) }
                .call()
                .entity(CoachingStructuredResult::class.java)
        } catch (_: RuntimeException) {
            throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
        }

        val allowedCitationIds = retrievedDocuments.map { it.id }.toSet()
        val audit = validator.validate(result, allowedCitationIds)
        val savedFeedbackId = if (persistencePolicy.shouldSave(command)) {
            null
        } else {
            null
        }

        return CoachingRagResult(
            result = result,
            audit = audit,
            model = modelInfo(),
            savedFeedbackId = savedFeedbackId
        )
    }

    private fun normalizeQuestion(question: String): String {
        val normalized = question.trim()
        if (normalized.isBlank()) throw BusinessException(ErrorCode.RAG_INVALID_REQUEST)
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

    private fun systemPrompt(): String {
        return """
            너는 농업 영농 코칭 보조자다.
            반드시 제공된 재배 context와 검색 근거만 사용한다.
            근거가 부족하면 riskLevel은 UNKNOWN, confidence는 0.3 이하로 둔다.
            모든 권장 작업과 다음 행동에는 근거 citationIds를 포함한다.
            응답은 요청된 JSON schema만 따른다.
        """.trimIndent()
    }

    private fun userPrompt(question: String, context: CoachingContext): String {
        return """
            질문:
            $question

            ${context.text}
        """.trimIndent()
    }

    private fun modelInfo(): RagModelInfo {
        return RagModelInfo(
            embedding = ragProperties.embedding.model,
            chat = ragProperties.chat.model
        )
    }
}
```

The `savedFeedbackId` remains `null` in this task because Task 7 adds persistence wiring.

- [ ] **Step 5: Compile service**

Run:

```bash
cd backend
./gradlew :application:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit validator and service shell**

Run:

```bash
git add backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidator.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingStructuredOutputValidatorTest.kt
git commit -m "feat(rag): Spring AI 코칭 오케스트레이션 적용" -m "ChatClient, VectorStore, RetrievalAugmentationAdvisor를 사용해 구조화 코칭 결과를 생성하고 citation 검증 경로를 추가한다." -m "Confidence: medium" -m "Scope-risk: broad" -m "Tested: ./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingStructuredOutputValidatorTest && ./gradlew :application:compileKotlin" -m "Not-tested: provider-backed RAG answer"
```

## Task 7: Persist Structured Results For Durable Modes

**Files:**

- Create: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapper.kt`
- Modify: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt`
- Test: `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapperTest.kt`

- [ ] **Step 1: Write feedback mapper test**

Create `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapperTest.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import com.godsmove.domain.coaching.CoachingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CoachingFeedbackMapperTest {
    @Test
    fun `structured result is converted to json maps`() {
        val mapper = CoachingFeedbackMapper()
        val result = CoachingStructuredResult.insufficientEvidence("부족")
        val command = CoachingRagCommand(
            memberId = UUID.fromString("00000000-0000-0000-0000-000000000042"),
            mode = CoachingMode.REPORT_MANUAL,
            question = "리포트"
        )

        val mapped = mapper.toStructuredMap(command, result)

        assertThat(mapped["summary"]).isEqualTo("부족")
        assertThat(mapped["riskLevel"]).isEqualTo("UNKNOWN")
        assertThat(mapped["question"]).isEqualTo("리포트")
    }
}
```

- [ ] **Step 2: Add feedback mapper**

Create `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapper.kt`:

```kotlin
package com.godsmove.application.coaching.rag

import org.springframework.stereotype.Component
import java.math.BigDecimal

data class CoachingFeedbackJsonPayload(
    val structuredResult: Map<String, Any?>,
    val citations: List<Map<String, Any?>>,
    val confidenceScore: BigDecimal
)

@Component
class CoachingFeedbackMapper {
    fun toPayload(command: CoachingRagCommand, result: CoachingStructuredResult): CoachingFeedbackJsonPayload {
        return CoachingFeedbackJsonPayload(
            structuredResult = toStructuredMap(command, result),
            citations = result.citations.map {
                mapOf(
                    "chunkId" to it.chunkId,
                    "label" to it.label,
                    "sourceType" to it.sourceType.name
                )
            },
            confidenceScore = BigDecimal.valueOf(result.confidence).setScale(4)
        )
    }

    fun toStructuredMap(command: CoachingRagCommand, result: CoachingStructuredResult): Map<String, Any?> {
        return mapOf(
            "question" to command.question,
            "mode" to command.mode.name,
            "summary" to result.summary,
            "riskLevel" to result.riskLevel.name,
            "confidence" to result.confidence,
            "observations" to result.observations.map {
                mapOf("title" to it.title, "detail" to it.detail, "citationIds" to it.citationIds)
            },
            "diagnosis" to result.diagnosis,
            "recommendations" to result.recommendations.map {
                mapOf(
                    "priority" to it.priority.name,
                    "action" to it.action,
                    "reason" to it.reason,
                    "caution" to it.caution,
                    "citationIds" to it.citationIds
                )
            },
            "nextActions" to result.nextActions.map {
                mapOf("due" to it.due.name, "action" to it.action, "citationIds" to it.citationIds)
            },
            "followUpQuestions" to result.followUpQuestions,
            "citations" to result.citations.map {
                mapOf("chunkId" to it.chunkId, "label" to it.label, "sourceType" to it.sourceType.name)
            }
        )
    }
}
```

- [ ] **Step 3: Update CoachingRagService dependencies**

Add these constructor dependencies to `CoachingRagService`:

```kotlin
private val feedbackRepository: com.godsmove.domain.coaching.CoachingFeedbackRepository,
private val memberRepository: com.godsmove.domain.member.MemberRepository,
private val farmRepository: com.godsmove.domain.farm.FarmRepository,
private val cropRepository: com.godsmove.domain.crop.CropRepository,
private val farmingRecordRepository: com.godsmove.domain.farming.FarmingRecordRepository,
private val feedbackMapper: CoachingFeedbackMapper,
```

- [ ] **Step 4: Replace savedFeedbackId non-persistence block**

In `CoachingRagService.answer`, replace the non-persistence block:

```kotlin
val savedFeedbackId = if (persistencePolicy.shouldSave(command)) {
    null
} else {
    null
}
```

with:

```kotlin
val savedFeedbackId = if (persistencePolicy.shouldSave(command)) {
    saveFeedback(command, result, audit)
} else {
    null
}
```

- [ ] **Step 5: Add saveFeedback helper to CoachingRagService**

Add this private method to `CoachingRagService`:

```kotlin
private fun saveFeedback(
    command: CoachingRagCommand,
    result: CoachingStructuredResult,
    audit: RagAuditResult
): java.util.UUID {
    val member = memberRepository.findById(command.memberId).orElseThrow {
        BusinessException(ErrorCode.MEMBER_NOT_FOUND)
    }
    val record = command.recordId?.let { farmingRecordRepository.findById(it).orElse(null) }
    val farm = command.farmId?.let { farmRepository.findById(it).orElse(null) } ?: record?.farm
    val crop = command.cropId?.let { cropRepository.findById(it).orElse(null) } ?: record?.crop
    val payload = feedbackMapper.toPayload(command, result)

    val feedback = com.godsmove.domain.coaching.CoachingFeedback(
        member = member,
        coachingMode = command.mode,
        record = record,
        farm = farm,
        crop = crop,
        question = command.question,
        periodStartsOn = command.periodStart,
        periodEndsOn = command.periodEnd,
        summary = result.summary,
        riskLevel = result.riskLevel.name,
        confidenceScore = payload.confidenceScore,
        structuredResult = payload.structuredResult,
        citations = payload.citations,
        auditStatus = audit.status.name,
        auditWarnings = audit.warnings,
        modelName = ragProperties.chat.model,
        embeddingModel = ragProperties.embedding.model
    )

    return feedbackRepository.save(feedback).id
        ?: throw BusinessException(ErrorCode.INTERNAL_ERROR)
}
```

- [ ] **Step 6: Run mapper test and compile**

Run:

```bash
cd backend
./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingFeedbackMapperTest :application:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit persistence wiring**

Run:

```bash
git add backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapper.kt backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/CoachingRagService.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/CoachingFeedbackMapperTest.kt
git commit -m "feat(rag): 구조화 코칭 결과 저장 연결" -m "RECORD_AUTO와 REPORT_MANUAL 결과를 coaching_feedback에 jsonb payload와 요약 컬럼으로 저장한다." -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :application:test --tests com.godsmove.application.coaching.rag.CoachingFeedbackMapperTest :application:compileKotlin" -m "Not-tested: database insert against PostgreSQL"
```

## Task 8: Migrate Seed And Indexing To VectorStore

**Files:**

- Modify: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/seed/DevRagSeedService.kt`
- Modify: `backend/api/src/main/kotlin/com/godsmove/api/dev/dto/DevRagSeedResponses.kt`
- Modify: `frontend/dev-rag-test.html`
- Test: `backend/api/src/test/kotlin/com/godsmove/api/dev/controller/DevRagSeedControllerTest.kt`

- [ ] **Step 1: Replace seed service dependencies**

In `DevRagSeedService`, replace `EmbeddingClient` and custom `upsertChunk` use with:

```kotlin
private val vectorStore: org.springframework.ai.vectorstore.VectorStore,
private val farmingRecordDocumentFactory: FarmingRecordDocumentFactory
```

Keep `JdbcTemplate` only for local relational seed and `vector_store` cleanup.

- [ ] **Step 2: Replace farming record indexing**

Replace `seedFarmingRecordChunks()` with:

```kotlin
private fun seedFarmingRecordChunks(): Int {
    val documents = SeedRecords.records.map { record ->
        farmingRecordDocumentFactory.build(
            IndexedFarmingRecord(
                recordId = record.id,
                memberId = SeedIds.MEMBER_ID,
                farmId = SeedIds.FARM_ID,
                cropId = SeedIds.CROP_ID,
                workTypeId = record.workTypeId,
                memberName = SeedPersona.name,
                memberRegion = SeedPersona.region,
                farmName = SeedFarm.name,
                cropName = SeedCrop.name,
                workTypeName = record.workTypeName,
                workedAt = record.workedAt,
                memo = record.memo,
                fieldValues = emptyList()
            )
        )
    }
    vectorStore.add(documents)
    return documents.size
}
```

- [ ] **Step 3: Replace PDF chunk indexing**

Replace `seedPdfChunks` loop with document creation:

```kotlin
val documents = chunks.mapIndexed { index, content ->
    org.springframework.ai.document.Document(
        "$TECH_DOC_SOURCE_ID:$index",
        content,
        mapOf(
            "sourceType" to "TECH_DOCUMENT",
            "sourceId" to TECH_DOC_SOURCE_ID,
            "label" to "농업기술길잡이7 약용작물 ${index + 1}",
            "documentTitle" to "농업기술길잡이7 약용작물",
            "pdfPath" to pdfPath,
            "seedName" to SEED_NAME,
            "chunkIndex" to index
        )
    )
}
vectorStore.add(documents)
return documents.size
```

- [ ] **Step 4: Replace reset index cleanup**

Replace `resetSeedIndex()` with:

```kotlin
private fun resetSeedIndex() {
    jdbcTemplate.update("delete from vector_store where metadata ->> 'seedName' = ?", SEED_NAME)
}
```

This intentionally resets both farming record and technical document seed chunks.

- [ ] **Step 5: Remove obsolete embedding helpers**

Delete these private methods from `DevRagSeedService`:

- `upsertChunk`
- `metadataJson`
- `jsonEscape`
- `sha256`

Remove imports that become unused:

- `EmbeddingClient`
- `RagIndexSqlBuilder`
- `MessageDigest`

- [ ] **Step 6: Update dev HTML response handling**

In `frontend/dev-rag-test.html`, update the RAG answer rendering to read structured output:

```javascript
$("answer").textContent = result.data.result?.summary || "";
setStatus(`RAG query 완료: citations ${result.data.result?.citations?.length || 0}개`);
```

- [ ] **Step 7: Run seed controller test**

Run:

```bash
cd backend
./gradlew :api:test --tests com.godsmove.api.dev.controller.DevRagSeedControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit seed/index migration**

Run:

```bash
git add backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/seed/DevRagSeedService.kt backend/api/src/main/kotlin/com/godsmove/api/dev/dto/DevRagSeedResponses.kt frontend/dev-rag-test.html backend/api/src/test/kotlin/com/godsmove/api/dev/controller/DevRagSeedControllerTest.kt
git commit -m "feat(rag): seed 색인을 VectorStore로 전환" -m "개발 seed가 Spring AI Document와 VectorStore를 사용해 영농일지와 기술문서를 색인하도록 전환한다." -m "Confidence: medium" -m "Scope-risk: moderate" -m "Tested: ./gradlew :api:test --tests com.godsmove.api.dev.controller.DevRagSeedControllerTest" -m "Not-tested: local pgvector vector_store write"
```

## Task 9: Controller Tests And Obsolete Class Removal

**Files:**

- Modify: `backend/api/src/test/kotlin/com/godsmove/api/coaching/controller/CoachingRagControllerTest.kt`
- Delete: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagClients.kt`
- Delete: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexRepository.kt`
- Delete: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexJdbcRepository.kt`
- Delete: `backend/application/src/main/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilder.kt`
- Delete: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClient.kt`
- Delete: `backend/api/src/main/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClient.kt`
- Delete or replace tests for deleted classes.

- [ ] **Step 1: Update controller response assertions**

In `CoachingRagControllerTest`, replace assertions for `$.data.answer` with:

```kotlin
.andExpect(jsonPath("$.data.result.summary", equalTo("요약")))
.andExpect(jsonPath("$.data.result.riskLevel", equalTo("LOW")))
.andExpect(jsonPath("$.data.savedFeedbackId").doesNotExist())
```

Use a fake service result:

```kotlin
CoachingRagResult(
    result = CoachingStructuredResult(
        summary = "요약",
        riskLevel = CoachingRiskLevel.LOW,
        confidence = 0.8,
        observations = emptyList(),
        diagnosis = "진단",
        recommendations = emptyList(),
        nextActions = emptyList(),
        followUpQuestions = emptyList(),
        citations = emptyList()
    ),
    audit = RagAuditResult(RagAuditStatus.PASS, emptyList(), emptyList()),
    model = RagModelInfo(embedding = "bge-m3", chat = "openclaw/agri-rag-coach")
)
```

- [ ] **Step 2: Delete direct RAG infrastructure**

Delete the obsolete files listed at the top of this task.

- [ ] **Step 3: Delete obsolete direct-client tests**

Delete tests that target removed classes:

- `backend/application/src/test/kotlin/com/godsmove/application/coaching/rag/RagIndexSqlBuilderTest.kt`
- `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OllamaEmbeddingClientTest.kt`
- `backend/api/src/test/kotlin/com/godsmove/api/rag/client/OpenClawChatCompletionClientTest.kt`

- [ ] **Step 4: Run compile and focused tests**

Run:

```bash
cd backend
./gradlew :application:test :api:test --tests com.godsmove.api.coaching.controller.CoachingRagControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit cleanup**

Run:

```bash
git add backend/application/src/main/kotlin/com/godsmove/application/coaching/rag backend/api/src/main/kotlin/com/godsmove/api/rag/client backend/api/src/test/kotlin/com/godsmove/api/coaching/controller/CoachingRagControllerTest.kt backend/application/src/test/kotlin/com/godsmove/application/coaching/rag backend/api/src/test/kotlin/com/godsmove/api/rag/client
git commit -m "refactor(rag): 직접 RAG 배관 제거" -m "Spring AI 경로로 대체된 직접 임베딩, pgvector SQL, OpenClaw completion 클라이언트와 관련 테스트를 제거한다." -m "Confidence: medium" -m "Scope-risk: broad" -m "Directive: New RAG provider work should enter through Spring AI abstractions" -m "Tested: ./gradlew :application:test :api:test --tests com.godsmove.api.coaching.controller.CoachingRagControllerTest" -m "Not-tested: end-to-end provider call"
```

## Task 10: Full Verification And Local Runtime Smoke

**Files:**

- Modify only files needed to fix failures found by this task.

- [ ] **Step 1: Run full backend tests**

Run:

```bash
cd backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full Kotlin compile**

Run:

```bash
cd backend
./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run local app with local profile**

Run:

```bash
cd backend
./gradlew :api:bootRun --args='--spring.profiles.active=local'
```

Expected: application starts and logs Tomcat on port 8080. Stop the process after startup is confirmed.

- [ ] **Step 4: Manual dev HTML smoke**

Open `frontend/dev-rag-test.html` in the browser and run:

1. `seed member + records + PDF`
2. `RAG query`

Expected:

- seed returns `accessToken`, `memberId`, and indexed counts
- query response includes `data.result.summary`
- query response includes `data.result.riskLevel`
- query response includes `data.audit.status`
- no 401/500 response

- [ ] **Step 5: Commit verification fixes**

If Step 1-4 required code changes, commit them:

```bash
git add backend frontend
git commit -m "fix(rag): Spring AI 전환 검증 오류 수정" -m "전체 테스트와 로컬 smoke 중 확인된 컴파일, 설정, 응답 매핑 문제를 수정한다." -m "Confidence: high" -m "Scope-risk: narrow" -m "Tested: ./gradlew test; ./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin; local dev HTML smoke" -m "Not-tested: production profile"
```

If no changes were needed, do not create a commit.

## Self-Review Checklist

- Spec coverage: covered Spring AI dependencies, OpenClaw adapter, VectorStore metadata, structured output, context provider, persistence redesign, seed migration, API shape, and verification.
- Red-flag scan: no incomplete markers or unspecified implementation sections are intentionally present.
- Type consistency: `CoachingMode` lives in `domain`, `CoachingRagCommand` imports it, `CoachingStructuredResult` is the API/application output model, and `CoachingFeedback` stores structured JSON.
- Scope: one implementation plan because all tasks deliver one working Spring AI coaching RAG vertical slice.
