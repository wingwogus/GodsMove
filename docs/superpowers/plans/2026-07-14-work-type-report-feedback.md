# Work-Type Report Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 완료 리포트 코칭을 실제 기록이 있는 작업 타입별로 독립 생성·저장·조회하고, HTML 작업 탭에 연결하며 기록 코칭과 리포트 코칭 생성을 요청 스레드 밖에서 실행한다.

**Architecture:** `ReportFeedback` 한 행을 `(reportId, workType)` 생성 단위로 바꾸고 각 행이 독립 상태·요약·항목·감사 데이터를 소유한다. 정확한 `CycleSlice`에서 대상 작업 기록과 통계만 뽑아 작업별 RAG/LLM 호출에 사용하고, 기존 GET 경로는 작업별 상태 목록을 반환한다. 기존 AFTER_COMMIT 준비 진입점만 `@Async`로 넘기며 별도 큐·공통 lifecycle 부모·전용 executor는 추가하지 않는다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.5, Spring Data JPA/Hibernate, Spring AI `ChatClient`/`VectorStore`, JUnit 5, Mockito, AssertJ, MockMvc, H2, vanilla HTML/JavaScript.

## Global Constraints

- 기준 설계는 `docs/superpowers/specs/2026-07-14-work-type-report-feedback-design.md`다.
- 전체 사이클 AI 요약과 합성 상태를 만들지 않는다.
- 최초 완료 slice에 실제 기록이 있는 `WorkType`만 생성한다.
- 작업 타입별 RAG, LLM, `PENDING/READY/FAILED`를 독립 처리한다.
- 결과 목록 수에는 상한·하한이 없으며 세 목록이 모두 비어도 유효한 요약으로 READY가 될 수 있다.
- 모든 사용자 노출 코칭 문장은 친근한 존댓말로 끝낸다.
- API 경로 `GET /api/v1/farming-reports/{reportId}/feedback`은 유지한다.
- API 필드는 `nextCycleActions`가 아니라 `nextActions`를 사용한다.
- 완료 뒤 기록 수정에 따른 stale/revision/rebuild, 수동 재생성, outbox와 복구 스케줄러는 만들지 않는다.
- 공통 Feedback 부모, 공통 validator/lifecycle 추상화, item의 중복 workType, 전용 async executor를 만들지 않는다.
- 운영 데이터 마이그레이션은 범위 밖이다. local/test 스키마 재생성만 코드 변경으로 검증한다.
- 기존 사용자 소유 `?? .claude/`는 읽거나 수정하거나 스테이징하지 않는다.
- 각 production 변경은 실패하는 테스트를 먼저 실행한 뒤 최소 구현으로 통과시킨다.
- 커밋은 Conventional Commit 한국어 제목과 Lore trailers를 사용한다.

---

## File Structure

