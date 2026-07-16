# Report Coaching Friendly Tone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 리포트 코칭 전체를 부드러운 직접형 문체로 유도하고, 개선점은 보완 방향까지 안내하며, 지난 재배 비교 변화율은 소수점 없는 정수로 모델에 전달한다.

**Architecture:** 기존 `ReportFeedbackPromptBuilder` 경계에서만 모델 입력 표현과 시스템 역할 계약을 바꾼다. 비교 계산 정밀도, 구조화 응답, 검증기, API, DB는 유지하고 PromptBuilder 단위 테스트로 사용자용 표현을 고정한다.

**Tech Stack:** Kotlin, Spring Boot, Spring AI `ChatClient`, JUnit 5, AssertJ, Gradle

## Global Constraints

- 작업 브랜치는 `feat/report-coaching-tone(backend)`이며 격리 워크트리에서만 수정한다.
- 개별 영농기록 코칭은 변경하지 않는다.
- API 응답, 구조화 출력 필드, 데이터베이스 스키마, 기존 저장 결과를 변경하지 않는다.
- `previousReport` 같은 내부 클래스·필드 이름과 비교 계산 정밀도를 유지한다.
- 새 의존성, 자연어 후처리, 문체 전용 검증기, 새 재시도 오류 코드를 추가하지 않는다.
- 기존 사용자 노출 문구의 20~65자, 항목 수, 근거 참조, 최대 두 번 재시도 계약을 유지한다.
- 비교 변화율은 모델 입력에서만 절댓값을 `RoundingMode.HALF_UP`으로 정수 반올림한다.
- 사용자에게 보이는 비교 개념은 `직전`이 아니라 `지난 재배`로 표현한다.
- 모든 커밋은 Conventional Commits 제목과 Lore 트레일러를 사용한다.
- 설계 기준: `docs/superpowers/specs/2026-07-16-report-coaching-friendly-tone-design.md`

---

## File Structure

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
  - 리포트 코칭 시스템 지침을 소유한다.
  - 현재·지난 재배 통계와 서버 비교값을 모델 입력용 문장으로 조립한다.
  - 비교 변화율의 사용자용 정수 반올림을 담당한다.
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
  - 섹션별 문체 계약, 지난 재배 표현, 반올림 경계, 변화율 부재 표현을 회귀 테스트한다.

새 파일이나 새 프로덕션 타입은 만들지 않는다.

---

### Task 1: 지난 재배 비교 문구와 정수 변화율

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt:3-14,24-48,88-120`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt:3-10,17-83`

**Interfaces:**
- Consumes: `ReportFeedbackContext.previousReport`, `ReportFeedbackContext.comparisons`, `ReportFeedbackComparison.difference: BigDecimal`, `ReportFeedbackComparison.relativeChangePct: BigDecimal?`, `ReportFeedbackComparison.currentCoverage`, `ReportFeedbackComparison.previousCoverage`
- Produces: `ReportFeedbackPrompt.user: String` 안의 `지난 재배` 통계·비교 문장과 정수 변화율
- Preserves: `ReportFeedbackComparison` 값 자체와 `report:<id>` 근거 식별자

- [ ] **Step 1: 비교 표현 회귀 테스트를 먼저 작성한다**

테스트 파일에 `Coverage` import를 추가한다.

```kotlin
import com.chamchamcham.domain.report.Coverage
```

기존 `prompt scopes instructions statistics and allowed evidence to one work type`의 사용자 프롬프트 assertion을 다음 내용으로 바꾼다.

```kotlin
assertThat(prompt.user)
    .contains("대상 작업: 물 주기")
    .contains("기록 횟수: 4회")
    .contains("평균 작업 간격: 3.5일")
    .contains("물을 준 방법: 호스로 조금씩 물을 줌")
    .contains("지난 재배 기록 횟수: 3회")
    .contains("record:$recordId")
    .contains("report:$currentReportId")
    .contains("report:$previousReportId")
    .contains("지난 재배보다 기록 횟수가 1회 늘었어요. 변화율은 33퍼센트예요.")
    .contains("document-1")
    .contains("황기 관수 기술", "관수 후 토양 수분을 확인한다.")
    .doesNotContain(
        "직전",
        "33.33퍼센트",
        "WATERING",
        "recordCount",
        "averageIntervalDays",
        "details=",
        "DRIP",
        "LOW",
        "code=",
        "label=",
        "CategoryRef",
    )
    .doesNotContain("FERTILIZING")
    .doesNotContain("수확량")
```

