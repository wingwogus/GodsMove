# dev 기록 계약 병합과 모바일형 코칭 콘솔 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `dev`의 최신 영농 기록 계약을 현재 코칭 브랜치에 병합하고 기록·리포트 코칭을 보존한 뒤, Git에서 무시되는 로컬 HTML을 모바일형 목록·상세 콘솔로 바꾼다.

**Architecture:** 추적되는 백엔드는 한 명의 병합 소유자가 의미 기반으로 통합하고 전체 테스트를 통과시킨 단일 merge commit으로 고정한다. 별도 HTML 작업자는 `frontend/dev-rag-test.html`만 수정해 정적 모바일 레이아웃을 병렬 준비하고, 병합된 API 계약이 확정된 뒤 요청 payload와 상세 렌더링을 연결한다.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA, JUnit 5, AssertJ, Mockito, Gradle, 단일 HTML/CSS/vanilla JavaScript

## Global Constraints

- 병합 방향은 `dev`에서 현재 `feat/coaching-rag`로 가져오는 방향이다.
- 사용자의 기존 결정에 따라 새 worktree를 만들지 않고 현재 checkout에서 작업한다.
- `frontend/dev-rag-test.html`은 로컬에서 수정하지만 `.gitignore`에서 제거하거나 스테이징·커밋하지 않는다.
- 추적되는 백엔드 파일은 한 명의 구현 소유자만 수정한다. HTML 작업자는 Git에서 무시되는 HTML 이외의 파일을 수정하지 않는다.
- `dev`의 기록 입력·저장 계약과 현재 브랜치의 `sourceRevision`, 리포트 재투영, 기록 코칭 enqueue/stale, 작업별 리포트 코칭을 함께 보존한다.
- `ML`을 `KG`로 임의 환산하지 않는다. `G`만 1,000으로 나눠 질량 통계에 포함하고 `ML`은 킬로그램 합계·평균·비교에서 제외한다.
- nullable 수확 부위·재배 기간은 값이 없을 때 통계·검색 질의·프롬프트에서 해당 문구를 생략한다.
- API enum 이름을 사용자 코칭 문장이나 HTML 사용자 화면에 직접 노출하지 않는다.
- 새 의존성, 합성 API, 범용 라우터, 공통 폴링 추상화, 운영 데이터 마이그레이션은 추가하지 않는다.
- `.claude/`와 다른 사용자 변경을 수정하거나 스테이징하지 않는다.
- 커밋은 Conventional Commits 제목과 Lore trailers를 사용한다.

---

### Task 1: dev 기록 계약과 코칭·리포트 생명주기 통합

**Files:**
- Modify through merge: `backend/api/src/main/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordController.kt`
- Modify through merge: `backend/api/src/main/kotlin/com/chamchamcham/api/farming/dto/FarmingRecordRequests.kt`
- Modify through merge: `backend/api/src/main/kotlin/com/chamchamcham/api/farming/dto/FarmingRecordResponses.kt`
- Modify through merge: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordCommand.kt`
- Modify through merge: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordResult.kt`
- Modify through merge: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/FarmingRecordService.kt`
- Modify through merge: `backend/application/src/main/kotlin/com/chamchamcham/application/farming/WorkTypeFieldCatalog.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecord.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FertilizingRecord.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/HarvestRecord.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PestControlRecord.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/PlantingRecord.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepository.kt`
- Modify through merge: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryImpl.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabels.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackWorkDetail.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackContextAssembler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackRetrievalQueryPlanner.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/CycleReportStatistics.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/farming/controller/FarmingRecordControllerTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordDetailValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/FarmingRecordServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/farming/WorkTypeCatalogServiceTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingRecordQueryRepositoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabelsTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackContextAssemblerTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilderTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoaderTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/CycleReportStatisticsCalculatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Test fixtures: `backend/application/src/test/resources/coaching/rag/today-record-feedback-*.json`

**Interfaces:**
- Consumes: local `dev` and `origin/dev` at `e4d1e31a`, current branch coaching/report APIs.
- Produces: one compiling merged backend where record DTOs use the dev contract and all existing coaching/report endpoints remain available.

- [ ] **Step 1: Verify refs, working tree, and current-branch baseline**

Run:

```bash
git status --short --branch
git rev-parse dev origin/dev
./gradlew --no-parallel :domain:test :application:test :api:test
```

Expected: `dev` and `origin/dev` both resolve to `e4d1e31a`; only `.claude/` is untracked; Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 2: Start the merge without creating an automatic merge commit**

Run from the repository root:

```bash
git merge --no-commit --no-ff dev
```

Expected: Git stops on the known changed-in-both files. Do not resolve a whole file with blanket `--ours` or `--theirs`.

- [ ] **Step 3: Resolve the record API, application, and domain model semantically**

Use dev's request/result shape:

```kotlin
data class PlantingDetail(
    val plantingMethod: PlantingMethod,
    val seedAmount: BigDecimal? = null,
    val seedAmountUnit: SeedAmountUnit? = null,
    val seedlingCount: Int? = null,
    val seedlingUnit: SeedlingUnit? = null,
    val propagationMethod: PropagationMethod? = null,
)

