# 기록 피드백 LLM 생성 Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 저장된 기록 피드백 스냅샷에서 공식문서 RAG·LLM을 자동 실행해, 근거 있는 짧은 피드백을 `READY`로 저장하고 기존 상태 조회 API로 반환한다.

**Architecture:** Phase 2의 `RecordFeedbackPreparationService`가 스냅샷 저장 후 생성 이벤트를 발행한다. 새 `RecordFeedbackGenerationProcessor`는 새 트랜잭션에서 feedback·revision·snapshot을 다시 검증하고, 제품 전용 생성기와 출력 검증기를 통해 `READY` 또는 `FAILED`를 저장한다. 기존 더미 `TodayRecordFeedbackService`와 공용 구조화 결과는 제품 기록 피드백 경로에서 제거한다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5.4, Spring AI ChatClient/VectorStore, Spring transactional events, Hibernate JSONB, JUnit 5, Mockito, MockMvc

## Global Constraints

- 이 계획은 기록 피드백만 구현한다. 완료 리포트 상세 코칭은 포함하지 않는다.
- 생성 입력은 `CoachingFeedback.inputSnapshot`뿐이다. 생성 중 현재 영농기록·과거 기록·주기 통계를 다시 조회해 섞지 않는다.
- 자동 생성은 `AFTER_COMMIT` + `REQUIRES_NEW`에서 수행한다. `@Async`, 스케줄러, 브로커, 새 라이브러리를 추가하지 않는다.
- 사용자 출력은 잘한 점 정확히 1개와 다음 행동 2~3개다. 항목마다 `basis`, `text`, `evidenceRefs`를 내부적으로 유지한다.
- `text`는 15~45자이며 강제로 자르지 않는다. 실패 시 한 번만 재생성한다.
- 날씨·병해충 행동은 해당 허용 근거가 있을 때만 허용한다. 허용되지 않은 citation ID, 근거 없는 일반 권장, 정확한 비료·농약량 처방은 거절한다.
- `READY` API에는 농부용 문구만 노출한다. 감사·모델·전체 citation 메타데이터·입력 스냅샷은 내부 데이터다.
- 검색 문서가 없거나 유효한 다음 행동 2개를 만들 수 없으면 `FAILED/INSUFFICIENT_EVIDENCE`다.
- 범용 `/api/v1/coaching/rag/query`는 CHAT 전용으로 유지한다.
- 모든 구현은 실패 테스트 작성 → 실패 확인 → 최소 구현 → 통과 확인 → 커밋 순서로 진행한다.

## File Structure

- `backend/domain/.../CoachingFeedback.kt`: READY 영속 상태 전이
- `backend/application/.../rag/record/RecordFeedbackCoachingResult.kt`: 제품 전용 LLM 출력 타입·enum
- `backend/application/.../rag/record/RecordFeedbackOutputValidator.kt`: 길이·근거·분량 검증
- `backend/application/.../rag/record/RecordFeedbackGenerationService.kt`: 스냅샷 Context의 검색·프롬프트·LLM 생성
- `backend/application/.../coaching/feedback/RecordFeedbackGeneration*.kt`: 이벤트·리스너·새 트랜잭션 processor
- `backend/application/.../coaching/feedback/RecordFeedbackQueryService.kt`: READY 사용자 문구 mapping
- `backend/api/.../FarmingRecordFeedbackResponses.kt`: nullable `feedback` 응답

---

### Task 1: READY 상태 전이와 제품 출력 계약

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackCoachingResult.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedback.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackCoachingResultTest.kt`

**Interfaces:**
- Produces `RecordFeedbackCoachingResult(goodPoint, nextActions)`.
- Produces `CoachingFeedback.markReady(structuredResult, citations, auditStatus, auditWarnings, modelName, embeddingModel)`.

- [ ] **Step 1: Write failing domain and JSON contract tests**

```kotlin
@Test
fun `pending feedback becomes ready with immutable result metadata`() {
    val feedback = CoachingFeedback.pendingRecord(member, record, 1)

    feedback.markReady(
        structuredResult = mapOf("goodPoint" to mapOf("text" to "점적관수로 토양 상태를 확인한 점이 좋았어요.")),
        citations = listOf(mapOf("id" to "record:${record.id}")),
        auditStatus = "PASS",
        auditWarnings = emptyList(),
        modelName = "test-chat",
        embeddingModel = "test-embedding",
    )

    assertThat(feedback.status).isEqualTo(CoachingFeedbackStatus.READY)
    assertThat(feedback.failureCode).isNull()
    assertThatThrownBy { feedback.attachInputSnapshot(emptyMap()) }.isInstanceOf(IllegalStateException::class.java)
}