같은 테스트 클래스에 반올림, 감소 방향, 변화율 부재, 입력 범위 표현을 검증하는 테스트를 추가한다.

```kotlin
@Test
fun `comparison percentage uses half up rounding without decimals`() {
    listOf(
        BigDecimal("33.33") to "33",
        BigDecimal("33.50") to "34",
        BigDecimal("0.49") to "0",
    ).forEach { (rawPercentage, expectedPercentage) ->
        val base = context()
        val comparison = base.comparisons.single().copy(relativeChangePct = rawPercentage)

        val prompt = ReportFeedbackPromptBuilder().build(
            base.copy(comparisons = listOf(comparison)),
            emptyList(),
        )

        assertThat(prompt.user)
            .contains("변화율은 ${expectedPercentage}퍼센트예요.")
            .doesNotContain("변화율은 ${rawPercentage.toPlainString()}퍼센트예요.")
    }
}

@Test
fun `comparison keeps decrease direction while rounding the absolute percentage`() {
    val base = context()
    val comparison = base.comparisons.single().copy(
        difference = BigDecimal("-1"),
        relativeChangePct = BigDecimal("-12.50"),
    )

    val prompt = ReportFeedbackPromptBuilder().build(
        base.copy(comparisons = listOf(comparison)),
        emptyList(),
    )

    assertThat(prompt.user)
        .contains("지난 재배보다 기록 횟수가 1회 줄었어요. 변화율은 13퍼센트예요.")
}

@Test
fun `comparison omits unavailable percentage instead of explaining the calculation limit`() {
    val base = context()
    val comparison = base.comparisons.single().copy(relativeChangePct = null)

    val prompt = ReportFeedbackPromptBuilder().build(
        base.copy(comparisons = listOf(comparison)),
        emptyList(),
    )

    assertThat(prompt.user)
        .contains("지난 재배보다 기록 횟수가 1회 늘었어요.")
        .doesNotContain("변화율")
}

@Test
fun `comparison coverage calls the prior period last cultivation`() {
    val base = context()
    val comparison = base.comparisons.single().copy(
        currentCoverage = Coverage(recordedCount = 3, targetCount = 4),
        previousCoverage = Coverage(recordedCount = 2, targetCount = 3),
    )

    val prompt = ReportFeedbackPromptBuilder().build(
        base.copy(comparisons = listOf(comparison)),
        emptyList(),
    )

    assertThat(prompt.user)
        .contains("입력 범위는 이번 3/4건, 지난 재배 2/3건이에요.")
        .doesNotContain("직전")
}
```

- [ ] **Step 2: 새 비교 테스트가 기존 구현에서 실패하는지 확인한다**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: FAIL. 기존 프롬프트에 `직전`과 소수점 변화율이 남아 있고, `relativeChangePct == null`일 때 `변화율은 계산하지 않았어요`가 포함된다.

- [ ] **Step 3: PromptBuilder에서 사용자용 비교 표현을 구현한다**

`ReportFeedbackPromptBuilder.kt`에 반올림 import를 추가한다.

```kotlin
import java.math.RoundingMode
```

허용 근거 설명과 비교 섹션 제목을 바꾼다.

```kotlin
val allowedEvidenceRefs = buildList {
    add("- report:${context.report.id} : 현재 완료 리포트")
    context.records.forEach { add("- record:${it.id} : 대상 영농기록") }
    context.previousReport?.let { add("- report:${it.id} : 지난 재배 리포트") }
    evidence.forEach { add("- ${it.id} : ${it.title}") }
}.joinToString("\n")
```

```kotlin
appendLine(formatPreviousReport(context))
appendLine("서버가 계산한 지난 재배의 동일 작업 비교:")
```

지난 재배 통계와 비교값 formatter를 다음 코드로 교체한다.