data class FertilizingDetail(
    val materialName: String,
    val amount: BigDecimal,
    val amountUnit: FertilizerAmountUnit,
    val applicationMethod: FertilizingMethod? = null,
)

data class PestControlDetail(
    val pesticideId: UUID,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: PesticideAmountUnit,
    val totalSprayAmount: BigDecimal,
    val totalSprayAmountUnit: SprayAmountUnit,
    val pestId: UUID? = null,
)

data class HarvestDetail(
    val harvestAmount: BigDecimal?,
    val amountUnknown: Boolean = false,
    val medicinalPart: CropUsePartCategory? = null,
    val harvestSource: HarvestSource = HarvestSource.CULTIVATED,
    val growthPeriod: Int? = null,
    val growthPeriodUnit: GrowthPeriodUnit? = null,
    val isLastHarvest: Boolean,
)
```

In `FarmingRecordService`, retain dev's `PesticideRepository`, `PestRepository`, and `EntryMode`. Preserve the current branch's member-crop validation, source revision increment, explicit flush, report projection rebuild, feedback enqueue, and feedback stale calls in their existing create/update/delete order. Verify the exact call sites instead of rebuilding them from an abbreviated example.

```kotlin
validateMemberCrop(command.memberId, command.cropId)
record.incrementSourceRevision()
entityManager.flush()
```

Keep each call in the same create/update/delete lifecycle position protected by its existing regression test. `FarmingRecord` must contain both `entryMode: EntryMode` and `sourceRevision: Long`.

- [ ] **Step 4: Establish RED coverage for the merge-only semantic gaps**

Update the harvest catalog expectation in `WorkTypeCatalogServiceTest` before adding the production field:

```kotlin
assertThat(harvest.fields.map { it.name }).containsExactly(
    "harvestAmount",
    "medicinalPart",
    "harvestSource",
    "growthPeriod",
    "growthPeriodUnit",
    "isLastHarvest",
)
assertThat(harvest.fields.first { it.name == "isLastHarvest" }.required).isTrue()
assertThat(harvest.fields.first { it.name == "isLastHarvest" }.type)
    .isEqualTo(FieldValueType.BOOLEAN)
```

Add a fertilizer statistics test with one `G` source and one `ML` source:

```kotlin
assertThat(result.totalAmountKg).isEqualByComparingTo("0.5000")
assertThat(result.amountCoverage).isEqualTo(Coverage(recordedCount = 1, targetCount = 2))
```

Add a source-loader assertion that `ML` remains `ML`, not kilograms:

```kotlin
assertThat(source.fertilizing!!.amount).isEqualByComparingTo("250")
assertThat(source.fertilizing!!.amountUnit).isEqualTo("ML")
assertThat(source.fertilizing!!.amountKg).isNull()
```

Run:

```bash
./gradlew :application:test --tests '*WorkTypeCatalogServiceTest' --tests '*FarmingCycleReportSourceLoaderTest' --tests '*CycleReportStatisticsCalculatorTest'
```

Expected: FAIL because `isLastHarvest` or the unit-safe source/statistics mapping is not implemented yet.

- [ ] **Step 5: Implement the minimal unit-safe report source and statistics mapping**

Represent the raw fertilizer value and optional mass conversion together:

```kotlin
data class FertilizingReportSource(
    val materialName: String,
    val amount: BigDecimal,
    val amountUnit: String,
    val amountKg: BigDecimal?,
    val applicationMethod: CategoryRef?,
)

private fun BigDecimal.toKilogramsOrNull(unit: FertilizerAmountUnit): BigDecimal? =
    when (unit) {
        FertilizerAmountUnit.G -> divide(BigDecimal("1000")).scale4()
        FertilizerAmountUnit.ML -> null
    }
