# Work Report List and Detail API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주기 리포트를 단일 저장 원본으로 유지하면서 작업 카드 목록, 작업별 상세, 결정적으로 계산된 비교 코칭을 공개 API로 제공한다.

**Architecture:** `FarmingCycleReport.statistics`를 작업별 조회 projection으로 펼치고 `(reportId, workType)`을 작업 리소스 키로 사용한다. 작업 카드의 이미지는 페이지 범위의 기록·최종 수확·미디어를 고정 횟수로 일괄 조회한 뒤 `FarmingCyclePartitioner`로 정확한 주기를 찾아 메모리에서 선택한다. 비교 코칭은 typed statistics를 비교하는 순수 calculator가 산출한 차이를 `input_snapshot`에 저장하고, LLM에는 계산이 아닌 친근한 존댓말 표현만 맡긴다.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Data JPA/Hibernate, Jackson, JUnit 5, Mockito, MockMvc, AssertJ, Gradle

## Global Constraints

- `FarmingCycleReport`가 재배 주기 통계의 단일 저장 원본이다. 독립 `FarmingWorkReport` JPA 엔티티나 테이블을 만들지 않는다.
- `ReportFeedbackItemSection.COMPARISON`과 기존 `report_feedback_item` 저장 구조를 재사용한다. 새 비교 테이블이나 `ReportFeedback` JSONB 필드를 추가하지 않는다.
- 작업 목록은 로그인 회원 범위에서 선택적 `farmId`, `cropId`, `workType` 필터를 지원하고 `endsAt DESC -> reportId DESC -> WorkType 선언 순서 ASC`로 안정적으로 정렬한다.
- opaque cursor는 마지막 작업 항목의 `endsAt`, `reportId`, `workType`을 담고 페이지 경계에서 항목이 누락되거나 중복되지 않아야 한다.
- 카드 대표 이미지는 정확한 주기와 작업에서 `(workedAt DESC, createdAt DESC, recordId DESC)` 첫 사진 기록의 `(displayOrder ASC, mediaId ASC)` 첫 URL이다. 사진이 없으면 `null`이다.
- 페이지의 카드 수나 scope 수에 비례한 repository 호출을 만들지 않는다.
- 작업 상세는 완료된 회원 소유 리포트의 `recordCount > 0` 작업만 공개하고, 해당 typed statistics 하나와 그 작업의 코칭 상태/내용만 반환한다.
- 주기 상세 공개 응답에서 `previous`를 제거하고 공개 수치형 comparison DTO를 만들지 않는다.
- API DTO는 JPA entity나 domain statistics를 직접 노출하지 않고 모든 필드를 명시적으로 매핑한다. `Map<String, Any?>`, `JsonNode`, reflection mapper, 범용 mapper를 공개 계약에 사용하지 않는다.
- 비교는 같은 작업·같은 키·호환 가능한 단위만 서버에서 계산한다. 한쪽 값이 `null`이면 생략하고, 직전 값이 0이면 상대 백분율을 만들지 않으며, coverage를 현재·직전 범위와 함께 보존한다.
- 비교 문장은 변화 사실만 담고 친근한 존댓말로 끝난다. 현재·직전 report evidence reference만 비교 근거로 허용하며 영어 enum을 공개 문장에 노출하지 않는다.
- `PENDING`과 `FAILED` 코칭은 `content = null`, `READY`만 content를 반환한다. 기존 `/farming-reports/{reportId}/feedback`은 작업 전체 상태 조회로 유지하고 `comparisons`를 포함한다.
- 실제 런타임 소비자가 없는 `/api/v1/farming-reports/current` 공개 API를 제거하되 `ACTIVE` 도메인 상태와 projection 내부 조회는 유지한다.
- `size` 범위와 path enum 같은 request-shape 검증은 API 경계에서 처리하고, application은 소유권·상태·작업 존재 같은 비즈니스 규칙만 검증한다.
- 새 의존성, 범용 query framework, 공통 validator 계층, 대표 이미지 저장 컬럼, materialized view, 캐시, 검색 인덱스를 추가하지 않는다.

---

