# Record and Report Feedback Copy Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 피드백의 역할별 생성 길이 목표를 분리하고, 기존 배열 API를 유지한 채 리포트 피드백의 각 표시 섹션을 정확히 한 문단으로 생성·검증한다.

**Architecture:** 기록 피드백은 프롬프트 지시만 역할별로 분리하고 기존 15~60자 출력 검증 안전망은 유지한다. 리포트 피드백은 구조화 출력 배열을 그대로 두되 프롬프트와 `ReportFeedbackOutputValidator`가 비교 섹션의 조건부 0/1개 및 나머지 섹션의 정확한 1개, 줄바꿈 없는 문단을 함께 보장한다. 새 검증 코드는 기존 한 번의 안전 재시도 흐름에 연결한다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5, Spring AI structured output, JUnit 5, AssertJ, Gradle

## Global Constraints

- `goodPoint.text`는 15~23자를 생성 목표로 삼는다.
- `nextActions[].text`는 각각 15~25자를 생성 목표로 삼는다.
- 기록 피드백 출력 검증의 공통 허용 범위 15~60자는 변경하지 않는다.
- 리포트 피드백 `summary`와 `comparisons`, `strengths`, `improvements`, `nextActions` 배열 API를 유지한다.
- 리포트 피드백 `strengths`, `improvements`, `nextActions`는 각각 정확히 1개다.
- 리포트 피드백 `comparisons`는 서버 비교값이 있으면 정확히 1개, 없으면 0개다.
- 리포트 피드백의 각 배열 항목 `text`는 여러 문장을 포함할 수 있지만 `\r` 또는 `\n`이 없는 한 문단이어야 한다.
- 문장을 강제로 잘라 길이를 맞추지 않는다.
- 기존 근거 ID, 비교 리포트 근거, 빈 값 검증과 구조화 출력 재시도 횟수 1회는 유지한다.
- 새로운 의존성, API 필드, DB 스키마, 예외 유형을 추가하지 않는다.
- 현재 작업 트리의 기존 리포트 피드백 변경을 보존하며, 겹치는 파일은 이번 작업의 hunk만 부분 스테이징한다.
- 저장소에는 Detekt, ktlint, Spotless 작업이 구성되어 있지 않으므로 Gradle compile/test를 정적 검증 근거로 사용한다.

---

### Task 1: 기록 피드백 역할별 생성 길이 목표 분리

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilder.kt:44-60`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilderTest.kt:25-69`
- Verify only: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidatorTest.kt:88-124`

**Interfaces:**
- Consumes: `RecordFeedbackPromptBuilder.build(context, queries, evidence): RecordFeedbackPrompt`
- Produces: `RecordFeedbackPrompt.system`에 `goodPoint.text` 15~23자, `nextActions[].text` 15~25자, 모든 `text` 최대 60자 지시
- Preserves: `RecordFeedbackOutputValidator.validate(content, context, evidence): List<String>`와 `MIN_TEXT_LENGTH = 15`, `MAX_TEXT_LENGTH = 60`

- [ ] **Step 1: 역할별 길이 지시를 요구하는 실패 테스트 작성**

`RecordFeedbackPromptBuilderTest`의 기존 공통 길이 assertion을 다음 세 assertion으로 교체하고, 이전 공통 지시가 사라졌는지도 확인한다.

```kotlin
assertThat(prompt.system)
    .contains(
        "잘한 점 text는 15~23자를 목표로 작성한다.",
        "nextActions의 각 text는 15~25자를 목표로 작성한다.",
        "모든 text는 최대 60자까지 허용한다. 강제로 자르지 말고, 길이를 맞춰 다시 쓴다.",
    )
    .doesNotContain("각 text는 15~25자를 목표로 하되")
```

- [ ] **Step 2: 집중 테스트를 실행해 RED 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*RecordFeedbackPromptBuilderTest'
```

Expected: FAIL. 현재 system prompt에는 `각 text는 15~25자를 목표로 하되, 최대 60자까지 허용한다.`만 있어 새 역할별 세 문구를 찾지 못한다.

- [ ] **Step 3: 공통 길이 지시를 역할별 지시로 교체**

`RecordFeedbackPromptBuilder.systemPrompt()`의 공통 길이 한 줄을 다음 세 줄로 교체한다. 기존 잘한 점 형식, 다음 행동 개수, 말투, 근거 지시는 그대로 둔다.

