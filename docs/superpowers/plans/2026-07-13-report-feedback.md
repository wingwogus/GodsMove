# 완료 리포트 기반 코칭 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 완료된 영농 사이클 리포트마다 상세 ReportFeedback을 한 번 자동 생성하고 통일된 /feedback 조회 API로 상태와 결과를 제공한다.

**Architecture:** ReportFeedback은 RecordFeedback과 분리된 엔티티와 생성 파이프라인이다. 완료 리포트 투영은 PENDING을 idempotent하게 만들고, AFTER_COMMIT 준비와 생성 단계를 통해 스냅샷, RAG, 구조화 LLM 출력을 처리한다. 결과는 summary 컬럼과 순서 있는 ReportFeedbackItem 행으로 저장하며 감사 입력과 인용만 JSONB로 둔다.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA/Hibernate JSONB, Spring AI ChatClient/VectorStore, PostgreSQL pgvector, JUnit 5, Mockito, MockMvc.

## Global Constraints

- member 용어를 사용하고 user 또는 userId를 새로 만들지 않는다.
- 의존성, 작업 큐, 메시지 브로커, 공통 Feedback 부모는 추가하지 않는다.
- domain -> application -> api 의존 방향을 지킨다.
- ReportFeedback은 완료 리포트당 한 건이며 STALE, sourceRevision 비교, 수동 재생성은 만들지 않는다.
- 이전 리포트와 기술 문서가 없으면 해당 비교와 기술 권고만 생략하고 나머지 결과는 READY가 될 수 있어야 한다.
- 목록 항목의 제품 상한은 만들지 않되 빈 목록, 빈 본문, 중복, 근거 없는 참조는 거부한다.
- 기존 RecordFeedbackGenerationService를 범용화하지 않는다. 리포트 전용 context, prompt, validator, generator를 둔다.

---

## 파일 구조

| 경로 | 책임 |
| --- | --- |
| backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedback.kt | 상태 전이, summary, 항목 소유 |
| backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackItem.kt | 섹션, 순서, 근거, 본문을 가진 자식 행 |
| backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackRepository.kt | report/member 조회와 비관적 잠금 |
| backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation | context, RAG 질의, 프롬프트, 구조화 출력 검증과 생성 |
| backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle | PENDING 생성, AFTER_COMMIT 준비/생성, 조회 결과 매핑 |
| backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt | COMPLETED report 저장 후 lifecycle 호출 |
| backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/ReportFeedbackController.kt | 리포트 피드백 GET endpoint |
| backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/ReportFeedbackResponses.kt | application 결과의 HTTP 변환 |

### Task 1: ReportFeedback 도메인 모델과 영속 계약

**Files:**
- Create: backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedback.kt
- Create: backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackItem.kt
- Create: backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackRepository.kt
- Create: backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackStatus.kt
- Create: backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackItemSection.kt
- Test: backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackTest.kt
- Test: backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/ReportFeedbackRepositoryJsonTest.kt

**Produces:**

~~~kotlin
enum class ReportFeedbackStatus { PENDING, READY, FAILED }
enum class ReportFeedbackItemSection { STRENGTH, IMPROVEMENT, NEXT_CYCLE_ACTION }
data class ReportFeedbackItemDraft(
    val section: ReportFeedbackItemSection,
    val basis: String,
    val text: String,
)
fun ReportFeedback.attachInputSnapshot(snapshot: Map<String, Any?>)
fun ReportFeedback.markReady(
    summary: String, items: List<ReportFeedbackItemDraft>,
    citations: List<Map<String, Any?>>, auditStatus: String,
    auditWarnings: List<String>, modelName: String, embeddingModel: String,
)
fun ReportFeedback.markFailed(code: String)
~~~

- [ ] **Step 1: Write the failing tests**

~~~kotlin
@Test
fun ready_feedback_stores_summary_and_unbounded_ordered_items() {
    val feedback = ReportFeedback.pending(member, report)
    feedback.markReady(
        summary = "이번 사이클은 관수 간격을 안정적으로 유지했습니다.",
        items = listOf(
            draft(STRENGTH, "관수 4회", "관수를 4회 기록해 건조 구간을 줄였습니다."),
            draft(IMPROVEMENT, "시비 1회", "시비 간격과 효과를 다음 사이클에 비교하세요."),
            draft(NEXT_CYCLE_ACTION, "파종 전", "파종 전 토양 상태를 기록하세요."),
            draft(NEXT_CYCLE_ACTION, "생육 중", "주간 관수 간격을 기록하세요."),
        ),
        citations = emptyList(), auditStatus = "PASS", auditWarnings = emptyList(),
        modelName = "chat", embeddingModel = "embedding",
    )
    assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.READY)
    assertThat(feedback.items()).extracting(ReportFeedbackItem::displayOrder)
        .containsExactly(0, 1, 2, 3)
}
~~~