### Task 1: 기존 주기 리포트 공개 계약 정리

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSearchCondition.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportResult.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingReportStatisticsResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingCycleReportResponses.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCycleReportQueryServiceTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/report/FarmingCycleReportIntegrationTest.kt`

**Interfaces:**
- Produces: `FarmingCycleReportSearchCondition(memberId, farmId: UUID?, cropId: UUID?, cursor, size)`
- Produces: `FarmingCycleReportResult.Detail(selected: Snapshot)` with no previous snapshot
- Produces: `FarmingReportStatisticsResponses.CycleStatisticsResponse.from(CycleReportStatistics)` as the one explicit API statistics mapper reused by Task 3
- Preserves: `FarmingCycleReportQueryRepository.findPreviousCompleted(...)` for feedback generation

- [ ] **Step 1: Write failing repository and application tests for optional completed-list filters**

Add tests whose assertions are equivalent to:

```kotlin
@Test
fun `completed list accepts member wide farm only crop only and combined filters`() {
    val all = repository.searchCompleted(condition(farmId = null, cropId = null, size = 20))
    assertThat(all.rows.map { it.id }).containsExactly(newestAcrossScopes.id, olderAcrossScopes.id)

    val byFarm = repository.searchCompleted(condition(farmId = farmId, cropId = null, size = 20))
    assertThat(byFarm.rows).allMatch { it.farm.id == farmId }

    val byCrop = repository.searchCompleted(condition(farmId = null, cropId = cropId, size = 20))
    assertThat(byCrop.rows).allMatch { it.crop.id == cropId }
}
```

Application tests must prove an unowned supplied farm returns `FARM_NOT_FOUND`, while absent filters do not trigger farm/crop ownership repository calls. Replace size-validation service tests with controller tests because size is request shape.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```bash
cd backend
./gradlew :domain:test --tests 'com.chamchamcham.domain.report.FarmingCycleReportQueryRepositoryTest' \
  :application:test --tests 'com.chamchamcham.application.report.FarmingCycleReportQueryServiceTest'
```

Expected: compilation or assertion failures because `farmId`/`cropId` are still required and list scope validation assumes both values.

- [ ] **Step 3: Implement optional filters and keep list ownership member-scoped**

Change the exact contracts to:

```kotlin
data class FarmingCycleReportSearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val cursor: String?,
    val size: Int,
)

data class SearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val cursor: Cursor?,
    val size: Int,
)
```

Build the JPQL predicates with mandatory member/status and optional farm/crop predicates. Keep the existing cycle-list cursor ordering, fetch farm/crop in the completed query, and validate only a supplied farm or supplied farm/crop ownership combination in application code.

- [ ] **Step 4: Write failing API contract tests for current removal, detail shape, typed mapping, and boundary validation**

The controller tests must assert:

```kotlin
mockMvc.perform(get("/api/v1/farming-reports/current").with(authenticatedMember(memberId.toString())))
    .andExpect(status().isBadRequest)
    .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))

mockMvc.perform(get("/api/v1/farming-reports/{reportId}", selectedReportId)
    .with(authenticatedMember(memberId.toString())))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.data.selected.statistics.watering.recordCount", equalTo(2)))
    .andExpect(jsonPath("$.data.previous").doesNotExist())
    .andExpect(jsonPath("$.data.comparison").doesNotExist())

mockMvc.perform(get("/api/v1/farming-reports")
    .with(authenticatedMember(memberId.toString()))
    .param("size", "101"))
    .andExpect(status().isBadRequest)
```

Also assert list requests work with no filters, either filter, and both filters. The response mapper test data must include nested coverage/distribution fields so the test proves the API no longer serializes the domain object by accident.

- [ ] **Step 5: Run API tests and confirm RED**

Run:

```bash
cd backend
./gradlew :api:test --tests 'com.chamchamcham.api.report.controller.FarmingCycleReportControllerTest'
```

Expected: `/current` still returns 200, `previous` is present, and size has no API-boundary annotation. 구현 후에는 정적 `/current` handler가 사라지고 `/{reportId}` UUID 변환이 요청을 거부하므로 400 `COMMON_001`을 기대한다.

- [ ] **Step 6: Remove the public current surface and explicitly map all statistics fields**

Delete `getCurrent`, `FarmingCycleReportResult.Current`, and `CurrentResponse`; retain internal ACTIVE projection repository methods. Change detail to:

```kotlin
data class Detail(val selected: Snapshot)

