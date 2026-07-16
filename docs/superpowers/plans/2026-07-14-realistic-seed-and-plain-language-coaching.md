# Realistic Seed and Plain-Language Coaching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 귀농 2년 차 농부의 이전 완료 주기와 현재 수확 전 주기를 로컬 시드로 재현하고, RecordFeedback과 작업별 ReportFeedback의 사용자 문장에서 영어 enum과 확인된 어려운 농업 용어가 저장·노출되지 않게 한다.

**Architecture:** `coaching/common`에는 생성 문장 지침과 문자열 위반 검사만 담당하는 작은 정책을 두고, enum을 쉬운 입력 문장으로 바꾸는 구체 매핑은 생성 프롬프트 경계에서 사용한다. 기록·리포트의 기존 PromptBuilder와 validator·2회 구조화 출력 흐름은 유지하며, 기록 컨텍스트는 7일보다 오래된 기록에 현재 날씨를 붙이지 않는다. 로컬 시드와 HTML은 기존 ignore 정책을 유지하고, 추적되는 local 설정만 loopback 기본값으로 보호한다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.5, Spring AI `ChatClient`/`VectorStore`, Spring Data JPA, JUnit 5, Mockito, AssertJ, vanilla HTML/JavaScript, Gradle multi-module build.

## Global Constraints

- 기준 설계는 `docs/superpowers/specs/2026-07-14-realistic-seed-and-plain-language-coaching-design.md`다.
- 페르소나는 평창에서 참당귀를 재배하는 귀농 2년 차 농부다.
- 이전 완료 주기는 2024년, 현재 수확 전 주기는 2025년 고정 날짜를 사용한다.
- 현재 최종 수확은 HTML에서 `2025-10-29T09:00`에 사용자가 직접 등록한다.
- ReportFeedback의 직전 비교는 동일 작업 통계만 사용하며 이전 기록 메모를 새 컨텍스트에 추가하지 않는다.
- RecordFeedback과 ReportFeedback의 사용자 노출 prose만 공통 쉬운 말 정책으로 검사한다.
- `basis`, `evidenceRefs`, 검색 질의, 공식 기술 문서, 통계 스냅샷과 API wire enum은 검사 대상이 아니다.
- 사용자 prose에 `[A-Za-z]`가 있거나 초기 금지 용어가 literal substring으로 있으면 검증 실패다.
- 생성 후 문자열 치환, 추가 LLM 감사 호출, 재시도 횟수 증가, 공통 generation service·validator 상속 계층은 만들지 않는다.
- `RecordFeedbackRetrievalQueryPlanner`, `ReportFeedbackRetrievalQueryPlanner`, 도메인 enum의 기존 `label`은 변경하지 않는다.
- 기록 날짜가 오늘부터 0~7일 전일 때만 live weather를 조회한다. 더 오래된 기록과 미래 기록은 조회하지 않는다.
- local 프로필은 `${SERVER_ADDRESS:127.0.0.1}`을 기본 바인딩으로 사용한다.
- 시드 서비스·runner·테스트·HTML은 기존 `.gitignore` 정책을 유지하고 `git add -f`하지 않는다.
- 새 dependency를 추가하지 않는다.
- production 변경은 실패하는 테스트를 먼저 확인한 뒤 최소 구현으로 통과시킨다.
- 커밋은 Conventional Commit 한국어 제목과 Lore trailers를 사용한다.
- 사용자 소유 `?? .claude/`는 읽거나 수정하거나 스테이징하지 않는다.

---

## File Structure

| Area | Files | Responsibility |
| --- | --- | --- |
| Common language | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicy.kt` | 공유 prompt 지침과 user prose 위반 검사 |
| Prompt labels | `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabels.kt` | 현재 도메인 enum을 코칭 입력용 쉬운 표현으로 exhaustive 변환 |
| Record context | `RecordFeedbackContextAssembler.kt` | 0~7일 기록에만 live weather 조립 |
| Record generation | `RecordFeedbackPromptBuilder.kt`, `RecordFeedbackOutputValidator.kt`, `RecordFeedbackGenerationProcessor.kt` | 쉬운 입력·출력 검증·안전 진단 코드 |
| Report generation | `ReportFeedbackPromptBuilder.kt`, `ReportFeedbackOutputValidator.kt`, `ReportFeedbackGenerationService.kt` | 작업별 맵을 한국어 문장으로 포맷하고 모든 공개 prose 검증 |
| Local security | `backend/api/src/main/resources/application-local.yml` | local 서버의 기본 loopback 바인딩 |
| Local seed | ignored `DevRagSeedService.kt`와 테스트 | 2024 완료 + 2025 ACTIVE 주기와 이전 리포트 선투영 |
| Local HTML | ignored `frontend/dev-rag-test.html` | 쉬운 enum 표시, 고정 최종 수확 폼, 로컬 검증 안내 |

---

### Task 1: Add the finite plain-language policy and coaching-only enum labels

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicy.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabels.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingTextPolicyTest.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/common/CoachingPromptLabelsTest.kt`

