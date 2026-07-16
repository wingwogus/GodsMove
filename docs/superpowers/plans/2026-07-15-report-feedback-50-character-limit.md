# Report Feedback 50-Character Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure newly generated report feedback summaries and every section text are at most 50 characters before persistence.

**Architecture:** Add the same explicit 50-character contract to the report prompt and `ReportFeedbackOutputValidator`. Route the new fixed diagnostic codes through the existing single-retry and sanitized logging paths instead of truncating generated text.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5, AssertJ, Gradle

## Global Constraints

- Apply the maximum to `summary`, `comparisons[].text`, `strengths[].text`, `improvements[].text`, and `nextActions[].text`.
- Accept exactly 50 characters and reject 51 or more.
- Do not truncate strings; retry generation with fixed diagnostic codes.
- Do not limit `basis` or `evidenceRefs`.
- Do not add a minimum report-feedback length.
- Do not modify the record-feedback 15–60 character validation contract.
- Do not change API response shapes, database schema, or previously stored feedback.
- Add no dependency or unrelated refactor.

## File Map

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`: tells the model to rewrite every public report string within 50 characters.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`: enforces the 50-character persistence boundary.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`: preserves the new length codes in the sanitized retry prompt.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandler.kt`: preserves only the new fixed codes in failure logs.
- The four matching test files lock prompt wording, 50/51 boundaries, retry sanitization, and log sanitization.

---

### Task 1: Prompt and validation boundary

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`

**Interfaces:**
- Consumes: `ReportFeedbackPromptBuilder.build(context, evidence): ReportFeedbackPrompt` and `ReportFeedbackOutputValidator.validate(content, context, documents): List<String>`.
- Produces: fixed warnings `summary_text_length`, `comparison_text_length`, `strength_text_length`, `improvement_text_length`, and `next_action_text_length`.

- [ ] **Step 1: Add failing prompt assertions**

Extend the main prompt test with:

```kotlin
assertThat(prompt.system)
    .contains(
        "summary와 모든 배열 항목의 text는 각각 최대 50자로 작성한다.",
        "50자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.",
    )