data class DetailResponse(val selected: SnapshotResponse) {
    companion object {
        fun from(source: FarmingCycleReportResult.Detail) =
            DetailResponse(selected = SnapshotResponse.from(source.selected))
    }
}
```

Annotate the controller with `@Validated` and both list-size parameters with `@Min(1)`/`@Max(100)`. `FarmingReportStatisticsResponses` must declare API data classes for `Coverage`, distributions, amounts, common fields, and each typed work statistic and perform field-by-field conversion from every type in `CycleReportStatistics.kt`; `SnapshotResponse.statistics` must be `CycleStatisticsResponse`, never `CycleReportStatistics`.

- [ ] **Step 7: Replace test-only current consumers and verify GREEN**

Update integration assertions to locate the completed report through `listCompleted`/`getDetail` or `reportRepository.findAllCurrent`, then run:

```bash
cd backend
./gradlew :domain:test --tests 'com.chamchamcham.domain.report.FarmingCycleReportQueryRepositoryTest' \
  :application:test --tests 'com.chamchamcham.application.report.FarmingCycleReportQueryServiceTest' \
  :api:test --tests 'com.chamchamcham.api.report.controller.FarmingCycleReportControllerTest' \
  --tests 'com.chamchamcham.api.report.FarmingCycleReportIntegrationTest'
```

Expected: all selected tests pass; repository-wide search finds no runtime controller/DTO/service current surface.

- [ ] **Step 8: Commit Task 1**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/report \
  backend/application/src/main/kotlin/com/chamchamcham/application/report \
  backend/api/src/main/kotlin/com/chamchamcham/api/report \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report \
  backend/application/src/test/kotlin/com/chamchamcham/application/report \
  backend/api/src/test/kotlin/com/chamchamcham/api/report
git commit -m 'refactor(report): 작업 리포트 조회 계약에 맞게 주기 API 정리' \
  -m 'Constraint: 진행 중 리포트의 런타임 소비자가 없고 ACTIVE 상태는 내부 projection에 필요함' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: 주기 query application controller integration focused tests'
```

### Task 2: 작업 카드 projection, cursor, 대표 이미지 일괄 조회

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingWorkReportSourceRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farming/FarmingWorkReportSourceRepositoryImpl.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/CycleReportSource.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCyclePartitioner.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingCycleReportSourceLoader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportSearchCondition.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportCursorPayload.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryService.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryTest.kt`
- Create: `backend/domain/src/test/kotlin/com/chamchamcham/domain/farming/FarmingWorkReportSourceRepositoryTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingCyclePartitionerTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryServiceTest.kt`

**Interfaces:**
- Consumes: optional-filter completed report query from Task 1
- Produces: `FarmingCycleReportQueryRepository.searchCompletedWorkItems(WorkItemSearchCondition): WorkItemSearchResult`
- Produces: `FarmingWorkReportSourceRepository.load(memberId, farmIds, cropIds): FarmingWorkReportSourceSnapshot`
- Produces: `FarmingWorkReportQueryService.list(condition): FarmingWorkReportResult.Page`

- [ ] **Step 1: Write failing projection tests for filters, record count, ordering, and item cursor**

Define the wished-for domain interface in tests:

```kotlin
data class WorkItemCursor(val endsAt: LocalDateTime, val reportId: UUID, val workType: WorkType)
data class WorkItemSearchCondition(
    val memberId: UUID,
    val farmId: UUID?,
    val cropId: UUID?,
    val workType: WorkType?,
    val cursor: WorkItemCursor?,
    val size: Int,
)
```

Persist reports with multiple non-zero work statistics and assert filter combinations, other-member exclusion, zero-count exclusion, `endsAt/reportId/workType.ordinal` order, and a cursor cut between two work types of the same report with no overlap between pages.

- [ ] **Step 2: Run projection tests and confirm RED**

Run:

```bash
cd backend
./gradlew :domain:test --tests 'com.chamchamcham.domain.report.FarmingCycleReportQueryRepositoryTest'
```

Expected: compilation fails because the work-item projection API does not exist.

- [ ] **Step 3: Implement a fixed typed projection without a generic JSON query layer**

Add a scalar completed-report projection containing report/farm/crop metadata, final harvest ID, and typed `CycleReportStatistics`. Expand it through this explicit selector:

```kotlin
private fun CycleReportStatistics.summary(workType: WorkType): Pair<Int, LocalDate?> = when (workType) {
    WorkType.PLANTING -> planting.recordCount to planting.lastWorkedOn
    WorkType.WATERING -> watering.recordCount to watering.lastWorkedOn
    WorkType.FERTILIZING -> fertilizing.recordCount to fertilizing.lastWorkedOn
    WorkType.PEST_CONTROL -> pestControl.recordCount to pestControl.lastWorkedOn
    WorkType.WEEDING -> weeding.recordCount to weeding.lastWorkedOn
    WorkType.PRUNING -> pruning.recordCount to pruning.lastWorkedOn
    WorkType.HARVEST -> harvest.recordCount to harvest.lastWorkedOn
    WorkType.ETC -> etc.recordCount to etc.lastWorkedOn
}
```

Return only positive counts, apply the item cursor tuple after expansion, and take exactly the requested lookahead count. Keep all expansion code local to this concrete repository.
Update the repository test JSON format mapper so persisted non-zero typed statistics round-trip instead of being replaced by `CycleReportStatistics.empty()`; the integration test must exercise the real projection values.

- [ ] **Step 4: Write failing partition/image source tests**

Add tests proving:

```kotlin
assertThat(partitioner.partition(equalWorkedAtRecords).flatMap(CycleSlice::records).map { it.id })
    .containsExactly(oldestCreatedId, newerCreatedId, newestCreatedId)