```kotlin
private fun formatPreviousReport(context: ReportFeedbackContext): String {
    val previous = context.previousReport ?: return "지난 재배 리포트 없음"
    return buildString {
        appendLine(
            "지난 재배 리포트(report:${previous.id}): " +
                "${previous.startsAt}~${previous.endsAt}",
        )
        formatStatistics(context.workType, previous.statistics, prefix = "지난 재배 ")
            .forEach { appendLine("- $it") }
    }.trim()
}

private fun formatComparison(comparison: ReportFeedbackComparison): String {
    val unit = comparison.unit.unitText() ?: comparison.unit
    val difference = comparison.difference
    val direction = when {
        difference.signum() > 0 -> "${difference.abs().toPlainString()}$unit 늘었어요."
        difference.signum() < 0 -> "${difference.abs().toPlainString()}$unit 줄었어요."
        else -> "변화가 없어요."
    }
    val relative = comparison.relativeChangePct?.let {
        val rounded = it.abs().setScale(0, RoundingMode.HALF_UP).toPlainString()
        " 변화율은 ${rounded}퍼센트예요."
    }.orEmpty()
    val coverage = formatCoverage(comparison)
    return "지난 재배보다 " +
        "${comparison.metricLabel}${comparison.metricLabel.subjectParticle()} " +
        "$direction$relative$coverage"
}

private fun formatCoverage(comparison: ReportFeedbackComparison): String {
    val current = comparison.currentCoverage ?: return ""
    val previous = comparison.previousCoverage ?: return ""
    return " 입력 범위는 이번 ${current.recordedCount}/${current.targetCount}건, " +
        "지난 재배 ${previous.recordedCount}/${previous.targetCount}건이에요."
}
```

- [ ] **Step 4: 비교 표현 집중 테스트가 통과하는지 확인한다**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: PASS. 모든 지난 재배 표현, `HALF_UP` 경계, 감소 방향, 변화율 부재, coverage 문장이 통과한다.

- [ ] **Step 5: 비교 표현 변경을 Lore 형식으로 커밋한다**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt
git commit \
  -m "fix(coaching): 지난 재배 비교를 읽기 쉽게 정리" \
  -m "모델 입력의 직전 리포트 표현을 지난 재배로 통일하고 변화율을 사용자용 정수로 반올림한다. 계산할 수 없는 변화율 설명은 생략하고 확인 가능한 절대 변화만 전달한다." \
  -m "Constraint: 내부 비교 정밀도와 API·DB 계약 유지" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'"
```

---

### Task 2: 리포트 코칭 전체의 역할별 친근한 문체

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt:58-86`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt:17-84`

**Interfaces:**
- Consumes: Task 1이 변경한 `ReportFeedbackPrompt.user`의 지난 재배 표현
- Produces: `ReportFeedbackPrompt.system: String` 안의 `summary`, `comparisons`, `strengths`, `improvements`, `nextActions` 역할·문체 계약
- Preserves: 섹션별 필수 항목 수, 20~65자, `basis`, `evidenceRefs`, 공식 문서 근거, 중복 방지 계약

- [ ] **Step 1: 역할별 문체 계약 테스트를 먼저 작성한다**

기존 첫 테스트의 두 assertion을 새 역할 표현으로 갱신한다.

```kotlin
assertThat(prompt.system)
    .contains("대상 작업 타입 하나만")
    .contains("nextActions")
    .contains("빈 배열")
    .contains("nextActions는 다음 작업에서 언제 무엇을 기록하거나 확인할지")
    .contains("summary와 모든 text는 친근한 존댓말로 끝낸다.")
    .contains("comparisons")
    .contains("서버가 계산한 비교값을 그대로 사용하고 다시 계산하지 않는다.")
    .contains("comparisons는 지난 재배와 달라진 사실만")
    .contains("comparison, strength, improvement, next-action 사이에 같은 내용을 반복하지 않는다.")
    .contains(CoachingTextPolicy.promptInstructions)
    .doesNotContain("다음 사이클 계획")