```kotlin
잘한 점 text는 15~23자를 목표로 작성한다.
nextActions의 각 text는 15~25자를 목표로 작성한다.
모든 text는 최대 60자까지 허용한다. 강제로 자르지 말고, 길이를 맞춰 다시 쓴다.
```

- [ ] **Step 4: 프롬프트와 기존 60자 안전망 테스트를 실행해 GREEN 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*RecordFeedbackPromptBuilderTest' \
  --tests '*RecordFeedbackOutputValidatorTest'
```

Expected: PASS. 프롬프트 테스트가 역할별 목표를 확인하고, validator의 기존 `accepts text at exact 15 and 60 character boundaries` 및 `rejects text at 14 and 61 character boundaries` 테스트도 통과한다.

- [ ] **Step 5: 기록 피드백 변경만 커밋**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilderTest.kt
git diff --cached --check
git diff --cached --name-only
git commit \
  -m "fix(coaching): 기록 피드백 역할별 길이 목표 분리" \
  -m "잘한 점은 더 짧게 읽히도록 유도하고 다음 행동은 기존 실행 정보를 담을 수 있는 길이를 유지한다. 짧은 초과로 전체 결과가 폐기되지 않도록 60자 검증 안전망은 건드리지 않는다." \
  -m "Constraint: 생성 목표와 출력 실패 상한을 분리해야 함" \
  -m "Rejected: 검증 상한을 23자와 25자로 축소 | 정상 모델 응답 폐기 이력이 있음" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: RecordFeedbackPromptBuilderTest, RecordFeedbackOutputValidatorTest" \
  -m "Not-tested: 실제 외부 LLM 생성 결과"
```

Expected staged files: 위의 기록 피드백 production/test 두 파일만 표시된다.

---

### Task 2: 리포트 피드백 섹션 개수와 한 문단 검증

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt:6-57`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt:132-176`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt:46-283`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt:41-309`

**Interfaces:**
- Consumes: `ReportFeedbackOutputValidator.validate(content, context, documents): List<String>`
- Produces: `comparison_count`, `strength_count`, `improvement_count`, `next_action_count`
- Produces: `comparison_text_paragraph`, `strength_text_paragraph`, `improvement_text_paragraph`, `next_action_text_paragraph`
- Preserves: `comparison_not_available`, `comparison_current_report_ref_required`, `comparison_previous_report_ref_required`, 빈 값 및 근거 ID warning
- Produces for retry: 원문 없이 위 개수·문단 warning을 그대로 전달하는 `SAFE_ITEM_WARNING`

- [ ] **Step 1: 모든 양성 fixture를 새 유효 계약으로 정렬**

`ReportFeedbackOutputValidatorTest`의 helper를 다음 코드로 바꾼다. 기존 comparison 테스트가 필수 세 섹션 누락 때문에 부수 warning을 만들지 않도록 `contentWithComparison`도 `validContent`를 사용한다.

```kotlin
private fun item(
    basis: String = "관수 1회",
    text: String = "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.",
    evidenceRef: String = "record:$recordId",
) = ReportFeedbackContentItem(
    basis = basis,
    text = text,
    evidenceRefs = listOf(evidenceRef),
)

private fun comparisonItem(
    evidenceRefs: List<String> = listOf("report:$reportId", "report:$previousReportId"),
    text: String = "직전 재배보다 물 주기 기록이 한 번 늘었어요.",
) = ReportFeedbackContentItem(
    basis = "직전보다 기록 1회 증가",
    text = text,
    evidenceRefs = evidenceRefs,
)

private fun validContent(
    comparisons: List<ReportFeedbackContentItem> = listOf(comparisonItem()),
) = ReportFeedbackContent(
    summary = "이번 물 주기 기록의 흐름을 확인했어요.",
    comparisons = comparisons,
    strengths = listOf(item()),
    improvements = listOf(
        item("물의 양", "물의 양이 알맞았는지 더 살펴볼 필요가 있어요."),
    ),
    nextActions = listOf(
        item("흙 속 수분", "다음에는 물을 준 뒤 흙 속까지 젖었는지 확인하세요."),
    ),
)

private fun contentWithComparison(evidenceRefs: List<String>) = validContent(
    comparisons = listOf(comparisonItem(evidenceRefs = evidenceRefs)),
)
```