- [ ] **Step 2: Run the test to verify failure**

Run: ./gradlew :domain:test --tests '*ReportFeedbackTest'

Expected: FAIL because ReportFeedback types do not exist.

- [ ] **Step 3: Implement the minimal domain model**

~~~kotlin
@Entity
@Table(name = "report_feedback")
class ReportFeedback(/* id, member, report, status and audit fields */) : BaseTimeEntity() {
    @Column(nullable = false, length = 500)
    var summary: String? = null
        private set

    @OneToMany(mappedBy = "reportFeedback", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private val itemRows = mutableListOf<ReportFeedbackItem>()

    companion object {
        fun pending(member: Member, report: FarmingCycleReport) =
            ReportFeedback(member = member, report = report, status = ReportFeedbackStatus.PENDING)
    }
}
~~~

Use report_feedback and report_feedback_item. Add a unique report_id on ReportFeedback and a unique report_feedback_id/display_order on the item table. Require a nonblank summary, at least one nonblank item, and no maximum item count.

- [ ] **Step 4: Run domain tests**

Run: ./gradlew :domain:test --tests '*ReportFeedback*'

Expected: PASS; JSONB mapping covers inputSnapshot, citations, and auditWarnings.

- [ ] **Step 5: Commit**

~~~bash
git add backend/domain/src/main backend/domain/src/test
git commit -m "feat(coaching): 리포트 피드백 도메인 추가"
~~~

### Task 2: 리포트 전용 context와 구조화 출력 모델

**Files:**
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/ReportFeedbackFailure.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContext.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContent.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssembler.kt
- Modify: backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportRepository.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssemblerTest.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContentTest.kt

**Produces:**

~~~kotlin
data class ReportFeedbackContext(
    val schemaVersion: Int,
    val report: ReportFeedbackReport,
    val records: List<CycleReportSourceRecord>,
    val previousReport: ReportFeedbackPreviousReport?,
    val warnings: List<String>,
)
data class ReportFeedbackContent(
    val summary: String,
    val strengths: List<ReportFeedbackContentItem>,
    val improvements: List<ReportFeedbackContentItem>,
    val nextCycleActions: List<ReportFeedbackContentItem>,
)
~~~

- [ ] **Step 1: Write failing context tests**

~~~kotlin
@Test
fun assemble_includes_target_cycle_records_and_previous_completed_report() {
    whenever(reportRepository.findByIdAndMember_Id(reportId, memberId)).thenReturn(report)
    whenever(sourceLoader.load(ReportScope(memberId, farmId, cropId))).thenReturn(allScopeRecords)
    whenever(reportRepository.findTopPreviousCompleted(memberId, farmId, cropId, report.endsAt!!, reportId))
        .thenReturn(previous)

    val context = assembler.assemble(memberId, reportId)

    assertThat(context.records).extracting(CycleReportSourceRecord::id)
        .containsExactlyElementsOf(selectedCycleRecordIds)
    assertThat(context.previousReport?.id).isEqualTo(previousId)
}
~~~

- [ ] **Step 2: Run the test to verify failure**

Run: ./gradlew :application:test --tests '*ReportFeedbackContextAssemblerTest'

Expected: FAIL because context classes and the previous-completed query do not exist.

- [ ] **Step 3: Implement deterministic assembly**

Add findTopPreviousCompleted(memberId, farmId, cropId, beforeEndsAt, excludedReportId), ordered by endsAt desc and id desc. The assembler rejects a non-COMPLETED target, loads its scope, and takes records inclusively between startsAt and endsAt.

~~~kotlin
private fun selectedRecords(report: FarmingCycleReport) =
    sourceLoader.load(ReportScope(report.member.id!!, report.farm.id!!, report.crop.id!!))
        .filter { !it.workedAt.isBefore(report.startsAt) && !it.workedAt.isAfter(requireNotNull(report.endsAt)) }
~~~

Do not add weather input. previousReport remains nullable and input snapshot remains audit JSONB.

- [ ] **Step 4: Run context tests**

Run: ./gradlew :application:test --tests '*ReportFeedbackContext*'

Expected: PASS for previous-present, previous-absent, range filtering, and invalid target status.

- [ ] **Step 5: Commit**

~~~bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/report backend/application/src/main backend/application/src/test
git commit -m "feat(coaching): 리포트 코칭 입력 컨텍스트 구성"
~~~

### Task 3: RAG 검색, 프롬프트, 출력 검증과 생성

**Files:**
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackRetrievalQueryPlanner.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationResult.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt

**Produces:** ReportFeedbackGenerationService.generate(context): ReportFeedbackGenerationResult.

- [ ] **Step 1: Write failing generator tests**

~~~kotlin
@Test
fun generation_remains_ready_capable_when_no_technical_document_is_retrieved() {
    whenever(vectorStore.similaritySearch(any<SearchRequest>())).thenReturn(emptyList())
    stubStructuredContent(validContent())

    val result = service.generate(context)

    assertThat(result.content.summary).isNotBlank()
    assertThat(result.citations).anyMatch { citation -> citation["sourceType"] == RagSourceType.FARMING_RECORD.name }
}

@Test
fun validator_rejects_unknown_evidence_and_duplicate_items_without_count_cap() {
    val warnings = ReportFeedbackOutputValidator.validate(contentWithDuplicateAndUnknownRef(), context, emptyList())
    assertThat(warnings).contains("unknown_evidence:unknown", "duplicate_item")
}
~~~

- [ ] **Step 2: Run the test to verify failure**

Run: ./gradlew :application:test --tests '*ReportFeedback*Generation*' --tests '*ReportFeedbackOutputValidatorTest'

Expected: FAIL because report-specific generation classes do not exist.

- [ ] **Step 3: Implement generation rules**

The planner produces deterministic queries from crop name, work types in the target cycle, and report statistics signals. Search only TECH_DOCUMENT for target crop and GENERAL. A retrieval exception is RETRIEVAL_FAILED; an empty successful search is permitted.

~~~kotlin
private fun allowedEvidenceRefs(context: ReportFeedbackContext, documents: List<ReportFeedbackEvidence>) =
    buildSet {
        addAll(context.records.map { record -> "record:" + record.id })
        context.previousReport?.let { previous -> add("report:" + previous.id) }
        addAll(documents.map(ReportFeedbackEvidence::id))
    }
~~~

Require nonblank summary and at least one valid item across every section. Reject blank basis/text, unknown refs, duplicate normalized section+basis+text, and refs to absent previous reports/documents. Retain two structured-output attempts and existing model/audit metadata behavior.

- [ ] **Step 4: Run generator tests**

Run: ./gradlew :application:test --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackOutputValidatorTest'

Expected: PASS; empty RAG does not prevent record-evidence-backed coaching.

- [ ] **Step 5: Commit**

~~~bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation
git commit -m "feat(coaching): 리포트 코칭 RAG 생성 추가"
~~~

### Task 4: AFTER_COMMIT lifecycle와 상태 조회

**Files:**
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleService.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationRequested.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationListener.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationService.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationRequested.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationListener.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationProcessor.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackDetailResult.kt
- Create: backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt
- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleServiceTest.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationProcessorTest.kt
- Test: backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryServiceTest.kt

**Produces:** enqueue(report), get(memberId, reportId), ReportFeedbackDetailResult.

- [ ] **Step 1: Write failing lifecycle tests**

~~~kotlin
@Test
fun enqueue_completed_report_creates_one_pending_feedback_and_publishes_event() {
    whenever(feedbackRepository.findByReport_Id(reportId)).thenReturn(null)
    whenever(feedbackRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] }