**Interfaces:**
- Produces: `CoachingTextPolicy.promptInstructions: String`
- Produces: `CoachingTextPolicy.hasDisallowedLanguage(text: String): Boolean`
- Produces: overloaded `toCoachingText()` extensions for `WorkType` and the typed detail enums used by both PromptBuilders.
- Does not change: domain `label`, API serialization, retrieval query labels.

- [ ] **Step 1: Write failing policy tests**

Create tests that pin the finite policy without trying to solve natural-language understanding:

```kotlin
class CoachingTextPolicyTest {
    @Test
    fun `allows friendly Korean text with Korean units`() {
        assertThat(CoachingTextPolicy.hasDisallowedLanguage("뿌리 쪽 흙을 살펴보고 물을 조금 주세요.")).isFalse()
        assertThat(CoachingTextPolicy.hasDisallowedLanguage("수확량은 96킬로그램이었어요.")).isFalse()
    }

    @Test
    fun `rejects any English letter and confirmed farming jargon`() {
        listOf("WATERING으로 했어요.", "10kg을 썼어요.", "pH를 확인하세요.")
            .forEach { assertThat(CoachingTextPolicy.hasDisallowedLanguage(it)).isTrue() }

        listOf("관수", "시비", "방제", "병해충", "생육", "정식", "파종", "제초", "전정",
            "과습", "배수", "추대", "하엽", "토양", "수분", "살포", "살균제", "유기질")
            .forEach { term ->
                assertThat(CoachingTextPolicy.hasDisallowedLanguage("${term}을 확인하세요.")).isTrue()
            }
    }

    @Test
    fun `does not reject ambiguous everyday weak expression`() {
        assertThat(CoachingTextPolicy.hasDisallowedLanguage("잎이 약해 보이면 먼저 살펴보세요.")).isFalse()
    }
}
```

- [ ] **Step 2: Write failing exhaustive enum-label tests**

Assert exact expected maps for every entry of `WorkType`, `PropagationMethod`, `IrrigationAmount`, `IrrigationMethod`, `FertilizerMaterialCategory`, `FertilizingMethod`, `PesticideCategory`, `WeedingMethod`, `CropUsePartCategory`, and `HarvestSource`. Also cover unit enums used in prompts.

```kotlin
@Test
fun `every work type has a coaching-only label`() {
    assertThat(WorkType.entries.associateWith { it.toCoachingText() }).containsExactlyEntriesOf(
        mapOf(
            WorkType.PLANTING to "심기",
            WorkType.WATERING to "물 주기",
            WorkType.FERTILIZING to "거름 주기",
            WorkType.PEST_CONTROL to "병이나 벌레 관리",
            WorkType.WEEDING to "풀 뽑기",
            WorkType.PRUNING to "가지 정리",
            WorkType.HARVEST to "수확",
            WorkType.ETC to "기타 작업",
        ),
    )
}

@Test
fun `coaching labels never fall back to raw enum names or domain labels`() {
    WorkType.entries.forEach { value ->
        assertThat(value.toCoachingText()).isNotEqualTo(value.name).isNotEqualTo(value.label)
    }
}
```

- [ ] **Step 3: Run the new tests and verify RED**

Workdir: `backend`

```bash
./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*CoachingPromptLabelsTest'
```

Expected: test compilation fails because the policy and extensions do not exist.

- [ ] **Step 4: Implement the minimal common policy**

Use one policy object with only shared instructions and a boolean violation result:

```kotlin
object CoachingTextPolicy {
    val promptInstructions: String = """
        농부에게 보여 줄 summary와 text는 친근한 존댓말로 작성한다.
        영어 알파벳, 내부 enum, 영어 필드명과 영어 단위를 쓰지 않는다.
        관수는 물 주기, 시비는 거름 주기처럼 일상에서 쓰는 말로 풀어 쓴다.
        공식 기술 문서의 어려운 표현을 그대로 복사하지 말고 뜻을 쉬운 말로 설명한다.
        농약 때문에 생긴 피해는 약해라고 줄이지 말고 약 때문에 생긴 피해라고 쓴다.
    """.trimIndent()

    fun hasDisallowedLanguage(text: String): Boolean =
        ENGLISH_LETTER.containsMatchIn(text) || DISALLOWED_TERMS.any(text::contains)

    private val ENGLISH_LETTER = Regex("[A-Za-z]")
    private val DISALLOWED_TERMS = setOf(
        "관수", "시비", "방제", "병해충", "생육", "정식", "파종", "제초", "전정",
        "과습", "배수", "추대", "하엽", "토양", "수분", "살포", "살균제", "유기질",
    )
}
```

Implement each typed label with exhaustive `when`. Do not use `name`, domain `label`, reflection fallback, or a generic string replacement map. Examples:

