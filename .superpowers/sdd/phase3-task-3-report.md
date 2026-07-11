# Phase 3 Task 3 Report — Snapshot RAG Generator Replacement

## Scope

- Implemented only the product record feedback generator replacement.
- Kept CHAT RAG service unchanged.
- Did not start Task 4 processor/API mapping work.
- Removed unused legacy Today generator surface and DTO.

## Changed Files

- Added `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationService.kt`
- Added `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationServiceTest.kt`
- Added `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/RecordFeedbackGenerationVectorStoreSmokeTest.kt`
- Deleted `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`
- Deleted `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`
- Deleted `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/dto/TodayRecordFeedbackResponses.kt`
- Deleted `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/TodayRecordFeedbackVectorStoreSmokeTest.kt`

## TDD Evidence

Red:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackGenerationServiceTest'
```

Result: failed at `:application:compileTestKotlin` because the new production API did not exist yet:

- `Unresolved reference: RecordFeedbackGenerationService`
- `Unresolved reference: RecordFeedbackGenerationException`
- `Unresolved reference: RecordFeedbackGenerationFailureCode`

Green/final focused gate:

```bash
cd backend
./gradlew :application:test \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackRetrievalQueryPlannerTest' \
  :api:test \
  --tests '*RecordFeedback*VectorStoreSmokeTest' \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 9s`, 15 actionable tasks executed.

Test XML evidence:

- `RecordFeedbackGenerationServiceTest`: 9 tests, 0 skipped, 0 failures, 0 errors.
- `RecordFeedbackRetrievalQueryPlannerTest`: 9 tests, 0 skipped, 0 failures, 0 errors.
- `RecordFeedbackGenerationVectorStoreSmokeTest`: 1 test, 1 skipped, 0 failures, 0 errors. This is expected unless `RUN_LOCAL_RAG_SMOKE=true`.

Additional checks:

```bash
git diff --check
```

Result: passed with no whitespace errors.

```bash
rg -n "TodayRecordFeedbackService|TodayRecordFeedbackResult|TodayRecordFeedbackResponses|TodayRecordFeedbackVectorStoreSmokeTest" backend
```

Result: no matches.

No separate `lsp_diagnostics` tool was available in this session; Kotlin compile/test tasks above completed successfully for modified application/API modules.

## Implementation Notes

- `GeneratedRecordFeedback` returns the product `RecordFeedbackCoachingResult`, authoritative citation maps, audit warnings, and `RagModelInfo`.
- `RecordFeedbackGenerationException` exposes only:
  - `INSUFFICIENT_EVIDENCE`
  - `STRUCTURED_OUTPUT_INVALID`
  - `GENERATION_FAILED`
- Retrieval reuses `RecordFeedbackRetrievalQueryPlanner`, `VectorStore`, the TECH_DOCUMENT crop filter, and `GENERAL` crop fallback.
- The generator does not attach a retrieval advisor.
- Retrieved documents are restricted to `TECH_DOCUMENT`; context-only/farming-record documents are not enough evidence.
- The prompt builder receives only `RecordFeedbackContext`, planned queries, and official document evidence.
- The LLM is called once, then exactly once more only for structured parse failure or product output validation failure.
- Invalid product output is not sanitized; after the retry is exhausted it fails with `STRUCTURED_OUTPUT_INVALID`.
- Vector store and chat runtime failures map to `GENERATION_FAILED`.
- Missing official documents map immediately to `INSUFFICIENT_EVIDENCE`.
- Citation maps are built only after product output validation, using server-side document metadata and context citation metadata.

## Coverage Added

- Context plus official document evidence only.
- No retrieval advisor use.
- TECH_DOCUMENT/crop filter construction.
- Context-only retrieved documents fail as insufficient evidence.
- Empty retrieval fails as insufficient evidence.
- One retry for product validation failure.
- One retry for structured output parse failure.
- Exhausted retry fails as `STRUCTURED_OUTPUT_INVALID`.
- Vector runtime failure maps to `GENERATION_FAILED`.
- Chat runtime failure maps to `GENERATION_FAILED` without structured-output retry.
- Server-side citation metadata for document and record citations.

## Residual Risks / Notes

- The local vector-store smoke test remains environment-gated by `RUN_LOCAL_RAG_SMOKE=true`; it was compiled and reported as skipped in the default local test environment.
- No Task 4 processor/controller exception mapping was added.

## Review Fix Evidence — 2026-07-11

Scope:

- Updated only `RecordFeedbackGenerationService.kt` and `RecordFeedbackGenerationServiceTest.kt`.
- Added a citable-official-evidence gate before the generator's empty-evidence check: retrieved `TECH_DOCUMENT` documents must have nonblank `id` and nonblank `text`.
- Added regression coverage for blank official document ID and blank official document text, both asserting `INSUFFICIENT_EVIDENCE` and zero LLM calls.

Red:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackGenerationServiceTest' --rerun-tasks
```

Result: `BUILD FAILED`; the two new tests failed because blank-ID and blank-text official documents still reached generation.

Green/final focused gate:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackGenerationServiceTest' --tests '*RecordFeedbackRetrievalQueryPlannerTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL in 7s`, 8 actionable tasks executed.

Additional check:

```bash
git diff --check
```

Result: passed with no whitespace errors.
