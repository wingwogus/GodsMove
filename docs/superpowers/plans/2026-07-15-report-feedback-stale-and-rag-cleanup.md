# Report Feedback STALE and RAG Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 완료 리포트의 통계는 계속 갱신하되 기존 코칭은 `STALE`로 숨기고 수동 재생성하며, 실제 배포 계약을 TECH_DOCUMENT-only `vector_store`로 정리한다.

**Architecture:** 리포트 코칭은 `(report_id, work_type)` 한 행을 유지하고 canonical `ReportFeedbackContext` SHA-256 fingerprint로 입력 변경을 판별한다. STALE 전환은 외부 호출을 만들지 않고, 사용자 재생성만 최신 fingerprint를 가진 PENDING을 만든다. 일반 코칭의 벡터 검색은 TECH_DOCUMENT로 한정하고 농장·작물·기록·기간 정보는 기존 관계형 context에서만 사용한다.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 3.5.4, Spring Data JPA, Jackson, PostgreSQL 16/pgvector, Spring AI 1.1.8, JUnit 5, Mockito, AssertJ

## Global Constraints

- 완료 리포트의 기록 수정·추가·삭제와 통계 재계산을 허용한다.
- 통계 변경만으로 OpenClaw preparation/generation 이벤트를 발행하지 않는다.
- `STALE` 응답은 `content=null`, `inputPrepared=false`, `failureCode=null`이다.
- `FAILED`와 `STALE`만 수동 재생성할 수 있고 `READY`는 거부한다.
- 신규 work type은 자동 생성하지 않고 STALE placeholder로 만든다.
- 현재 리포트 통계에 존재하지 않는 work type은 GET 목록에서 제외한다.
- 새 의존성을 추가하지 않는다.
- 구현 중에는 focused test만 실행하고 마지막에 `./gradlew test`, `./gradlew build`를 각각 한 번 실행한다.

---

### Task 1: 중단된 완료 리포트 불변화 변경 복구

**Files:**
- Restore to `HEAD`: `backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt`
- Restore to `HEAD`: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Restore to `HEAD`: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Restore to `HEAD`: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`
- Restore to `HEAD`: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt`
- Restore to `HEAD`: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt`
- Restore to `HEAD`: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt`

**Interfaces:**
- Consumes: commit `011bb49e`의 승인된 STALE 설계.
- Produces: 완료 리포트 통계 재계산이 가능한 깨끗한 `HEAD` 기준선.

- [ ] **Step 1: 중단된 불변화 diff만 확인한다**

Run:

```bash
git diff -- backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt
```

Expected: `COMPLETED_REPORT_IMMUTABLE`, `assertRecordMutable`, `assertCompletedReportsUnchanged` 관련 미커밋 변경만 표시된다.

- [ ] **Step 2: 위 7개 경로만 HEAD로 복구한다**

```bash
git restore -- backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReport.kt backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportTest.kt
```

Expected: 위 7개 경로가 `git status --short`에서 사라지고 `.claude/`, `.omx/`는 그대로 남는다.

---

### Task 2: ReportFeedback 상태와 canonical fingerprint 도입

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextFingerprint.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextFingerprintTest.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackStatus.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedback.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/CycleReportStatistics.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackTest.kt`

**Interfaces:**
- Produces: `ReportFeedbackContextFingerprint.calculate(context: ReportFeedbackContext): String`.
- Produces: `CycleReportStatistics.recordCountFor(workType: WorkType): Int`.
- Produces: `ReportFeedback.pending(member, report, workType, sourceFingerprint)` and `ReportFeedback.stalePlaceholder(member, report, workType)`.
- Produces: `markStale()` and `retry(sourceFingerprint: String)`.

- [ ] **Step 1: fingerprint와 상태 전이 실패 테스트를 작성한다**

```kotlin
@Test
fun `map insertion order does not change the context fingerprint`() {
    val first = context(statistics = linkedMapOf("count" to 2, "amount" to 3))
    val second = context(statistics = linkedMapOf("amount" to 3, "count" to 2))
    assertThat(fingerprint.calculate(first)).isEqualTo(fingerprint.calculate(second))
}

