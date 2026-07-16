# Report Feedback Minimum Length Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject and safely regenerate report feedback that is too short for its section role while preserving the existing 50-character maximum.

**Architecture:** Put role-specific ranges in the report prompt and enforce the same ranges in `ReportFeedbackOutputValidator`. Reuse the existing `summary_text_length` and `<section>_text_length` diagnostics so the already-tested retry and logging allowlists need no production change.

**Tech Stack:** Kotlin, Spring Boot, JUnit 5, AssertJ, Gradle

## Global Constraints

- `summary` must be 20–50 characters.
- `comparisons[].text` must be 25–50 characters.
- `strengths[].text`, `improvements[].text`, and `nextActions[].text` must be 30–50 characters.
- Exact minimums and exactly 50 characters are valid.
- Do not limit `basis` or `evidenceRefs`.
- Do not truncate or pad generated strings.
- Reuse existing fixed length diagnostics and the existing single retry.
- Do not change API shapes, database schema, stored feedback, paragraph rules, evidence validation, or record-feedback 15–60 validation.
- Add no dependency or unrelated refactor.

## File Map

- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`: states the role-specific ranges and asks for substantive rewrites.
- `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`: enforces inclusive minimum and maximum boundaries.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`: locks prompt wording.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`: locks all minimum boundary values and keeps unrelated fixtures valid.
- `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`: proves short output enters the existing sanitized retry path.

---

### Task 1: Role-specific minimum boundaries and retry

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`

**Interfaces:**
- Consumes: `ReportFeedbackOutputValidator.validate(content, context, documents): List<String>` and existing safe diagnostics `summary_text_length`, `comparison_text_length`, `strength_text_length`, `improvement_text_length`, `next_action_text_length`.
- Produces: inclusive role-specific ranges with no new public API or diagnostic code.

- [ ] **Step 1: Add failing prompt assertions**

Replace the previous generic maximum assertions in the main prompt test with:

```kotlin
assertThat(prompt.system)
    .contains(
        "summary는 20~50자로 작성한다.",
        "comparisons의 text는 25~50자로 작성한다.",
        "strengths, improvements, nextActions의 text는 각각 30~50자로 작성한다.",
        "최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.",
        "50자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.",
    )
```

- [ ] **Step 2: Keep shared test fixtures inside the future contract**

Use these defaults in both validator and generation service tests:

```kotlin
private fun validContent(
    comparisons: List<ReportFeedbackContentItem> = listOf(comparisonItem()),
) = ReportFeedbackContent(
    summary = "이번 물 주기 기록의 전체 흐름과 관리 방향을 확인했어요.",
    comparisons = comparisons,
    strengths = listOf(
        item(text = "흙 상태를 살핀 뒤 물을 주어 필요한 곳부터 관리한 점이 좋았어요."),
    ),
    improvements = listOf(
        item(text = "물의 양이 알맞았는지 흙 속 수분까지 확인해 기록할 필요가 있어요."),
    ),
    nextActions = listOf(
        item(text = "다음에는 물을 준 뒤 흙 속까지 젖었는지 손으로 확인해 기록하세요."),
    ),
)

private fun comparisonItem(
    evidenceRefs: List<String> = listOf("report:$reportId", "report:$previousReportId"),
    text: String = "직전 재배보다 물 주기 기록이 한 번 늘어 흐름이 안정됐어요.",
) = ReportFeedbackContentItem(
    basis = "직전보다 기록 1회 증가",
    text = text,
    evidenceRefs = evidenceRefs,
)
```

Set the default coaching item text to the same valid 37-character strength in
both test files. Preserve each file's existing parameter shape:

```kotlin
// ReportFeedbackOutputValidatorTest
private fun item(
    basis: String = "관수 1회",
    text: String = "흙 상태를 살핀 뒤 물을 주어 필요한 곳부터 관리한 점이 좋았어요.",
    evidenceRef: String = "record:$recordId",
) = ReportFeedbackContentItem(
    basis = basis,
    text = text,
    evidenceRefs = listOf(evidenceRef),
)

// ReportFeedbackGenerationServiceTest
private fun item(
    text: String = "흙 상태를 살핀 뒤 물을 주어 필요한 곳부터 관리한 점이 좋았어요.",
    evidenceRefs: List<String> = listOf("record:$recordId"),
) = ReportFeedbackContentItem(
    basis = "관수 기록 1회",
    text = text,
    evidenceRefs = evidenceRefs,
)
```

Keep each test focused by using these exact replacements for explicitly valid variants:

```kotlin
val validEnglishSummary = "WATERING 작업 기록의 전체 흐름과 관리 방향을 확인했어요."
val validEnglishComparison = "WATERING 기록이 직전보다 늘어 작업 흐름의 변화를 확인했어요."
val validEnglishStrength = "DRIP으로 물을 준 기록을 꾸준히 남겨 작업 흐름을 확인하기 좋았어요."
val validEnglishImprovement = "토양 수분을 더 확인하고 물을 준 양과 상태를 함께 기록할 필요가 있어요."
val validEnglishNextAction = "다음에는 병해충 상태를 살핀 뒤 필요한 방제 방법을 정해 실행하세요."
val validRepeatedFact = "직전 재배보다 물 주기 기록이 한 번 늘어 작업 흐름이 안정됐어요."
val validRecordBackedStrength = "물 준 기록을 꾸준히 남겨 전체 작업 흐름을 확인하기 좋았어요."
val validFormalSummary = "이번 물 주기 기록을 꾸준히 남겨 전체 작업 흐름을 확인할 수 있습니다."
val validFormalStrength = "물 준 기록을 꾸준히 남겨 전체 작업 흐름을 확인할 수 있습니다."
val validUnitNextAction = "다음에는 kg 단위 대신 물의 양과 흙 상태를 쉬운 한글로 함께 기록하세요."
val validPhNextAction = "다음에는 pH 수치와 흙 상태를 쉬운 말로 함께 기록해 변화를 확인하세요."
val validTechnicalBasisStrength = "물 준 방법을 꾸준히 지키고 기록까지 남긴 점은 관리에 도움이 됐어요."
```