```

같은 테스트 클래스에 섹션 역할과 개선점 예시를 검증하는 테스트를 추가한다.

```kotlin
@Test
fun `prompt defines friendly and distinct roles for every public section`() {
    val prompt = ReportFeedbackPromptBuilder().build(context(), emptyList())

    assertThat(prompt.system).contains(
        "summary는 이번 재배에서 확인한 핵심을 작업과 기록 중심으로 균형 있게 요약한다.",
        "comparisons는 지난 재배와 달라진 사실만 설명하고 평가나 권고를 넣지 않는다.",
        "strengths는 근거에서 확인한 잘한 행동과 그 행동이 도움이 된 이유를 함께 설명한다.",
        "improvements는 부족한 점, 그 점이 판단이나 관리에 미친 영향, 앞으로 보완할 방향을 함께 설명한다.",
        "자료가 부족해도 판단이 어렵거나 해석이 제한됐다는 설명으로 끝내지 않고, 다음에 함께 남길 기록 항목을 안내한다.",
        "nextActions는 다음 작업에서 언제 무엇을 기록하거나 확인할지 실행 가능한 한 가지 행동으로 작성한다.",
        "사용자에게 내부 보고서나 시스템을 설명하지 말고 농부가 남긴 작업과 기록을 먼저 말한다.",
        "공식 기술 문서를 근거로 사용해도 문서를 문장의 주어로 내세우지 않는다.",
        "다음 개선점 예시는 형식만 참고하고 내용을 복사하지 않는다.",
        "나쁜 예: \"정보가 없어 해석이 제한됐어요.\"",
        "좋은 예: \"기록에 필요한 정보가 빠져 판단하기 어려웠어요. 다음에는 빠진 정보도 함께 기록해 보세요.\"",
        "summary, comparisons, strengths는 \"~했어요.\"처럼 회고형 존댓말로 작성한다.",
        "improvements는 부족한 점을 부드럽게 설명하고 보완 방향은 \"~해 보세요.\"처럼 제안한다.",
        "nextActions는 \"~하세요.\"처럼 분명한 행동형 존댓말로 작성한다.",
    )
}
```

- [ ] **Step 2: 새 문체 테스트가 기존 구현에서 실패하는지 확인한다**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: FAIL. 기존 system prompt에는 섹션별 의미, 개선 방향, 한계 설명 금지, 역할별 어미 계약이 없다.

- [ ] **Step 3: system prompt를 승인된 역할 계약으로 교체한다**

`ReportFeedbackPromptBuilder.systemPrompt()`를 다음 코드로 교체한다.

```kotlin
private fun systemPrompt(): String {
    return """
        당신은 약용작물 재배 회고 코치다. 제공된 근거에 없는 수치나 사실을 만들지 않는다.
        지정된 대상 작업 타입 하나만 회고하고 다른 작업을 비교하거나 권고하지 않는다.
        지난 재배 비교는 제공된 동일 작업 통계만 사용하고 이전 기록이나 메모를 본 것처럼 말하지 않는다.
        summary, comparisons, strengths, improvements, nextActions를 구조화해 응답한다.
        summary는 이번 재배에서 확인한 핵심을 작업과 기록 중심으로 균형 있게 요약한다.
        comparisons는 지난 재배와 달라진 사실만 설명하고 평가나 권고를 넣지 않는다.
        strengths는 근거에서 확인한 잘한 행동과 그 행동이 도움이 된 이유를 함께 설명한다.
        improvements는 부족한 점, 그 점이 판단이나 관리에 미친 영향, 앞으로 보완할 방향을 함께 설명한다.
        자료가 부족해도 판단이 어렵거나 해석이 제한됐다는 설명으로 끝내지 않고, 다음에 함께 남길 기록 항목을 안내한다.
        nextActions는 다음 작업에서 언제 무엇을 기록하거나 확인할지 실행 가능한 한 가지 행동으로 작성한다.
        사용자에게 내부 보고서나 시스템을 설명하지 말고 농부가 남긴 작업과 기록을 먼저 말한다.
        공식 기술 문서를 근거로 사용해도 문서를 문장의 주어로 내세우지 않는다.
        다음 개선점 예시는 형식만 참고하고 내용을 복사하지 않는다.
        나쁜 예: "정보가 없어 해석이 제한됐어요."
        좋은 예: "기록에 필요한 정보가 빠져 판단하기 어려웠어요. 다음에는 빠진 정보도 함께 기록해 보세요."
        strengths, improvements, nextActions는 각각 정확히 1개의 항목으로 응답한다.
        서버가 계산한 비교값이 있으면 comparisons는 정확히 1개의 항목으로 응답한다.
        서버가 계산한 비교값이 없으면 comparisons는 반드시 빈 배열로 응답한다.
        각 배열 항목의 text는 줄바꿈이나 목록 기호 없이 하나의 문단으로 작성한다.
        한 문단 안에는 자연스럽게 이어지는 여러 문장을 작성해도 된다.
        summary는 20~65자로 작성한다.
        comparisons의 text는 20~65자로 작성한다.
        strengths, improvements, nextActions의 text는 각각 20~65자로 작성한다.
        최소 길이를 맞출 때 의미 없는 표현을 덧붙이지 말고 근거, 판단, 실행 방법을 보강해 다시 쓴다.
        65자를 넘으면 문장을 자르지 말고 핵심 내용을 남겨 다시 쓴다.
        각 항목은 basis, text, evidenceRefs를 가져야 한다.
        evidenceRefs에는 허용 evidenceRefs에 나열된 값을 정확히 그대로 사용한다.
        통계 필드명이나 통계값은 evidenceRefs로 사용하지 않는다.
        기술 문서가 없더라도 현재 리포트와 대상 기록을 근거로 strengths, improvements, nextActions를 각각 작성한다.
        공식 기술 문서가 필요한 기술적 주장은 문서 근거 없이 만들지 않는다.
        서버가 계산한 비교값을 그대로 사용하고 다시 계산하지 않는다.
        comparison, strength, improvement, next-action 사이에 같은 내용을 반복하지 않는다.
        summary와 모든 text는 친근한 존댓말로 끝낸다.
        summary, comparisons, strengths는 "~했어요."처럼 회고형 존댓말로 작성한다.
        improvements는 부족한 점을 부드럽게 설명하고 보완 방향은 "~해 보세요."처럼 제안한다.
        nextActions는 "~하세요."처럼 분명한 행동형 존댓말로 작성한다.
    """.trimIndent() + "\n" + CoachingTextPolicy.promptInstructions
}
```

- [ ] **Step 4: 문체 계약 집중 테스트가 통과하는지 확인한다**

Run:

```bash
cd backend
./gradlew :application:test --tests '*ReportFeedbackPromptBuilderTest'
```

Expected: PASS. 기존 구조·길이·근거 지침과 새 역할·문체 지침이 함께 보호된다.

- [ ] **Step 5: 출력 검증과 생성 재시도 회귀 테스트를 실행한다**

Run:

```bash
cd backend
./gradlew :application:test \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: PASS. PromptBuilder 변경이 구조화 결과 정규화, 검증, 재시도 계약을 바꾸지 않는다.

