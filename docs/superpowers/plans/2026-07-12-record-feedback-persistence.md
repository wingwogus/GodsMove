# RecordFeedback Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the speculative shared coaching-feedback model with a record-only feedback model whose user-facing result is stored in typed columns and ordered action rows.

**Architecture:** `RecordFeedback` becomes the only feedback aggregate currently implemented and owns one fixed good point plus 2–3 `RecordFeedbackNextAction` children. The LLM response remains an application-only validation DTO; the generation processor maps it into the domain aggregate. The mutable input snapshot and audit metadata remain JSONB because they are not fixed product fields.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA/Hibernate, PostgreSQL JSONB, JUnit 5, MockK/Mockito test doubles.

## Global Constraints

- Keep dependency direction `api -> application -> domain`; domain must not import application or API types.
- Do not add dependencies or a migration framework; current environments may recreate the schema.
- Rename the physical table to `record_feedback`; preserve no `coaching_feedback` data.
- Preserve the current record-feedback HTTP route and response JSON contract.
- Keep `input_snapshot`, `citations`, and `audit_warnings` as JSONB; remove only `structured_result` JSONB.
- `READY` feedback requires exactly 2 or 3 consecutive next-action rows.
- Use Conventional Commit titles and Lore trailers for every commit.

---

### Task 1: Replace the shared domain aggregate with record-only persistence

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/RecordFeedback.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/RecordFeedbackNextAction.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/RecordFeedbackRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/RecordFeedbackStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/RecordFeedbackActionType.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/RecordFeedbackTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContent.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedback.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackRepository.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/CoachingFeedbackStatus.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/FeedbackType.kt`

**Interfaces:**
- Produces `RecordFeedback.pending(member, record, sourceRevision)`.
- Produces `RecordFeedback.markReady(goodPointBasis, goodPointText, nextActions, citations, auditStatus, auditWarnings, modelName, embeddingModel)`.
- Produces `RecordFeedback.nextActions(): List<RecordFeedbackNextAction>` sorted by `displayOrder`.
- Produces `RecordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, sourceRevision)`.

- [ ] **Step 1: Write failing domain tests for the ready result shape**

```kotlin
@Test
fun `ready feedback persists a good point and ordered actions`() {
    val feedback = RecordFeedback.pending(member, record, sourceRevision = 1)

    feedback.markReady(
        goodPointBasis = "관수 기록",
        goodPointText = "관수 기록이 구체적입니다.",
        nextActions = listOf(action("오늘 토양을 확인하세요."), action("이번 주 배수로를 점검하세요.")),
        citations = emptyList(),
        auditStatus = "PASS",
        auditWarnings = emptyList(),
        modelName = "chat",
        embeddingModel = "embed",
    )

    assertThat(feedback.goodPointText).isEqualTo("관수 기록이 구체적입니다.")
    assertThat(feedback.nextActions().map(RecordFeedbackNextAction::displayOrder)).containsExactly(0, 1)
}