```

For planting sources, keep `plantingMethod` required and `propagationMethod` nullable. For pest-control sources, use pesticide UUID/name and optional pest name rather than deleted category enums. For harvest sources, use nullable medicinal part/growth period and `isLastHarvest`.

Only `amountKg != null` contributes to fertilizer kg total, average, amount coverage, or comparison. The raw value/unit remains available to record/report prompts. Add the required catalog field:

```kotlin
WorkTypeResult.FieldSummary(
    name = "isLastHarvest",
    type = FieldValueType.BOOLEAN,
    required = true,
    options = emptyList(),
)
```

- [ ] **Step 6: Adapt record coaching to the merged record model**

Replace old detail fields with explicit merged fields. The sealed detail types must preserve source values without exposing raw enums in public coaching text:

```kotlin
data class FertilizingFeedbackDetail(
    val materialName: String,
    val amount: BigDecimal,
    val amountUnit: FertilizerAmountUnit,
    val applicationMethod: FertilizingMethod?,
) : RecordFeedbackWorkDetail

data class PestControlFeedbackDetail(
    val pesticideName: String,
    val pesticideAmount: BigDecimal,
    val pesticideAmountUnit: PesticideAmountUnit,
    val totalSprayAmount: BigDecimal,
    val totalSprayAmountUnit: SprayAmountUnit,
    val pestName: String?,
) : RecordFeedbackWorkDetail

data class HarvestFeedbackDetail(
    val harvestAmount: BigDecimal?,
    val medicinalPart: CropUsePartCategory?,
    val harvestSource: HarvestSource,
    val growthPeriod: Int?,
    val growthPeriodUnit: GrowthPeriodUnit?,
    val isLastHarvest: Boolean,
) : RecordFeedbackWorkDetail
```

Prompt lines for optional values must use `?.let { add(...) }`. Fertilizer and pesticide units must use Korean labels. Retrieval must use `pestName` only when present and must not retain `pestTarget` or deleted category enums.

Run:

```bash
./gradlew :application:test --tests '*CoachingPromptLabelsTest' --tests '*RecordFeedbackContextAssemblerTest' --tests '*RecordFeedbackPromptBuilderTest' --tests '*RecordFeedbackRetrievalQueryPlannerTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Adapt cycle reports and report coaching to merged fields**

Cycle completion must use:

```kotlin
record.harvest?.isLastHarvest == true
```

Report record lines must preserve raw fertilizer units and omit missing harvest values:

```kotlin
detail.number("amount")?.let { amount ->
    detail.text("amountUnit")?.fertilizerAmountUnitText()?.let { unit ->
        add("거름 양: $amount$unit")
    }
}
detail.text("medicinalPart")?.medicinalPartText()?.let { add("수확 부위: $it") }
detail.number("growthPeriod")?.let { period ->
    detail.text("growthPeriodUnit")?.growthPeriodUnitText()?.let { unit ->
        add("재배 기간: $period$unit")
    }
}
```

Remove imports and mappings for `FertilizerMaterialCategory`, `PesticideCategory`, `materialCategory`, `pesticideCategory`, `pestTarget`, and `isFinalHarvest`. Update seed JSON and `DevRagSeedService` to the merged request shape.

Run:

```bash
./gradlew :application:test --tests '*FarmingCycleReportSourceLoaderTest' --tests '*CycleReportStatisticsCalculatorTest' --tests '*FarmingCyclePartitionerTest' --tests '*FarmingCycleReportProjectionServiceTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackContextAssemblerTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run focused API/domain tests and static contract scans**

Run:

```bash
./gradlew :domain:test :application:test :api:test --tests '*FarmingRecord*Test' --tests '*WorkType*Test' --tests '*RecordFeedback*Test' --tests '*ReportFeedback*Test' --tests '*FarmingCycle*Test'
rg -n 'FertilizerMaterialCategory|PesticideCategory|materialCategory|pesticideCategory|pestTarget|isFinalHarvest|FertilizerAmountUnit\.KG' backend --glob '*.kt' --glob '*.json' --glob '!**/build/**'
```

Expected: focused tests succeed. The static scan returns no project-owned old-contract matches except an explicitly documented historical migration artifact; no production/test source match is allowed.

- [ ] **Step 9: Run the complete backend suite and finish the merge commit**

Run:

```bash
./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
git diff --check
git status --short
```

Expected: `BUILD SUCCESSFUL`; no unresolved paths; HTML and `.claude/` are not staged.

Commit the completed merge with Lore trailers:

```text
fix(farming): 기록 계약 분기 간 의미 손실 방지

dev의 최신 기록 입력 모델을 코칭·리포트 생명주기와 함께 통합했다.

