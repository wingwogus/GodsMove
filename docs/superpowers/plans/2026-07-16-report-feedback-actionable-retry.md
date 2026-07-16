# Report Feedback Actionable Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리포트 구조화 출력이 검증에 실패했을 때 내부 코드 대신 실제 필드와 글자 수를 포함한 안전하고 실행 가능한 한국어 교정 지시로 한 번 재생성한다.

**Architecture:** 첫 생성과 서버 검증은 기존 20~65자 계약을 그대로 공유한다. `ReportFeedbackPromptBuilder`는 반환 전 길이 확인을 요구하고, `ReportFeedbackGenerationService`는 안전 진단 코드와 정규화된 응답의 숫자 정보만 사용해 재시도 지시를 만든다. API, DB, 검증기와 최대 2회 생성 계약은 변경하지 않는다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5, Spring AI 1.1, JUnit 5, AssertJ, Gradle

## Global Constraints

- `summary`와 모든 섹션의 `text`는 첫 생성과 서버 검증에서 모두 20~65자 계약을 사용한다.
- 45~55자 별도 목표, 서버 자동 축약, 문자열 강제 치환은 도입하지 않는다.
- 최대 생성 시도는 2회로 유지한다.
- OpenClaw 요청은 Spring AI가 프롬프트에 추가하는 JSON Schema를 사용하고 네이티브 `response_format`을 재도입하지 않는다.
- 모델 원문과 허용되지 않은 `evidenceRefs` 실제 값은 재시도 지시, 오류 메시지와 로그에 포함하지 않는다.
- API, 데이터베이스 스키마, `STRUCTURED_OUTPUT_INVALID` 실패 코드와 `ReportFeedbackOutputValidator` 저장 계약을 변경하지 않는다.
- 새 의존성과 새 공개 추상화를 추가하지 않는다.
- 프로젝트 용어 `member`를 유지한다.
- 각 커밋은 Conventional Commits 형식의 한국어 제목과 Lore trailers를 사용한다.

---

## File Map

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
  - 첫 응답을 반환하기 전에 공백과 문장부호를 포함한 글자 수를 확인하도록 지시한다.
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
  - 20~65자 계약 유지와 반환 전 확인 지시를 고정한다.
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
  - 안전 진단 코드와 정규화된 응답을 구체적인 재시도 문장으로 변환한다.
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
  - 길이, 항목 수, 근거 참조와 JSON 변환 실패의 교정 지시와 비노출 계약을 검증한다.

---

### Task 1: 첫 생성의 20~65자 자체 확인 지시

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt:81-86`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt:44-61`

**Interfaces:**
- Consumes: `ReportFeedbackPromptBuilder.build(context, evidence): ReportFeedbackPrompt`
- Produces: 기존 20~65자 계약과 반환 전 글자 수 확인 문장을 포함한 `ReportFeedbackPrompt.system`

- [ ] **Step 1: 프롬프트 계약 실패 테스트 작성**

`prompt scopes instructions statistics and allowed evidence to one work type` 테스트의 길이 검증 구간에 아래 assertion을 추가한다.

```kotlin
assertThat(prompt.system)
    .contains(
        "summary는 20~65자로 작성한다.",
        "comparisons의 text는 20~65자로 작성한다.",
        "strengths, improvements, nextActions의 text는 각각 20~65자로 작성한다.",
        "JSON을 반환하기 전에 공백과 문장부호를 포함한 summary와 모든 text의 글자 수가 20~65자인지 확인한다.",
    )
    .doesNotContain("45~55자")
```

- [ ] **Step 2: 실패 테스트 실행**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: FAIL. `prompt.system`에 `JSON을 반환하기 전에 ... 확인한다.` 문장이 없다고 보고한다.

- [ ] **Step 3: 최소 프롬프트 변경 구현**

`ReportFeedbackPromptBuilder.systemPrompt()`의 길이 지시 바로 뒤에 다음 한 줄을 추가한다.

```kotlin
summary는 20~65자로 작성한다.
comparisons의 text는 20~65자로 작성한다.
strengths, improvements, nextActions의 text는 각각 20~65자로 작성한다.
JSON을 반환하기 전에 공백과 문장부호를 포함한 summary와 모든 text의 글자 수가 20~65자인지 확인한다.
최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.
```

