# Task 5 Report — report cursor 오류의 요청 경계 번역

## Integration Review Finding

공용 `OpaqueCursorCodec`는 잘못된 cursor를 `BusinessException(ErrorCode.INVALID_CURSOR)`로
일관되게 표현한다. 그러나 report HTTP 목록의 cursor는 요청 shape이므로 두 report
controller가 해당 오류만 `INVALID_INPUT`으로 번역해 공개 API 응답을 `COMMON_001`로
맞춰야 한다는 통합 리뷰 finding이 있었다.

다음 두 경로에만 번역을 적용했다.

- `GET /api/v1/farming-reports?cursor=not-base64`
- `GET /api/v1/farming-reports/work-items?cursor=not-base64`

공용 `OpaqueCursorCodec`, application query service, domain 및 다른 cursor 소비자는
변경하지 않았다. 두 controller의 list 호출에서 `INVALID_CURSOR`만 변환하며, 다른
`BusinessException`은 원본 그대로 다시 던진다. detail 호출도 영향을 받지 않는다.

## RED Evidence

두 MockMvc 테스트에서 query service가 `BusinessException(ErrorCode.INVALID_CURSOR)`를
던지도록 구성하고 HTTP 400의 `$.error.code == COMMON_001`을 먼저 요구했다.

```bash
cd backend
./gradlew :api:test \
  --tests 'com.chamchamcham.api.report.controller.FarmingCycleReportControllerTest' \
  --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Result: `20 tests completed, 2 failed`. 두 실패 모두 실제 응답이 `COMMON_003`이고
기대값이 `COMMON_001`이어서 발생했음을 XML test result에서 확인했다.

## GREEN Evidence

두 controller focused tests:

```bash
cd backend
./gradlew :api:test \
  --tests 'com.chamchamcham.api.report.controller.FarmingCycleReportControllerTest' \
  --tests 'com.chamchamcham.api.report.controller.FarmingWorkReportControllerTest'
```

Result: `BUILD SUCCESSFUL` (3s).

전체 application report 회귀, report-feedback query, report API controller 및 기존
report-feedback controller:

```bash
cd backend
./gradlew :application:test \
  --tests 'com.chamchamcham.application.report.*' \
  --tests 'com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackQueryServiceTest' \
  :api:test \
  --tests 'com.chamchamcham.api.report.controller.*' \
  --tests 'com.chamchamcham.api.coaching.reportfeedback.controller.ReportFeedbackControllerTest'
```

Result: `BUILD SUCCESSFUL` (4s).

## Changed Files

- `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportController.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportController.kt`
- `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingCycleReportControllerTest.kt`
- `backend/api/src/test/kotlin/com/chamchamcham/api/report/controller/FarmingWorkReportControllerTest.kt`
- `.superpowers/sdd/task-5-report.md`

## Self-review / Remaining Risk

- 번역은 list query service 호출의 `try/catch`에만 있다.
- `exception.errorCode == ErrorCode.INVALID_CURSOR`만 새 `INVALID_INPUT`으로 바꾼다.
- 공통 validator 계층이나 의존성을 추가하지 않았다.
- codec/application/domain 파일에는 diff가 없다.
- 기존 Gradle 9 호환성 및 Spring `MockBean` deprecation 경고 외 신규 경고는 없다.
- 실제 서버를 실행한 수동 HTTP 호출은 하지 않았다.
- 정확한 token usage는 현재 실행 표면에서 제공되지 않았다.
