# Report Last Worked Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 작업 리포트 목록과 완료 리포트 코칭 목록을 작업 종류별 마지막 작업일 최신순으로 반환한다.

**Architecture:** `CycleReportStatistics`가 작업 종류별 마지막 작업일을 제공하고, 도메인 저장소는 하나의 정렬 키 comparator를 목록 정렬과 커서 경계 판정에 함께 사용한다. 애플리케이션 커서 payload는 마지막 작업일을 포함하며, 코칭 조회 서비스는 같은 통계 접근자로 피드백을 정렬한다.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Jackson, JUnit 5, AssertJ, Mockito, Gradle

## Global Constraints

- 작업 리포트 정렬은 `lastWorkedOn DESC`, `status ASC`, `sortAt DESC`, `reportId DESC`, `workType.ordinal ASC` 순서다.
- 코칭 목록 정렬은 `lastWorkedOn DESC`, `workType.ordinal ASC` 순서다.
- nullable `lastWorkedOn`은 유지하며 null은 가장 마지막에 둔다.
- API 응답 형태와 데이터베이스 스키마는 변경하지 않는다.
- 새 의존성이나 범용 추상화를 추가하지 않는다.
- 작업 리포트 커서는 정렬 버전을 검증하며 구형 커서는 `INVALID_CURSOR`로 거절한다.

---

### Task 1: 도메인 작업 항목 정렬과 커서 경계 통일

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/CycleReportStatistics.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryImpl.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/report/FarmingCycleReportQueryRepositoryTest.kt`

**Interfaces:**
- Produces: `CycleReportStatistics.lastWorkedOnFor(workType: WorkType): LocalDate?`
- Produces: `WorkItemCursor(lastWorkedOn, status, sortAt, reportId, workType)`
- Produces: 목록 정렬과 커서 판정이 공유하는 private `WorkItemSortKey`

- [ ] **Step 1: 최근 작업일이 상태와 enum 순서보다 우선하는 실패 테스트 작성**

기존 작업 항목 정렬 테스트에서 ACTIVE 작업일을 더 오래된 값으로 바꾸고 정확한 순서를 검증한다.

```kotlin
val active = persistActive(
    startsAt = day(10),
    statistics = CycleReportStatistics(
        watering = WateringStatistics(recordCount = 4, lastWorkedOn = day(20).toLocalDate()),
    ),
)

assertThat(all.map { it.reportId to it.workType }).containsExactly(
    requireNotNull(latest.id) to WorkType.HARVEST,
    requireNotNull(latest.id) to WorkType.WATERING,
    requireNotNull(active.id) to WorkType.WATERING,
    requireNotNull(sameEndOtherScope.id) to WorkType.PLANTING,
)
```

- [ ] **Step 2: 도메인 저장소 테스트를 실행해 기존 정렬 때문에 실패 확인**

Run: `cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportQueryRepositoryTest*' --rerun-tasks`

Expected: 최신 완료 리포트보다 ACTIVE 항목이 먼저 반환되어 순서 assertion 실패.

- [ ] **Step 3: null 마지막 작업일과 같은 날짜 동률 테스트 추가**

양수 record count와 null 작업일을 가진 항목을 fixture에 추가하고 마지막인지 검증한다. 같은 날짜의 ACTIVE와 COMPLETED는 ACTIVE가 먼저인지 검증한다.

```kotlin
val missingWorkedOn = persistCompleted(
    endedAt = day(70),
    statistics = CycleReportStatistics(
        etc = CommonOnlyStatistics(recordCount = 1, lastWorkedOn = null),
    ),
)

assertThat(all.last().reportId to all.last().workType)
    .isEqualTo(requireNotNull(missingWorkedOn.id) to WorkType.ETC)
```

같은 작업일의 상태 동률 처리는 별도 테스트로 고정한다.

```kotlin
val tiedActive = persistActive(
    startsAt = day(5),
    statistics = CycleReportStatistics(
        watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(25).toLocalDate()),
    ),
)
val tiedCompleted = persistCompleted(
    endedAt = day(30),
    statistics = CycleReportStatistics(
        watering = WateringStatistics(recordCount = 1, lastWorkedOn = day(25).toLocalDate()),
    ),
)

val tied = repository.searchWorkItems(workCondition(size = 20)).rows
assertThat(tied.map { it.reportId }).containsExactly(tiedActive.id, tiedCompleted.id)
```

- [ ] **Step 4: 통계 접근자와 공용 정렬 키 최소 구현**

`CycleReportStatistics`에 다음 접근자를 추가한다.