기존 양성 테스트의 직접 생성 코드는 `validContent().copy(...)`로 바꾼다. 각 테스트의 관심 필드만 덮어쓰고 나머지 필수 섹션은 유효 fixture로 유지한다.

```kotlin
val politeContent = validContent()

val stylisticContent = validContent().copy(
    comparisons = listOf(
        comparisonItem(
            evidenceRefs = listOf("report:$reportId", "report:$previousReportId", unknown),
            text = "WATERING 기록이 늘었다.",
        ),
    ),
)

val repeatedContent = validContent().copy(
    strengths = listOf(comparison.copy(basis = "이번 기록과 직전 기록의 차이")),
)

val formalContent = validContent().copy(
    summary = "이번 물 주기 기록을 꾸준히 남겼습니다.",
    strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인할 수 있습니다.")),
)

val technicalBasisContent = validContent().copy(
    strengths = listOf(item("WATERING DRIP 관수", "물 준 방법을 꾸준히 지킨 점은 좋았어요.")),
)
```

나머지 양성 테스트도 다음처럼 관심 필드만 바꾼다.

```kotlin
val recordBackedContent = validContent().copy(
    summary = "이번 물 주기 기록을 꾸준히 남겼어요.",
    strengths = listOf(item("관수 1회", "물 준 기록을 남겨 작업 흐름을 확인하기 좋았어요.")),
)

val englishAndKoreanContent = validContent().copy(
    summary = "WATERING 흐름을 확인했어요.",
    strengths = listOf(item("DRIP 관수", "DRIP으로 물을 준 점은 좋았어요.")),
    improvements = listOf(item("토양", "토양 수분을 더 확인하세요.")),
    nextActions = listOf(item("방제", "병해충을 방제하세요.")),
)

val englishEverywhereContent = validContent().copy(
    summary = "이번 물 주기 기록을 확인했어요.",
    strengths = listOf(item("기록", "DRIP 방식을 꾸준히 사용했어요.")),
    improvements = listOf(item("양", "다음에는 kg 단위 대신 한글로 기록하세요.")),
    nextActions = listOf(item("확인", "pH 대신 흙 상태를 쉬운 말로 적으세요.")),
)
```

각 변수는 기존 같은 이름의 테스트에서 `content`로 사용한다. omitted JSON 역직렬화 테스트는 validation을 호출하지 않으므로 그대로 유지한다.

- [ ] **Step 2: 섹션 개수와 한 문단 경계 실패 테스트 작성**

기존 `allows a grounded summary when every item list is empty` 테스트는 새 계약과 반대이므로 제거하고 다음 네 테스트를 추가한다.

```kotlin
@Test
fun `requires exactly one item for each mandatory paragraph section`() {
    val content = validContent().copy(
        strengths = emptyList(),
        improvements = listOf(item(), item(text = "흙 속 수분도 함께 확인할 필요가 있어요.")),
        nextActions = emptyList(),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .containsExactly("strength_count", "improvement_count", "next_action_count")
}

@Test
fun `requires one comparison paragraph when server comparison exists`() {
    val content = validContent(comparisons = emptyList())

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .containsExactly("comparison_count")
}

@Test
fun `allows no comparison paragraph when server comparison is unavailable`() {
    val unavailableContext = context.copy(previousReport = null, comparisons = emptyList())
    val content = validContent(comparisons = emptyList())

    assertThat(ReportFeedbackOutputValidator.validate(content, unavailableContext, emptyList()))
        .isEmpty()
}

@Test
fun `rejects line breaks inside a section paragraph`() {
    val content = validContent().copy(
        nextActions = listOf(
            item(text = "다음에는 흙 속을 확인하세요.\n젖은 깊이도 함께 살펴보세요."),
        ),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .containsExactly("next_action_text_paragraph")
}
```

기존 `rejects an unknown evidence reference while allowing a duplicate item`은 중복 항목을 더 이상 허용하지 않으므로 다음처럼 개수와 근거 오류를 함께 보호하도록 바꾼다.

```kotlin
@Test
fun `rejects multiple items and an unknown evidence reference`() {
    val duplicate = item()
    val unknown = "record:${UUID.randomUUID()}"
    val content = validContent().copy(
        strengths = listOf(duplicate, duplicate),
        improvements = listOf(item(evidenceRef = unknown)),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .contains("strength_count", "unknown_evidence:$unknown")
}
```