- [ ] **Step 6: 전체 백엔드 테스트와 diff 정적 검사를 실행한다**

Run:

```bash
cd backend
./gradlew test
cd ..
git diff --check
```

Expected: `BUILD SUCCESSFUL`; `git diff --check` 출력 없음. 기존 JDK 23/Kotlin 21 fallback 및 `MockBean` deprecation warning은 기준 실행에서도 존재하므로 실패로 보지 않는다.

- [ ] **Step 7: 문체 계약 변경을 Lore 형식으로 커밋한다**

```bash
git add \
  backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt
git commit \
  -m "fix(coaching): 리포트 개선점을 행동 방향까지 안내" \
  -m "리포트 코칭의 다섯 섹션에 서로 다른 역할과 부드러운 존댓말을 지정한다. 자료가 부족한 경우에도 한계 설명으로 끝내지 않고 다음 기록 방향을 안내하도록 좋은 예와 나쁜 예를 제공한다." \
  -m "Constraint: 구조화 응답·검증기·API·DB 계약 유지" \
  -m "Rejected: 자연어 의미 검증기 | 정상 문장 오탐과 생성 실패 증가 위험" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Directive: 문체 규칙을 더 강제하기 전에 라이브 생성 위반 사례를 수집할 것" \
  -m "Tested: PromptBuilder·OutputValidator·GenerationService 집중 테스트; ./gradlew test; git diff --check" \
  -m "Not-tested: 라이브 모델 생성 문체"
```

---

## Final Verification Checklist

- [ ] `git status --short --branch`에 계획 밖 변경이 없다.
- [ ] 프로덕션 변경은 `ReportFeedbackPromptBuilder.kt` 하나뿐이다.
- [ ] `ReportFeedbackPromptBuilderTest`가 지난 재배 표현, 반올림, 역할별 문체를 모두 보호한다.
- [ ] `ReportFeedbackOutputValidatorTest`와 `ReportFeedbackGenerationServiceTest`가 통과한다.
- [ ] `./gradlew test`가 `BUILD SUCCESSFUL`로 끝난다.
- [ ] `git diff --check` 출력이 없다.
- [ ] API, DB, 구조화 출력, 기존 저장 결과에는 변경이 없다.
- [ ] 라이브 모델 문체 미검증을 최종 보고에 남긴다.