    lifecycle.enqueue(completedReport)
    lifecycle.enqueue(completedReport)

    verify(feedbackRepository, times(1)).save(any())
    verify(eventPublisher, times(1)).publishEvent(ReportFeedbackPreparationRequested(any(), memberId, reportId))
}
~~~

- [ ] **Step 2: Run the test to verify failure**

Run: ./gradlew :application:test --tests '*ReportFeedback*Lifecycle*' --tests '*ReportFeedback*GenerationProcessor*' --tests '*ReportFeedback*Query*'

Expected: FAIL because lifecycle types and REPORT_FEEDBACK_NOT_FOUND do not exist.

- [ ] **Step 3: Implement the lifecycle**

~~~kotlin
@Transactional
fun enqueue(report: FarmingCycleReport): ReportFeedback {
    require(report.status == FarmingCycleReportStatus.COMPLETED)
    feedbackRepository.findByReport_Id(requireNotNull(report.id))?.let { existing -> return existing }
    val saved = feedbackRepository.save(ReportFeedback.pending(report.member, report))
    eventPublisher.publishEvent(
        ReportFeedbackPreparationRequested(requireNotNull(saved.id), requireNotNull(report.member.id), requireNotNull(report.id)),
    )
    return saved
}
~~~

Mirror record feedback short REQUIRES_NEW transactions: preparation assembles and saves an ObjectMapper snapshot then publishes generation; processor reads snapshot, calls LLM outside write transaction, then locks and marks READY or FAILED. Add REPORT_FEEDBACK_NOT_FOUND as COACHING_003. Do not add retry or stale transitions.