- [ ] **Step 3: validator 테스트를 실행해 RED 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: FAIL. 현재 validator는 비교 데이터가 있을 때 comparison 누락, 필수 세 섹션의 0/2개, `text` 줄바꿈을 거부하지 않는다.

- [ ] **Step 4: 섹션 개수와 줄바꿈 없는 문단을 검증**

`ReportFeedbackOutputValidator.validate()`에서 `content.items()` 순회 전에 다음 구조 검증을 수행한다. 비교값이 없는 경우에는 기존 `comparison_not_available` 코드를 유지한다.

```kotlin
if (context.comparisons.isEmpty()) {
    if (content.comparisons.isNotEmpty()) {
        warnings += "comparison_not_available"
    }
} else if (content.comparisons.size != 1) {
    warnings += "comparison_count"
}
if (content.strengths.size != 1) {
    warnings += "strength_count"
}
if (content.improvements.size != 1) {
    warnings += "improvement_count"
}
if (content.nextActions.size != 1) {
    warnings += "next_action_count"
}

val items = content.items()
```

각 item의 `text_blank` 검사 바로 다음에 문단 형식 검사를 추가한다.

```kotlin
if (item.text.any { it == '\r' || it == '\n' }) {
    warnings += "${structured.section.name.lowercase()}_text_paragraph"
}
```

- [ ] **Step 5: validator 테스트를 실행해 GREEN 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackOutputValidatorTest'
```

Expected: PASS. 새 개수·문단 테스트와 기존 comparison 근거·unknown evidence 테스트가 모두 통과한다.

- [ ] **Step 6: 재시도용 안전 warning 실패 테스트 작성**

`ReportFeedbackGenerationServiceTest.validContent()`를 새 유효 계약에 맞게 바꾼다.

```kotlin
private fun validContent() = ReportFeedbackContent(
    summary = "이번 물 주기 기록의 흐름을 확인했어요.",
    comparisons = listOf(comparisonItem()),
    strengths = listOf(item()),
    improvements = listOf(item(text = "물의 양이 알맞았는지 더 살펴볼 필요가 있어요.")),
    nextActions = listOf(item(text = "다음에는 물을 준 뒤 흙 속까지 젖었는지 확인하세요.")),
)
```

개수와 문단 warning이 원문 없이 두 번째 요청에 전달되는 테스트를 추가한다.

```kotlin
@Test
fun `section shape failures are retried with safe diagnostic codes`() {
    val generatedText = "다음에는 흙 속을 확인하세요.\n젖은 깊이도 함께 살펴보세요."
    val invalid = validContent().copy(
        strengths = emptyList(),
        nextActions = listOf(item(text = generatedText)),
    )
    val client = FakeChatClient(invalid, validContent())

    service(client).generate(context())

    assertThat(client.attempts).isEqualTo(2)
    assertThat(client.requestSpec.userTexts.last())
        .contains("strength_count", "next_action_text_paragraph")
        .doesNotContain(generatedText)
}
```

- [ ] **Step 7: generation service 테스트를 실행해 RED 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: FAIL. validator warning은 발생하지만 현재 `SAFE_ITEM_WARNING`이 `*_count`와 `*_text_paragraph`를 허용하지 않아 retry prompt에는 `invalid_output`만 들어간다.

- [ ] **Step 8: 새 warning을 안전 재시도 코드로 허용**

`ReportFeedbackGenerationService`의 `SAFE_ITEM_WARNING` 정규식을 다음처럼 확장한다. warning에는 생성 원문이나 evidence 값이 포함되지 않는다.

```kotlin
val SAFE_ITEM_WARNING = Regex(
    "^(comparison|strength|improvement|next_action)_" +
        "(count|basis_blank|text_blank|text_paragraph|evidence_refs_blank)$",
)
```

- [ ] **Step 9: validator와 generation service 테스트를 실행해 GREEN 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: PASS. invalid section shape is retried once with fixed safe codes, raw generated paragraph is not echoed, and existing unknown evidence masking remains green.

- [ ] **Step 10: 기존 변경과 겹치지 않게 이번 hunk만 커밋**

아래 네 파일에는 현재 작업 트리의 선행 변경이 있으므로 전체 파일을 `git add`하지 않는다. `git add -p`에서 이번 task의 개수·문단·safe warning hunk만 선택하고, 한 hunk에 선행 변경이 섞이면 `s`로 나누거나 `e`로 이번 줄만 남긴다.

```bash
git add -p \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt
git diff --cached --check
git diff --cached --name-only
git diff --cached
git commit \
  -m "fix(coaching): 리포트 문단 계약 위반 재생성" \
  -m "섹션 누락·중복과 문단 줄바꿈을 구조 검증에서 거부하고, 모델이 한 번의 기존 재시도로 정확한 배열 계약을 고칠 수 있도록 원문 없는 진단 코드를 전달한다." \
  -m "Constraint: 공개 배열 API와 기존 재시도 횟수를 유지해야 함" \
  -m "Rejected: 배열을 단일 문자열로 변경 | 기존 클라이언트 계약이 깨짐" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Directive: section count warning에는 생성 원문이나 evidence 값을 추가하지 말 것" \
  -m "Tested: ReportFeedbackOutputValidatorTest, ReportFeedbackGenerationServiceTest" \
  -m "Not-tested: 실제 외부 LLM 생성 결과"
