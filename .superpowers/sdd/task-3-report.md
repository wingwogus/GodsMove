# Task 3 Report — 작업 목록·상세 HTTP API와 typed statistics/feedback 조합

## Result

- `GET /api/v1/farming-reports/work-items`를 추가했다.
- `GET /api/v1/farming-reports/{reportId}/work-types/{workType}`를 추가했다.
- 작업 상세는 회원 소유의 `COMPLETED` 리포트만 공개하며, 작업 기록이 0건이면
  `WORK_REPORT_NOT_FOUND (REPORT_002)`를 반환한다.
- 작업 통계는 `common`과 선택한 작업의 구체 타입 하나만 API DTO로 변환한다.
- `PRUNING`, `ETC`는 `common`만 반환한다.
- 작업 라벨은 `물 주기`, `거름 주기`, `가지 정리` 같은 쉬운 한국어를 재사용한다.
- 작업 피드백은 기존 `ReportFeedbackQueryService.get` 결과를 조합한다. 저장 행이
  일시적으로 없으면 `PENDING`/`content: null`이며, `READY` content에는 Task 4 전까지
  `comparisons: []`를 제공한다.

## RED Evidence

### Application detail contract

Command:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest'
```

Expected failure observed:

- `FarmingWorkReportQueryService`에 `reportRepository`, `feedbackQueryService` 의존성이 없음
- `getDetail` 미정의
- `ErrorCode.WORK_REPORT_NOT_FOUND` 미정의

테스트 fixture의 잘못된 `sourceRevision` 인자를 먼저 제거한 후 다시 실행하여 위 신규
계약 부재만으로 실패함을 확인했다.

### HTTP contract

Command:

```bash
cd backend
./gradlew :api:test --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Expected failure observed:

- `FarmingWorkReportController` 미정의

### Feedback comparisons result contract

Command:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest'
```

Expected failure observed:

- `ReportFeedbackResultContent.comparisons` 미정의

## GREEN Evidence

Focused Task 3 tests:

```bash
cd backend
./gradlew :application:test --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest' \
  :api:test --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Result: `BUILD SUCCESSFUL` (6s).

Focused tests plus existing report/feedback regressions:

```bash
cd backend
./gradlew :application:test \
  --tests 'com.chamchamcham.application.report.FarmingWorkReportQueryServiceTest' \
  --tests 'com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryServiceTest' \
  :api:test \
  --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest' \
  --tests 'com.chamchamcham.api.report.controller.FarmingCycleReportControllerTest' \
  --tests 'com.chamchamcham.api.coaching.reportfeedback.controller.ReportFeedbackControllerTest'
```

Result: `BUILD SUCCESSFUL` (4s).

Full changed-module checks:

```bash
cd backend
./gradlew :application:check :api:check
```

Result: `BUILD SUCCESSFUL` (18s). The build has no detekt/ktlint or separate static-analysis
task; Kotlin compilation and all configured verification for both modules ran through `check`.

## Changed Files

- `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportResult.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryService.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackQueryService.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportController.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/report/dto/FarmingWorkReportResponses.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/report/FarmingWorkReportQueryServiceTest.kt`
- `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportControllerTest.kt`
- `.superpowers/sdd/task-3-report.md`

## Self-review

- 컨트롤러는 인증 주체/쿼리/path를 바인딩하고 application result를 DTO로 변환하는 일만 한다.
- path와 query의 `workType`은 `WorkType`으로 직접 바인딩하므로 잘못된 enum은
  `COMMON_001`로 처리된다.
- `size`는 API 경계에서 `1..100`으로 검증한다.
- API 응답은 `Map`, `Any`, `JsonNode`, 범용 mapper 또는 JPA entity를 사용하지 않는다.
- 통계 DTO는 Task 1의 `FarmingReportStatisticsResponses` 구체 타입을 재사용하며,
  선택하지 않은 nullable branch는 JSON에서 제외한다.
- 상세 검증 순서는 회원 범위/완료 상태, 작업 `recordCount`, 피드백 조회 순서다.
- 목록의 기존 cursor/thumbnail 계산 경로는 변경하지 않고 라벨만 결과에 추가했다.
- `git diff --check`에서 whitespace 오류가 없음을 확인했다.

## Concerns / Follow-up

- Task 4가 `COMPARISON` 저장 항목을 추가할 때
  `ReportFeedbackResultContent.comparisons`를 실제 저장 결과로 채우면 작업 상세 HTTP
  mapper 변경 없이 항목이 노출된다. 현재는 의도적으로 빈 배열이다.
- Gradle/Spring 테스트 출력에는 기존 `MockBean` deprecation 및 Gradle 9 호환성 경고가
  있으나 실패나 이번 변경으로 새로 도입된 런타임 오류는 없다.
- 정확한 token usage는 현재 실행 표면에서 제공되지 않았다.