@Test
fun `ready feedback becomes stale and retries with the latest fingerprint`() {
    val feedback = readyFeedback(sourceFingerprint = "a".repeat(64))
    feedback.markStale()
    assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.STALE)
    feedback.retry("b".repeat(64))
    assertThat(feedback.status).isEqualTo(ReportFeedbackStatus.PENDING)
    assertThat(feedback.sourceFingerprint).isEqualTo("b".repeat(64))
    assertThat(feedback.inputSnapshot).isNull()
}
```

- [ ] **Step 2: focused test가 현재 실패하는지 확인한다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*ReportFeedbackTest' :application:test --tests '*ReportFeedbackContextFingerprintTest'
```

Expected: `STALE`, `sourceFingerprint`, `ReportFeedbackContextFingerprint` 미정의로 FAIL.

- [ ] **Step 3: 최소 상태 모델과 SHA-256 canonical JSON을 구현한다**

```kotlin
enum class ReportFeedbackStatus { PENDING, READY, FAILED, STALE }

fun markStale() {
    if (status == ReportFeedbackStatus.STALE) return
    status = ReportFeedbackStatus.STALE
    failureCode = null
    inputSnapshot = null
}

fun retry(sourceFingerprint: String) {
    check(status == ReportFeedbackStatus.FAILED || status == ReportFeedbackStatus.STALE)
    require(sourceFingerprint.matches(Regex("^[0-9a-f]{64}$")))
    status = ReportFeedbackStatus.PENDING
    this.sourceFingerprint = sourceFingerprint
    failureCode = null
    inputSnapshot = null
}
```

`ReportFeedbackContextFingerprint`는 `ObjectMapper.valueToTree<JsonNode>()` 결과의 object field 이름을 재귀적으로 정렬하고 array 순서는 보존한 뒤 UTF-8 JSON bytes를 `MessageDigest.getInstance("SHA-256")`로 해시한다.

- [ ] **Step 4: focused test를 통과시킨다**

Run:

```bash
cd backend && ./gradlew :domain:test --tests '*ReportFeedbackTest' :application:test --tests '*ReportFeedbackContextFingerprintTest'
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: lifecycle reconcile, STALE API, 비동기 경합 방지

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackRepository.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationHandler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackLifecycleServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackPreparationHandlerTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandlerTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportProjectionServiceTest.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackRepositoryTest.kt`
- Create: `backend/docs/db/report-feedback-stale-schema.sql`

**Interfaces:**
- Replaces: `enqueue(report, workTypes)` with `reconcile(report, workTypes)`.
- Produces: lifecycle `regenerate(memberId, reportId, workType)` that locks, assembles latest context, replaces fingerprint, and publishes preparation.
- Produces: `ReportFeedbackPreparationRequested.sourceFingerprint` and `ReportFeedbackGenerationRequested.sourceFingerprint`.

- [ ] **Step 1: 핵심 lifecycle/API/race 실패 테스트를 추가한다**

Required assertions:

```kotlin
verify(eventPublisher, never()).publishEvent(any()) // READY -> STALE
assertThat(stale.content).isNull()
assertThat(stale.inputPrepared).isFalse()
assertThat(stale.failureCode).isNull()
verify(generationService, never()).generate(any()) // event fingerprint mismatch
```

Test cases: 최초 PENDING 자동 이벤트, READY 입력 변경 STALE 무이벤트, 신규 work type STALE placeholder, 제거 work type GET 제외, STALE 수동 재생성 최신 fingerprint, 오래된 preparation/generation 결과 저장 거부.