```kotlin
internal fun IrrigationMethod.toCoachingText(): String = when (this) {
    IrrigationMethod.DRIP -> "호스로 조금씩 물을 줌"
    IrrigationMethod.SPRINKLER -> "물을 뿌리는 장치로 줌"
    IrrigationMethod.SPRAYING -> "물을 넓게 뿌려 줌"
    IrrigationMethod.MANUAL -> "손으로 물을 줌"
}

internal fun FertilizingMethod.toCoachingText(): String = when (this) {
    FertilizingMethod.SOIL -> "흙에 거름을 줌"
    FertilizingMethod.FOLIAR -> "잎에 거름물을 뿌림"
}

internal fun PesticideCategory.toCoachingText(): String = when (this) {
    PesticideCategory.FUNGICIDE -> "병을 막는 약"
    PesticideCategory.INSECTICIDE -> "벌레를 막는 약"
    PesticideCategory.HERBICIDE -> "풀을 없애는 약"
    PesticideCategory.ACARICIDE -> "응애를 막는 약"
    PesticideCategory.BIOPESTICIDE -> "생물에서 얻은 재료로 만든 약"
    PesticideCategory.OTHER -> "기타 약"
}
```

- [ ] **Step 5: Run the common tests and verify GREEN**

```bash
./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*CoachingPromptLabelsTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```text
feat(coaching): 사용자 코칭 쉬운 말 정책 추가

Constraint: 검색 질의와 API enum은 기존 기술 용어 계약을 유지
Rejected: 외부 용어 사전과 형태소 분석 | 확인된 노출 문제보다 범위가 큼
Confidence: high
Scope-risk: narrow
Directive: 새 금지어는 실제 사용자 노출 회귀 테스트와 함께 추가할 것
Tested: CoachingTextPolicyTest, CoachingPromptLabelsTest
```

---

### Task 2: Make RecordFeedback input and output obey the policy

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackContextAssembler.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessor.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackContextAssemblerTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/generation/RecordFeedbackGenerationServiceTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/recordfeedback/lifecycle/RecordFeedbackGenerationProcessorTest.kt`

**Interfaces:**
- Changes constructor: `RecordFeedbackContextAssembler(..., weatherPort, clock: Clock = Clock.systemDefaultZone())`.
- Keeps: `assemble(memberId, recordId): RecordFeedbackContext` and context schema.
- Adds warning: `weather_skipped_for_historical_record`.
- Adds validator warnings: `good_point_text_language`, `next_action_<index>_text_language`.
- Keeps generation attempts at exactly two.

- [ ] **Step 1: Write failing historical-weather tests**

Freeze the clock and cover both sides of the 7-day boundary:

```kotlin
private val clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)

@Test
fun `record from seven days ago still fetches live weather`() {
    stubOwnedRecord(WorkType.WATERING, workedAt = LocalDateTime.of(2026, 7, 7, 9, 0))
    stubSharedContext()

    val context = assembler.assemble(memberId, recordId)

    assertThat(context.weather).isNotNull()
    verify(weatherPort).fetch(record.farm.latitude!!, record.farm.longitude!!, 7)
}

@Test
fun `older and future records skip live weather`() {
    listOf(
        LocalDateTime.of(2026, 7, 6, 9, 0),
        LocalDateTime.of(2026, 7, 15, 9, 0),
    ).forEach { workedAt ->
        reset(weatherPort)
        stubOwnedRecord(WorkType.WATERING, workedAt = workedAt)
        stubSharedContextWithoutWeather()

        val context = assembler.assemble(memberId, recordId)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_skipped_for_historical_record")
        verifyNoInteractions(weatherPort)
    }
}
```

- [ ] **Step 2: Write failing prompt and validator tests**

Change the current prompt test so it proves internal raw values are removed while the record memo and official document remain unchanged:

```kotlin
assertThat(prompt.system).contains(CoachingTextPolicy.promptInstructions)
assertThat(prompt.user).contains(
    "작업 유형: 물 주기",
    "물 준 양: 보통 양",
    "물을 준 방법: 호스로 조금씩 물을 줌",
    "기온 30도",
)
assertThat(prompt.user).doesNotContain(
    "schemaVersion", "managementType", "NON_REGISTERED_FARMER",
    "irrigationAmount", "irrigationMethod", "NORMAL", "DRIP",
    "riskFlags", "HEAVY_RAIN", "source=", "crop_work_type",
)
```

Add validator coverage that only public `text` is checked:

```kotlin
@Test
fun `rejects English and jargon in public text but allows them in basis`() {
    val content = validResult().copy(
        goodPoint = validItem(basis = "DRIP 관수", text = "DRIP으로 관수한 점은 잘했어요."),
    )

    assertThat(RecordFeedbackOutputValidator.validate(content, context, documents))
        .contains("good_point_text_language")
        .doesNotContain("good_point_basis_language")
}
```