- [ ] **Step 4: 프롬프트 테스트 통과 확인**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: BUILD SUCCESSFUL, `ReportFeedbackPromptBuilderTest` 전체 통과.

- [ ] **Step 5: Task 1 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt
git commit \
  -m "fix(coaching): 구조화 응답 길이를 반환 전에 확인" \
  -m "첫 생성과 서버 검증이 동일한 20~65자 계약을 사용하도록 유지하면서 모델이 JSON 반환 전 각 공개 문장의 글자 수를 다시 확인하게 한다." \
  -m "Constraint: 45~55자 별도 목표를 도입하지 않음
Rejected: 서버 자동 축약 | 코칭 의미 훼손 위험
Confidence: high
Scope-risk: narrow
Directive: 생성 프롬프트와 검증기의 길이 범위를 다르게 변경하지 말 것
Tested: ./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'"
```

---

### Task 2: 검증 실패를 실행 가능한 재시도 지시로 변환

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt:86-180`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt:31-210`

**Interfaces:**
- Consumes: `ReportFeedbackOutputValidator.validate(content, context, evidence): List<String>`와 정규화된 `ReportFeedbackContent`
- Produces: `List<String>.toRetryInstructions(content: ReportFeedbackContent?): List<String>`
- Preserves: `List<String>.toSafeRetryWarnings(): List<String>`가 만든 안전 진단 코드는 최종 실패 cause에만 사용

- [ ] **Step 1: 길이 초과와 미달 재시도 실패 테스트 작성**

기존 `length failures are retried with safe diagnostic codes`와 `minimum length failures are retried with safe diagnostic codes`를 다음 테스트로 교체한다.

```kotlin
@Test
fun `length failures retry with field length range and correction direction`() {
    val overlongSummary = "가".repeat(66)
    val overlongImprovement = "나".repeat(73)
    val client = FakeChatClient(
        validContent().copy(
            summary = overlongSummary,
            improvements = listOf(item(text = overlongImprovement)),
        ),
        validContent(),
    )

    service(client).generate(context())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.requestSpec.userTexts.last())
        .contains(
            "summary는 현재 66자입니다.",
            "improvements[0].text는 현재 73자입니다.",
            "공백과 문장부호를 포함해 20~65자로 줄이세요.",
            "부족한 점, 영향, 보완 방향을 유지하면서",
        )
        .doesNotContain(overlongSummary, overlongImprovement, "summary_text_length", "improvement_text_length")
}

@Test
fun `minimum length failures retry with field length range and correction direction`() {
    val shortSummary = "가".repeat(19)
    val shortImprovement = "나".repeat(18)
    val client = FakeChatClient(
        validContent().copy(
            summary = shortSummary,
            improvements = listOf(item(text = shortImprovement)),
        ),
        validContent(),
    )

    service(client).generate(context())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.requestSpec.userTexts.last())
        .contains(
            "summary는 현재 19자입니다.",
            "improvements[0].text는 현재 18자입니다.",
            "공백과 문장부호를 포함해 20~65자로 늘리세요.",
            "부족한 점, 영향, 보완 방향을 유지하면서",
        )
        .doesNotContain(shortSummary, shortImprovement, "summary_text_length", "improvement_text_length")
}
```

- [ ] **Step 2: 구조, 근거와 JSON 재시도 기대값을 구체화**

`basis`, `text`, `evidenceRefs`가 비어 있을 때 각 필드를 직접 고치도록 다음 테스트를 추가한다.

```kotlin
@Test
fun `blank item fields retry with actionable field instructions`() {
    val client = FakeChatClient(
        validContent().copy(
            strengths = listOf(
                ReportFeedbackContentItem(
                    basis = "",
                    text = "",
                    evidenceRefs = emptyList(),
                ),
            ),
        ),
        validContent(),
    )

    service(client).generate(context())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.requestSpec.userTexts.last())
        .contains(
            "strengths[0].basis에 판단 근거를 작성하세요.",
            "strengths[0].text에 잘한 행동과 도움이 된 이유를 공백과 문장부호를 포함해 20~65자로 작성하세요.",
            "strengths[0].evidenceRefs에 허용 evidenceRefs의 식별자를 하나 이상 포함하세요.",
        )
        .doesNotContain("strength_basis_blank", "strength_text_blank", "strength_evidence_refs_blank")
}
```

기존 테스트의 assertion도 다음 계약으로 바꾼다. 테스트 입력과 `FakeChatClient` 구성은 유지한다.

```kotlin
assertThat(client.requestSpec.userTexts.last())
    .contains("strengths 배열은 정확히 1개로 작성하세요.")
    .doesNotContain("strength_count")