- [ ] **Step 2: focused test가 실패하는지 확인한다**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*ReportFeedbackLifecycleServiceTest' --tests '*ReportFeedbackQueryServiceTest' --tests '*ReportFeedbackPreparationHandlerTest' --tests '*ReportFeedbackGenerationHandlerTest' --tests '*FarmingCycleReportProjectionServiceTest' :domain:test --tests '*ReportFeedbackRepositoryTest'
```

Expected: 신규 STALE/fingerprint 계약 미구현으로 FAIL.

- [ ] **Step 3: reconcile과 수동 regenerate를 구현한다**

`reconcile`은 현재 work type을 catalog 순으로 순회한다. 첫 생성이면 최신 context fingerprint를 저장한 PENDING을 만들고 이벤트를 발행한다. 기존 행이 하나라도 있으면 fingerprint가 달라진 행은 `markStale()`, 없는 work type은 `ReportFeedback.stalePlaceholder(...)`, 제거된 work type도 `markStale()`만 수행하고 이벤트를 발행하지 않는다.

```kotlin
fun regenerate(memberId: UUID, reportId: UUID, workType: WorkType): ReportFeedback {
    val feedback = feedbackRepository.findByReportAndWorkTypeForUpdate(reportId, memberId, workType)
        ?: throw BusinessException(ErrorCode.REPORT_FEEDBACK_NOT_FOUND)
    val context = contextAssembler.assemble(memberId, reportId, workType)
    feedback.retry(contextFingerprint.calculate(context))
    publishPreparation(feedback)
    return feedback
}
```

GET은 `report.statistics.recordCountFor(feedback.workType) > 0`인 행만 반환한다. STALE은 저장된 summary/item을 읽지 않고 null content를 반환한다.

- [ ] **Step 4: preparation/generation의 fingerprint guard를 구현한다**

preparation은 조립한 context fingerprint가 이벤트와 잠긴 행의 fingerprint에 모두 일치할 때만 snapshot과 generation event를 저장한다. generation의 READY/FAILED 최종 write는 `status`, `reportId`, `workType`, `inputSnapshot`, `sourceFingerprint`가 모두 같을 때만 수행한다.

- [ ] **Step 5: 수동 적용 SQL을 추가한다**

```sql
alter table report_feedback add column if not exists source_fingerprint varchar(64);
alter table report_feedback drop constraint if exists report_feedback_status_check;
alter table report_feedback drop constraint if exists ck_report_feedback_status;
alter table report_feedback add constraint report_feedback_status_check
  check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));
```

- [ ] **Step 6: focused test를 통과시킨다**

Run: Step 2와 동일.

Expected: BUILD SUCCESSFUL.

---

### Task 4: TECH_DOCUMENT-only RAG 계약 정리

**Files:**
- Delete: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/indexing/FarmingRecordDocumentFactory.kt`
- Delete: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/indexing/FarmingRecordDocumentFactoryTest.kt`
- Delete: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/chat/CoachingMode.kt`
- Delete: `backend/application/src/test/resources/coaching/rag/today-record-feedback-fertilizing.json`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/chat/CoachingRagCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/chat/CoachingRagResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/chat/CoachingRetrievalFilterBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/chat/CoachingRagService.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/chat/dto/CoachingRagRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/chat/dto/CoachingRagResponses.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/RagProperties.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/chat/CoachingRetrievalFilterBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/chat/CoachingRagServiceTest.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/chat/controller/CoachingRagControllerTest.kt`
- Replace: `backend/docs/db/rag-index-schema.sql`
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/main/resources/application-dev.yml`
- Modify: `backend/api/src/main/resources/application-prod.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`

**Interfaces:**
- Produces: `CoachingRetrievalFilterBuilder.build(): String` returning exactly `sourceType == 'TECH_DOCUMENT'`.
- Preserves: relational context fields `farmId`, `cropId`, `recordId`, `periodStart`, `periodEnd`.
- Removes: unused `mode`, UUID `workTypeId`, always-null `savedFeedbackId`, duplicate `rag.embedding.dimension`.

- [ ] **Step 1: TECH_DOCUMENT-only 실패 테스트를 먼저 바꾼다**

```kotlin
assertThat(builder.build()).isEqualTo("sourceType == 'TECH_DOCUMENT'")
verify(vectorStore).similaritySearch(argThat { filterExpression == "sourceType == 'TECH_DOCUMENT'" })
```

API test는 `mode`, `workTypeId`, `savedFeedbackId`가 OpenAPI/응답 계약에 없음을 확인한다.

- [ ] **Step 2: focused test의 실패를 확인한다**

Run:

```bash
cd backend && ./gradlew :application:test --tests '*CoachingRetrievalFilterBuilderTest' --tests '*CoachingRagServiceTest' :api:test --tests '*CoachingRagControllerTest' --tests '*AuthSecurityIntegrationTest'
```

Expected: 기존 FARMING_RECORD filter/DTO 계약 때문에 FAIL.

- [ ] **Step 3: dead indexing/API/config를 삭제하고 filter를 고정한다**

`RagSourceType.FARMING_RECORD`는 관계형 record citation metadata가 사용하므로 유지한다. `RAG_EMBEDDING_UNAVAILABLE`, `RAG_INDEX_UNAVAILABLE`, `RAG_EMBEDDING_DIMENSION_MISMATCH`는 참조가 없으므로 제거한다. local의 `app.dev.rag-seed-*`, SecurityConfig의 local public dev endpoints, 해당 auth smoke test도 제거한다.

- [ ] **Step 4: 실제 Spring AI vector_store DDL로 교체한다**

