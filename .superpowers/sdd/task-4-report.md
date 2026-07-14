# Task 4 Report: 결정적 비교 계산과 코칭 저장·조회

## Result

- `ReportFeedbackComparisonCalculator`가 선택한 작업의 typed 통계만 읽어 공통 지표와 작업별 수량 차이를 고정 순서로 계산한다.
- context schema를 v3으로 올리고 현재/직전 리포트 revision, 비교 결과, coverage를 `input_snapshot`에 직렬화한다.
- `ReportFeedbackItemSection.COMPARISON`을 기존 item 행에 첫 섹션으로 저장하고, 전체 feedback API와 작업 상세 API에서 문장만 반환한다.
- 프롬프트는 서버 계산값을 최종값으로 사용하고 재계산하지 않도록 제한하며, validator는 친근한 존댓말·영문·evidence ref·섹션 간 중복을 검사한다.

## RED evidence

1. Calculator
   - Command: `./gradlew :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackComparisonCalculatorTest'`
   - Expected failure: `ReportFeedbackComparisonCalculator`, `ReportFeedbackComparison` unresolved.
2. Context v3 / snapshot
   - Command: `./gradlew :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackContextAssemblerTest' --tests 'com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackPreparationHandlerTest'`
   - Expected failure: `sourceRevision`, `comparisons`, `comparisonCalculator` contract absent.
3. Output / persistence / public response
   - Command: `./gradlew :domain:test --tests 'com.chamchamcham.domain.coaching.reportfeedback.ReportFeedbackTest' :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.*' :api:test --tests 'com.chamchamcham.api.coaching.reportfeedback.controller.ReportFeedbackControllerTest'`
   - Expected failure: `COMPARISON` enum and `ReportFeedbackContent.comparisons` absent.

## GREEN evidence

- Calculator focused test: PASS.
- Context assembler + preparation snapshot focused tests: PASS (13 tests).
- Required domain/application/API focused command: PASS.
- Explicit calculator/context/prompt/validator/generation/persistence/query/work-detail regression command: PASS.
- `./gradlew --no-parallel :domain:test :application:test :api:test`: PASS in 27s.
- `git diff --check`: PASS.

## Changed files

- Domain: `ReportFeedbackItemSection.kt`, `ReportFeedbackTest.kt`
- Application generation: `ReportFeedbackComparisonCalculator.kt`, `ReportFeedbackContent.kt`, `ReportFeedbackContext.kt`, `ReportFeedbackContextAssembler.kt`, `ReportFeedbackPromptBuilder.kt`, `ReportFeedbackOutputValidator.kt`, `ReportFeedbackGenerationService.kt`
- Application query: `ReportFeedbackQueryService.kt`
- API: `ReportFeedbackResponses.kt`
- Tests: calculator, context snapshot, prompt, validator, generation, persistence handler, query, feedback controller, work-detail service/controller regressions

## Self-review

- Common metric order is fixed; planting and pesticide quantities are sorted/matched by stable key and exact unit.
- Previous zero omits relative percentage; null or incompatible units are omitted; kg/L normalized aggregate fields and coverage are preserved.
- No reflection/JSON traversal is used for comparison calculation, and no new dependency, table, JSONB field, or generic mapper was added.
- PENDING/FAILED content remains null; READY content exposes `comparisons` as text-only items.
- Current and previous report evidence refs are allowed and emitted as citations.

## Residual concern

- The full module suite covers generated-schema JPA behavior, but production PostgreSQL DDL rollout was not exercised in this task. `COMPARISON` uses the existing string enum column and does not require a new table or column.
- Exact token usage is unavailable in this execution surface.

## Review fixes

### Findings resolved

- 서버 `context.comparisons`가 비어 있으면 모델이 만든 비교 문장을 `comparison_not_available`로 거부한다.
- 서버 비교가 있을 때 모든 비교 문장은 현재·직전 `report:*` 근거를 각각 포함해야 하며, 누락 시 값이 없는 고정 warning code를 반환한다.
- 중복 검사는 내부 basis를 제외하고 정규화된 공개 `text`만 사용해 섹션 전체에서 같은 문장을 거부한다.
- 직전 리포트가 없는 context도 `input_snapshot.comparisons`를 명시적인 빈 배열로 보존하는 회귀 테스트를 추가했다.

### Review-fix RED evidence

- Command: `./gradlew :application:test --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackOutputValidatorTest' --tests 'com.chamchamcham.application.coaching.reportfeedback.generation.ReportFeedbackGenerationServiceTest' --tests 'com.chamchamcham.application.coaching.reportfeedback.lifecycle.ReportFeedbackPreparationHandlerTest'`
- Result: 35 tests 중 7개가 예상대로 실패했다.
- Failure causes: 비교 가용성 검증 없음, 양쪽 report ref 강제 없음, basis가 다른 동일 공개 문장 중복 미검출, safe retry 고정 code 없음.
- 빈 `comparisons: []` 직렬화 회귀 테스트는 기존 동작이 이미 올바르게 보존해 첫 실행부터 통과했으며 production 변경이 필요하지 않았다.

### Review-fix GREEN evidence

- 위 validator/generation/preparation focused command: PASS.
- calculator/context/prompt/validator/generation/persistence/query/work-detail와 feedback API 전체 focused 회귀: PASS in 5s.
- `./gradlew --no-parallel :domain:test :application:test :api:test`: PASS in 25s.
- `git diff --check`: PASS.
