# Record Feedback Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make record-feedback failure states terminal and explicit, remove redundant validation/projection layers, and use one `RecordFeedback` feature name from API to application.

**Architecture:** Keep the existing `PENDING → READY|FAILED|STALE` lifecycle and HTTP route/JSON shape. Introduce one application-level failure enum used by preparation and generation, keep API DTO filtering at the API boundary, and make validators pure package objects rather than injected Spring beans.

**Tech Stack:** Kotlin, Spring Boot, Spring AI, JUnit 5, Mockito, MockMvc.

## Global Constraints

- Do not change `/api/v1/farming-records/{recordId}/coaching-feedback` or its JSON fields.
- Do not add dependencies or an `@Async` execution model.
- Preserve `STALE` feedback from being overwritten by a late generation result or failure.
- Keep application validation only for the persisted, non-HTTP feedback context snapshot.
- Leave the unrelated untracked `.claude/` directory untouched.

---

### Task 1: Lock terminal failure behavior

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationServiceTest.kt`

- [ ] Add a processor test where `generationService.generate(...)` throws an unexpected `IllegalStateException` and assert the pending feedback becomes `FAILED` with `UNEXPECTED`.
- [ ] Change the malformed snapshot assertion to the explicit `INVALID_CONTEXT_SNAPSHOT` code.
- [ ] Run `cd backend && ./gradlew :application:test --tests '*RecordFeedbackGenerationProcessorTest' --tests '*RecordFeedbackPreparationServiceTest'` and confirm the new assertions fail before implementation.

### Task 2: Make feedback failures one explicit application contract

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackFailure.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackPreparationService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/feedback/RecordFeedbackGenerationProcessor.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationService.kt`

- [ ] Define `RecordFeedbackFailureCode` with `CONTEXT_ASSEMBLY_FAILED`, `INVALID_CONTEXT_SNAPSHOT`, `INVALID_CONTEXT`, `INSUFFICIENT_EVIDENCE`, `RETRIEVAL_FAILED`, `CHAT_UNAVAILABLE`, `STRUCTURED_OUTPUT_INVALID`, and `UNEXPECTED`.
- [ ] Define `RecordFeedbackGenerationFailure(code, cause)` for the generation boundary only; persist its enum name through the processor.
- [ ] Convert known preparation/generation failures to those codes and make a non-stale unexpected generation runtime failure terminal with `UNEXPECTED`.
- [ ] Assemble the context outside the mutation transaction, then lock-and-reload the feedback row before attaching a snapshot, publishing generation, or recording a preparation failure.
- [ ] Keep listener logging only as a last-resort persistence/transaction failure path.
- [ ] Re-run the Task 1 tests and confirm they pass.

### Task 3: Collapse single-use validation wrappers

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContextValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackGenerationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackContextValidatorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackOutputValidatorTest.kt`

- [ ] Replace injected `@Component` validators with pure Kotlin objects.
- [ ] Return context warnings directly after validation; remove `RecordFeedbackContextValidationResult`.
- [ ] Make output validation return its warning list directly; keep allowed evidence references private and remove its pass/fail enum/result wrapper.
- [ ] Run the three validator/generator test classes after the simplification.

### Task 4: Normalize feature names and remove the duplicate display projection

**Files:**
- Rename: `RecordFeedbackCoachingResult.kt` to `RecordFeedbackContent.kt`
- Rename: `RecordFeedbackResult.kt` to `RecordFeedbackStatusResult.kt`
- Rename: `FarmingRecordFeedbackController.kt` to `RecordFeedbackController.kt`
- Rename: `FarmingRecordFeedbackResponses.kt` to `RecordFeedbackResponses.kt`
- Rename: `FarmingRecordFeedbackControllerTest.kt` to `RecordFeedbackControllerTest.kt`
- Modify: all record-feedback production and test references.

- [ ] Use `RecordFeedbackContent` for the structured LLM content, `RecordFeedbackGenerationResult` for the generation operation output, and `RecordFeedbackStatusResult` for status polling.
- [ ] Remove the application-only `RecordFeedbackUserResponse`/good-point/action projection; map `RecordFeedbackContent` to `RecordFeedbackResponses.FeedbackResponse` only in the API layer.
- [ ] Rename API controller/DTO namespace from `FarmingRecordFeedback*` to `RecordFeedback*`; retain the existing path and JSON names.
- [ ] Run the application and API feedback test classes and confirm the route contract remains unchanged.

### Task 5: Verify the bounded cleanup

**Files:**
- Verify all files changed by Tasks 1–4.

- [ ] Run `cd backend && ./gradlew :application:test :api:test`.
- [ ] Run `git diff --check` and `git diff --name-only` to confirm the scope contains only record-feedback cleanup files and this plan.
- [ ] Report any intentionally deferred issue: persistent-store failure cannot reliably mark a row failed because the write itself failed.