@Test
fun `coaching result keeps one good point and typed next actions in JSON`() {
    val json = objectMapper.writeValueAsString(validResult())
    assertThat(json).contains("goodPoint", "nextActions", "NEXT_WEEK", "WEATHER")
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run:

```bash
cd backend
./gradlew :domain:test --tests '*CoachingFeedbackTest' \
  :application:test --tests '*RecordFeedbackCoachingResultTest'
```

Expected: FAIL because READY transition and product output types do not exist.

- [ ] **Step 3: Add minimal product types and READY transition**

```kotlin
data class RecordFeedbackCoachingResult(
    val goodPoint: RecordFeedbackItem,
    val nextActions: List<RecordFeedbackNextAction>,
)

data class RecordFeedbackItem(
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)

data class RecordFeedbackNextAction(
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
    val basis: String,
    val text: String,
    val evidenceRefs: List<String>,
)
```

Use `TODAY`, `THIS_WEEK`, `NEXT_WEEK`, `NEXT_CHECK` for due and `WEATHER`,
`PEST_DISEASE`, `IRRIGATION`, `FERTILIZING`, `PEST_CONTROL`, `HARVEST`,
`CULTIVATION`, `GENERAL` for category. `markReady` accepts only PENDING,
sets all operational fields, clears failureCode, and never changes inputSnapshot.

- [ ] **Step 4: Run focused tests**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedback.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackCoachingResult.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackCoachingResultTest.kt
git commit -m "feat(coaching): 기록 피드백 READY 결과를 저장"
```

---

### Task 2: 근거·길이 전용 출력 검증기

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidator.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`

**Interfaces:**
- Consumes `RecordFeedbackCoachingResult`, `RecordFeedbackContext`, `allowedEvidenceRefs`.
- Produces `RecordFeedbackOutputValidation(status, warnings)`.

- [ ] **Step 1: Write failing validator tests**

```kotlin
@Test
fun `validates exactly one good point and two to three cited actions`() {
    val validation = validator.validate(validResult(), allowedEvidence(context, documents))
    assertThat(validation.isValid).isTrue()
}

@Test
fun `rejects text outside 15 to 45 characters and unknown evidence`() {
    val invalid = validResult().copy(goodPoint = validItem(text = "짧음", refs = listOf("unknown")))
    assertThat(validator.validate(invalid, allowedEvidence(context, documents)).warnings)
        .contains("good_point_text_length", "unknown_evidence:unknown")
}

@Test
fun `rejects weather action without weather evidence`() {
    val invalid = validResult(category = WEATHER, refs = listOf(context.recordCitationId()))
    assertThat(validator.validate(invalid, allowedEvidence(context, documents)).warnings)
        .contains("weather_action_without_weather_evidence")
}
```

- [ ] **Step 2: Run test and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest'
```

Expected: FAIL because the validator does not exist.

- [ ] **Step 3: Implement validator and rewrite prompt contract**

Build allowed IDs from `record:<id>`, `weather:current`, each
`weather:<forecast-date>`, and retrieved document IDs. Validate nonblank
fields, action count 2..3, 15..45 Kotlin character count, citation subset,
weather category evidence, and a normalized two-character-or-longer basis token
appearing in text. Add explicit Korean output instructions to the prompt and
remove old `summary`, risk, diagnosis, observations, recommendations, and
follow-up question instructions.

- [ ] **Step 4: Run focused tests**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackPromptBuilderTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt
git commit -m "feat(coaching): 기록 피드백 근거 출력 규칙을 검증"
```

---

### Task 3: 스냅샷 기반 RAG 생성기 교체

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationService.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationServiceTest.kt`
- Delete: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`
- Delete: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/TodayRecordFeedbackVectorStoreSmokeTest.kt`
- Delete: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/TodayRecordFeedbackResponses.kt`

**Interfaces:**
- Produces `GeneratedRecordFeedback(result, citations, auditWarnings, modelInfo)`.
- Throws `RecordFeedbackGenerationException` with `INSUFFICIENT_EVIDENCE`, `STRUCTURED_OUTPUT_INVALID`, or `GENERATION_FAILED`.

- [ ] **Step 1: Write failing generator tests**

```kotlin
@Test
fun `generates only from context and official document evidence`() {
    val generated = service(documents = listOf(officialDocument("doc-1")))
        .generate(context, topK = 2)

    assertThat(generated.result.nextActions).hasSize(2)
    assertThat(generated.citations).extracting<String> { it["id"] as String }
        .contains("doc-1", context.recordCitationId())
}

@Test
fun `retries once when parsed output violates product validation`() {
    val client = FakeChatClient(invalidResult, validResult)
    service(documents = listOf(officialDocument("doc-1")), chatClient = client).generate(context)
    assertThat(client.attempts).isEqualTo(2)
}

@Test
fun `fails with insufficient evidence when no official document is retrieved`() {
    assertThatThrownBy { service(documents = emptyList()).generate(context) }
        .isInstanceOfSatisfying(RecordFeedbackGenerationException::class.java) {
            assertThat(it.code).isEqualTo(INSUFFICIENT_EVIDENCE)
        }
}
```

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackGenerationServiceTest'
```

Expected: FAIL because the new generator does not exist.

- [ ] **Step 3: Implement the replacement generator**

Reuse `RecordFeedbackContextValidator`, `RecordFeedbackRetrievalQueryPlanner`,
`RecordFeedbackPromptBuilder`, `ChatClient`, `VectorStore`, and the existing
TECH_DOCUMENT crop filter. Do not attach a retrieval advisor. Convert document
metadata to authoritative citation maps after the LLM result is validated.

Define the failure surface in the same file so the processor can map it without
depending on HTTP errors:

```kotlin
enum class RecordFeedbackGenerationFailureCode {
    INSUFFICIENT_EVIDENCE,
    STRUCTURED_OUTPUT_INVALID,
    GENERATION_FAILED,
}

class RecordFeedbackGenerationException(
    val code: RecordFeedbackGenerationFailureCode,
    cause: Throwable? = null,
) : RuntimeException(code.name, cause)
```

Call the LLM once, then exactly once more only when parsing or output validation
fails. Do not sanitize partial results. Missing documents is immediately
`INSUFFICIENT_EVIDENCE`; vector/chat runtime failures are `GENERATION_FAILED`.
Rename the smoke test and update it to use the new generator constant rather
than `TodayRecordFeedbackService.GENERAL_CROP_NAME`.

- [ ] **Step 4: Run focused tests**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackRetrievalQueryPlannerTest' \
  :api:test --tests '*RecordFeedback*VectorStoreSmokeTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record \
  backend/api/src/test/kotlin/com/chamchamcham/api/coaching \
  backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/TodayRecordFeedbackResponses.kt
git commit -m "feat(coaching): 기록 스냅샷 기반 RAG 생성을 추가"
```

---

### Task 4: 준비 완료 뒤 자동 생성과 상태 저장

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationRequested.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessor.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationListener.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationServiceTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessorTest.kt`

**Interfaces:**
- Consumes a persisted PENDING feedback snapshot and `RecordFeedbackGenerationRequested`.
- Produces one READY or FAILED state for the matching feedback revision.

- [ ] **Step 1: Write failing orchestration tests**

```kotlin
@Test
fun `preparation publishes generation request only after snapshot is attached`() {
    preparationService.prepare(event)
    verify(eventPublisher).publishEvent(RecordFeedbackGenerationRequested(feedbackId, memberId, recordId, 3))
}

@Test
fun `processor ignores stale event and never calls generator`() {
    processor.generate(event.copy(sourceRevision = 2))
    verifyNoInteractions(generationService)
}

@Test
fun `processor persists ready result from the stored snapshot`() {
    processor.generate(event)
    assertThat(feedback.status).isEqualTo(READY)
    assertThat(feedback.structuredResult).containsKey("goodPoint")
}

@Test
fun `processor stores insufficient evidence failure without touching record`() {
    whenever(generationService.generate(any())).thenThrow(RecordFeedbackGenerationException(INSUFFICIENT_EVIDENCE))
    processor.generate(event)
    assertThat(feedback.status).isEqualTo(FAILED)
    assertThat(feedback.failureCode).isEqualTo("INSUFFICIENT_EVIDENCE")
}
```

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackPreparationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest'
```

Expected: FAIL because generation event, listener, and processor do not exist.

- [ ] **Step 3: Implement event boundaries**

After `attachInputSnapshot`, publish `RecordFeedbackGenerationRequested` from
the preparation transaction. Add an `AFTER_COMMIT` listener that calls a
`@Transactional(REQUIRES_NEW)` processor. The processor must load with
`findByIdAndMember_Id`, verify PENDING/record/sourceRevision/inputSnapshot,
deserialize the snapshot to `RecordFeedbackContext`, call the generator, and
call `markReady`.

Catch only expected generation failures to call `markFailed(code)`; log an
unexpected listener exception without reopening the original farming record
transaction. Snapshot parse failure maps to `STRUCTURED_OUTPUT_INVALID`.

- [ ] **Step 4: Run focused tests**

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.feedback.*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback
git commit -m "feat(coaching): 기록 피드백을 자동 생성한다"
```

---

### Task 5: READY 피드백 조회 응답

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryServiceTest.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/FarmingRecordFeedbackResponses.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/controller/FarmingRecordFeedbackControllerTest.kt`

**Interfaces:**
- `RecordFeedbackResult.feedback: RecordFeedbackUserResponse?`.
- `READY` exposes user text/due/category only; all other states expose null.

- [ ] **Step 1: Write failing query and controller tests**

```kotlin
@Test
fun `ready feedback returns only user facing text fields`() {
    val result = service.get(memberId, recordId)
    assertThat(result.feedback?.goodPoint?.text).contains("점적관수")
    assertThat(result.feedback?.nextActions).hasSize(2)
}

@Test
fun `pending feedback does not expose output or audit data`() {
    assertThat(service.get(memberId, recordId).feedback).isNull()
}

@Test
fun `ready response serializes feedback but not basis evidence citations or model`() {
    mockMvc.perform(get(path).with(authenticatedMember(memberId.toString())))
        .andExpect(jsonPath("$.data.feedback.goodPoint.text").exists())
        .andExpect(jsonPath("$.data.feedback.goodPoint.basis").doesNotExist())
        .andExpect(jsonPath("$.data.auditStatus").doesNotExist())
}
```

- [ ] **Step 2: Run tests and confirm failure**

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackQueryServiceTest' \
  :api:test --tests '*FarmingRecordFeedbackControllerTest'
```

Expected: FAIL because feedback output is not mapped into the response.

- [ ] **Step 3: Add READY-only mapping**

Deserialize `structuredResult` into `RecordFeedbackCoachingResult` only when
status is READY. Map the public DTO to `text`, `due`, and `category`; do not
copy basis, evidenceRefs, citations, audit, model, or snapshot maps. A malformed
READY map is an internal consistency error and must not be silently shown as
partial feedback.

- [ ] **Step 4: Run focused tests**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback \
  backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/FarmingRecordFeedbackResponses.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/coaching/controller/FarmingRecordFeedbackControllerTest.kt
git commit -m "feat(coaching): 완료된 기록 피드백 문구를 반환"
```

---

### Task 6: 통합 회귀와 계약 문서

**Files:**
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/RecordFeedbackLifecycleIntegrationTest.kt`
- Modify: `frontend/docs/API 명세서/API 명세서/영농일지 코칭 피드백 조회 3909e2d9440581c1917ce4dcf0be3aa3.md`
- Modify: `frontend/docs/API 명세서/DTO(데이터 전달 객체)/CoachingFeedbackResponse 3909e2d9440581699f25f89de41df770.md`
- Modify: `docs/superpowers/specs/2026-07-10-cycle-report-record-coaching-rag-redesign.md` only when implementation reveals a contract contradiction

- [ ] **Step 1: Write failing end-to-end lifecycle test**

Mock `RecordFeedbackGenerationService` in the Spring test context. Create a
record, allow snapshot preparation and generation listener completion, then
assert one RECORD feedback with `READY`, immutable snapshot, structured good
point/action output, and no extra row after a duplicate event.

- [ ] **Step 2: Run test and confirm failure**

```bash
cd backend
./gradlew :api:test --tests '*RecordFeedbackLifecycleIntegrationTest'
```

Expected: FAIL before the automatic generation path exists.

- [ ] **Step 3: Update documentation and static gates**

Document `feedback: null` until READY, the READY public field list, automatic
generation after record save, and manual regenerate only for failures. Update
the DTO document to state that basis/evidence/model/audit are internal.

- [ ] **Step 4: Run full verification**

```bash
cd backend
./gradlew test --rerun-tasks
git diff --check
rg -n "TodayRecordFeedback|CoachingStructuredResult|riskLevel|diagnosis|followUpQuestions" \
  application/src/main/kotlin/com/chamchamcham/application/coaching/feedback \
  application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record \
  api/src/main/kotlin/com/chamchamcham/api/coaching
```

Expected: Gradle succeeds; the static gate has no product record-feedback matches
for the removed legacy output path.

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/test/kotlin/com/chamchamcham/api/coaching \
  frontend/docs/API\ 명세서 docs/superpowers/specs
git commit -m "test(coaching): 기록 피드백 자동 생성 흐름을 검증"
```