assertThat(snapshot.firstImageUrlByRecordId[picturedRecordId]).isEqualTo("https://img/first.jpg")
```

Cover latest pictured record, fallback to older pictured record, equal-workedAt createdAt/UUID tie-break, duplicate display order media UUID tie-break, no photo, other work type, adjacent cycle, and multiple scopes. Enable Hibernate statistics and assert prepared statement count is constant for one card and many cards.

- [ ] **Step 5: Run source/partition tests and confirm RED**

Run:

```bash
cd backend
./gradlew :domain:test --tests 'com.chamchamcham.domain.farming.FarmingWorkReportSourceRepositoryTest' \
  :application:test --tests 'com.chamchamcham.application.report.FarmingCyclePartitionerTest'
```

Expected: missing batch source types and the old `(workedAt, id)` partition tie-break fail the tests.

- [ ] **Step 6: Implement the fixed-query batch source and exact-cycle thumbnail selector**

Use these concrete result types:

```kotlin
data class FarmingWorkReportSourceSnapshot(
    val records: List<FarmingRecord>,
    val finalHarvestRecordIds: Set<UUID>,
    val firstImageUrlByRecordId: Map<UUID, String>,
)

interface FarmingWorkReportSourceRepository {
    fun load(
        memberId: UUID,
        farmIds: Set<UUID>,
        cropIds: Set<UUID>,
    ): FarmingWorkReportSourceSnapshot
}
```

Fetch member-scoped records for the batched farm/crop sets once, final-harvest flags once, and media `(recordId, mediaId, displayOrder, fileUrl)` once. Filter cross-product overfetch back to the exact page scopes in application code. Add `createdAt` to `CycleReportSourceRecord`; partition ascending `(workedAt, createdAt, id)`. For every page report, match a completed slice by `finalHarvestRecordId`, filter its records by work type and existing image URL, then choose descending `(workedAt, createdAt, id)`.
Pass the persisted `FarmingRecord.createdAt` explicitly from `FarmingCycleReportSourceLoader`; update existing source/partition fixtures so the tie-break is deterministic.

- [ ] **Step 7: Write and run RED/GREEN service tests for stable pagination and thumbnails**

The service tests must decode `FarmingWorkReportCursorPayload`, call the work projection once and batch source once, and assert:

```kotlin
assertThat(page.items.map { it.workType }).containsExactly(WorkType.WATERING, WorkType.HARVEST)
assertThat(page.items.first().thumbnailUrl).isEqualTo("https://img/watering.jpg")
assertThat(decoded.reportId).isEqualTo(page.items.last().reportId)
assertThat(decoded.workType).isEqualTo(page.items.last().workType)
verify(queryRepository).searchCompletedWorkItems(expectedCondition)
verify(sourceRepository).load(memberId, setOf(farmId), setOf(cropId))
```

Run:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest'
```

Expected GREEN: filter/cursor/count/ownership/thumbnail tests pass and repository calls are fixed per page.

- [ ] **Step 8: Commit Task 2**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/report \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/farming \
  backend/application/src/main/kotlin/com/chamchamcham/application/report \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/report \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/farming \
  backend/application/src/test/kotlin/com/chamchamcham/application/report