Add generation tests for first language failure then success and two language failures. The retry prompt must contain only `*_text_language`, not a matched word or raw generated sentence.

- [ ] **Step 3: Run the Record tests and verify RED**

```bash
./gradlew :application:test \
  --tests '*RecordFeedbackContextAssemblerTest' \
  --tests '*RecordFeedbackPromptBuilderTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest'
```

Expected: constructor/policy assertions fail; current prompt still exposes raw enum and validator accepts prohibited text.

- [ ] **Step 4: Implement the 0–7 day weather boundary**

Add a default `Clock` using the established repository pattern and short-circuit before coordinate/provider access:

```kotlin
private fun fetchWeather(record: FarmingRecord): Pair<RecordFeedbackLiveWeather?, List<String>> {
    val today = LocalDate.now(clock)
    val workedOn = record.workedAt.toLocalDate()
    if (workedOn.isBefore(today.minusDays(WEATHER_LIMIT_DAYS.toLong())) || workedOn.isAfter(today)) {
        return null to listOf(WEATHER_SKIPPED_FOR_HISTORICAL_RECORD)
    }
    // Existing coordinate and provider handling remains unchanged.
}
```

Use the fixed warning code `weather_skipped_for_historical_record`. Do not add a second date service or change stored record weather.

- [ ] **Step 5: Simplify RecordFeedbackPromptBuilder input**

Append `CoachingTextPolicy.promptInstructions` to the existing safety/system rules. Keep medical, evidence, tone, length and action-count rules.

Replace raw context formatting with Korean field sentences:

```kotlin
appendLine("- 농장: ${context.farm.name} (${context.farm.roadAddress})")
appendLine("- 영농 경력: ${context.member.experienceLevel ?: "미상"}년")
appendLine("- 작물: ${context.crop.name}")
appendLine("- 쓰는 부위: ${context.crop.usePartCategory.toCoachingText()}")
appendLine("- 기록 시각: ${record.workedAt}")
appendLine("- 작업 유형: ${record.workType.toCoachingText()}")
appendLine("- 작업 상세: ${formatDetail(record.detail)}")
appendLine("- 메모: ${record.memo}")
appendLine("- 사진 수: ${record.photoCount}장")
appendLine("- 기록 당시 날씨: ${record.recordedWeatherCondition}, ${record.recordedTemperatureC}도")
```

Format all typed detail variants with the Task 1 extensions and Korean units. Remove schema version, management type, context warnings, weather provider source, forecast risk flags, English unit abbreviations and query `reason` from the user prompt. Keep the actual retrieval query strings and official evidence unchanged.

Do not alter `CropUsePartCategory.recordFeedbackLabel()` because `RecordFeedbackRetrievalQueryPlanner.harvestQuery()` uses it for technical retrieval.

- [ ] **Step 6: Apply policy validation and safe diagnostics**

In `validateItem`, after blank/length/tone checks:

```kotlin
if (CoachingTextPolicy.hasDisallowedLanguage(item.text)) {
    warnings += "${prefix}_text_language"
}
```

Do not inspect `basis` or evidence IDs. Add `text_language` to `RecordFeedbackGenerationProcessor.SAFE_VALIDATION_DIAGNOSTIC`; `RecordFeedbackGenerationService` already sends fixed validator warnings to its single retry, so no production service branch is needed.

Update existing valid test content from `관수`, `토양`, `배수` prose to `물 주기`, `흙`, `물 빠짐`, while retaining technical values in internal basis and retrieved document fixtures.

- [ ] **Step 7: Run Record tests and verify GREEN**

```bash
./gradlew :application:test \
  --tests '*RecordFeedbackContextAssemblerTest' \
  --tests '*RecordFeedbackPromptBuilderTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest'
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```text
fix(coaching): 기록 코칭의 과거 날씨와 어려운 표현 차단

Constraint: 기술 검색어와 공식 문서 원문은 그대로 유지
Rejected: 생성 후 문자열 치환 | 행동 의미와 근거가 달라질 수 있음
Confidence: high
Scope-risk: moderate
Directive: 7일보다 오래된 기록에는 현재 날씨 citation을 만들지 말 것
Tested: RecordFeedback context, prompt, validator, generation, processor tests
```

---

### Task 3: Format and validate ReportFeedback without adding a new abstraction layer

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilder.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidator.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackPromptBuilderTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackOutputValidatorTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/reportfeedback/generation/ReportFeedbackGenerationServiceTest.kt`
- Modify fixture text where needed: `ReportFeedbackGenerationHandlerTest.kt`, `ReportFeedbackControllerTest.kt`

**Interfaces:**
- Keeps: `ReportFeedbackPromptBuilder.build(context, evidence)`.
- Keeps: `ReportFeedbackContext` schema and `Map<String, Any?>` snapshot contract.
- Adds warnings: `summary_text_language`, `strength_text_language`, `improvement_text_language`, `next_action_text_language`.
- Keeps empty item arrays valid and maximum generation attempts at two.