assertThat(client.requestSpec.userTexts.last())
    .contains("모든 evidenceRefs에는 허용 evidenceRefs에 나열된 값만 사용하세요.")
    .doesNotContain(privateValue, "unknown_evidence")

assertThat(client.requestSpec.userTexts.last())
    .contains("comparisons는 빈 배열로 반환하세요.")
    .doesNotContain(generatedText, "comparison_not_available")

val retryInstruction = client.requestSpec.userTexts.last().substringAfter("직전 응답은")
assertThat(retryInstruction)
    .contains("comparisons[0].evidenceRefs에 현재 리포트 근거를 허용 evidenceRefs에서 선택해 포함하세요.")
    .doesNotContain(reportId.toString(), previousReportId.toString(), "comparison_current_report_ref_required")

assertThat(client.requestSpec.userTexts.last())
    .contains(
        "설명이나 Markdown 없이 JSON Schema의 필드명과 타입을 그대로 따른 완전한 JSON만 반환하세요.",
        "summary는 문자열이고 comparisons, strengths, improvements, nextActions는 배열입니다.",
    )
    .doesNotContain("structured_output_parse_failed")
```

두 번의 파싱 실패 테스트에서는 사용자 프롬프트가 위의 구체적인 JSON 지시를 포함하되 최종 cause는 계속 안전 코드인지 함께 확인한다.

```kotlin
assertThat(it.cause?.message).isEqualTo("structured_output_parse_failed")
assertThat(client.requestSpec.userTexts.last())
    .contains("JSON Schema의 필드명과 타입")
    .doesNotContain(rawMessage, "structured_output_parse_failed")
```

- [ ] **Step 3: 재시도 서비스 테스트가 의도대로 실패하는지 확인**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: FAIL. 재시도 프롬프트에 아직 내부 코드가 있고 필드 경로, 실제 길이와 한국어 교정 문장이 없다고 보고한다.

- [ ] **Step 4: 재시도 흐름에서 진단 코드와 교정 지시 분리**

`requestValidatedContent`의 재시도 상태와 실패 분기를 다음 형태로 변경한다.

```kotlin
var lastFailure: Throwable? = null
var retryInstructions = emptyList<String>()
repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS) {
    val attemptPrompt = prompt.withValidationRetryInstructions(retryInstructions)
    val response = try {
        chatClient.prompt().system(attemptPrompt.system).user(attemptPrompt.user).call()
    } catch (exception: RuntimeException) {
        throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE, exception)
    }
    val content = try {
        response
            .entity(ReportFeedbackContent::class.java)
            ?.normalizedParagraphs()
            ?: throw IllegalStateException("empty structured output")
    } catch (exception: BusinessException) {
        throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.CHAT_UNAVAILABLE, exception)
    } catch (_: RuntimeException) {
        val diagnostics = listOf("structured_output_parse_failed")
        retryInstructions = diagnostics.toRetryInstructions(content = null)
        lastFailure = IllegalStateException(diagnostics.single())
        return@repeat
    }
    val warnings = ReportFeedbackOutputValidator.validate(content, context, evidence)
    if (warnings.isEmpty()) {
        return content
    }
    val diagnostics = warnings.toSafeRetryWarnings()
    retryInstructions = diagnostics.toRetryInstructions(content)
    lastFailure = IllegalStateException(diagnostics.joinToString(","))
}
throw ReportFeedbackGenerationFailure(ReportFeedbackFailureCode.STRUCTURED_OUTPUT_INVALID, lastFailure)
```

`withValidationRetryInstructions`의 parameter와 머리말도 교정 지시 기준으로 바꾼다.

```kotlin
private fun ReportFeedbackPrompt.withValidationRetryInstructions(
    instructions: List<String>,
): ReportFeedbackPrompt {
    if (instructions.isEmpty()) return this
    return copy(
        user = "$user\n\n" +
            "직전 응답은 내부 검증을 통과하지 못했습니다. 다음 지시를 모두 반영한 완전한 JSON만 다시 반환하세요:\n" +
            instructions.joinToString("\n") { "- $it" },
    )
}
```

- [ ] **Step 5: 안전한 길이와 구조 교정 함수 구현**

`toSafeRetryWarnings()` 아래에 다음 private 함수들을 추가한다. 모델 원문은 어떤 반환문에도 연결하지 않는다.

```kotlin
private fun List<String>.toRetryInstructions(
    content: ReportFeedbackContent?,
): List<String> = map { it.toRetryInstruction(content) }.distinct()