| Area | Files | Responsibility |
| --- | --- | --- |
| Domain | `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedback.kt` | 작업 타입별 상태와 READY 전이 |
| Domain | `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackItemSection.kt` | `NEXT_ACTION` 섹션 이름 |
| Domain | `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackRepository.kt` | report 다건 조회와 ID 잠금 |
| Context | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContext.kt` | 작업별 생성 스냅샷 계약 |
| Context | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssembler.kt` | 정확한 slice와 동일 작업 데이터만 조립 |
| Generation | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContent.kt` | 작업별 구조화 출력 |
| Generation | `ReportFeedbackRetrievalQueryPlanner.kt`, `ReportFeedbackPromptBuilder.kt`, `ReportFeedbackOutputValidator.kt`, `ReportFeedbackGenerationService.kt` | 작업별 검색·프롬프트·검증·재시도 |
| Lifecycle | `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt` | 완료 slice의 실제 WorkType 전달 |
| Lifecycle | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/*.kt` | 작업별 enqueue, snapshot, 생성, 상태 전이 |
| API | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt` | 작업별 컬렉션 결과 |
| API | `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/**` | HTTP 컬렉션 응답 |
| HTML | `frontend/dev-rag-test.html` | 기존 작업 탭에 선택 작업 코칭 표시 |
| Async | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackPreparationListener.kt` | 기록 코칭 첫 AFTER_COMMIT 비동기 진입 |
| Docs | `frontend/docs/API 명세서/**` | 작업별 응답 계약 반영 |

---

### Task 1: Convert ReportFeedback persistence to one row per work type

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedback.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackItemSection.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackRepository.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackTest.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackRepositoryTest.kt`

**Interfaces:**
- Consumes: `WorkType`, `FarmingCycleReport`, existing `ReportFeedbackStatus` and item draft.
- Produces: `ReportFeedback.pending(member, report, workType)`, `ReportFeedback.workType`, `(report_id, work_type)` uniqueness, empty-item READY support, report-level collection repository methods.

- [ ] **Step 1: Write failing domain tests for work type ownership and empty-item READY**

Replace cycle-global expectations with these assertions while retaining blank summary and blank item tests:

```kotlin
@Test
fun `pending feedback belongs to one report work type`() {
    val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)

    assertThat(feedback.workType).isEqualTo(WorkType.WATERING)
    assertThat(feedback.report).isSameAs(report)
    assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
}

@Test
fun `ready feedback permits a summary without forced items`() {
    val feedback = ReportFeedback.pending(member, report, WorkType.WATERING)

    feedback.markReady(
        summary = "이번 관수 기록의 흐름을 확인했어요.",
        items = emptyList(),
        citations = emptyList(),
        auditStatus = "PASS",
        auditWarnings = emptyList(),
        modelName = "test-chat",
        embeddingModel = "test-embedding",
    )

    assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.READY)
    assertThat(feedback.items()).isEmpty()
}
```

Change ordered item fixtures from `NEXT_CYCLE_ACTION` to `NEXT_ACTION` and keep all item texts within one work type.

- [ ] **Step 2: Write a failing JPA uniqueness test**

Create a `@DataJpaTest` that persists member, farm, crop, final-harvest record and completed report, then verifies:

```kotlin
entityManager.persist(ReportFeedback.pending(member, report, WorkType.WATERING))
entityManager.persist(ReportFeedback.pending(member, report, WorkType.HARVEST))
entityManager.flush() // succeeds

entityManager.persist(ReportFeedback.pending(member, report, WorkType.WATERING))
assertThatThrownBy(entityManager::flush)
    .isInstanceOf(RuntimeException::class.java)
```

Use the same private `@SpringBootConfiguration`, `@EntityScan`, `@EnableJpaRepositories`, and `@EnableJpaAuditing` pattern as `MemberCropRepositoryTest` so the test is self-contained.

- [ ] **Step 3: Run the domain tests and verify RED**

Workdir: `backend`

```bash
./gradlew :domain:test --tests '*ReportFeedbackTest' --tests '*ReportFeedbackRepositoryTest'
```

Expected: compilation/test failure because `pending` has no workType parameter, `NEXT_ACTION` does not exist, and the current unique constraint rejects the second work type.

- [ ] **Step 4: Implement the minimal persistence change**

Use this entity contract:

```kotlin
@Table(
    name = "report_feedback",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_report_feedback_report_work_type",
            columnNames = ["report_id", "work_type"],
        ),
    ],
)
class ReportFeedback(
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 32)
    val workType: WorkType,
)
```

Insert this property immediately after the existing non-null `report` association; keep all
existing ID, member, status, snapshot, citation, audit, failure, and model fields unchanged.

Remove only `require(items.isNotEmpty())`; keep nonblank summary and validation of any supplied item. Change the factory to:

```kotlin
fun pending(member: Member, report: FarmingCycleReport, workType: WorkType): ReportFeedback =
    ReportFeedback(member = member, report = report, workType = workType, status = ReportFeedbackStatus.PENDING)
```

Rename the enum value to `NEXT_ACTION`. Replace report-singleton repository methods with:

```kotlin
fun existsByReport_Id(reportId: UUID): Boolean
fun findAllByReport_IdAndMember_Id(reportId: UUID, memberId: UUID): List<ReportFeedback>
fun findByIdAndMember_Id(id: UUID, memberId: UUID): ReportFeedback?
```

Keep `findByIdAndMemberIdForUpdate` unchanged.

- [ ] **Step 5: Run domain tests and verify GREEN**

```bash
./gradlew :domain:test --tests '*ReportFeedbackTest' --tests '*ReportFeedbackRepositoryTest'
```

Expected: PASS.

- [ ] **Step 6: Commit the persistence boundary**

Stage only Task 1 files and commit:

```text
refactor(coaching): 리포트 코칭을 작업 타입별 저장 단위로 분리

Constraint: 운영 데이터가 없어 local/test 스키마 재생성을 사용
Rejected: item에도 workType 저장 | 부모 값과 중복됨
Confidence: high
Scope-risk: moderate
Tested: ReportFeedback domain and JPA repository tests
```

---

### Task 2: Assemble an exact work-type context

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContext.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssembler.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssemblerTest.kt`

**Interfaces:**
- Consumes: `FarmingCyclePartitioner.partition`, target report final-harvest ID, domain `WorkType`, typed `CycleReportStatistics`.
- Produces: `assemble(memberId, reportId, workType)`, context schema version 2, target-only records/current statistics/previous statistics.

- [ ] **Step 1: Replace the period-filter regression test with exact slice tests**

Build two adjacent completed slices where the first final harvest and the target watering record share a timestamp and UUID ordering places the final harvest first. Assert:

```kotlin
val context = assembler.assemble(memberId, reportId, WorkType.WATERING)

assertThat(context.schemaVersion).isEqualTo(2)
assertThat(context.workType).isEqualTo(WorkType.WATERING)
assertThat(context.records.map { it.id }).containsExactly(targetWateringId)
assertThat(context.records).allMatch { it.workType == WorkType.WATERING }
assertThat(context.report.statistics)
    .containsEntry("recordCount", 1)
    .doesNotContainKeys("watering", "fertilizing", "harvest")
```

Add a second test where the previous report exists but its watering count is zero:

```kotlin
assertThat(assembler.assemble(memberId, reportId, WorkType.WATERING).previousReport).isNull()
```

Add a third test asserting `ReportFeedbackGenerationFailure(INVALID_CONTEXT)` when the selected slice has no requested work type.

- [ ] **Step 2: Run the context test and verify RED**

```bash
./gradlew :application:test --tests '*ReportFeedbackContextAssemblerTest'
```

Expected: compilation failure because `assemble` lacks workType and context still stores full cycle statistics.

- [ ] **Step 3: Implement the target-only context contract**

Use:

```kotlin
const val REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION = 2

data class ReportFeedbackContext(
    val schemaVersion: Int,
    val workType: WorkType,
    val report: ReportFeedbackReport,
    val records: List<ReportFeedbackRecord>,
    val previousReport: ReportFeedbackPreviousReport?,
    val warnings: List<String>,
)

data class ReportFeedbackReport(
    val id: UUID,
    val farmName: String,
    val cropName: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val statistics: Map<String, Any?>,
)

data class ReportFeedbackRecord(
    val id: UUID,
    val workedAt: LocalDateTime,
    val workType: WorkType,
    val memo: String,
    val details: Map<String, Any?>,
)
```

Inject `FarmingCyclePartitioner` and `ObjectMapper`. Select the exact slice:

```kotlin
val targetSlice = partitioner.partition(sourceLoader.load(scope)).singleOrNull {
    it.status == FarmingCycleReportStatus.COMPLETED &&
        it.finalHarvestRecordId == report.finalHarvestRecord?.id
} ?: throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)

val records = targetSlice.records.filter { it.workType == workType }
if (records.isEmpty()) throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
```

Select one typed statistics object with a private exhaustive `when (workType)`, convert only that object to `Map<String, Any?>`, and include a previous report only when the selected map's numeric `recordCount` is greater than zero. Do not place the full `CycleReportStatistics` in context.

- [ ] **Step 4: Run context tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackContextAssemblerTest'
```

Expected: PASS.

- [ ] **Step 5: Commit the exact context boundary**

```text
fix(coaching): 리포트 코칭 입력을 정확한 작업 범위로 제한

Constraint: 인접 사이클 기록은 같은 workedAt을 가질 수 있음
Rejected: 기간 포함 필터 유지 | 사이클 경계 기록이 섞일 수 있음
Confidence: high
Scope-risk: moderate
Directive: 전체 CycleReportStatistics를 작업별 컨텍스트에 다시 넣지 말 것
Tested: ReportFeedbackContextAssembler tests
```

---

### Task 3: Generate, retry, and validate one work type at a time

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContent.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackRetrievalQueryPlanner.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackRetrievalQueryPlannerTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`

**Interfaces:**
- Consumes: Task 2 `ReportFeedbackContext` and scoped evidence.
- Produces: `nextActions`, scoped retrieval queries, safe retry feedback, empty-list-valid output.

- [ ] **Step 1: Write failing planner, prompt, and validator tests**

Use a WATERING context whose statistics map contains `recordCount=4` and `averageIntervalDays=3.5`. Assert the planner output contains `관수` and `4회` but does not contain `방제`, `수확량`, or `시비`.

Assert the prompt:

```kotlin
assertThat(prompt.system)
    .contains("대상 작업 타입 하나만")
    .contains("nextActions")
    .contains("빈 배열")
assertThat(prompt.user)
    .contains("작업 타입: WATERING")
    .contains("record:$wateringRecordId")
    .doesNotContain("record:$fertilizingRecordId")
```

Add a validator test with a valid friendly summary and all three lists empty and expect no warning. Keep tone, duplicate, blank field, and unknown evidence tests; rename content fixtures to `nextActions`.

- [ ] **Step 2: Write failing generation retry tests**

Reuse the test-only `FakeChatClient` and `FakeVectorStore` pattern from `RecordFeedbackGenerationServiceTest`. Cover:

```kotlin
@Test
fun `validation failure is retried with safe diagnostic codes`() {
    val client = FakeChatClient(invalidToneContent(), validContent())

    service(client).generate(wateringContext())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.userTexts.last()).contains("summary_text_tone")
}

@Test
fun `unknown evidence value is not echoed into retry prompt`() {
    val client = FakeChatClient(contentWithUnknownRef("private-value"), validContent())

    service(client).generate(wateringContext())

    assertThat(client.userTexts.last())
        .contains("unknown_evidence")
        .doesNotContain("private-value")
}
```

Also test two invalid attempts map to `STRUCTURED_OUTPUT_INVALID`, an invalid schema or mixed work-type records map to `INVALID_CONTEXT` before vector search, and empty retrieved documents still allow record-backed output.

- [ ] **Step 3: Run generation tests and verify RED**

```bash
./gradlew :application:test --tests '*ReportFeedbackRetrievalQueryPlannerTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: compilation/assertion failures from full-cycle statistics, `nextCycleActions`, `items_empty`, and identical retry prompts.

- [ ] **Step 4: Implement minimal work-type generation**

Rename the field and section mapping:

```kotlin
data class ReportFeedbackContent(
    val summary: String,
    val strengths: List<ReportFeedbackContentItem>,
    val improvements: List<ReportFeedbackContentItem>,
    val nextActions: List<ReportFeedbackContentItem>,
)
```

Planner queries must derive only from `context.report.cropName`, `context.workType.label`, and that type's statistics map. Prompt user content must contain only scoped statistics, scoped records, optional same-type previous statistics, and scoped documents.

Remove validator `items_empty`; keep validation of every supplied item. Add generator context checks:

```kotlin
if (
    context.schemaVersion != REPORT_FEEDBACK_CONTEXT_SCHEMA_VERSION ||
    context.report.farmName.isBlank() ||
    context.report.cropName.isBlank() ||
    context.records.isEmpty() ||
    context.records.any { it.workType != context.workType } ||
    (context.report.statistics["recordCount"] as? Number)?.toInt()?.let { it <= 0 } != false
) throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.INVALID_CONTEXT)
```

Build the base prompt once. On the second structured-output attempt append only normalized validator codes; normalize `unknown_evidence:<value>` to `unknown_evidence`. Reuse retrieved documents across attempts.

- [ ] **Step 5: Run generation tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackRetrievalQueryPlannerTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: PASS.

- [ ] **Step 6: Commit work-type generation**

```text
fix(coaching): 작업별 근거만 사용하는 리포트 코칭 생성

Constraint: 구조화 출력은 작업별 최대 두 번만 요청
Rejected: 단일 호출의 작업 배열 | 근거 혼입과 부분 실패를 격리하지 못함
Confidence: high
Scope-risk: moderate
Directive: 재시도 프롬프트에 원문 근거 값이나 모델 응답을 포함하지 말 것
Tested: report feedback planner, prompt, validator, generation tests
```

---

### Task 4: Create and finalize independent work-type feedback jobs

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationHandler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandler.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleServiceTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationHandlerTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandlerTest.kt`

**Interfaces:**
- Consumes: Task 1 repository/domain and Task 2/3 assembler/generator.
- Produces: `enqueue(report, workTypes)`, workType-bearing events, exact ID/snapshot guarded transitions, async report preparation entry.

- [ ] **Step 1: Write failing projection and lifecycle tests**

Update projection verification to:

```kotlin
verify(reportFeedbackLifecycleService).enqueue(
    same(completedReport),
    eq(setOf(WorkType.WATERING, WorkType.HARVEST)),
)
```

Update lifecycle tests so unordered `{HARVEST, WATERING}` creates two PENDING rows in enum order, calls `saveAll` once, and publishes two events whose IDs and work types match their rows. A repository `existsByReport_Id=true` must result in no save and no event.

- [ ] **Step 2: Write failing handler isolation tests**

Preparation tests must verify `assemble(memberId, reportId, workType)`, snapshot attachment, and a generation event carrying the same workType. Add mismatch tests for report/workType and failure mapping tests for `INVALID_CONTEXT` and generic `CONTEXT_ASSEMBLY_FAILED`.

Lock the asynchronous boundary in the same test file:

```kotlin
val preparationOn = ReportFeedbackPreparationHandler::class.java.getDeclaredMethod(
    "on",
    ReportFeedbackPreparationRequested::class.java,
)
assertThat(preparationOn.isAnnotationPresent(Async::class.java)).isTrue()

val generationOn = ReportFeedbackGenerationHandler::class.java.getDeclaredMethod(
    "on",
    ReportFeedbackGenerationRequested::class.java,
)
assertThat(generationOn.isAnnotationPresent(Async::class.java)).isFalse()
```

Generation tests must create two feedback entities and verify only the event target changes. Require status, reportId, workType, and snapshot equality before `markReady`/`markFailed`; a mismatched snapshot or workType must leave PENDING unchanged. Include an empty-item successful result.

- [ ] **Step 3: Run lifecycle tests and verify RED**

```bash
./gradlew :application:test --tests '*FarmingCycleReportProjectionServiceTest' --tests '*ReportFeedbackLifecycleServiceTest' --tests '*ReportFeedbackPreparationHandlerTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: compile failures because enqueue/events/assembler lack workType and handlers still query a report singleton.

- [ ] **Step 4: Implement exact enqueue and guarded handlers**

Projection passes only the current completed slice types:

```kotlin
reportFeedbackLifecycleService.enqueue(
    report,
    slice.records.map(CycleReportSourceRecord::workType).toSet(),
)
```

Lifecycle signature:

```kotlin
fun enqueue(report: FarmingCycleReport, workTypes: Set<WorkType>) {
    require(report.status == FarmingCycleReportStatus.COMPLETED)
    require(workTypes.isNotEmpty())
    val reportId = requireNotNull(report.id)
    if (feedbackRepository.existsByReport_Id(reportId)) return

    val saved = feedbackRepository.saveAll(
        WorkType.entries.filter(workTypes::contains).map {
            ReportFeedback.pending(report.member, report, it)
        },
    )
    saved.forEach(::publishPreparation)
}
```

Both event data classes contain `feedbackId`, `memberId`, `reportId`, and `workType`. All initial reads use `feedbackId + memberId`; all final writes use the existing locked ID query. Guard with entity report/workType/status, and for generation completion also guard `inputSnapshot == snapshotUsedForGeneration`.

Annotate only `ReportFeedbackPreparationHandler.on` with `@Async`; do not annotate generation handler.

- [ ] **Step 5: Run lifecycle tests and verify GREEN**

```bash
./gradlew :application:test --tests '*FarmingCycleReportProjectionServiceTest' --tests '*ReportFeedbackLifecycleServiceTest' --tests '*ReportFeedbackPreparationHandlerTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit the independent lifecycle**

```text
feat(coaching): 작업별 리포트 코칭 생명주기 연결

Constraint: 최초 작업 행은 완료 리포트 트랜잭션에서 함께 저장
Rejected: 리포트 합성 상태 | 부분 성공을 가리고 상태가 중복됨
Confidence: high
Scope-risk: broad
Directive: reportId 단건 조회로 작업별 피드백을 처리하지 말 것
Tested: report projection and report feedback lifecycle tests
```

---

### Task 5: Return a work-type feedback collection from the API

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryServiceTest.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/controller/ReportFeedbackController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/dto/ReportFeedbackResponses.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/reportfeedback/controller/ReportFeedbackControllerTest.kt`
- Modify: `frontend/docs/API 명세서/API 명세서/영농 리포트 조회 3909e2d9440581ef91dbf34bba44c407.md`
- Modify: `frontend/docs/API 명세서/API 명세서 0a59e2d94405832fbd1b813496c52cdf.csv`
- Modify: `frontend/docs/API 명세서/API 명세서 0a59e2d94405832fbd1b813496c52cdf_all.csv`

**Interfaces:**
- Consumes: Task 1 `findAllByReport_IdAndMember_Id` and per-row status.
- Produces: `ReportFeedbackListResult`, API `ListResponse`, child `StatusResponse`, `nextActions`, empty-list 200.

- [ ] **Step 1: Write failing query service tests**

Define:

```kotlin
data class ReportFeedbackListResult(
    val reportId: UUID,
    val feedbacks: List<ReportFeedbackDetailResult>,
)

data class ReportFeedbackDetailResult(
    val feedbackId: UUID,
    val workType: WorkType,
    val status: ReportFeedbackStatus,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val content: ReportFeedbackResultContent?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
```

Tests must return unsorted FERTILIZING/PLANTING/WATERING entities and assert enum order, READY-only content, `nextActions`, and an empty list when no rows exist. ACTIVE, SUPERSEDED, missing, and other-member reports must throw `REPORT_NOT_FOUND`.

- [ ] **Step 2: Write failing controller collection tests**

Mock a mixed result and assert:

```kotlin
.andExpect(jsonPath("$.data.reportId", equalTo(reportId.toString())))
.andExpect(jsonPath("$.data.feedbacks[0].workType", equalTo("WATERING")))
.andExpect(jsonPath("$.data.feedbacks[0].feedback.nextActions[0].text", equalTo("관수량을 기록하세요.")))
.andExpect(jsonPath("$.data.feedbacks[1].status", equalTo("FAILED")))
.andExpect(jsonPath("$.data.feedbacks[1].feedback").doesNotExist())
.andExpect(jsonPath("$.data.status").doesNotExist())
.andExpect(jsonPath("$.data.feedbacks[0].feedback.nextCycleActions").doesNotExist())
.andExpect(jsonPath("$.data.feedbacks[0].feedback.strengths[0].basis").doesNotExist())
```

Keep the unauthenticated 401 test.

- [ ] **Step 3: Run query and API tests and verify RED**

```bash
./gradlew :application:test --tests '*ReportFeedbackQueryServiceTest'
./gradlew :api:test --tests '*ReportFeedbackControllerTest'
```

Expected: compilation/assertion failures because the current contract is a singleton with top-level status and `nextCycleActions`.

- [ ] **Step 4: Implement collection query and DTO mapping**

`get(memberId, reportId)` must require an owned `COMPLETED` report, load all rows, sort by `workType.ordinal`, and return an empty `feedbacks` list when none exist. READY maps summary and section items; PENDING/FAILED maps null content. Remove now-unreachable `REPORT_FEEDBACK_NOT_FOUND` only after confirming `rg -n 'REPORT_FEEDBACK_NOT_FOUND' backend` has no remaining caller.

DTO shape:

```kotlin
data class ListResponse(val reportId: UUID, val feedbacks: List<StatusResponse>)

data class StatusResponse(
    val feedbackId: UUID,
    val workType: WorkType,
    val status: ReportFeedbackStatus,
    val inputPrepared: Boolean,
    val failureCode: String?,
    val feedback: FeedbackResponse?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
```

Update API markdown and both CSV indices from `ReportFeedbackStatusResponse` to `ReportFeedbackListResponse`; document work-type children and `nextActions`.

- [ ] **Step 5: Run query and API tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackQueryServiceTest'
./gradlew :api:test --tests '*ReportFeedbackControllerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit the collection API**

```text
feat(api): 작업별 리포트 코칭 상태 목록 제공

Constraint: 기존 GET 경로는 유지하고 응답 본문만 작업별 목록으로 전환
Rejected: 최상위 합성 상태 | 부분 성공을 표현하지 못함
Confidence: high
Scope-risk: broad
Directive: READY가 아닌 작업에 feedback 본문을 노출하지 말 것
Tested: ReportFeedback query and controller tests
```

---

### Task 6: Connect the HTML report tabs to per-work feedback

**Files:**
- Modify: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes: Task 5 `{reportId, feedbacks[]}` response and existing lower-camel report statistics tabs.
- Produces: explicit enum-to-tab mapping, selected-work rendering, report-level polling independent of tab display.

- [ ] **Step 1: Run a static RED check for the new contract**

Workdir: repository root.

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const token of ["PEST_CONTROL: \"pestControl\"","reportFeedbacksByWorkType","feedbacks.some","content?.nextActions","작업별 코칭은 위 작업 탭에서 확인하세요"]){if(!h.includes(token))throw new Error(`missing ${token}`)}'
```

Expected: FAIL on the first missing token.

- [ ] **Step 2: Implement explicit mapping and state separation**

Add:

```javascript
const reportFeedbackWorkTypeKeys = {
  PLANTING: "planting",
  WATERING: "watering",
  FERTILIZING: "fertilizing",
  PEST_CONTROL: "pestControl",
  WEEDING: "weeding",
  PRUNING: "pruning",
  HARVEST: "harvest",
  ETC: "etc"
};
```

Replace single `reportFeedbackStatus` state with a response cache and map:

```javascript
reportFeedbackResponse: null,
reportFeedbacksByWorkType: {},
```

When a response arrives, map entries by `reportFeedbackWorkTypeKeys[item.workType]`. The selected-tab renderer must:

- show guidance for `all` without changing the polling timer;
- show no-coaching text for a zero-record tab;
- show only the selected work type's PENDING/READY/FAILED state;
- read `content?.nextActions`;
- preserve the global stop button while another work type is PENDING.

Polling continues only when:

```javascript
const hasPending = (response?.feedbacks || []).some((item) => item?.status === "PENDING");
```

Tab changes call the selected renderer with cached data and do not fetch again.

- [ ] **Step 3: Run static checks and JavaScript syntax validation**

```bash
node -e 'const fs=require("fs");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const token of ["PEST_CONTROL: \"pestControl\"","reportFeedbacksByWorkType","feedbacks || []).some","content?.nextActions","작업별 코칭은 위 작업 탭에서 확인하세요"]){if(!h.includes(token))throw new Error(`missing ${token}`)}'
node -e 'const fs=require("fs"),vm=require("vm");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const m of h.matchAll(/<script(?:\\s[^>]*)?>([\\s\\S]*?)<\\/script>/g))new vm.Script(m[1]);'
```

Expected: both commands exit 0.

- [ ] **Step 4: Commit the HTML work-tab rendering**

```text
feat(front): 작업 탭별 리포트 코칭 표시

Constraint: HTML 개발 콘솔만 변경하고 iOS 화면은 범위에서 제외
Rejected: 코칭 전용 탭 추가 | 기존 작업 탭과 중복됨
Confidence: high
Scope-risk: moderate
Directive: 전체 탭 선택이 리포트 단위 폴링을 중단하지 않게 유지할 것
Tested: required token and inline JavaScript syntax checks
Not-tested: live mixed-state server response
```

---

### Task 7: Move RecordFeedback preparation off the request thread

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackPreparationListener.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackPreparationListenerTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/recordfeedback/RecordFeedbackLifecycleIntegrationTest.kt`

**Interfaces:**
- Consumes: existing `AsyncConfig(@EnableAsync)` and record preparation/generation events.
- Produces: `@Async` only on the first record preparation listener, deterministic eventual integration tests.

- [ ] **Step 1: Write a failing async entry-point test**

```kotlin
@Test
fun `record feedback preparation listener is the async entry point`() {
    val on = RecordFeedbackPreparationListener::class.java.getDeclaredMethod(
        "on",
        RecordFeedbackPreparationRequested::class.java,
    )

    assertThat(on.isAnnotationPresent(Async::class.java)).isTrue()
    val generationOn = RecordFeedbackGenerationListener::class.java.getDeclaredMethod(
        "on",
        RecordFeedbackGenerationRequested::class.java,
    )
    assertThat(generationOn.isAnnotationPresent(Async::class.java)).isFalse()
}
```

- [ ] **Step 2: Run the listener test and verify RED**

```bash
./gradlew :application:test --tests '*RecordFeedbackPreparationListenerTest'
```

Expected: FAIL because the preparation listener lacks `@Async`.

- [ ] **Step 3: Add the single async boundary and make integration assertions eventual**

Add `@Async` to `RecordFeedbackPreparationListener.on` only. Do not annotate `RecordFeedbackGenerationListener`.

In `RecordFeedbackLifecycleIntegrationTest`, replace immediate READY lookups after create/update with a bounded helper:

```kotlin
private fun awaitFeedback(recordId: UUID, revision: Long): RecordFeedback {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
        val feedback = recordFeedbackRepository.findByRecord_IdAndSourceRevision(recordId, revision)
        if (feedback?.status == RecordFeedbackStatus.READY) return feedback
        Thread.sleep(25)
    }
    error("record feedback did not become READY: recordId=$recordId revision=$revision")
}
```

Use this helper before update/delete and before each READY assertion so background writes finish before the next mutation or test cleanup.

- [ ] **Step 4: Run record async tests and verify GREEN**

```bash
./gradlew :application:test --tests '*RecordFeedbackPreparationListenerTest'
./gradlew :api:test --tests '*RecordFeedbackLifecycleIntegrationTest'
```

Expected: PASS without race failures.

- [ ] **Step 5: Commit record feedback async behavior**

```text
fix(coaching): 기록 코칭 생성을 저장 요청과 분리

Constraint: 기존 Spring async 설정을 재사용하고 전용 executor는 추가하지 않음
Rejected: generation listener까지 중첩 async 처리 | 순서와 오류 추적만 복잡해짐
Confidence: high
Scope-risk: moderate
Directive: 준비 listener만 async 진입점으로 유지할 것
Tested: preparation listener and record feedback lifecycle integration tests
```

---

### Task 8: Full verification and live HTML smoke test

**Files:**
- Modify only if verification exposes a defect: files already owned by Tasks 1–7.

**Interfaces:**
- Consumes: all completed tasks.
- Produces: clean targeted/full tests, valid HTML JavaScript, evidence for final handoff.

- [ ] **Step 1: Run all backend module tests**

Workdir: `backend`

```bash
./gradlew :domain:test :application:test :api:test
```

Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Run repository static checks**

Workdir: repository root.

```bash
node -e 'const fs=require("fs"),vm=require("vm");const h=fs.readFileSync("frontend/dev-rag-test.html","utf8");for(const m of h.matchAll(/<script(?:\\s[^>]*)?>([\\s\\S]*?)<\\/script>/g))new vm.Script(m[1]);'
git diff --check
rg -n 'NEXT_CYCLE_ACTION|nextCycleActions|findByReport_IdAndMember_Id|findByReport_Id\(' backend frontend/dev-rag-test.html
```

Expected: syntax check and diff check exit 0; the final `rg` finds no stale ReportFeedback singleton/output symbols. Any intentional historical design/plan references are outside this command scope.

- [ ] **Step 3: Inspect scope and commit history**

```bash
git status --short
git log --oneline -8
```

Expected: only `?? .claude/` remains untracked; implementation files are committed in focused Conventional/Lore commits.

- [ ] **Step 4: Run the server and manually smoke-test HTML when local dependencies are available**

Workdir: `backend`

```bash
./gradlew :api:bootRun
```

Verify a completed report containing at least two work types:

1. The GET response contains one child per recorded work type.
2. A READY work type is visible while another remains PENDING.
3. Switching tabs sends no extra GET beyond the active polling loop.
4. `all` shows guidance and does not stop polling.
5. Polling stops when all children are READY or FAILED.
6. Missing-work tabs show no AI coaching.

If PostgreSQL/Redis/vector/chat dependencies are unavailable, stop the server and report the exact skipped smoke-test dependency; do not weaken automated verification.

---

## Plan Self-Review

- Spec coverage: domain cardinality, exact slice, per-work context/RAG/output, independent lifecycle, async entry, collection API, HTML tabs, API docs, empty lists, stable order, and residual non-goals each have an owning task.
- Placeholder scan: no TBD/TODO or undefined implementation step remains.
- Type consistency: `WorkType` is used end-to-end; output uses `nextActions`; list result/DTO are distinct from per-work status; events carry the same four identity fields.
- YAGNI check: no aggregate parent/status, shared feedback framework, statistics strategy hierarchy, executor, queue, retry API, or stale model is introduced.
