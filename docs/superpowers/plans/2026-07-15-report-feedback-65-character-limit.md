# Report Feedback 65-Character Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the report-feedback maximum from 50 to 65 characters without changing role-specific minimums or retry behavior.

**Architecture:** Update the role-specific prompt ranges and the validator's shared maximum constant together. Move existing maximum boundary and retry tests from 50/51 to 65/66 so the current diagnostics and safe retry path remain the contract.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5, AssertJ, Gradle

## Global Constraints

- `summary` must be 20–65 characters.
- `comparisons[].text` must be 25–65 characters.
- `strengths[].text`, `improvements[].text`, and `nextActions[].text` must be 30–65 characters.
- Exactly 65 characters are valid and 66 or more are invalid.
- Do not change `basis`, `evidenceRefs`, minimum lengths, paragraph rules, API shapes, database schema, stored feedback, or record-feedback 15–60 validation.
- Do not truncate or pad generated strings.
- Reuse existing length diagnostics, retry behavior, and log allowlists.
- Add no dependency or unrelated refactor.

## File Map

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`: advertises the 65-character role ranges.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`: changes the shared maximum constant from 50 to 65.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`: locks the new prompt ranges.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`: locks 65 accepted and 66 rejected while retaining minimum tests.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`: proves a 66-character result still enters the existing sanitized retry path.

---

### Task 1: Raise the report maximum to 65

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`

**Interfaces:**
- Consumes: existing minimum constants and fixed `*_text_length` diagnostics.
- Produces: `MAX_TEXT_LENGTH = 65` with no new API, schema, or diagnostic code.

- [ ] **Step 1: Add failing prompt expectations**

Replace the three role-range assertions with:

```kotlin
"summary는 20~65자로 작성한다.",
"comparisons의 text는 25~65자로 작성한다.",
"strengths, improvements, nextActions의 text는 각각 30~65자로 작성한다.",
```

Keep the existing substantive-rewrite and no-truncation assertions unchanged.

- [ ] **Step 2: Move the validator maximum boundary tests**

Replace the current exact-50 test with:

```kotlin
@Test
fun `allows summary and every section text at exactly sixty five characters`() {
    val text = "가".repeat(65)
    val content = validContent().copy(
        summary = text,
        comparisons = listOf(comparisonItem(text = text)),
        strengths = listOf(item(text = text)),
        improvements = listOf(item(text = text)),
        nextActions = listOf(item(text = text)),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
}
```

Replace the current 51-character rejection test with:

```kotlin
@Test
fun `rejects summary and every section text over sixty five characters`() {
    val text = "가".repeat(66)
    val content = validContent().copy(
        summary = text,
        comparisons = listOf(comparisonItem(text = text)),
        strengths = listOf(item(text = text)),
        improvements = listOf(item(text = text)),
        nextActions = listOf(item(text = text)),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .containsExactly(
            "summary_text_length",
            "comparison_text_length",
            "strength_text_length",
            "improvement_text_length",
            "next_action_text_length",
        )
}
```

Do not modify `allows every public text at its exact minimum length` or the one-character-below-minimum test.

- [ ] **Step 3: Move the safe-retry maximum input to 66**

In `length failures are retried with safe diagnostic codes`, change only:

```kotlin
val generatedText = "가".repeat(66)
```

Keep its assertions for `summary_text_length`, `next_action_text_length`, two attempts, and rejected-text masking.

- [ ] **Step 4: Run focused tests and verify RED**

Run from `backend`:

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: FAIL because the prompt still says 50 and the validator rejects 65 characters, although 66-character rejection remains safe.

- [ ] **Step 5: Update the prompt ranges**

In `ReportFeedbackPromptBuilder.systemPrompt()`, use:

```text
summary는 20~65자로 작성한다.
comparisons의 text는 25~65자로 작성한다.
strengths, improvements, nextActions의 text는 각각 30~65자로 작성한다.
```

Keep the minimum-quality and no-truncation instructions unchanged.

- [ ] **Step 6: Raise the shared validator maximum**

In `ReportFeedbackOutputValidator`, change only:

```kotlin
private const val MAX_TEXT_LENGTH = 65
```

Keep `MIN_SUMMARY_TEXT_LENGTH = 20`, `MIN_COMPARISON_TEXT_LENGTH = 25`, and `MIN_COACHING_TEXT_LENGTH = 30` unchanged.

- [ ] **Step 7: Run focused tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: BUILD SUCCESSFUL; all exact minimums and 65 characters pass, while 66 characters retry or fail with existing fixed codes.

- [ ] **Step 8: Commit Task 1**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt
git commit -m "fix(coaching): 리포트 상한을 65자로 확장"
```

---

### Task 2: Regression verification

**Files:**
- Verify only; no production or test file should change.

**Interfaces:**
- Consumes: Task 1.
- Produces: completion evidence for report feedback, record-feedback isolation, API compatibility, and all backend modules.

- [ ] **Step 1: Run affected application tests**

```bash
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: BUILD SUCCESSFUL, including unchanged report minimums, record-feedback 15–60 validation, and safe logging.

- [ ] **Step 2: Run the API contract test**

```bash
./gradlew :api:test --tests '*ReportFeedbackControllerTest'
```

Expected: BUILD SUCCESSFUL with no response-shape change.

- [ ] **Step 3: Run a fresh full backend suite**

```bash
./gradlew test --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Inspect the final workspace**

Run from the repository root:

```bash
git diff --check
git status --short --branch
git log -6 --oneline
```

Expected: `git diff --check` has no output; only known local-only `.claude/` may remain untracked; the implementation commit appears above the design and plan commits.