```

- [ ] **Step 2: Add failing 50/51 boundary tests**

Add to `ReportFeedbackOutputValidatorTest`:

```kotlin
@Test
fun `allows summary and every section text at exactly fifty characters`() {
    val text = "가".repeat(50)
    val content = validContent().copy(
        summary = text,
        comparisons = listOf(comparisonItem(text = text)),
        strengths = listOf(item(text = text)),
        improvements = listOf(item(text = text)),
        nextActions = listOf(item(text = text)),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
}

@Test
fun `rejects summary and every section text over fifty characters`() {
    val text = "가".repeat(51)
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

- [ ] **Step 3: Run focused tests and verify RED**

Run from `backend`:

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: FAIL because the prompt lacks the 50-character instructions and the validator accepts 51-character strings.

- [ ] **Step 4: Add the prompt contract**

In `ReportFeedbackPromptBuilder.systemPrompt()`, immediately after the one-paragraph instructions, add:

```text
summary와 모든 배열 항목의 text는 각각 최대 50자로 작성한다.
50자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.
```

- [ ] **Step 5: Add the validator boundary**

Retain the summary blank check and add:

```kotlin
if (content.summary.length > MAX_TEXT_LENGTH) {
    warnings += "summary_text_length"
}
```

Inside `items.forEach`, immediately after the item text blank check, add:

```kotlin
if (item.text.length > MAX_TEXT_LENGTH) {
    warnings += "${structured.section.name.lowercase()}_text_length"
}
```

At the bottom of the validator, add:

```kotlin
private const val MAX_TEXT_LENGTH = 50
```

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: BUILD SUCCESSFUL; 50 characters pass and all 51-character public strings return fixed length codes.

- [ ] **Step 7: Commit Task 1**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt
git commit -m "fix(coaching): 리포트 문구 50자 경계 적용"
```

---

### Task 2: Safe retry and diagnostic logging

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandlerTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandler.kt`

**Interfaces:**
- Consumes: Task 1 fixed warnings and the existing maximum of two structured-output attempts.
- Produces: safe retry and failure logging that retain length codes without generated text.

- [ ] **Step 1: Add a failing safe-retry test**

Add to `ReportFeedbackGenerationServiceTest`:

```kotlin
@Test
fun `length failures are retried with safe diagnostic codes`() {
    val generatedText = "가".repeat(51)
    val invalid = validContent().copy(
        summary = generatedText,
        nextActions = listOf(item(text = generatedText)),
    )
    val client = FakeChatClient(invalid, validContent())

    service(client).generate(context())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.requestSpec.userTexts.last())
        .contains("summary_text_length", "next_action_text_length")
        .doesNotContain(generatedText)
}
```

- [ ] **Step 2: Extend the failing handler sanitization test**

Include the new warnings in its thrown message:

```kotlin
"comparison_not_available,strength_count,next_action_text_paragraph," +
    "summary_text_length,comparison_text_length," +
    "unknown_evidence:untrusted-generated-value"
```

Extend the log assertions with:

```kotlin
.contains("summary_text_length")
.contains("comparison_text_length")
```

- [ ] **Step 3: Run focused tests and verify RED**

```bash
./gradlew :application:test --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: FAIL because the retry path converts or drops the new codes and the handler drops both new codes.

- [ ] **Step 4: Allow only fixed length diagnostics in retries**

Extend `SAFE_ITEM_WARNING`:

```kotlin
val SAFE_ITEM_WARNING = Regex(
    "^(comparison|strength|improvement|next_action)_" +
        "(count|basis_blank|text_blank|text_length|text_paragraph|evidence_refs_blank)$",
)
```

Update `SAFE_RETRY_WARNINGS` to:

```kotlin
val SAFE_RETRY_WARNINGS = setOf(
    "summary_blank",
    "summary_text_length",
    "comparison_not_available",
    "comparison_current_report_ref_required",
    "comparison_previous_report_ref_required",
    "structured_output_parse_failed",
)
```

- [ ] **Step 5: Keep the handler allowlist synchronized**

Update `SAFE_ITEM_DIAGNOSTIC` to:

```kotlin
val SAFE_ITEM_DIAGNOSTIC = Regex(
    "^(comparison|strength|improvement|next_action)_" +
        "(count|basis_blank|text_blank|text_length|text_paragraph|evidence_refs_blank)$",
)
```

Update `SAFE_VALIDATION_DIAGNOSTICS` to:

```kotlin
val SAFE_VALIDATION_DIAGNOSTICS = setOf(
    "summary_blank",
    "summary_text_length",
    "comparison_not_available",
    "comparison_current_report_ref_required",
    "comparison_previous_report_ref_required",
    "structured_output_parse_failed",
    "invalid_output",
)
```

- [ ] **Step 6: Run focused tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: BUILD SUCCESSFUL; retries and logs contain fixed codes but not rejected text or unknown evidence values.

- [ ] **Step 7: Commit Task 2**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandler.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/lifecycle/ReportFeedbackGenerationHandlerTest.kt
git commit -m "fix(coaching): 리포트 길이 초과 안전 재생성"
```

---

### Task 3: Regression verification

**Files:**
- Verify only; no production or test files should change.

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: completion evidence for report feedback, API compatibility, and the full backend.

- [ ] **Step 1: Run all affected application tests together**

```bash
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: BUILD SUCCESSFUL, including the unchanged record-feedback 15–60 boundary.

- [ ] **Step 2: Run the report-feedback API contract test**

```bash
./gradlew :api:test --tests '*ReportFeedbackControllerTest'
```

Expected: BUILD SUCCESSFUL with no response-shape changes.

- [ ] **Step 3: Run a fresh full backend test suite**

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

Expected: `git diff --check` has no output; only known local-only `.claude/` may remain untracked; the two implementation commits appear above the design and plan commits.