private fun String.toRetryInstruction(content: ReportFeedbackContent?): String = when (this) {
    "summary_blank" ->
        "summary에 이번 재배에서 확인한 핵심을 공백과 문장부호를 포함해 20~65자로 작성하세요."
    "summary_text_length" -> lengthRetryInstruction(
        path = "summary",
        text = content?.summary,
        role = "이번 재배에서 확인한 핵심",
    )
    "comparison_not_available" -> "서버가 제공한 지난 재배 비교가 없으므로 comparisons는 빈 배열로 반환하세요."
    "comparison_current_report_ref_required" ->
        "comparisons[0].evidenceRefs에 현재 리포트 근거를 허용 evidenceRefs에서 선택해 포함하세요."
    "comparison_previous_report_ref_required" ->
        "comparisons[0].evidenceRefs에 지난 재배 리포트 근거를 허용 evidenceRefs에서 선택해 포함하세요."
    "structured_output_parse_failed" ->
        "설명이나 Markdown 없이 JSON Schema의 필드명과 타입을 그대로 따른 완전한 JSON만 반환하세요. " +
            "summary는 문자열이고 comparisons, strengths, improvements, nextActions는 배열입니다."
    "unknown_evidence" -> "모든 evidenceRefs에는 허용 evidenceRefs에 나열된 값만 사용하세요."
    "invalid_output" -> "JSON의 모든 필드, 항목 수, 글자 수와 evidenceRefs 조건을 다시 확인하세요."
    else -> SAFE_ITEM_WARNING.matchEntire(this)?.let { match ->
        itemRetryInstruction(
            section = match.groupValues[1],
            violation = match.groupValues[2],
            content = content,
        )
    } ?: "JSON의 모든 필드, 항목 수, 글자 수와 evidenceRefs 조건을 다시 확인하세요."
}

private fun itemRetryInstruction(
    section: String,
    violation: String,
    content: ReportFeedbackContent?,
): String {
    val (field, items, role) = when (section) {
        "comparison" -> Triple("comparisons", content?.comparisons, "지난 재배와 달라진 사실")
        "strength" -> Triple("strengths", content?.strengths, "잘한 행동과 도움이 된 이유")
        "improvement" -> Triple("improvements", content?.improvements, "부족한 점, 영향, 보완 방향")
        "next_action" -> Triple("nextActions", content?.nextActions, "언제 무엇을 할지 한 가지 행동")
        else -> return "JSON의 모든 필드, 항목 수, 글자 수와 evidenceRefs 조건을 다시 확인하세요."
    }
    return when (violation) {
        "count" -> "$field 배열은 정확히 1개로 작성하세요."
        "basis_blank" -> "$field[0].basis에 판단 근거를 작성하세요."
        "text_blank" -> "$field[0].text에 $role을 공백과 문장부호를 포함해 20~65자로 작성하세요."
        "text_length" -> lengthRetryInstruction("$field[0].text", items?.singleOrNull()?.text, role)
        "text_paragraph" -> "$field[0].text를 줄바꿈이나 목록 기호 없이 한 문단으로 작성하세요."
        "evidence_refs_blank" ->
            "$field[0].evidenceRefs에 허용 evidenceRefs의 식별자를 하나 이상 포함하세요."
        else -> "JSON의 모든 필드, 항목 수, 글자 수와 evidenceRefs 조건을 다시 확인하세요."
    }
}