- [ ] **Step 1: Write failing prompt-format tests**

Use current and previous watering statistics containing code/label pairs and a record detail map as it appears after snapshot deserialization:

```kotlin
assertThat(prompt.user).contains(
    "대상 작업: 물 주기",
    "기록 횟수: 4회",
    "평균 작업 간격: 3.5일",
    "물을 준 방법: 호스로 조금씩 물을 줌",
    "직전 기록 횟수: 3회",
)
assertThat(prompt.user).doesNotContain(
    "WATERING", "recordCount", "averageIntervalDays", "details=",
    "DRIP", "LOW", "code=", "label=", "CategoryRef",
)
assertThat(prompt.user).contains("record:$recordId", "report:$previousReportId")
```

Run a second test with an unknown code and assert the raw value is not printed. Do not assert that the official-document section lacks technical terms; evidence text is intentionally preserved.

- [ ] **Step 2: Write failing output and retry tests**

Cover summary and every item list independently:

```kotlin
@Test
fun `rejects language violations in every public report text`() {
    val content = ReportFeedbackContent(
        summary = "WATERING 관수 흐름을 확인했어요.",
        strengths = listOf(item("DRIP 관수", "DRIP으로 관수한 점은 좋았어요.")),
        improvements = listOf(item("토양", "토양 수분을 더 확인하세요.")),
        nextActions = listOf(item("방제", "병해충을 방제하세요.")),
    )

    assertThat(ReportFeedbackOutputValidator.validate(content, context, emptyList())).contains(
        "summary_text_language",
        "strength_text_language",
        "improvement_text_language",
        "next_action_text_language",
    )
}
```

Keep a separate test proving `basis = "WATERING/관수"` is allowed when `text` is easy Korean. In generation tests, first language violation then valid content must retry once; two violations must end in `STRUCTURED_OUTPUT_INVALID`; the retry prompt must contain only fixed codes.

- [ ] **Step 3: Run the Report tests and verify RED**

```bash
./gradlew :application:test \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest'
```

Expected: formatter assertions and language warnings fail.

- [ ] **Step 4: Format current/previous statistics and record details in the existing builder**

Keep formatting private to `ReportFeedbackPromptBuilder`; do not create a base builder or general map serializer.

```kotlin
private fun formatStatistics(
    workType: WorkType,
    statistics: Map<String, Any?>,
    prefix: String = "",
): List<String> = buildList {
    statistics.number("recordCount")?.let { add("${prefix}기록 횟수: ${it.toInt()}회") }
    statistics.value("firstWorkedOn")?.let { add("${prefix}첫 작업일: $it") }
    statistics.value("lastWorkedOn")?.let { add("${prefix}마지막 작업일: $it") }
    statistics.number("averageIntervalDays")?.let { add("${prefix}평균 작업 간격: ${it}일") }
    when (workType) {
        WorkType.WATERING -> addAll(formatWateringStatistics(statistics, prefix))
        WorkType.FERTILIZING -> addAll(formatFertilizingStatistics(statistics, prefix))
        WorkType.PEST_CONTROL -> addAll(formatPestControlStatistics(statistics, prefix))
        WorkType.WEEDING -> addAll(formatWeedingStatistics(statistics, prefix))
        WorkType.HARVEST -> addAll(formatHarvestStatistics(statistics, prefix))
        WorkType.PLANTING -> addAll(formatPlantingStatistics(statistics, prefix))
        WorkType.PRUNING, WorkType.ETC -> Unit
    }
}
```

Whitelist known keys only. Convert nested `{code,label}` maps by parsing the `code` into the matching enum and then calling its exhaustive `toCoachingText()` extension. If a key/code is missing or unknown, omit that sentence; never fall back to raw code or existing label.

Format records as lines containing only record citation, date, easy task name, memo, Korean weather/temperature/photo sentence and whitelisted detail sentences. Keep record memo unchanged because it is user source material. Keep evidence title/content unchanged.

The system prompt must append `CoachingTextPolicy.promptInstructions` and explicitly say previous comparison is limited to the supplied same-work statistics.

- [ ] **Step 5: Apply policy validation and safe retry codes**

Validate `summary` directly and each item `text` using the common policy:

```kotlin
if (CoachingTextPolicy.hasDisallowedLanguage(content.summary)) {
    warnings += "summary_text_language"
}

if (CoachingTextPolicy.hasDisallowedLanguage(item.text)) {
    warnings += "${structured.section.name.lowercase()}_text_language"
}
```

Extend safe retry handling without including generated values:

```kotlin
val SAFE_ITEM_WARNING = Regex(
    "^(strength|improvement|next_action)_(basis_blank|text_blank|text_tone|text_language|evidence_refs_blank)$",
)

val SAFE_RETRY_WARNINGS = setOf(
    "summary_blank",
    "summary_text_tone",
    "summary_text_language",
    "duplicate_item",
    "structured_output_parse_failed",
)
```