```kotlin
fun lastWorkedOnFor(workType: WorkType): LocalDate? = when (workType) {
    WorkType.PLANTING -> planting.lastWorkedOn
    WorkType.WATERING -> watering.lastWorkedOn
    WorkType.FERTILIZING -> fertilizing.lastWorkedOn
    WorkType.PEST_CONTROL -> pestControl.lastWorkedOn
    WorkType.WEEDING -> weeding.lastWorkedOn
    WorkType.PRUNING -> pruning.lastWorkedOn
    WorkType.HARVEST -> harvest.lastWorkedOn
    WorkType.ETC -> etc.lastWorkedOn
}
```

`WorkItemCursor`에 `lastWorkedOn: LocalDate?`를 추가하고 저장소 투영은 `recordCountFor`와 `lastWorkedOnFor`를 사용한다. 목록과 커서가 같은 comparator를 쓰도록 다음 키를 둔다.

```kotlin
private data class WorkItemSortKey(
    val lastWorkedOn: LocalDate?,
    val statusRank: Int,
    val sortAt: LocalDateTime,
    val reportId: UUID,
    val workTypeOrdinal: Int,
)

private val workItemSortKeyComparator =
    compareByDescending<WorkItemSortKey> { it.lastWorkedOn ?: LocalDate.MIN }
        .thenBy { it.statusRank }
        .thenByDescending { it.sortAt }
        .thenByDescending { it.reportId }
        .thenBy { it.workTypeOrdinal }

private fun WorkItem.sortKey() = WorkItemSortKey(
    lastWorkedOn = lastWorkedOn,
    statusRank = status.rank(),
    sortAt = sortAt,
    reportId = reportId,
    workTypeOrdinal = workType.ordinal,
)

private fun WorkItemCursor.sortKey() = WorkItemSortKey(
    lastWorkedOn = lastWorkedOn,
    statusRank = status.rank(),
    sortAt = sortAt,
    reportId = reportId,
    workTypeOrdinal = workType.ordinal,
)

private fun WorkItem.isAfter(cursor: WorkItemCursor): Boolean =
    workItemSortKeyComparator.compare(sortKey(), cursor.sortKey()) > 0
```

- [ ] **Step 5: 커서 테스트를 최신 작업 항목과 새 키에 맞게 갱신**

```kotlin
cursor = FarmingCycleReportQueryRepository.WorkItemCursor(
    lastWorkedOn = first.lastWorkedOn,
    status = first.status,
    sortAt = first.sortAt,
    reportId = first.reportId,
    workType = first.workType,
)

assertThat(first.workType).isEqualTo(WorkType.HARVEST)
assertThat(secondPage.map { it.reportId to it.workType }).containsExactly(
    requireNotNull(active.id) to WorkType.WATERING,
    requireNotNull(completed.id) to WorkType.WATERING,
)
```

- [ ] **Step 6: 도메인 저장소 테스트 통과 확인**

Run: `cd backend && ./gradlew :domain:test --tests '*FarmingCycleReportQueryRepositoryTest*' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`, 대상 테스트 실패 0건.

- [ ] **Step 7: 도메인 변경 커밋**

Stage the four Task 1 files and commit with title `fix(report): 최근 작업을 먼저 찾도록 목록 순서 변경`. Lore trailers에는 검증 명령과 구형 커서 제약을 기록한다.

---

### Task 2: 애플리케이션 커서에 마지막 작업일 포함

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryServiceTest.kt`

**Interfaces:**
- Consumes: Task 1의 `WorkItemCursor.lastWorkedOn`
- Produces: `FarmingWorkReportCursorPayload.version: Int`와 `lastWorkedOn: LocalDate?`

- [ ] **Step 1: 커서 인코딩·디코딩 실패 테스트 작성**

```kotlin
assertThat(decoded.lastWorkedOn).isEqualTo(page.items.last().lastWorkedOn)
assertThat(decoded.version).isEqualTo(FarmingWorkReportCursorPayload.CURRENT_VERSION)

val cursorPayload = FarmingWorkReportCursorPayload(
    version = FarmingWorkReportCursorPayload.CURRENT_VERSION,
    lastWorkedOn = day(29).toLocalDate(),
    status = FarmingCycleReportStatus.ACTIVE,
    sortAt = day(30),
    reportId = id("399"),
    workType = WorkType.WATERING,
)

cursor = FarmingCycleReportQueryRepository.WorkItemCursor(
    lastWorkedOn = cursorPayload.lastWorkedOn,
    status = cursorPayload.status,
    sortAt = cursorPayload.sortAt,
    reportId = cursorPayload.reportId,
    workType = cursorPayload.workType,
)
```

- [ ] **Step 2: 애플리케이션 서비스 테스트를 실행해 payload 필드 부재로 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests '*FarmingWorkReportQueryServiceTest*' --rerun-tasks`

Expected: `FarmingWorkReportCursorPayload.lastWorkedOn`이 없어 컴파일 실패.

- [ ] **Step 3: payload와 서비스 매핑 최소 구현**