Constraint: dev 기록 계약과 현재 코칭 비동기 생명주기를 모두 보존
Rejected: 충돌 파일 일괄 ours/theirs 선택 | 한쪽 생명주기 또는 새 입력 계약이 사라짐
Confidence: high
Scope-risk: broad
Directive: 거름 ML 값을 kg 통계에 포함하지 말 것
Tested: domain/application/api 전체 테스트
Not-tested: 운영 데이터 마이그레이션
```

---

### Task 2: 로컬 HTML을 모바일형 기록·작업별 리포트 콘솔로 재구성

**Files:**
- Modify locally only, never stage: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes: merged `GET /api/v1/farming-records`, record detail/feedback endpoints, report list/detail/feedback endpoints.
- Produces: local mobile-width record list/detail/coaching and report cycle/work/detail/coaching flows while preserving collapsed developer controls.

- [ ] **Step 1: Capture the pre-change local validation baseline**

Run:

```bash
git check-ignore -v frontend/dev-rag-test.html
sed -n '/<script>/,/<\/script>/p' frontend/dev-rag-test.html | sed '1d;$d' | node --check
```

Expected: `.gitignore` matches the HTML and the current inline script parses without creating a tracked or temporary artifact.

- [ ] **Step 2: Rebuild the shell while preserving developer-control IDs**

Use one mobile application shell and one collapsed diagnostics section:

```html
<main class="mobile-console">
  <header class="app-header">
    <h1>영농 기록</h1>
    <nav class="primary-tabs" aria-label="영농 기록 화면">
      <button data-mobile-view="records" aria-selected="true">기록</button>
      <button data-mobile-view="reports" aria-selected="false">리포트</button>
    </nav>
  </header>
  <section id="recordMobileView"></section>
  <section id="reportMobileView" hidden></section>
</main>
<details class="developer-tools">
  <summary>개발 도구</summary>
  <!-- 기존 인증, CRUD, fixture, 진단 DOM을 ID 변경 없이 이동 -->
</details>
```

Keep the main surface centered at 430~520px and prevent horizontal scrolling at 390px. Do not render decorative image placeholders or nonfunctional menu icons.

- [ ] **Step 3: Add record list state and rendering**

Use only the existing list endpoint and cursor. Add these state fields:

```javascript
state.recordList = [];
state.recordNextCursor = null;
state.recordScreen = "list";
state.selectedRecord = null;
```

Implement the functions with the following exact responsibilities:

- `loadRecordList({ append = false } = {})`: build `URLSearchParams` from the selected crop/work/date filters and `state.recordNextCursor`, call `GET /api/v1/farming-records`, replace or append `response.data.items`, store `response.data.nextCursor`, then call `renderRecordList()`.
- `renderRecordList()`: render each summary's `cropName`, easy Korean work label, `workedAt`, `weatherCondition`, `weatherTemperature`, `memoPreview`, and optional `thumbnailUrl` into `#recordList`; toggle `#recordLoadMore` from `state.recordNextCursor`; use a real `<button>` per row so keyboard activation works.
- `openRecordDetail(recordId)`: call `stopPolling()`, then load `GET /api/v1/farming-records/{recordId}` and `fetchFeedback(recordId)` with `Promise.all`; store the detail in `state.selectedRecord`, render the detail immediately, and hand the feedback to the existing status renderer/poller.
- `showRecordScreen(screen)`: accept only `list` or `detail`, set `state.recordScreen`, toggle the two screen containers and back button, and call `stopPolling()` when leaving `detail`.

Do not request feedback for every list item. Render real images from `imageUrls`; otherwise show `등록된 사진이 없어요.`

- [ ] **Step 4: Render merged work details and record coaching**

Map the merged detail keys to Korean labels:

```javascript
const recordDetailRenderers = {
  PLANTING: renderPlantingDetail,
  WATERING: renderWateringDetail,
  FERTILIZING: renderFertilizingDetail,
  PEST_CONTROL: renderPestControlDetail,
  WEEDING: renderWeedingDetail,
  HARVEST: renderHarvestDetail,
};
```

Handle `plantingMethod`, seed/seedling fields, `materialName` with `G/ML`, pesticide/pest names, nullable harvest fields, and `isLastHarvest`. Reuse `renderFeedback`, `fetchFeedback`, and `pollFeedback` for the coaching area, but show internal status/failure details only inside developer tools.

- [ ] **Step 5: Add completed-cycle and work-card report navigation**

Use explicit screens rather than a generic router. Add these state fields:

```javascript
state.reportScreen = "cycles";
state.selectedReportWorkType = null;
```

Implement the functions with the following exact responsibilities:

- `renderReportCycleList(items)`: keep only `status === "COMPLETE"`, then render crop/farm names and cycle dates as buttons into `#reportCycleList`; the empty state says `완료된 리포트가 아직 없어요.`.
- `renderReportWorkList(snapshot)`: read `snapshot.statistics.byWorkType`, keep entries whose `recordCount > 0`, join each entry with its cached feedback by `workType`, and render one card per work type into `#reportWorkList`.
- `openReportWorkDetail(workType)`: set `state.selectedReportWorkType`, read the already cached statistics and feedback without another HTTP request, render them into `#reportWorkDetail`, and switch to `detail`.
- `showReportScreen(screen)`: accept only `cycles`, `works`, or `detail`; toggle the matching containers; stop report-feedback polling when returning to `cycles` or changing reports.

Fetch report detail and all work feedback once per selected report. A work card shows its easy Korean name, record count, recent date, average interval, status, and READY summary. It must not create an `전체` card. Mixed READY/PENDING/FAILED work cards render independently.

- [ ] **Step 6: Update the developer CRUD payload to the merged API**

The local creation form must send `plantingMethod`, `materialName`, `amountUnit: G|ML`, `pesticideId`, optional `pestId`, nullable harvest fields, and `isLastHarvest`. Remove payload keys that no longer exist:

```text
materialCategory
pesticideCategory
pestTarget
isFinalHarvest
```

Keep existing auth, token, farm/crop IDs, fixtures, regenerate, stop-polling, request log, and raw response controls inside the collapsed section.

- [ ] **Step 7: Validate HTML structure and JavaScript syntax**

Run duplicate-ID and parse checks:

```bash
python3 -c 'from html.parser import HTMLParser; from pathlib import Path; import collections; p=Path("frontend/dev-rag-test.html"); ids=[]; H=type("H",(HTMLParser,),{"handle_starttag":lambda self,tag,attrs:ids.extend(v for k,v in attrs if k=="id")}); h=H(); h.feed(p.read_text()); duplicates=[k for k,v in collections.Counter(ids).items() if v>1]; assert not duplicates, duplicates'
sed -n '/<script>/,/<\/script>/p' frontend/dev-rag-test.html | sed '1d;$d' | node --check
git check-ignore -v frontend/dev-rag-test.html
git status --short
```

Expected: no duplicate IDs; JavaScript parses; the HTML remains ignored and absent from staged/untracked output.

- [ ] **Step 8: Run local browser flow and visual verification**

At a 390x844 mobile viewport verify:

1. Record list filters and cursor more-loading.
2. Record detail/back navigation and actual image/empty state.
3. Record PENDING/READY/FAILED/STALE coaching.
4. Completed report selection and work cards with no `전체` card.
5. Report work detail with statistics, summary, strengths, improvements, next actions.
6. Mixed work feedback statuses and polling cancellation on navigation.
7. Developer tools remain collapsed by default and all retained controls work when expanded.

Expected: no horizontal scroll, clipped text, off-screen primary controls, console errors, or stale response overwrites.

---

### Task 3: 통합 검증과 전체 변경 리뷰

**Files:**
- Review: all files in the merge commit from Task 1
- Review locally: `frontend/dev-rag-test.html`
- Verify: `docs/superpowers/specs/2026-07-14-dev-record-merge-mobile-coaching-console-design.md`

**Interfaces:**
- Consumes: completed merge commit and local HTML result.
- Produces: verified branch with no staged HTML and a review record with no open Critical/Important finding.

- [ ] **Step 1: Run final backend regression from a clean merge state**

Run:

```bash
./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify Git and ignored-file boundaries**

Run:

```bash
git show --check --oneline HEAD
git status --short --branch
git check-ignore -v frontend/dev-rag-test.html
git ls-files --error-unmatch frontend/dev-rag-test.html
```

Expected: merge commit check succeeds; `.claude/` is the only visible untracked path; `git check-ignore` reports the HTML ignore rule; `git ls-files` fails because the HTML is not tracked.

- [ ] **Step 3: Review the complete merge and local HTML behavior**

The reviewer must verify:

- dev record DTO/domain behavior is present.
- current branch coaching/report lifecycle behavior is present.
- no `ML -> KG` conversion exists.
- no deleted enum or old field name remains in executable/test source.
- record and report feedback remain work-scoped.
- HTML does not make feedback N+1 calls from the record list.
- HTML stops polling and rejects stale responses during navigation.
- HTML remains ignored and uncommitted.

Expected: both spec compliance and code quality are approved with no open Critical/Important issue.