ReportFeedbackDetailResult contains feedbackId, reportId, status, inputPrepared, failureCode, nullable content(summary, strengths, improvements, nextCycleActions), createdAt, updatedAt.

- [ ] **Step 4: Run lifecycle tests**

Run: ./gradlew :application:test --tests '*ReportFeedback*Lifecycle*' --tests '*ReportFeedback*GenerationProcessor*' --tests '*ReportFeedback*Query*'

Expected: PASS for idempotent enqueue, context failure, generator failure, READY mapping, and missing resource cases.

- [ ] **Step 5: Commit**

~~~bash
git add backend/application/src/main backend/application/src/test
git commit -m "feat(coaching): 리포트 피드백 생성 흐름 추가"
~~~

### Task 5: 완료 리포트 투영 연결

**Files:**
- Modify: backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt
- Modify: backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt

- [ ] **Step 1: Write failing projection tests**

~~~kotlin
@Test
fun completed_report_is_enqueued_after_projection_save() {
    service.rebuild(scopeWithFinalHarvest)
    verify(reportFeedbackLifecycleService).enqueue(activeReport)
}

@Test
fun active_report_is_not_enqueued() {
    service.rebuild(scopeWithoutFinalHarvest)
    verifyNoInteractions(reportFeedbackLifecycleService)
}
~~~

- [ ] **Step 2: Run the test to verify failure**

Run: ./gradlew :application:test --tests '*FarmingCycleReportProjectionServiceTest'

Expected: FAIL because lifecycle is not injected.

- [ ] **Step 3: Inject and call lifecycle after save**

~~~kotlin
val saved = reportRepository.save(report)
if (saved.status == FarmingCycleReportStatus.COMPLETED) {
    reportFeedbackLifecycleService.enqueue(saved)
}
~~~

Use the returned entity. Completed re-projection is safe because lifecycle owns duplicate suppression; never enqueue superseded reports.

- [ ] **Step 4: Run projection tests**

Run: ./gradlew :application:test --tests '*FarmingCycleReportProjectionServiceTest'

Expected: PASS.

- [ ] **Step 5: Commit**

~~~bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt
git commit -m "feat(report): 완료 리포트 코칭 자동 생성 연결"
~~~

### Task 6: 통일된 HTTP 조회 계약

**Files:**
- Modify: backend/api/src/main/kotlin/com/chamchamcham/api/coaching/controller/RecordFeedbackController.kt
- Modify: backend/api/src/test/kotlin/com/chamchamcham/api/coaching/controller/RecordFeedbackControllerTest.kt
- Create: backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/ReportFeedbackController.kt
- Create: backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/ReportFeedbackResponses.kt
- Create: backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/ReportFeedbackControllerTest.kt

**Produces:**
- GET /api/v1/farming-records/{recordId}/feedback
- GET /api/v1/farming-reports/{reportId}/feedback

- [ ] **Step 1: Write failing MVC tests**