```kotlin
data class FarmingWorkReportCursorPayload(
    val version: Int,
    val lastWorkedOn: LocalDate?,
    val status: FarmingCycleReportStatus,
    val sortAt: LocalDateTime,
    val reportId: UUID,
    val workType: WorkType,
)
```

`CURRENT_VERSION = 2`를 companion object에 두고, `decodeCursor`에서 버전이 다르면 `INVALID_CURSOR`를 던진다. `decodeCursor`에는 `lastWorkedOn = payload.lastWorkedOn`, `encodeCursor`에는 현재 버전과 `lastWorkedOn = row.lastWorkedOn`을 추가한다. 버전 필드가 없는 legacy payload를 인코딩한 회귀 테스트로 저장소 호출 전에 거절되는지 검증한다.

- [ ] **Step 4: 애플리케이션 서비스 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests '*FarmingWorkReportQueryServiceTest*' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`, 대상 테스트 실패 0건.

- [ ] **Step 5: 커서 변경 커밋**

Stage the three Task 2 files and commit with title `fix(report): 마지막 작업일 커서 경계 반영`. Lore trailers에 구형 커서가 거절됨을 기록한다.

---

### Task 3: 완료 리포트 코칭 목록 최신 작업순 정렬

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryServiceTest.kt`

**Interfaces:**
- Consumes: Task 1의 `CycleReportStatistics.lastWorkedOnFor(workType)`
- Produces: `ReportFeedbackListResult.feedbacks`의 최근 작업일 내림차순 계약

- [ ] **Step 1: 카탈로그 순서와 다른 마지막 작업일 fixture로 실패 테스트 작성**

테스트 이름을 `completed report returns work type feedbacks by latest work date with ready content only`로 바꾸고 통계 날짜를 다음처럼 만든다.

```kotlin
private fun statisticsFor(vararg workTypes: WorkType) = CycleReportStatistics(
    watering = WateringStatistics(
        recordCount = WorkType.WATERING.countIn(workTypes),
        lastWorkedOn = LocalDate.of(2026, 6, 2),
    ),
    fertilizing = FertilizingStatistics(
        recordCount = WorkType.FERTILIZING.countIn(workTypes),
        lastWorkedOn = LocalDate.of(2026, 6, 1),
    ),
    harvest = HarvestStatistics(
        recordCount = WorkType.HARVEST.countIn(workTypes),
        lastWorkedOn = LocalDate.of(2026, 6, 3),
    ),
)

assertThat(result.feedbacks.map { it.workType }).containsExactly(
    WorkType.HARVEST,
    WorkType.WATERING,
    WorkType.FERTILIZING,
)
```

콘텐츠 상세 assertion은 `first { it.workType == WorkType.WATERING }`처럼 찾아 정렬 검증과 분리한다.

- [ ] **Step 2: 코칭 조회 테스트를 실행해 기존 ordinal 정렬 때문에 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests '*ReportFeedbackQueryServiceTest*' --rerun-tasks`

Expected: 실제 catalog 순서와 기대 최신순이 달라 assertion 실패.

- [ ] **Step 3: 코칭 목록 comparator 최소 구현**

```kotlin
.sortedWith(
    compareByDescending<ReportFeedback> {
        report.statistics.lastWorkedOnFor(it.workType) ?: LocalDate.MIN
    }.thenBy { it.workType.ordinal },
)
```

- [ ] **Step 4: 코칭 조회 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests '*ReportFeedbackQueryServiceTest*' --rerun-tasks`

Expected: `BUILD SUCCESSFUL`, 대상 테스트 실패 0건.

- [ ] **Step 5: 코칭 목록 변경 커밋**

Stage the two Task 3 files and commit with title `fix(coaching): 최근 작업 코칭을 먼저 노출`. Lore trailers에 null 작업일의 맨 뒤 정렬을 기록한다.

---

### Task 4: 전체 회귀 검증

**Files:**
- Verify only: all changed files

**Interfaces:**
- Consumes: Tasks 1-3의 정렬·커서 계약
- Produces: 병합 가능한 검증 증거

- [ ] **Step 1: 변경 파일 정적 점검**

Run: `git diff --check dev...HEAD`

Expected: 출력 없음, exit code 0.

- [ ] **Step 2: backend 전체 검증 실행**

Run: `cd backend && ./gradlew check --rerun-tasks`

Expected: `BUILD SUCCESSFUL`, 테스트 실패 0건.

- [ ] **Step 3: 최종 변경 범위와 커서 호환성 검토**

Run: `git status --short`, `git diff --stat dev...HEAD`, `git log --oneline dev..HEAD`.

Expected: worktree clean, 정렬·커서·코칭 및 설계/계획 파일만 변경, 의도한 커밋만 존재.