```

Expected staged diff: 개수 warning, 줄바꿈 warning, safe warning 정규식, 새 계약용 test fixture와 경계 테스트만 포함한다. 기존 tone/English 허용, 기본 빈 배열, 다른 재생성 변경은 포함하지 않는다.

---

### Task 3: 리포트 피드백 섹션별 한 문단 생성 지시

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt:59-80`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt:17-104`

**Interfaces:**
- Consumes: `ReportFeedbackPromptBuilder.build(context, evidence): ReportFeedbackPrompt`
- Produces: `ReportFeedbackPrompt.system`에 필수 세 섹션 정확히 1개, comparison 조건부 0/1개, 줄바꿈·목록 없는 한 문단 지시
- Preserves: 동일 작업 범위, 허용 `evidenceRefs`, 비교값 재계산 금지, 말투와 섹션 간 중복 방지 지시

- [ ] **Step 1: 정확한 항목 수와 한 문단 지시 실패 테스트 작성**

`prompt scopes instructions statistics and allowed evidence to one work type`의 system prompt assertion에 다음 계약을 추가하고, 모든 섹션을 빈 배열로 허용하던 이전 문구가 사라졌는지 확인한다.

```kotlin
assertThat(prompt.system)
    .contains(
        "strengths, improvements, nextActions는 각각 정확히 1개의 항목으로 응답한다.",
        "서버가 계산한 비교값이 있으면 comparisons는 정확히 1개의 항목으로 응답한다.",
        "서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.",
        "각 배열 항목의 text는 줄바꿈이나 목록 기호 없이 하나의 문단으로 작성한다.",
        "한 문단 안에는 자연스럽게 이어지는 여러 문장을 작성해도 된다.",
    )
    .doesNotContain(
        "comparisons, strengths, improvements, nextActions는 근거가 없으면 빈 배열로 응답해도 된다.",
    )
```

- [ ] **Step 2: prompt builder 테스트를 실행해 RED 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: FAIL. 현재 prompt는 모든 item 배열을 빈 배열로 허용하며, 정확한 1개와 한 문단 형식 지시가 없다.

- [ ] **Step 3: system prompt를 새 섹션 계약으로 교체**

`summary, comparisons, strengths, improvements, nextActions를 구조화해 응답한다.`와 바로 다음의 기존 빈 배열 허용 문구를 아래 블록 전체로 교체한다. 아래쪽에 이미 있는 비교값 없음 문장은 제거해 같은 규칙이 이 블록에 한 번만 남게 한다.

```kotlin
summary, comparisons, strengths, improvements, nextActions를 구조화해 응답한다.
strengths, improvements, nextActions는 각각 정확히 1개의 항목으로 응답한다.
서버가 계산한 비교값이 있으면 comparisons는 정확히 1개의 항목으로 응답한다.
서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.
각 배열 항목의 text는 줄바꿈이나 목록 기호 없이 하나의 문단으로 작성한다.
한 문단 안에는 자연스럽게 이어지는 여러 문장을 작성해도 된다.
각 항목은 basis, text, evidenceRefs를 가져야 한다.
```

기술 문서 부재 규칙은 다음처럼 명시해 필수 세 섹션과 충돌하지 않게 한다.

```kotlin
기술 문서가 없더라도 현재 리포트와 대상 기록을 근거로 strengths, improvements, nextActions를 각각 작성한다.
공식문서가 필요한 기술적 주장은 문서 근거 없이 만들지 않는다.
```

- [ ] **Step 4: prompt와 report generation 집중 테스트를 실행해 GREEN 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: PASS. prompt 계약, 구조 검증, 안전 재시도가 서로 같은 섹션 개수와 문단 규칙을 사용한다.

- [ ] **Step 5: 기존 변경과 겹치지 않게 prompt hunk만 커밋**

두 파일 모두 선행 미커밋 변경이 있으므로 새 항목 수·문단 문구와 그 assertion만 부분 스테이징한다.

```bash
git add -p \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt
git diff --cached --check
git diff --cached --name-only
git diff --cached
git commit \
  -m "fix(coaching): 리포트 섹션을 한 문단으로 유도" \
  -m "모델이 기존 배열 계약 안에서 잘한 점·개선점·추천 행동을 각각 하나의 문단으로 만들고, 비교 데이터 유무에 맞춰 비교 문단을 생성하도록 지시를 구체화한다." \
  -m "Constraint: summary와 공개 배열 필드를 유지해야 함" \
  -m "Rejected: 화면에서 여러 항목을 이어 붙이기 | 생성·저장 계약이 계속 여러 항목을 허용함" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Directive: 필수 세 섹션의 빈 배열 허용 문구를 다시 추가하지 말 것" \
  -m "Tested: ReportFeedbackPromptBuilderTest, ReportFeedbackOutputValidatorTest, ReportFeedbackGenerationServiceTest" \
  -m "Not-tested: 실제 외부 LLM 생성 결과"