@Test
fun `ready feedback rejects fewer than two or more than three actions`() {
    assertThatThrownBy {
        pending().markReady("근거", "잘한 점", listOf(action("하나뿐인 행동")), emptyList(), "PASS", emptyList(), "chat", "embed")
    }
        .isInstanceOf(IllegalArgumentException::class.java)
}
```

- [ ] **Step 2: Run the domain test to verify it fails**

Run: `cd backend && ./gradlew :domain:test --tests '*RecordFeedbackTest'`

Expected: compilation failure because `RecordFeedback` and `RecordFeedbackNextAction` do not exist.

- [ ] **Step 3: Implement the record-only aggregate and child entity**

```kotlin
@Entity
@Table(name = "record_feedback")
class RecordFeedback(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "member_id", nullable = false) val member: Member,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "record_id", nullable = false) val record: FarmingRecord,
    @Column(name = "source_revision", nullable = false) val sourceRevision: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: RecordFeedbackStatus,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "input_snapshot", columnDefinition = "jsonb") var inputSnapshot: Map<String, Any?>? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var citations: List<Map<String, Any?>> = emptyList(),
    @Column(name = "failure_code", length = 128) var failureCode: String? = null,
) : BaseTimeEntity() {
    @Column(name = "good_point_basis", length = 255)
    var goodPointBasis: String? = null
        private set

    @Column(name = "good_point_text", length = 255)
    var goodPointText: String? = null
        private set

    @OneToMany(mappedBy = "recordFeedback", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private val nextActionRows = mutableListOf<RecordFeedbackNextAction>()

    fun nextActions(): List<RecordFeedbackNextAction> = nextActionRows.toList()
}

@Entity
@Table(
    name = "record_feedback_next_action",
    uniqueConstraints = [UniqueConstraint(columnNames = ["record_feedback_id", "display_order"])],
)
class RecordFeedbackNextAction(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "record_feedback_id", nullable = false)
    val recordFeedback: RecordFeedback,
    @Column(name = "display_order", nullable = false) val displayOrder: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val due: RecordFeedbackActionDue,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val category: RecordFeedbackActionCategory,
    @Column(nullable = false) val basis: String,
    @Column(nullable = false, length = 255) val text: String,
)
```

Move `RecordFeedbackActionDue` and `RecordFeedbackActionCategory` into `domain.coaching` so the child entity does not depend on application code. Add the domain input value below and keep `RecordFeedbackContent`, `RecordFeedbackGoodPoint`, and the LLM action DTO in application as generation-only values.

```kotlin
data class RecordFeedbackNextActionDraft(
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
    val basis: String,
    val text: String,
)
```

- [ ] **Step 4: Remove shared-target concepts**

Remove `cycleReport`, `feedbackType`, and the `FeedbackType` enum. Replace all repository methods with record-only equivalents:

```kotlin
interface RecordFeedbackRepository : JpaRepository<RecordFeedback, UUID> {
    fun findByRecord_IdAndSourceRevision(recordId: UUID, sourceRevision: Long): RecordFeedback?
    fun findAllByRecord_IdAndStatusIn(recordId: UUID, statuses: Collection<RecordFeedbackStatus>): List<RecordFeedback>
    fun findTopByRecord_IdAndStatusOrderByUpdatedAtDesc(recordId: UUID, status: RecordFeedbackStatus): RecordFeedback?
    fun findByIdAndMember_Id(id: UUID, memberId: UUID): RecordFeedback?
}
```

- [ ] **Step 5: Run the domain test to verify it passes**

Run: `cd backend && ./gradlew :domain:test --tests '*RecordFeedbackTest'`

Expected: PASS.

- [ ] **Step 6: Commit the domain slice**

```bash
git add backend/domain backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContent.kt
git commit -m "refactor(coaching): 기록 피드백 모델을 독립시킨다" -m "기록과 리포트의 서로 다른 대상과 상태 전이를 공유 엔티티에 유지하지 않도록 RecordFeedback과 순서 있는 행동 모델로 분리한다." -m "Constraint: 기존 운영 데이터는 보존하지 않는다\nRejected: next_action_1~3 슬롯 컬럼 | 반복 데이터와 불변식이 중복됨\nConfidence: high\nScope-risk: moderate\nDirective: 리포트 코칭은 ReportFeedback으로 별도 구현한다\nTested: RecordFeedback 도메인 테스트\nNot-tested: API 회귀 테스트"
```

### Task 2: Map validated LLM output into the aggregate and restore query results

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackLifecycleService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessor.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackStatusResult.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackQueryServiceTest.kt`

**Interfaces:**
- Consumes `RecordFeedbackRepository` and `RecordFeedback.markReady` from Task 1.
- Produces `RecordFeedbackStatusResult` with a persistence-independent `RecordFeedbackResultContent`.

- [ ] **Step 1: Write failing processor and query tests**