```sql
create extension if not exists vector;
create extension if not exists hstore;
create extension if not exists "uuid-ossp";

create table if not exists public.vector_store (
  id uuid default uuid_generate_v4() primary key,
  content text,
  metadata json,
  embedding vector(1024)
);

create index if not exists vector_store_embedding_hnsw_idx
  on public.vector_store using hnsw (embedding vector_cosine_ops);
```

- [ ] **Step 5: profile 기본값을 정렬한다**

모든 local/dev/prod/test에서 `rag.retrieval.low-similarity-threshold` 기본값을 `0.5`로 맞춘다. `rag.embedding.dimension`은 삭제하고 `spring.ai.vectorstore.pgvector.dimensions=${RAG_EMBEDDING_DIMENSION:1024}`만 단일 계약으로 유지한다.

- [ ] **Step 6: focused test를 통과시킨다**

Run: Step 2와 동일.

Expected: BUILD SUCCESSFUL.

---

### Task 5: 배포 문서와 생성 보고서 정리

**Files:**
- Modify: `docs/superpowers/plans/2026-07-15-home-server-rag-deployment.md`
- Modify: `docs/superpowers/specs/2026-07-15-home-server-rag-deployment-design.md`
- Delete: `.superpowers/sdd/phase3-task-2-report.md`
- Delete: `.superpowers/sdd/phase3-task-3-report.md`
- Delete: `.superpowers/sdd/task-3-report.md`
- Delete: `.superpowers/sdd/task-4-report.md`
- Delete: `.superpowers/sdd/task-5-report.md`

**Interfaces:**
- Produces: Spring Boot 3.5.4, TECH_DOCUMENT-only vector migration, OpenClaw connect/read timeout env names가 일치하는 배포 문서.

- [ ] **Step 1: stale 문서 값을 교체한다**

`Spring Boot 3.5.7`을 `3.5.4`로 고치고 `RAG_TIMEOUT_MILLIS`를 `OPENCLAW_CONNECT_TIMEOUT_MILLIS=3000`, `OPENCLAW_READ_TIMEOUT_MILLIS=30000`로 바꾼다. `rag_index_chunk`는 런타임 계약이 아니라는 설명을 `public.vector_store` 계약으로 맞춘다.

- [ ] **Step 2: 생성 산출물과 placeholder를 검사한다**

Run:

```bash
rg -n '3\.5\.7|RAG_TIMEOUT_MILLIS|rag_index_chunk|TODO|TBD|FIXME' docs/superpowers/plans/2026-07-15-home-server-rag-deployment.md docs/superpowers/specs/2026-07-15-home-server-rag-deployment-design.md backend/docs/db
```

Expected: 과거 계약 잔여물과 placeholder 0건.

---

### Task 6: 최종 검증, 리뷰, 커밋, push

**Files:**
- Verify: all changed files from Tasks 1-5.

**Interfaces:**
- Produces: 검증된 `feat/coaching-rag` 원격 브랜치.

- [ ] **Step 1: 전체 테스트를 한 번 실행한다**

```bash
cd backend && ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 전체 build를 한 번 실행한다**

```bash
cd backend && ./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 정적 잔여물과 diff를 한 번 리뷰한다**

```bash
git diff --check
rg -n 'COMPLETED_REPORT_IMMUTABLE|FarmingRecordDocumentFactory|CoachingMode|rag_index_chunk|RAG_TIMEOUT_MILLIS|RAG_EMBEDDING_UNAVAILABLE|RAG_INDEX_UNAVAILABLE|RAG_EMBEDDING_DIMENSION_MISMATCH' backend docs/superpowers/plans/2026-07-15-home-server-rag-deployment.md docs/superpowers/specs/2026-07-15-home-server-rag-deployment-design.md
git diff --stat
```

Expected: 의도적으로 남긴 문서 설명 외 불필요한 runtime 잔여물 0건, whitespace 오류 0건.

- [ ] **Step 4: `.claude/`, `.omx/`, secret 파일을 제외하고 커밋한다**

Commit title:

```text
fix(coaching): 통계 변경 코칭을 수동 재생성으로 전환
```

Lore trailers에는 STALE 무자동호출 제약, history-row 방식 거절, 실행한 test/build, 미검증 실제 PostgreSQL/OpenClaw 경합을 기록한다.

- [ ] **Step 5: 현재 브랜치를 일반 push한다**

```bash
git push -u origin feat/coaching-rag
```

Expected: 원격 `feat/coaching-rag`가 새 커밋을 가리킨다.