```

Expected staged diff: prompt의 섹션 개수·문단·근거 지시와 해당 prompt assertion만 포함한다.

---

### Task 4: API 호환성과 전체 백엔드 회귀 검증

**Files:**
- Verify only: `backend/api/src/main/kotlin/com/chamchamcham/api/coaching/reportfeedback/dto/ReportFeedbackResponses.kt:38-66`
- Verify only: `backend/api/src/test/kotlin/com/chamchamcham/api/coaching/reportfeedback/controller/ReportFeedbackControllerTest.kt:50-128`
- Verify only: all backend Gradle modules

**Interfaces:**
- Preserves: `FeedbackResponse(summary, comparisons, strengths, improvements, nextActions)`
- Preserves: 각 섹션의 공개 item shape `ItemResponse(text)`
- Produces: 관련 application/API 테스트와 전체 backend 테스트 통과 증거

- [ ] **Step 1: application 코칭 집중 테스트 실행**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*RecordFeedbackPromptBuilderTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: BUILD SUCCESSFUL, 선택한 다섯 테스트 클래스 모두 PASS.

- [ ] **Step 2: 공개 리포트 피드백 API 회귀 테스트 실행**

Run:

```bash
cd backend
./gradlew :api:test \
  --tests '*ReportFeedbackControllerTest'
```

Expected: BUILD SUCCESSFUL. READY 응답이 `summary`와 네 배열을 계속 직렬화하고 PENDING/FAILED 계약도 유지된다.

- [ ] **Step 3: 전체 backend 테스트 실행**

Run:

```bash
cd backend
./gradlew test
```

Expected: BUILD SUCCESSFUL. application, api, domain, batch 전체 테스트가 PASS하며 Kotlin main/test compile도 함께 통과한다.

- [ ] **Step 4: diff와 작업 트리 범위 확인**

Run:

```bash
git diff --check
git status --short
git log -4 --oneline --decorate
```

Expected:

- `git diff --check` 출력 없음.
- 이번 계획의 구현 커밋은 기록 길이 목표, 리포트 구조 검증, 리포트 prompt의 세 커밋으로 보인다.
- 기존 리포트 피드백 선행 변경과 `.claude/`는 남아 있을 수 있지만 되돌리거나 이번 커밋에 섞지 않는다.
- 알려진 compile/test 오류가 없다.

## Completion Evidence

최종 보고에는 다음을 기록한다.

- 변경 파일과 각 파일에서 단순화·보장한 계약
- 실행한 집중 테스트, API 테스트, 전체 `./gradlew test` 결과
- 실제 외부 LLM 생성 호출은 수행하지 않았다는 검증 공백
- 섹션을 정확히 1개로 강제해 실제 생성 실패율이 달라질 수 있다는 잔여 위험
- 현재 도구가 제공하면 토큰 사용량, 제공하지 않으면 확인 불가 사실