```kotlin
@Test
fun `processor stores generated good point and actions without structured result json`() {
    processor.generate(event)

    verify {
        feedback.markReady(
            goodPointBasis = "관수 기록",
            goodPointText = "관수 기록이 구체적입니다.",
            nextActions = match { it.size == 2 && it[0].displayOrder == 0 },
            citations = any(), auditStatus = "PASS", auditWarnings = emptyList(),
            modelName = "chat", embeddingModel = "embed",
        )
    }
}

@Test
fun `query maps ordered action rows to the existing response content`() {
    assertThat(result.content!!.nextActions.map { it.text })
        .containsExactly("오늘 토양을 확인하세요.", "이번 주 배수로를 점검하세요.")
}
```

- [ ] **Step 2: Run the focused application tests to verify they fail**

Run: `cd backend && ./gradlew :application:test --tests '*RecordFeedbackGenerationProcessorTest' --tests '*RecordFeedbackQueryServiceTest'`

Expected: FAIL because the services still refer to `CoachingFeedbackRepository` and `structuredResult`.

- [ ] **Step 3: Replace persistence JSON mapping with explicit result mapping**

Use an application query result that has no LLM evidence references:

```kotlin
data class RecordFeedbackResultContent(
    val goodPoint: String,
    val nextActions: List<RecordFeedbackNextActionResult>,
)

data class RecordFeedbackNextActionResult(
    val text: String,
    val due: RecordFeedbackActionDue,
    val category: RecordFeedbackActionCategory,
)
```

`RecordFeedbackGenerationProcessor` must convert the validated LLM DTO into domain action inputs. `RecordFeedbackQueryService` must read `goodPointText` and `nextActions()` directly; it must not use `ObjectMapper.convertValue` for product output.

- [ ] **Step 4: Update lifecycle and concurrency guards**

Replace generic entity/repository imports without changing the state-machine semantics:

```kotlin
return status == RecordFeedbackStatus.PENDING &&
    record.id == event.recordId &&
    sourceRevision == event.sourceRevision &&
    inputSnapshot == snapshot
```

Retain `PESSIMISTIC_WRITE`, revision checks, input-snapshot checks, and the existing failure-code mapping.

- [ ] **Step 5: Run the focused application tests to verify they pass**

Run: `cd backend && ./gradlew :application:test --tests '*RecordFeedbackGenerationProcessorTest' --tests '*RecordFeedbackPreparationServiceTest' --tests '*RecordFeedbackQueryServiceTest'`

Expected: PASS.

- [ ] **Step 6: Commit the application slice**

```bash
git add backend/application/src/main backend/application/src/test
git commit -m "refactor(coaching): 기록 피드백 결과를 정규화한다" -m "생성 결과를 JSONB 역직렬화에 의존하지 않고 명시적 도메인 필드와 행동 행으로 저장하도록 바꾼다." -m "Constraint: input snapshot과 감사 데이터는 가변 구조라 JSONB를 유지한다\nRejected: 구조화 결과 전체 JSONB 유지 | 고정 제품 필드를 조회할 때 변환 오류가 생김\nConfidence: high\nScope-risk: moderate\nDirective: 생성 DTO는 영속 모델로 재사용하지 않는다\nTested: RecordFeedback application 테스트\nNot-tested: HTTP 계약 테스트"
```

### Task 3: Preserve the HTTP contract

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/RecordFeedbackResponses.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/controller/RecordFeedbackControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/RecordFeedbackLifecycleIntegrationTest.kt`

**Interfaces:**
- Consumes `RecordFeedbackResultContent` from Task 2.
- Produces the unchanged `feedback.goodPoint.text` and `feedback.nextActions[]` JSON shape.

- [ ] **Step 1: Write failing API regression tests**

```kotlin
mockMvc.perform(get("/api/v1/farming-records/$recordId/coaching-feedback").with(jwt(memberId)))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.data.feedback.goodPoint.text").value("관수 기록이 구체적입니다."))
    .andExpect(jsonPath("$.data.feedback.nextActions[1].category").value("CULTIVATION"))