Update valid report fixtures to easy prose but keep `workType == WATERING` API assertions and internal `basis` technical values.

- [ ] **Step 6: Run Report and API contract tests and verify GREEN**

```bash
./gradlew :application:test \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest' \
  --tests '*ReportFeedbackGenerationHandlerTest'

./gradlew :api:test --tests '*ReportFeedbackControllerTest'
```

Expected: PASS, including unchanged `workType: WATERING` wire value.

- [ ] **Step 7: Commit Task 3**

```text
fix(coaching): 작업별 리포트 코칭의 내부 표현 노출 차단

Constraint: 직전 비교는 동일 작업 통계 범위만 사용
Rejected: 범용 Map formatter | 현재 컨텍스트 밖의 값까지 추측하게 됨
Confidence: high
Scope-risk: moderate
Directive: 공식 문서와 검색 질의의 기술 용어는 변환하지 말 것
Tested: ReportFeedback prompt, validator, generation, handler, controller tests
```

---

### Task 4: Bind the tracked local profile to loopback by default

**Files:**
- Modify: `backend/api/src/main/resources/application-local.yml`
- Create: `backend/api/src/test/kotlin/com/chamchamcham/config/LocalServerBindingConfigurationTest.kt`

**Interfaces:**
- Produces default `server.address=127.0.0.1` under local profile.
- Allows explicit `SERVER_ADDRESS=0.0.0.0` override for physical-device testing.
- Does not alter test/dev/prod profiles.

- [ ] **Step 1: Write a failing configuration test**

Load the tracked YAML with Spring's existing `YamlPropertySourceLoader`, then resolve placeholders with only the loaded property source:

```kotlin
class LocalServerBindingConfigurationTest {
    @Test
    fun `local server defaults to loopback and permits explicit override`() {
        val local = YamlPropertySourceLoader()
            .load("local", ClassPathResource("application-local.yml"))
            .single()

        val defaults = MutablePropertySources().apply { addLast(local) }
        assertThat(PropertySourcesPropertyResolver(defaults).getProperty("server.address"))
            .isEqualTo("127.0.0.1")

        val overridden = MutablePropertySources().apply {
            addFirst(MapPropertySource("override", mapOf("SERVER_ADDRESS" to "0.0.0.0")))
            addLast(local)
        }
        assertThat(PropertySourcesPropertyResolver(overridden).getProperty("server.address"))
            .isEqualTo("0.0.0.0")
    }
}
```

- [ ] **Step 2: Run the configuration test and verify RED**

```bash
./gradlew :api:test --tests '*LocalServerBindingConfigurationTest'
```

Expected: `server.address` is absent.

- [ ] **Step 3: Add the local-only bind setting**

At the top level of `application-local.yml`:

```yaml
server:
  address: ${SERVER_ADDRESS:127.0.0.1}
```

- [ ] **Step 4: Run the configuration test and verify GREEN**

```bash
./gradlew :api:test --tests '*LocalServerBindingConfigurationTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```text
fix(local): 개발 서버의 기본 접근 범위를 로컬로 제한

Constraint: 고정 로컬 시드 계정은 public 로그인 API를 사용
Rejected: CORS만으로 제한 | 비브라우저와 LAN 접근을 막지 못함
Confidence: high
Scope-risk: narrow
Directive: 물리 기기 테스트에서만 SERVER_ADDRESS를 명시적으로 해제할 것
Tested: LocalServerBindingConfigurationTest
```

---

### Task 5: Replace the ignored local fixture with two realistic fixed cycles

**Files (workspace-only, ignored):**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedServiceTest.kt`
- Verify unchanged caller: `backend/api/src/main/kotlin/com/chamchamcham/api/dev/seed/DevRagSeedRunner.kt`
- Verify test: `backend/api/src/test/kotlin/com/chamchamcham/api/dev/seed/DevRagSeedRunnerTest.kt`

**Interfaces:**
- Keeps: `seedReportFeedbackScenario()` no-argument runner call.
- Adds dependencies: `HarvestRecordRepository`, `FarmingCycleReportProjectionService`.
- Calls `projectionService.rebuild(ReportScope(memberId, farmId, cropId))` exactly once after all 15 records and details are stored.
- Produces one 2024 completed slice and one 2025 active slice.

- [ ] **Step 1: Rewrite the ignored seed unit test first**

Stub persisted member/farm/records with fixed UUIDs because projection requires non-null IDs. Assert:

```kotlin
verify(farmingRecordRepository, times(15)).save(recordCaptor.capture())
assertThat(recordCaptor.allValues.map { it.workedAt.toLocalDate() }).containsExactly(
    LocalDate.of(2024, 4, 25), LocalDate.of(2024, 5, 10),
    LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 24),
    LocalDate.of(2024, 7, 15), LocalDate.of(2024, 8, 8),
    LocalDate.of(2024, 9, 12), LocalDate.of(2024, 10, 28),
    LocalDate.of(2025, 4, 24), LocalDate.of(2025, 5, 8),
    LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 20),
    LocalDate.of(2025, 7, 7), LocalDate.of(2025, 8, 14),
    LocalDate.of(2025, 9, 9),
)
verify(harvestRecordRepository).save(harvestCaptor.capture())
assertThat(harvestCaptor.value.isFinalHarvest).isTrue()
assertThat(harvestCaptor.value.harvestAmount).isEqualByComparingTo("82.0000")
verify(projectionService).rebuild(ReportScope(memberId, farmId, cropId))
```

Also assert detail save counts `2/4/2/2/4/1`, common work types in both cycles, all memos nonblank and absence of `로컬`, `테스트`, `샘플`. Extend the duplicate-member test so harvest repository and projection service have no interactions.

- [ ] **Step 2: Run ignored seed tests and verify RED**

```bash
./gradlew :application:test --tests '*DevRagSeedServiceTest'
./gradlew :api:test --tests '*DevRagSeedRunnerTest'
```

Expected: seed test fails because only six relative-date records exist and no harvest/projection dependency is present.

- [ ] **Step 3: Implement the fixed persona and cycles**

Use the exact persona and fixture values from the design:

```text
김도현 / 평창당귀새내기 / 영농 경력 2년 / 개인 농업경영체
솔바람 참당귀밭 / 강원특별자치도 평창군 진부면 참당귀길 12 / 330㎡
```

Keep the existing ignored login identifiers and credential constants without copying them to a tracked file. Replace `baseDate` arithmetic with fixed `LocalDateTime.of(...)` calls. Store the exact design memos and these comparison values:

```text
2024: 씨앗 1.2kg, 물 보통/DRIP, 거름 18kg/SOIL,
      병 관리 60ml + 40L, 두 번째 물 충분/DRIP, 최종 수확 82kg/6개월
2025: 씨앗 1.2kg, 물 적음/DRIP 2회, 거름 14kg/SOIL,
      병 관리 45ml + 30L, 최종 수확 없음
```

After saving the single previous `HarvestRecord(isFinalHarvest=true)`, current records and all typed detail rows, invoke projection once. Do not publish record-feedback events manually and do not create another queue/executor.

- [ ] **Step 4: Run ignored seed tests and verify GREEN**

```bash
./gradlew :application:test --tests '*DevRagSeedServiceTest'
./gradlew :api:test --tests '*DevRagSeedRunnerTest'
```

Expected: PASS.

- [ ] **Step 5: Confirm these files remain ignored and uncommitted**

```bash
git check-ignore -v \
  application/src/main/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedService.kt \
  application/src/test/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedServiceTest.kt \
  api/src/main/kotlin/com/chamchamcham/api/dev/seed/DevRagSeedRunner.kt \
  api/src/test/kotlin/com/chamchamcham/api/dev/seed/DevRagSeedRunnerTest.kt
```

Expected: every path is matched by `.gitignore`. No commit is created for Task 5.

---

### Task 6: Make the ignored HTML console show the scenario and metadata in easy Korean

**Files (workspace-only, ignored):**
- Modify: `frontend/dev-rag-test.html`

**Interfaces:**
- Keeps API payload values: `WorkType`, `due`, `category` raw enum values.
- Changes only user-facing labels in the test console.
- Prepares current final harvest at `2025-10-29T09:00`, 12도, 96킬로그램, 6개월, final=true.

- [ ] **Step 1: Add explicit display maps**

Use the approved work labels and map the complete RecordFeedback metadata sets:

```javascript
const workTypeLabels = {
  all: "전체",
  planting: "심기",
  watering: "물 주기",
  fertilizing: "거름 주기",
  pestControl: "병이나 벌레 관리",
  weeding: "풀 뽑기",
  pruning: "가지 정리",
  harvest: "수확",
  etc: "기타"
};

const dueLabels = {
  TODAY: "오늘",
  THIS_WEEK: "이번 주",
  NEXT_WEEK: "다음 주",
  NEXT_CHECK: "다음 확인 때"
};

const categoryLabels = {
  WEATHER: "날씨",
  PEST_DISEASE: "병이나 벌레",
  IRRIGATION: "물 주기",
  FERTILIZING: "거름 주기",
  PEST_CONTROL: "약 뿌리기",
  HARVEST: "수확",
  CULTIVATION: "밭 관리",
  GENERAL: "일반"
};
```

Change `renderAction` to render `${dueLabels[action?.due] || "-"} · ${categoryLabels[action?.category] || "-"}`. Keep the raw response/debug area unchanged.

- [ ] **Step 2: Update the fixed farm and final-harvest form**