git commit -m 'feat(report): 작업 카드의 안정적 페이지 조회 제공' \
  -m 'Constraint: 주기 리포트가 단일 저장 원본이며 카드별 기록 조회를 허용하지 않음' \
  -m 'Rejected: 작업 리포트 엔티티 추가 | 저장 중복과 생명주기 불일치' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: projection cursor thumbnail partition and fixed-query-count focused tests'
```

### Task 3: 작업 목록·상세 HTTP API와 typed statistics/feedback 조합

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportResult.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingWorkReportResponses.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryServiceTest.kt`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportControllerTest.kt`

**Interfaces:**
- Consumes: `FarmingReportStatisticsResponses` from Task 1 and list service from Task 2
- Produces: `GET /api/v1/farming-reports/work-items`
- Produces: `GET /api/v1/farming-reports/{reportId}/work-types/{workType}`
- Produces: `ErrorCode.WORK_REPORT_NOT_FOUND`

- [ ] **Step 1: Write failing application tests for detail ownership, state, work existence, and feedback status**

Tests must cover member-owned completed detail, cross-member `REPORT_NOT_FOUND`, ACTIVE/SUPERSEDED `REPORT_NOT_FOUND`, zero-count `WORK_REPORT_NOT_FOUND`, and `PENDING/READY/FAILED` content rules. The wished-for result is:

```kotlin
data class Detail(
    val reportId: UUID,
    val workType: WorkType,
    val workTypeLabel: String,
    val farmId: UUID,
    val farmName: String,
    val cropId: UUID,
    val cropName: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val statistics: WorkStatistics,
    val feedback: FeedbackStatus,
)
```

`WorkStatistics` has one `common` result and exactly one nullable typed branch for detail-bearing work types; pruning/etc have only common. Reuse `ReportFeedbackQueryService.get(memberId, reportId)` and select the work item. Treat a transiently absent feedback row as `PENDING` with null content so an existing statistics resource does not disappear.

- [ ] **Step 2: Run detail service tests and confirm RED**

Run:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest'
```

Expected: the list-only service has no detail API or work-not-found error.

- [ ] **Step 3: Implement minimal detail composition**

Use an explicit `when (workType)` over `CycleReportStatistics`; never return `Any` or a map. Check report ownership first, then completed state, then selected `recordCount`. Return `REPORT_NOT_FOUND` for inaccessible/non-completed reports and `WORK_REPORT_NOT_FOUND` only for an owned completed report without the requested work.

- [ ] **Step 4: Write failing MockMvc tests for list/detail transport contracts**

Assert optional list filters, cursor, size `1..100`, invalid enum `COMMON_001`, Korean label, nullable thumbnail, typed detail branch, common-only work, and content nullability:

```kotlin
mockMvc.perform(get("/api/v1/farming-reports/work-items")
    .with(authenticatedMember(memberId.toString()))
    .param("workType", "WATERING")
    .param("size", "2"))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.data.items[0].workType", equalTo("WATERING")))
    .andExpect(jsonPath("$.data.items[0].workTypeLabel", equalTo("물 주기")))

mockMvc.perform(get("/api/v1/farming-reports/{reportId}/work-types/{workType}", reportId, "WATERING")
    .with(authenticatedMember(memberId.toString())))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.data.statistics.watering.recordCount", equalTo(3)))
    .andExpect(jsonPath("$.data.statistics.harvest").doesNotExist())
    .andExpect(jsonPath("$.data.feedback.content.comparisons").isArray)
```

- [ ] **Step 5: Run controller tests and confirm RED**

Run:

```bash
cd backend
./gradlew :api:test --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Expected: controller and DTO types do not exist.

- [ ] **Step 6: Implement thin controller and explicit work response mapper**

Bind `workType` directly as `WorkType`, annotate both sizes with `@Min(1)`/`@Max(100)`, map authentication failures consistently, and convert results with concrete DTO constructors. `FarmingWorkReportResponses.StatisticsResponse` must use the shared explicit statistics DTOs and expose only the selected branch. `FeedbackStatusResponse` exposes only `status` and `content`; content exposes `summary`, `comparisons`, `strengths`, `improvements`, and `nextActions`, with every item reduced to `{ text }`.
Until Task 4 extends the persisted/query feedback model, initialize the work-detail result's `comparisons` to `emptyList()`; Task 4 replaces that empty projection with stored `COMPARISON` items without changing the HTTP shape.

- [ ] **Step 7: Verify GREEN and commit Task 3**

Run:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest' \
  :api:test --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Then commit:

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/report \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/report \
  backend/application/src/test/kotlin/com/chamchamcham/application/report \
  backend/api/src/test/kotlin/com/chamchamcham/api/report
git commit -m 'feat(report): 작업별 리포트 상세 계약 제공' \
  -m 'Constraint: API는 domain statistics를 직접 노출하지 않고 작업 하나만 공개함' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: work report application and MockMvc focused tests'
```