```

Add a lifecycle integration assertion that no `structured_result` mapping is needed to return READY content.

- [ ] **Step 2: Run API tests to verify they fail**

Run: `cd backend && ./gradlew :api:test --tests '*RecordFeedbackControllerTest' --tests '*RecordFeedbackLifecycleIntegrationTest'`

Expected: FAIL until API DTO mapping consumes the new query result shape.

- [ ] **Step 3: Implement API mapping and YAGNI cleanup**

```kotlin
data class FeedbackResponse(
    val goodPoint: GoodPointResponse,
    val nextActions: List<NextActionResponse>,
) {
    companion object {
        fun from(source: RecordFeedbackResultContent) = FeedbackResponse(
            goodPoint = GoodPointResponse(source.goodPoint),
            nextActions = source.nextActions.map { NextActionResponse(it.text, it.due, it.category) },
        )
    }
}
```

Do not change generic RAG chat modes in this refactor. They are outside the persistence change and altering that API contract would widen scope.

- [ ] **Step 4: Run API tests to verify they pass**

Run: `cd backend && ./gradlew :api:test --tests '*RecordFeedbackControllerTest' --tests '*RecordFeedbackLifecycleIntegrationTest'`

Expected: PASS.

- [ ] **Step 5: Commit the API slice**

```bash
git add backend/api
git commit -m "refactor(coaching): 기록 피드백 API 경계를 유지한다" -m "저장 모델을 교체해도 기존 상태 조회 응답과 재생성 API가 바뀌지 않도록 응답 매핑을 명시적으로 유지한다." -m "Constraint: 외부 API 경로와 JSON 필드는 호환되어야 한다\nRejected: API 응답에 LLM DTO를 직접 노출 | 영속과 생성 스키마가 다시 결합됨\nConfidence: high\nScope-risk: narrow\nDirective: evidenceRefs는 생성 검증 전용이며 현재 API 계약에 추가하지 않는다\nTested: controller 및 lifecycle integration 테스트\nNot-tested: 실서비스 LLM 호출"
```

### Task 4: Run repository-wide coaching verification and finalize documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-07-12-record-feedback-persistence-design.md`
- Modify: `docs/superpowers/plans/2026-07-12-record-feedback-persistence.md`

**Interfaces:**
- Consumes all production code from Tasks 1–3.
- Produces verification evidence and checked-off plan tasks.

- [ ] **Step 1: Search for deleted shared-model symbols**

Run:

```bash
rg -n "CoachingFeedback|FeedbackType|coaching_feedback|structuredResult" backend --glob '!**/build/**'
```

Expected: no production references; any intentional historical references must be removed from the current spec/plan before completion.

- [ ] **Step 2: Run the complete relevant suite**

Run:

```bash
cd backend && ./gradlew :domain:test :application:test :api:test
git diff --check
```

Expected: all tests pass and no whitespace errors.

- [ ] **Step 3: Record completed checks and commit**

Update plan checkboxes and any changed final design details. Then run:

```bash
git add docs/superpowers/specs/2026-07-12-record-feedback-persistence-design.md docs/superpowers/plans/2026-07-12-record-feedback-persistence.md
git commit -m "docs(coaching): 기록 피드백 전환 검증을 남긴다" -m "정규화된 RecordFeedback 저장 모델의 검증 결과와 의도적인 제외 범위를 남긴다." -m "Constraint: 리포트 피드백은 이번 범위에 포함하지 않는다\nRejected: ReportFeedback 골격 선행 추가 | 실제 생성 기능이 없어 불필요함\nConfidence: high\nScope-risk: narrow\nTested: domain, application, api 테스트 및 diff 검사\nNot-tested: 운영 DB 마이그레이션"
```