Change `localReportSeed.farmName` to `솔바람 참당귀밭` without changing ignored login constants. Set:

```javascript
$("recordWorkType").value = "HARVEST";
$("recordWorkedAt").value = "2025-10-29T09:00";
$("recordWeatherCondition").value = "맑음";
$("recordWeatherTemperature").value = "12";
$("recordMemo").value = "두 번째 수확. 작년보다 굵기가 고르고 잔뿌리가 적음.";
$("harvestAmount").value = "96";
$("harvestMedicinalPart").value = "ROOT_BARK";
$("harvestSource").value = "CULTIVATED";
$("harvestGrowthPeriod").value = "6";
$("harvestGrowthPeriodUnit").value = "MONTH";
$("harvestFinal").value = "true";
```

Remove `localDateTimeValue()` if it has no remaining caller. Change record work-type option text to the same easy labels. Add a short visible instruction near the report feedback refresh controls: previous report work types should reach `READY` or `FAILED` before submitting the current final harvest.

- [ ] **Step 3: Perform static HTML/JavaScript checks**

From the repository root:

```bash
node -e 'const fs=require("fs");const html=fs.readFileSync("frontend/dev-rag-test.html","utf8");const scripts=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]);scripts.forEach(code=>new Function(code));console.log(`parsed ${scripts.length} script block(s)`);'
```

Expected: `parsed 1 script block(s)` and exit code 0.

Check the local file remains ignored:

```bash
git check-ignore -v frontend/dev-rag-test.html
```

Expected: `.gitignore` match. No commit is created for Task 6.

---

### Task 7: Run layered verification and inspect the real local flow

**Files:**
- No planned code changes; fix only failures caused by Tasks 1–6.

- [ ] **Step 1: Run all focused tracked tests**

Workdir: `backend`

```bash
./gradlew :application:test \
  --tests '*CoachingTextPolicyTest' \
  --tests '*CoachingPromptLabelsTest' \
  --tests '*RecordFeedbackPromptBuilderTest' \
  --tests '*RecordFeedbackContextAssemblerTest' \
  --tests '*RecordFeedbackOutputValidatorTest' \
  --tests '*RecordFeedbackGenerationServiceTest' \
  --tests '*RecordFeedbackGenerationProcessorTest' \
  --tests '*ReportFeedbackPromptBuilderTest' \
  --tests '*ReportFeedbackOutputValidatorTest' \
  --tests '*ReportFeedbackGenerationServiceTest' \
  --tests '*ReportFeedbackGenerationHandlerTest'

./gradlew :api:test \
  --tests '*LocalServerBindingConfigurationTest' \
  --tests '*RecordFeedbackControllerTest' \
  --tests '*ReportFeedbackControllerTest'
```

Expected: PASS.

- [ ] **Step 2: Run ignored local fixture tests**

```bash
./gradlew :application:test --tests '*DevRagSeedServiceTest'
./gradlew :api:test --tests '*DevRagSeedRunnerTest'
```

Expected: PASS in this workspace only. Report these separately from clean-checkout evidence.

- [ ] **Step 3: Run the whole affected backend suite**

```bash
./gradlew --no-parallel :domain:test :application:test :api:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Recreate local data and verify runtime behavior**

Start the backend with local profile and confirm the listener is loopback-only unless `SERVER_ADDRESS` is explicitly set. Then use the static HTML console to verify:

1. 2024 COMPLETED report and 2025 ACTIVE report exist.
2. Previous work-type ReportFeedback rows start first and settle independently.
3. Record/Report user prose contains no English letters or initial banned terms.
4. Report work tabs and Record next-action metadata show easy Korean labels.
5. Submitting the prepared 2025 final harvest creates current ReportFeedback and harvest RecordFeedback asynchronously.
6. The 2025 historical harvest RecordFeedback has no current/forecast weather citation.
7. Current ReportFeedback compares only the supplied previous same-work statistics.

If external OpenClaw/Ollama/pgvector services are unavailable, report runtime generation as skipped with the exact dependency blocker; do not weaken validators to make the run pass.

- [ ] **Step 5: Inspect diff, commit history and ignored boundaries**

```bash
git diff --check
git status --short
git log --oneline -6
```

Expected:

- tracked implementation and tests are committed in Tasks 1–4;
- the plan commit remains separate;
- ignored seed/HTML changes do not appear in `git status` and were not force-added;
- `.claude/` remains the only unrelated untracked path;
- no secret or fixed local credential appears in tracked diffs or commit bodies.

---

## Completion Evidence

- Exact focused and full Gradle commands with pass/fail results.
- Commit hashes for the plan and each tracked implementation task.
- Tracked changed-file list separated from ignored workspace-only changed files.
- Static HTML parse result and, if dependencies are available, manual local report/feedback results.
- Explicit skipped runtime checks and remaining risks.
- Exact token usage when the active surface provides it; otherwise state that it is unavailable.