### Task 4: 결정적 비교 계산과 코칭 저장·조회

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackItemSection.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContent.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContext.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackComparisonCalculator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackContextAssembler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/dto/ReportFeedbackResponses.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback/ReportFeedbackTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackComparisonCalculatorTest.kt`
- Test: all existing `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/**` fixtures using changed constructors
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/reportfeedback/controller/ReportFeedbackControllerTest.kt`

**Interfaces:**
- Produces: `ReportFeedbackItemSection.COMPARISON`
- Produces: context schema version 3 with current/previous revisions and `comparisons: List<ReportFeedbackComparison>`
- Produces: `ReportFeedbackComparisonCalculator.calculate(workType, current, previous)`
- Extends: existing feedback content and public feedback API with `comparisons`

- [ ] **Step 1: Write failing pure calculator tests**

Use this bounded typed output:

```kotlin
data class ReportFeedbackComparison(
    val metricKey: String,
    val metricLabel: String,
    val currentValue: BigDecimal,
    val previousValue: BigDecimal,
    val difference: BigDecimal,
    val relativeChangePct: BigDecimal?,
    val unit: String,
    val currentCoverage: Coverage? = null,
    val previousCoverage: Coverage? = null,
)
```

Tests must prove stable metric order; common count/day/interval/photo-rate differences; previous zero produces null relative percentage; null omission; planting/pesticide same-unit matching; `G` versus `ML` omission; fertilizing/harvest normalized kg; pest spray liters; coverage preservation; and selected work isolation.

- [ ] **Step 2: Run calculator tests and confirm RED**

Run:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackComparisonCalculatorTest'
```

Expected: calculator and comparison type do not exist.

- [ ] **Step 3: Implement explicit typed comparison calculation**

Implement common metrics with direct properties and work quantities with a `when (workType)`. Compute `difference = current - previous`; compute relative percent only when previous is non-zero; normalize scale/rounding in one private decimal helper. Match list quantities by stable metric key and identical unit. Do not inspect arbitrary maps or JSON.

- [ ] **Step 4: Write failing context snapshot tests for previous/no-previous cases**

Assert schema version 3, current/previous IDs and revisions, deterministic comparison list, empty list when no previous or same-work count is zero, and preserved coverage. Expected context shape:

```kotlin
data class ReportFeedbackContext(
    val schemaVersion: Int,
    val workType: WorkType,
    val report: ReportFeedbackReport,
    val records: List<ReportFeedbackRecord>,
    val previousReport: ReportFeedbackPreviousReport?,
    val comparisons: List<ReportFeedbackComparison>,
    val warnings: List<String>,
)
```

Both report context types include `sourceRevision: Long`. Calculator input must be the typed current/previous `CycleReportStatistics` before statistics are converted to maps for existing prompt/retrieval code.

- [ ] **Step 5: Run context tests RED, implement assembly, then verify GREEN**

Run before and after implementation:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssemblerTest' \
  --tests 'com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackPreparationHandlerTest'
```

RED reason: context lacks revisions/comparisons. GREEN result: serialized snapshot contains both revisions and calculated differences and preserves empty comparisons.

- [ ] **Step 6: Write failing content/prompt/validator/generation/storage/query tests**

Add `comparisons` first in `ReportFeedbackContent.items()`. Tests must assert both `report:{currentId}` and `report:{previousId}` are allowed and cited, polite Korean comparison passes, non-존댓말 and English comparison fail, unknown refs fail, empty comparisons pass, comparison facts are not duplicated across sections, and COMPARISON rows persist before other sections. The feedback controller must return comparison text only and keep PENDING/FAILED content null.

- [ ] **Step 7: Run generation/public tests and confirm RED**

Run:

```bash
cd backend
./gradlew :domain:test --tests 'com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackTest' \
  :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.*' \
  :api:test --tests 'com.chamchamcham.api.coaching.reportfeedback.controller.ReportFeedbackControllerTest'
```

Expected: missing `COMPARISON` and constructor fields cause compilation/assertion failures.

- [ ] **Step 8: Implement comparison output, evidence, prompt, and existing-table persistence**

Add `COMPARISON` as the first section, add `comparisons` to generation/query/API content, include both report refs in prompt/validator/citations, and extend safe retry warning matching with `comparison`. The prompt must say the server-provided differences are final, the model must not recalculate them, comparisons contain factual changes only, and no statement may repeat across comparison/strength/improvement/next-action sections. Do not add item-count validation for comparisons.

- [ ] **Step 9: Verify GREEN and commit Task 4**

Run the command from Step 7 again and expect all selected tests to pass. Then commit:

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/coaching/reportfeedback \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback \
  backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/coaching/reportfeedback \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback \
  backend/api/src/test/kotlin/com/chamchamcham/api/coaching/reportfeedback
git commit -m 'feat(coaching): 직전 동일 작업의 비교 코칭 추가' \
  -m 'Constraint: 비교 산술은 서버가 결정하고 LLM은 친근한 존댓말 표현만 담당함' \
  -m 'Rejected: 비교 JSONB 또는 별도 테이블 | 기존 item section으로 충분함' \
  -m 'Confidence: high' -m 'Scope-risk: moderate' \
  -m 'Tested: calculator context prompt validator generation persistence query and API focused tests'
```

### Task 5: 전체 계약 회귀 검증과 최종 정리

**Files:**
- Modify only if a failing verification exposes a scoped regression in files already owned by Tasks 1-4.
- Verify: all changed production and test files

**Interfaces:**
- Consumes: all prior task outputs
- Produces: fresh full-suite and diff-hygiene evidence

- [ ] **Step 1: Search forbidden/deleted surfaces and schema additions**

Run:

```bash
rg -n '/api/v1/farming-reports/current|fun getCurrent|CurrentResponse|FarmingCycleReportResult.Current' backend frontend --glob '!**/build/**'
rg -n 'class FarmingWorkReport.*@Entity|@Table\(.*farming_work_report|comparison.*jsonb|Map<String, Any\?>|JsonNode' \
  backend/api/src/main/kotlin/com/chamchamcham/api/report backend/domain/src/main/kotlin --glob '!**/build/**'
```

Expected: no runtime current API, no work-report entity/table/comparison JSONB, and no generic map/JSON in the new public report DTOs. Test or historical documentation references are inspected and retained only when they are not runtime consumers.

- [ ] **Step 2: Run focused report and coaching tests**

Run:

```bash
cd backend
./gradlew --no-parallel :domain:test \
  --tests 'com.chamchamcham.domain.report.*' \
  --tests 'com.chamchamcham.domain.farming.FarmingWorkReportSourceRepositoryTest' \
  :application:test \
  --tests 'com.chamchamcham.application.report.*' \
  --tests 'com.chamchamcham.application.coaching.reportfeedback.*' \
  :api:test \
  --tests 'com.chamchamcham.api.report.*' \
  --tests 'com.chamchamcham.api.coaching.reportfeedback.*' \
  --rerun-tasks
```

Expected: all focused tests pass with zero failures.

- [ ] **Step 3: Run the required full module verification**

Run exactly:

```bash
cd backend
./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. Pre-existing Gradle/`@MockBean` deprecation warnings may remain; no new warning attributable to this change is accepted.

- [ ] **Step 4: Verify diff hygiene and commit metadata**

Run:

```bash
git diff --check
git status --short
git log --format=full -5
```

Expected: `git diff --check` has no output; only planned files are changed; every implementation commit has a Conventional Commit subject and Lore trailers including `Constraint`, `Confidence`, `Scope-risk`, and `Tested` or `Not-tested` where applicable.

- [ ] **Step 5: Fix only evidenced regressions and repeat the failing verification**

For every failure, add or retain the reproducing test, make the smallest scoped code correction, rerun the focused failing command, then rerun Steps 2-4. Do not add dependencies, generic infrastructure, or unrelated cleanup during this pass.