~~~kotlin
@Test
fun record_feedback_uses_unified_feedback_path() {
    mockMvc.perform(get("/api/v1/farming-records/{recordId}/feedback", recordId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
}

@Test
fun report_feedback_returns_ready_sections_without_audit_internals() {
    whenever(queryService.get(memberId, reportId)).thenReturn(readyResult())
    mockMvc.perform(get("/api/v1/farming-reports/{reportId}/feedback", reportId).with(authenticatedMember(memberId.toString())))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.feedback.summary").value("이번 사이클 요약"))
        .andExpect(jsonPath("$.data.feedback.strengths[0].text").isNotEmpty)
        .andExpect(jsonPath("$.data.citations").doesNotExist())
}
~~~

- [ ] **Step 2: Run MVC tests to verify failure**

Run: ./gradlew :api:test --tests '*RecordFeedbackControllerTest' --tests '*ReportFeedbackControllerTest'

Expected: FAIL because report controller does not exist and record route is coaching-feedback.

- [ ] **Step 3: Implement controllers and DTOs**

Change only RecordFeedbackController RequestMapping to /api/v1/farming-records/{recordId}/feedback; preserve regenerate as /feedback/regenerate. Create ReportFeedbackController with GET and the same UUID principal parser used by FarmingCycleReportController.

~~~kotlin
data class FeedbackResponse(
    val summary: String,
    val strengths: List<ItemResponse>,
    val improvements: List<ItemResponse>,
    val nextCycleActions: List<ItemResponse>,
)
data class ItemResponse(val text: String)
~~~

Expose display content only: no basis, citations, input snapshot, audit, model, or raw source records.

- [ ] **Step 4: Run MVC tests**

Run: ./gradlew :api:test --tests '*RecordFeedbackControllerTest' --tests '*ReportFeedbackControllerTest'

Expected: PASS for PENDING, READY, FAILED, authentication, malformed principal, and not-found cases.

- [ ] **Step 5: Commit**

~~~bash
git add backend/api/src/main backend/api/src/test
git commit -m "feat(api): 리포트 피드백 조회 API 추가"
~~~

### Task 7: 통합 회귀와 최종 검증

**Files:**
- Create: backend/api/src/test/kotlin/com/chamchamcham/api/report/ReportFeedbackLifecycleIntegrationTest.kt

- [ ] **Step 1: Write integration test**

~~~kotlin
@Test
fun final_harvest_projection_creates_pending_report_feedback() {
    val completedReport = persistCompletedReportFixture()
    reportFeedbackLifecycleService.enqueue(completedReport)

    assertThat(reportFeedbackRepository.findByReport_Id(requireNotNull(completedReport.id))?.status)
        .isEqualTo(ReportFeedbackStatus.PENDING)
}
~~~

The test uses a mocked generator port or lifecycle-only assertion and must not call a real LLM or vector database.

- [ ] **Step 2: Run integration test**

Run: ./gradlew :api:test --tests '*ReportFeedbackLifecycleIntegrationTest'

Expected: PASS.

- [ ] **Step 3: Run final checks**

Run: git diff --check

Expected: no whitespace errors.

Run: ./gradlew :domain:test :application:test :api:test

Expected: BUILD SUCCESSFUL. Existing MockBean deprecation warnings may remain, but no new feature failure.

- [ ] **Step 4: Inspect scope**

Run: git status --short

Expected: only ReportFeedback, report projection/API, tests, and approved docs are changed.

- [ ] **Step 5: Commit remaining test changes with Lore trailers**

~~~bash
git add backend/api/src/test
git commit -m "test(coaching): 리포트 피드백 생성 흐름 검증" \
  -m "Constraint: 실제 LLM과 벡터 DB 없이 transaction/event 연결을 검증한다
Confidence: high
Scope-risk: narrow
Tested: ./gradlew :domain:test :application:test :api:test"
~~~

## Plan self-review

- Spec coverage: 독립 모델(Task 1), 리포트·기록·직전 리포트·문서 근거(Task 2–3), 근거 부재 시 부분 READY(Task 3), AFTER_COMMIT 자동 생성(Task 4–5), 통일 API(Task 6), 회귀 검증(Task 7)를 모두 포함한다.
- Scope: stale, sourceRevision, 수동 재생성, 큐·브로커, 프론트 화면을 추가하지 않는다.
- Type consistency: application 경계는 ReportFeedbackContext, ReportFeedbackContent, ReportFeedbackGenerationResult, ReportFeedbackDetailResult를 사용하며 api는 detail result만 참조한다.
- 미완성 표기 점검: 임시 표기 없이 테스트와 명령, 생성 타입을 각각 명시했다.
