# Coaching English-Only Output Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록·리포트 코칭의 공개 문장은 영어만 언어 오류로 거부하고, 한국어 농업 용어는 프롬프트로만 쉽게 표현하도록 유도한다.

**Architecture:** `CoachingTextPolicy`의 하드 검증 책임을 영어 알파벳 검사 하나로 줄인다. Record와 Report의 기존 validator가 이 함수를 호출하고, 재시도 및 안전 로그 warning 이름을 실제 의미에 맞게 `*_text_english`로 통일한다. 구조·근거·길이·개수·중복·친근한 존댓말 검증은 변경하지 않는다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5, JUnit 5, AssertJ, Gradle

## Global Constraints

- 쉬운 표현은 기록·리포트 코칭의 공통 프롬프트 지침으로만 요구한다.
- 농업 용어 블랙리스트는 출력 실패 조건에서 완전히 제거한다.
- 농부에게 보여 주는 생성 문장에 영어 알파벳 `[A-Za-z]`가 있으면 실패시킨다.
- 친근한 존댓말 `~요`, JSON 구조, 필수 값, 길이, 항목 수, 중복, 근거 참조 검증은 유지한다.
- JSON 필드명, enum wire 값, `basis`, `evidenceRefs`, RAG 문서와 검색 질의는 영어 검사 대상이 아니다.
- 새로운 의존성, DB 변경, API 변경, 재시도 횟수 변경, 리포트 재생성 API를 추가하지 않는다.
- 기존 `.claude/`, `.omx/`와 ignore된 로컬 시드·HTML을 수정하거나 스테이징하지 않는다.

---

### Task 1: 공통 영어 정책과 기록·리포트 검증 계약 변경

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicy.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessor.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicyTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabelsTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackGenerationServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`

**Interfaces:**
- Consumes: `CoachingTextPolicy.promptInstructions: String`, 기존 Record/Report content와 validator 입력 타입
- Produces: `CoachingTextPolicy.containsEnglishLetter(text: String): Boolean`
- Produces: `good_point_text_english`, `next_action_<index>_text_english`, `summary_text_english`, `strength_text_english`, `improvement_text_english`, `next_action_text_english`

- [ ] **Step 1: 영어만 거부하고 한국어 농업 용어는 허용하는 실패 테스트 작성**

`CoachingTextPolicyTest`의 호출명을 새 인터페이스로 바꾸고, 기존 금지어 테스트를 허용 테스트로 뒤집는다.

```kotlin
@Test
fun `allows Korean text including farming terms`() {
    listOf(
        "관수", "시비", "방제", "병해충", "생육", "정식", "파종", "제초", "전정",
        "과습", "배수", "추대", "하엽", "토양", "수분", "살포", "살균제", "유기질",
    ).forEach { term ->
        assertThat(CoachingTextPolicy.containsEnglishLetter("${term}을 확인하세요.")).isFalse()
    }
}