private fun lengthRetryInstruction(
    path: String,
    text: String?,
    role: String,
): String {
    if (text == null) {
        return "$path에 $role을 공백과 문장부호를 포함해 20~65자로 작성하세요."
    }
    val direction = when {
        text.length > MAX_RETRY_TEXT_LENGTH -> "줄이세요"
        text.length < MIN_RETRY_TEXT_LENGTH -> "늘리세요"
        else -> "다시 작성하세요"
    }
    return "$path는 현재 ${text.length}자입니다. $role을 유지하면서 " +
        "공백과 문장부호를 포함해 20~65자로 $direction."
}
```

`ReportFeedbackGenerationService` companion object에 검증기와 같은 경계를 private 상수로 둔다.

```kotlin
const val MIN_RETRY_TEXT_LENGTH = 20
const val MAX_RETRY_TEXT_LENGTH = 65
```

- [ ] **Step 6: 재시도 집중 테스트 통과 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackGenerationServiceTest' \
  --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: BUILD SUCCESSFUL. 길이 교정 지시는 실제 숫자와 방향을 포함하고 원문·내부 코드·허용되지 않은 근거값은 포함하지 않는다.

- [ ] **Step 7: Task 2 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt
git commit \
  -m "fix(coaching): 구조화 출력 재시도를 구체적으로 안내" \
  -m "모델에게 내부 진단 코드만 전달하던 재시도를 필드 경로, 실제 글자 수, 허용 범위와 수정 방향을 포함한 안전한 한국어 지시로 바꾼다. 최종 실패 cause에는 기존 안전 코드만 유지한다." \
  -m "Constraint: 모델 원문과 허용되지 않은 evidenceRefs 값은 재시도와 로그에 노출하지 않음
Rejected: 생성 횟수 증가 | 지연 증가 없이 기존 한 번의 재시도를 먼저 개선
Confidence: high
Scope-risk: narrow
Directive: 교정 지시에는 고정 필드명과 숫자 정보만 사용할 것
Tested: ReportFeedbackGenerationServiceTest, ReportFeedbackOutputValidatorTest"
```

---

### Task 3: 전체 회귀 검증과 런타임 확인 준비

**Files:**
- Verify only: `backend`
- Verify status: repository root

**Interfaces:**
- Consumes: Task 1과 Task 2의 커밋
- Produces: 전체 백엔드 검사 결과와 실제 OpenClaw 수동 검증에 필요한 재시작 안내

- [ ] **Step 1: 관련 집중 테스트를 캐시 없이 다시 실행**

Run:

```bash
cd backend
./gradlew :application:test --rerun-tasks \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackGenerationServiceTest' \
  --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: BUILD SUCCESSFUL, 관련 테스트 실패 0개.

- [ ] **Step 2: 전체 백엔드 검사 실행**

Run:

```bash
cd backend
./gradlew check --rerun-tasks
```

Expected: BUILD SUCCESSFUL. 기존 deprecation warning은 허용하지만 신규 compilation error와 test failure는 0개.

- [ ] **Step 3: diff와 작업 트리 확인**

Run:

```bash
git diff --check
git status --short --branch
git log --oneline -4
```

Expected: `git diff --check` 출력 없음. 추적 파일 변경 없음. 사용자 소유 `.claude/`와 `.omx/`가 있으면 untracked 상태 그대로 유지. 최근 로그에 Task 1과 Task 2 커밋이 각각 존재.

- [ ] **Step 4: 수동 검증 한계 기록**

IntelliJ에서 실행 중인 백엔드는 재시작 전까지 이전 클래스를 사용한다. 재시작 후 dev-rag console에서 실패한 HARVEST를 재생성할 수 있지만 모델 응답은 비결정적이므로 자동 테스트의 통과 조건으로 삼지 않는다. 수동 검증을 실행하지 못했다면 최종 보고의 `Not-tested`에 그대로 기록한다.

---

## Completion Checklist

- [ ] 첫 생성과 검증이 모두 20~65자 계약을 유지한다.
- [ ] 재시도 프롬프트에 45~55자 목표가 없다.
- [ ] 길이 초과와 미달 모두 필드 경로, 실제 길이와 수정 방향을 받는다.
- [ ] 항목 수, 근거와 JSON 형식 위반이 실행 가능한 한국어 지시를 받는다.
- [ ] 모델 원문, 잘못된 근거값과 내부 진단 코드는 재시도 프롬프트에 노출되지 않는다.
- [ ] 최종 실패 cause는 기존 안전 진단 코드를 유지한다.
- [ ] 최대 생성 시도는 2회다.
- [ ] 집중 테스트와 `./gradlew check --rerun-tasks`가 통과한다.