For the paragraph-only failure, use
`"다음에는 흙 속을 확인하세요.\n젖은 깊이와 물의 양도 함께 기록하세요."` so its
length is otherwise valid. For the multiple-item count failure, use
`"흙 속 수분도 함께 확인해 물의 양과 젖은 깊이를 기록할 필요가 있어요."`.
Do not change strings that a test intentionally makes invalid, such as blank or
19/24/29/51-character inputs.

- [ ] **Step 3: Add failing inclusive minimum boundary tests**

Add to `ReportFeedbackOutputValidatorTest`:

```kotlin
@Test
fun `allows every public text at its exact minimum length`() {
    val content = validContent().copy(
        summary = "가".repeat(20),
        comparisons = listOf(comparisonItem(text = "가".repeat(25))),
        strengths = listOf(item(text = "가".repeat(30))),
        improvements = listOf(item(text = "가".repeat(30))),
        nextActions = listOf(item(text = "가".repeat(30))),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).isEmpty()
}

@Test
fun `rejects every public text below its role minimum`() {
    val content = validContent().copy(
        summary = "가".repeat(19),
        comparisons = listOf(comparisonItem(text = "가".repeat(24))),
        strengths = listOf(item(text = "가".repeat(29))),
        improvements = listOf(item(text = "가".repeat(29))),
        nextActions = listOf(item(text = "가".repeat(29))),
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

- [ ] **Step 4: Add a failing short-output retry test**

Add to `ReportFeedbackGenerationServiceTest`:

```kotlin
@Test
fun `minimum length failures are retried with safe diagnostic codes`() {
    val generatedText = "가".repeat(29)
    val invalid = validContent().copy(
        summary = "가".repeat(19),
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

- [ ] **Step 5: Run focused tests and verify RED**

Run from `backend`:

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: FAIL because the prompt lacks role-specific ranges, 19/24/29-character values pass validation, and short output is not retried.

- [ ] **Step 6: Add the role-specific prompt contract**

Replace the generic maximum instruction in `ReportFeedbackPromptBuilder.systemPrompt()` with:

```text
summary는 20~50자로 작성한다.
comparisons의 text는 25~50자로 작성한다.
strengths, improvements, nextActions의 text는 각각 30~50자로 작성한다.
최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.
50자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.
```

- [ ] **Step 7: Enforce inclusive ranges in the validator**

Replace the summary blank and maximum checks together so blank text keeps only its existing blank warning:

```kotlin
if (content.summary.isBlank()) {
    warnings += "summary_blank"
} else if (content.summary.length !in MIN_SUMMARY_TEXT_LENGTH..MAX_TEXT_LENGTH) {
    warnings += "summary_text_length"
}
```

Replace the item blank and maximum checks together so blank text keeps only its existing blank warning:

```kotlin
if (item.text.isBlank()) {
    warnings += "${structured.section.name.lowercase()}_text_blank"
} else {
    val minimumTextLength = when (structured.section) {
        ReportFeedbackItemSection.COMPARISON -> MIN_COMPARISON_TEXT_LENGTH
        ReportFeedbackItemSection.STRENGTH,
        ReportFeedbackItemSection.IMPROVEMENT,
        ReportFeedbackItemSection.NEXT_ACTION -> MIN_COACHING_TEXT_LENGTH
    }
    if (item.text.length !in minimumTextLength..MAX_TEXT_LENGTH) {
        warnings += "${structured.section.name.lowercase()}_text_length"
    }
}
```

Define the constants at the bottom of the validator:

```kotlin
private const val MIN_SUMMARY_TEXT_LENGTH = 20
private const val MIN_COMPARISON_TEXT_LENGTH = 25
private const val MIN_COACHING_TEXT_LENGTH = 30
private const val MAX_TEXT_LENGTH = 50
```

- [ ] **Step 8: Run focused tests and verify GREEN**

```bash
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: BUILD SUCCESSFUL; exact minimums and 50 characters pass, values one character below each minimum retry or fail with existing safe codes.

- [ ] **Step 9: Commit Task 1**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt
git commit -m "fix(coaching): 리포트 역할별 최소 길이 적용"
```

---

### Task 2: Regression verification

**Files:**
- Verify only; no production or test file should change.

**Interfaces:**
- Consumes: Task 1.
- Produces: completion evidence for report feedback, unchanged record feedback, API compatibility, and all backend modules.

- [ ] **Step 1: Run all affected application tests**

```bash
./gradlew :application:test --tests '*RecordFeedbackOutputValidatorTest' --tests '*ReportFeedbackPromptBuilderTest' --tests '*ReportFeedbackOutputValidatorTest' --tests '*ReportFeedbackGenerationServiceTest' --tests '*ReportFeedbackGenerationHandlerTest'
```

Expected: BUILD SUCCESSFUL, including the unchanged record-feedback 15–60 boundary and existing safe logging.

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