@Test
fun `rejects any English letter`() {
    listOf("WATERING으로 했어요.", "10kg을 썼어요.", "pH를 확인하세요.").forEach { text ->
        assertThat(CoachingTextPolicy.containsEnglishLetter(text)).isTrue()
    }
}
```

`CoachingPromptLabelsTest`의 마지막 검증은 쉬운 용어 사전이 아니라 영어 enum 노출 방지를 보호하도록 바꾼다.

```kotlin
@Test
fun `coaching labels never expose English or raw enum names`() {
    val labels = buildList {
        addAll(WorkType.entries.map { it.toCoachingText() })
        addAll(PropagationMethod.entries.map { it.toCoachingText() })
        addAll(IrrigationAmount.entries.map { it.toCoachingText() })
        addAll(IrrigationMethod.entries.map { it.toCoachingText() })
        addAll(FertilizerMaterialCategory.entries.map { it.toCoachingText() })
        addAll(FertilizingMethod.entries.map { it.toCoachingText() })
        addAll(PesticideCategory.entries.map { it.toCoachingText() })
        addAll(WeedingMethod.entries.map { it.toCoachingText() })
        addAll(CropUsePartCategory.entries.map { it.toCoachingText() })
        addAll(HarvestSource.entries.map { it.toCoachingText() })
    }

    assertThat(labels).allMatch { !CoachingTextPolicy.containsEnglishLetter(it) }
    WorkType.entries.forEach { value ->
        assertThat(value.toCoachingText()).isNotEqualTo(value.name)
    }
}
```

`RecordFeedbackOutputValidatorTest`는 public text의 영어만 새 warning으로 거부하고 basis 및 한국어 농업 용어는 허용하게 고친다.

```kotlin
@Test
fun `rejects English only in public text`() {
    val invalid = validResult().copy(
        goodPoint = validItem(
            basis = "DRIP 관수",
            text = "DRIP으로 물을 준 점은 잘했어요.",
        ),
        nextActions = listOf(
            validAction(
                due = RecordFeedbackActionDue.THIS_WEEK,
                category = RecordFeedbackActionCategory.WEATHER,
                basis = "비 예보",
                text = "비 오기 전 물길을 정리하세요.",
                refs = listOf("weather:2026-07-04"),
            ),
            validAction(
                basis = "토양 수분",
                text = "다음에는 토양 수분을 확인하세요.",
            ),
        ),
    )

    assertThat(RecordFeedbackOutputValidator.validate(invalid, context, documents))
        .contains("good_point_text_english")
        .doesNotContain("next_action_1_text_english", "good_point_basis_english")
}
```

`ReportFeedbackOutputValidatorTest`도 동일한 경계를 보호한다.

```kotlin
@Test
fun `rejects English while allowing Korean farming terms in public report text`() {
    val content = ReportFeedbackContent(
        summary = "WATERING 흐름을 확인했어요.",
        strengths = listOf(item("DRIP 관수", "DRIP으로 물을 준 점은 좋았어요.")),
        improvements = listOf(item("토양", "토양 수분을 더 확인하세요.")),
        nextActions = listOf(item("방제", "병해충을 방제하세요.")),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList()))
        .contains("summary_text_english", "strength_text_english")
        .doesNotContain("improvement_text_english", "next_action_text_english")
}
```

생성 재시도 및 기록 processor 테스트의 기대 warning을 새 이름으로 바꾼다.

```kotlin
assertThat(chatClient.requestSpec.userTexts.last())
    .contains("good_point_text_english")
    .doesNotContain(generatedText, "DRIP으로")

assertThat(client.requestSpec.userTexts.last())
    .contains("summary_text_english")
    .doesNotContain(generatedText, "WATERING")

IllegalStateException(
    "invalid product output: next_action_1_text_length,next_action_1_text_english," +
        "unknown_evidence:untrusted-generated-value",
)
```

- [ ] **Step 2: 집중 테스트를 실행해 RED 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*CoachingPromptLabelsTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: FAIL. `containsEnglishLetter`가 아직 없어서 Kotlin compile 오류가 발생하거나, 이전 용어 블랙리스트 때문에 한국어 농업 용어 허용 assertion이 실패한다.

- [ ] **Step 3: 공통 정책을 영어 검사 하나로 축소**

`CoachingTextPolicy`의 프롬프트 지침은 유지하고 하드 검증 구현만 다음처럼 줄인다.

```kotlin
object CoachingTextPolicy {
    val promptInstructions: String = """
        농부에게 보여 줄 모든 문장은 친근한 존댓말로 작성한다.
        영어 알파벳, 내부 enum, 영어 필드명과 영어 단위를 쓰지 않는다.
        관수는 물 주기, 시비는 거름 주기처럼 일상에서 쓰는 말로 풀어 쓴다.
        공식 기술 문서의 어려운 표현을 그대로 복사하지 말고 뜻을 쉬운 말로 설명한다.
        농약 때문에 생긴 피해는 약해라고 줄이지 말고 약 때문에 생긴 피해라고 쓴다.
    """.trimIndent()

    fun containsEnglishLetter(text: String): Boolean = ENGLISH_LETTER.containsMatchIn(text)

    private val ENGLISH_LETTER = Regex("[A-Za-z]")
}
```

- [ ] **Step 4: Record와 Report validator warning을 영어 전용 이름으로 변경**

Record public text 검사:

```kotlin
if (CoachingTextPolicy.containsEnglishLetter(item.text)) {
    warnings += "${prefix}_text_english"
}
```

Report summary 및 item text 검사:

```kotlin
if (CoachingTextPolicy.containsEnglishLetter(content.summary)) {
    warnings += "summary_text_english"
}

