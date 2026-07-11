# Phase 3 Task 2 Report

## Status

DONE

## Scope

- Added `RecordFeedbackOutputValidator` for `RecordFeedbackCoachingResult`.
- Updated `RecordFeedbackPromptBuilder` to request the product-specific `RecordFeedbackCoachingResult` contract.
- Updated prompt tests and added focused validator tests.
- Did not modify Task 1 files.
- Did not start Task 3 generation-service work.

## TDD Evidence

### RED

Command:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest'
```

Result:

```text
FAILED: compileTestKotlin
Unresolved reference: RecordFeedbackOutputValidator
```

This failed for the expected reason: Task 2 production validator did not exist.

### GREEN

Command:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --tests '*RecordFeedbackPromptBuilderTest'
```

Result:

```text
BUILD SUCCESSFUL in 2s
```

### Broader Verification

Command:

```bash
cd backend
./gradlew :application:test
```

Result:

```text
BUILD SUCCESSFUL in 4s
```

## Implementation Notes

- `RecordFeedbackAllowedEvidenceRefs` is built from:
  - `record:<recordId>`
  - `weather:current`
  - each `weather:<forecast-date>`
  - retrieved document IDs
- Validation covers:
  - nonblank `basis` and `text`
  - nonempty `evidenceRefs`
  - next action count `2..3`
  - text length `15..45`
  - unknown citation refs
  - weather action requiring weather evidence
  - pest/disease action requiring document evidence
  - at least one normalized two-character-or-longer basis token appearing in text
- Prompt contract now instructs Korean output as one `goodPoint` and two to three `nextActions` with `basis`, `text`, and `evidenceRefs`.
- Removed old prompt instructions for summary/risk/diagnosis/observations/recommendations/follow-up style output.

## Self Review

- Scope checked with `git diff --name-only` and untracked file listing.
- Task 3 search found only pre-existing `TodayRecordFeedbackService` files; no generation-service files were created or edited.
- Prompt wording was adjusted once during review to avoid implying weather evidence should use the record citation.
- No new dependencies were added.

## Changed Files

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidator.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidatorTest.kt`
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`
- `.superpowers/sdd/phase3-task-2-report.md`

## Concerns

- No separate LSP diagnostics tool is available in this session; Kotlin compilation and application tests were used as diagnostics/typecheck evidence.
- Gradle reports existing deprecation warnings about Gradle 9 compatibility; not introduced by this task.

## Review Fix Evidence

### RED

Command:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --rerun-tasks
```

Result:

```text
RecordFeedbackOutputValidatorTest > rejects blank text and blank evidence ref() FAILED
RecordFeedbackOutputValidatorTest > does not allow blank document ids as evidence refs() FAILED
BUILD FAILED
```

This failed for the intended review findings: blank output refs had no dedicated nonblank warning, and blank document IDs could enter allowed evidence refs.

### GREEN

Command:

```bash
cd backend
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --tests '*RecordFeedbackPromptBuilderTest' --rerun-tasks
```

Result:

```text
BUILD SUCCESSFUL in 7s
8 actionable tasks: 8 executed
```

### Review Fix Notes

- Blank retrieved document IDs are filtered out before constructing allowed evidence refs.
- Blank output `evidenceRefs` entries now emit `*_evidence_ref_blank`; lists with no nonblank refs also keep the existing `*_evidence_refs_blank` warning.
- Unknown evidence checks skip blank refs so blank refs are reported through the nonblank contract warning.
- Added focused contract tests for 15/45 valid text lengths, 14/46 invalid text lengths, 3/4 action boundaries, blank text plus blank ref, blank document IDs, and pest/disease action without document evidence.