if (CoachingTextPolicy.containsEnglishLetter(item.text)) {
    warnings += "${structured.section.name.lowercase()}_text_english"
}
```

- [ ] **Step 5: 재시도와 안전 진단 warning 이름을 동기화**

`ReportFeedbackGenerationService`의 안전 warning 목록을 다음처럼 바꾼다.

```kotlin
val SAFE_ITEM_WARNING = Regex(
    "^(strength|improvement|next_action)_(basis_blank|text_blank|text_tone|text_english|evidence_refs_blank)$",
)
val SAFE_RETRY_WARNINGS = setOf(
    "summary_blank",
    "summary_text_tone",
    "summary_text_english",
    "duplicate_item",
    "structured_output_parse_failed",
)
```

`RecordFeedbackGenerationProcessor`의 안전 진단 정규식도 `text_language` 대신
`text_english`를 허용한다.

```kotlin
val SAFE_VALIDATION_DIAGNOSTIC = Regex(
    "^(good_point|next_action_[0-9]+)_(basis_blank|text_blank|evidence_refs_blank|evidence_ref_blank|text_length|text_tone|text_english)$" +
        "|^(action_count|weather_action_without_weather_evidence|pest_disease_action_without_document_evidence)$",
)
```

- [ ] **Step 6: 집중 테스트를 실행해 GREEN 확인**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*CoachingPromptLabelsTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: `BUILD SUCCESSFUL`. 기존 구조·근거·말투 테스트와 새 영어 전용 검증 테스트가 모두 통과한다.

- [ ] **Step 7: 이전 정책명과 warning 잔존 여부 확인**

Run:

```bash
rg -n 'hasDisallowedLanguage|DISALLOWED_TERMS|text_language' \
  application/src/main application/src/test
```

Expected: 출력 없음.

- [ ] **Step 8: 구현을 하나의 Lore 커밋으로 기록**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicy.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessor.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicyTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabelsTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackGenerationServiceTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt

git commit -m "fix(coaching): 과도한 용어 검증으로 인한 생성 실패 완화" \
  -m "쉬운 표현은 프롬프트로 유도하고 공개 코칭 문장의 영어만 하드 검증하도록 기록·리포트 정책을 맞췄다." \
  -m "Constraint: 친근한 ~요 말투와 구조·근거 검증은 유지
Rejected: 한국어 농업 용어 감사 경고 저장 | 현재 소비 기능이 없음
Confidence: high
Scope-risk: moderate
Directive: 영문 고유명사 보존 요구가 생기면 입력 컨텍스트 기반 예외를 별도 설계할 것
Tested: application 집중 테스트
Not-tested: 외부 모델 실생성"
```

Expected: 구현 파일과 테스트만 포함하는 새 커밋 1개. `.claude/`, `.omx/`, ignore된 HTML과 시드는 포함되지 않는다.

### Task 2: 전체 회귀 검증과 완료 증거 수집

**Files:**
- Verify only: `backend/domain`
- Verify only: `backend/application`
- Verify only: `backend/api`

**Interfaces:**
- Consumes: Task 1의 `containsEnglishLetter`와 `*_text_english` warning 계약
- Produces: 전체 backend 회귀 테스트 및 diff 무결성 증거

- [ ] **Step 1: 전체 영향 모듈 테스트 실행**

Run:

```bash
cd backend
./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`. domain, application, api의 기존 테스트가 모두 통과한다.

- [ ] **Step 2: diff와 working tree 범위 확인**

Run:

```bash
git show --stat --oneline HEAD
git show --check HEAD
git status --short
```

Expected:

- HEAD는 Task 1의 운영 코드·테스트 변경만 포함한다.
- `git show --check HEAD` 출력에 whitespace 오류가 없다.
- working tree에는 기존 `.claude/`, `.omx/` 외에 누락된 tracked 변경이 없다.

- [ ] **Step 3: 런타임 검증 한계 기록**

외부 OpenClaw에 기존 스냅샷을 재전송하려면 실행 중인 서버의 자격 증명을 재사용해야
하므로 자동 검증하지 않는다. 단위·서비스 테스트로 다음을 증명한다.

```text
- 한국어 농업 용어는 language warning을 만들지 않음
- 공개 문장의 영어는 english warning을 만들고 한 번 재시도함
- 두 번 영어가 남으면 STRUCTURED_OUTPUT_INVALID
- 친근한 ~요, 구조, 근거 검증은 유지됨
```

Expected: 최종 보고에 외부 모델 실생성을 수행하지 않은 이유와 남는 위험을 명시한다.
