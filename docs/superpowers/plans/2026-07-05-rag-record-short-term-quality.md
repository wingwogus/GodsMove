# 영농일지 기록 피드백 RAG 단기 품질 개선 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 영농일지 기록 피드백 RAG 파이프라인의 검색 품질 게이트를 살리고, 메모 반영·환각 인용 차단·프롬프트 컨텍스트 보강으로 코칭 실용성을 높인다.

**Architecture:** `application/coaching/rag/record`의 서비스·플래너·프롬프트빌더와 `rag/common`의 검증기를 수정하고, 검증 실패 정화기(sanitizer)를 common에 신설한다. 시드 측(로컬 전용)은 벡터스토어 메타데이터 계약(`cropName`/`page`/`publisher`/`year`)을 채우도록 보강한다.

**Tech Stack:** Kotlin, Spring Boot 3.x, Spring AI (ChatClient/VectorStore/pgvector), JUnit 5 + AssertJ

**스펙:** `docs/superpowers/specs/2026-07-04-rag-record-short-term-quality-design.md`

## Global Constraints

- 커밋 메시지는 Conventional Commits 한국어 제목: `type(scope): 제목`. 본문 끝에 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 추가.
- 도메인 용어 `member` 유지 — `user`/`userId` 네이밍 금지.
- 새 의존성 추가 금지. `application` 모듈은 `api`에 의존 금지.
- **로컬 전용 경로는 절대 커밋하지 않는다** (`.gitignore` 등록됨): `backend/api/src/{main,test}/kotlin/com/chamchamcham/api/dev/`, `backend/application/src/{main,test}/kotlin/com/chamchamcham/application/coaching/rag/seed/`, `/data/`, `/outputs/`. Task 9는 커밋 단계가 없다.
- 커밋되는 코드·테스트는 `com.chamchamcham.application.coaching.rag.seed` 패키지를 import하지 않는다.
- 테스트 실행은 `backend/` 디렉토리에서 `./gradlew` 명령으로.
- 모든 테스트 클래스는 기존 패턴을 따른다: JUnit5 `@Test` + AssertJ `assertThat`, 픽스처는 `coaching/rag/*.json`을 ObjectMapper로 로드.

---

### Task 1: 검색 유사도 임계값 적용

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt` (retrieveDocuments, 97-112행 부근)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

**Interfaces:**
- Consumes: `RagProperties.Retrieval.lowSimilarityThreshold: Double` (기본 0.55, 이미 존재)
- Produces: 모든 `SearchRequest`에 `similarityThreshold` 설정 — 이후 태스크의 검색 동작 전제

- [ ] **Step 1: 실패하는 테스트 작성**

`TodayRecordFeedbackServiceTest.kt`의 `generate retrieves official documents with planned queries and audits structured response` 테스트의 `allSatisfy` 블록에 임계값 검증을 추가:

```kotlin
assertThat(vectorStore.requests).allSatisfy {
    assertThat(it.filterExpression?.toString().orEmpty())
        .contains("sourceType")
        .contains("TECH_DOCUMENT")
    assertThat(it.similarityThreshold).isEqualTo(0.55)
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest" `
Expected: FAIL — `similarityThreshold`가 0.0이라 0.55 기대와 불일치

- [ ] **Step 3: 최소 구현**

`TodayRecordFeedbackService.retrieveDocuments()`의 SearchRequest 빌더에 한 줄 추가:

```kotlin
vectorStore.similaritySearch(
    SearchRequest.builder()
        .query(query.query)
        .topK(perQueryTopK)
        .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
        .filterExpression("sourceType == '${RagSourceType.TECH_DOCUMENT.name}'")
        .build()
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "fix(rag): 검색 유사도 임계값 적용

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: 근거 이중 주입 제거 (RetrievalAugmentationAdvisor 삭제)

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt` (generate, 59-76행 부근)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

**Interfaces:**
- Consumes: `FakeRequestSpec` (기존 테스트 페이크)
- Produces: `chatClient.prompt().system(...).user(...).call()` 호출 형태 — Task 8의 재시도 래핑 대상

- [ ] **Step 1: 실패하는 테스트 작성**

`FakeRequestSpec`에 advisor 호출 카운터를 추가하고 새 테스트를 작성:

```kotlin
// FakeRequestSpec 클래스 내부: 기존 3개 advisors 오버라이드를 아래로 교체
var advisorUseCount = 0

override fun advisors(advisorSpecConsumer: Consumer<ChatClient.AdvisorSpec>): ChatClient.ChatClientRequestSpec {
    advisorUseCount += 1
    return this
}

override fun advisors(vararg advisors: Advisor): ChatClient.ChatClientRequestSpec {
    advisorUseCount += advisors.size
    return this
}

override fun advisors(advisors: List<Advisor>): ChatClient.ChatClientRequestSpec {
    advisorUseCount += advisors.size
    return this
}
```

```kotlin
@Test
fun `generate does not attach retrieval advisor`() {
    val chatClient = FakeChatClient(structuredResult("doc-1"))
    service(vectorStore = FakeVectorStore(listOf(officialDocument("doc-1"))), chatClient = chatClient)
        .generate(readFixture("today-record-feedback-watering.json"), topK = 2)

    assertThat(chatClient.requestSpec.advisorUseCount).isZero()
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: FAIL — advisorUseCount == 1

- [ ] **Step 3: 최소 구현**

`TodayRecordFeedbackService.generate()`에서 advisor 생성·전달 제거:

```kotlin
val evidence = documents.map { it.toRecordFeedbackEvidence() }
val prompt = promptBuilder.build(context, queries, evidence)

val result = try {
    chatClient.prompt()
        .system(prompt.system)
        .user(prompt.user)
        .call()
        .entity(CoachingStructuredResult::class.java)
} catch (exception: BusinessException) {
    throw exception
} catch (_: RuntimeException) {
    throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
}
```

사용하지 않게 된 import 제거: `RetrievalAugmentationAdvisor`, `DocumentRetriever`.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: PASS (전체 클래스)

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "refactor(rag): 근거 이중 주입 제거

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: 메모 기반 검색 쿼리 추가 + 쿼리 상한 6

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt`

**Interfaces:**
- Consumes: `TodayRecordFeedbackContext.targetRecord.memo: String`
- Produces: reason `"memo_text"`인 `RecordFeedbackRetrievalQuery`가 인덱스 1 위치에 삽입됨. `plan()` 반환 상한 6개.

- [ ] **Step 1: 실패하는 테스트 작성**

`RecordFeedbackRetrievalQueryPlannerTest.kt`에 추가 (픽스처 로드에 이미 objectMapper 헬퍼 존재):

```kotlin
@Test
fun `memo text creates second priority query and keeps forecast query`() {
    val queries = planner.plan(readFixture("today-record-feedback-watering.json"))

    assertThat(queries[1].reason).isEqualTo("memo_text")
    assertThat(queries[1].query).isEqualTo("참당귀 오전 흙 표면이 말라 보여 점적 관수함.")
    assertThat(queries.map { it.query }).contains("참당귀 강우 예보 배수 과습 병해충")
}

@Test
fun `blank memo does not create memo query`() {
    val base = readFixture("today-record-feedback-watering.json")
    val context = base.copy(targetRecord = base.targetRecord.copy(memo = "  "))

    assertThat(planner.plan(context).map { it.reason }).doesNotContain("memo_text")
}

@Test
fun `long memo is truncated to 120 chars`() {
    val base = readFixture("today-record-feedback-watering.json")
    val context = base.copy(targetRecord = base.targetRecord.copy(memo = "가".repeat(200)))

    val memoQuery = planner.plan(context).first { it.reason == "memo_text" }
    assertThat(memoQuery.query).isEqualTo("참당귀 " + "가".repeat(120))
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"`
Expected: FAIL — memo_text 쿼리 부재

- [ ] **Step 3: 최소 구현**

`plan()`에서 crop_work_type 쿼리 직후 메모 쿼리를 삽입하고 상한을 6으로:

```kotlin
fun plan(context: TodayRecordFeedbackContext): List<RecordFeedbackRetrievalQuery> {
    val cropName = context.crop.name.trim()
    val workTypeLabel = context.targetRecord.workType.label
    val queries = mutableListOf<RecordFeedbackRetrievalQuery>()

    queries += RecordFeedbackRetrievalQuery(
        query = "$cropName $workTypeLabel 재배 관리 약용작물",
        reason = "crop_work_type"
    )

    memoQuery(context)?.let { queries += it }

    context.cropCycle?.let { cycle ->
        queries += RecordFeedbackRetrievalQuery(
            query = "$cropName ${cycle.daysAfterPlanting}일차 생육 관리",
            reason = "days_after_planting"
        )
    }

    weatherRiskQuery(context)?.let { queries += it }
    forecastWeatherRiskQuery(context)?.let { queries += it }
    pestControlQuery(context)?.let { queries += it }
    harvestQuery(context)?.let { queries += it }

    return queries
        .distinctBy { it.query }
        .take(6)
}

private fun memoQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
    val memo = context.targetRecord.memo.trim()
    if (memo.isBlank()) {
        return null
    }
    return RecordFeedbackRetrievalQuery(
        query = "${context.crop.name.trim()} ${memo.take(120)}",
        reason = "memo_text"
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"`
Expected: PASS (기존 4개 + 신규 3개 전부)

- [ ] **Step 5: 서비스 테스트 회귀 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.*"`
Expected: PASS — 서비스 테스트의 쿼리 개수 가정이 깨지지 않는지 확인

- [ ] **Step 6: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt
git commit -m "feat(rag): 메모 기반 검색 쿼리 추가

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: 검증기 UNKNOWN 허점 봉합 + 감사 실패 정화(sanitize)

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredOutputValidator.kt` (21-27행)
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizer.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt` (생성자 + generate)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredOutputValidatorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizerTest.kt` (신규)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

**Interfaces:**
- Produces: `CoachingStructuredResultSanitizer.sanitize(result: CoachingStructuredResult, allowedCitationIds: Set<String>): CoachingStructuredResult` — 변경 없으면 동일 인스턴스(`===`) 반환. 상수 `CoachingStructuredResultSanitizer.SANITIZED_LIMITATION = "일부 조언이 근거 검증을 통과하지 못해 제외되었습니다."`
- Produces: `TodayRecordFeedbackService` 생성자에 `sanitizer: CoachingStructuredResultSanitizer` 파라미터 추가 (outputValidator 다음 위치). audit 경고 `"sanitized_output"`.

- [ ] **Step 1: 검증기 실패 테스트 작성**

`CoachingStructuredOutputValidatorTest.kt`에 추가:

```kotlin
@Test
fun `recommendation without citation fails audit even for unknown risk`() {
    val result = CoachingStructuredResult(
        summary = "요약",
        riskLevel = CoachingRiskLevel.UNKNOWN,
        confidence = 0.2,
        observations = emptyList(),
        diagnosis = "진단",
        recommendations = listOf(
            CoachingRecommendation(CoachingPriority.LOW, "무근거 조언", "이유 없음", null, emptyList())
        ),
        nextActions = emptyList(),
        followUpQuestions = emptyList(),
        citations = emptyList()
    )

    val audit = validator.validate(result, emptySet())

    assertThat(audit.status).isEqualTo(RagAuditStatus.FAIL)
    assertThat(audit.warnings).contains("recommendation_without_citation:무근거 조언")
}
```

- [ ] **Step 2: 검증기 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.common.CoachingStructuredOutputValidatorTest"`
Expected: FAIL — UNKNOWN이면 경고가 생성되지 않음

- [ ] **Step 3: 검증기 수정**

`CoachingStructuredOutputValidator.kt` 21-27행에서 UNKNOWN 예외 조건 제거:

```kotlin
result.recommendations
    .filter { it.citationIds.isEmpty() }
    .forEach { warnings += "recommendation_without_citation:${it.action}" }

result.nextActions
    .filter { it.citationIds.isEmpty() }
    .forEach { warnings += "next_action_without_citation:${it.action}" }
```

`CoachingRiskLevel` import가 불필요해지면 제거.

- [ ] **Step 4: 검증기 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.common.CoachingStructuredOutputValidatorTest"`
Expected: PASS — 기존 `unknown risk with limitations and no citations can pass audit`는 recommendations가 비어 있어 여전히 PASS

- [ ] **Step 5: 정화기 실패 테스트 작성 (신규 파일)**

`backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizerTest.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoachingStructuredResultSanitizerTest {
    private val sanitizer = CoachingStructuredResultSanitizer()

    @Test
    fun `removes uncited recommendations and next actions`() {
        val result = baseResult().copy(
            recommendations = listOf(
                recommendation("근거 있는 조언", listOf("doc-1")),
                recommendation("무근거 조언", emptyList())
            ),
            nextActions = listOf(
                nextAction("근거 있는 행동", listOf("doc-1")),
                nextAction("무근거 행동", emptyList())
            )
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized.recommendations.map { it.action }).containsExactly("근거 있는 조언")
        assertThat(sanitized.nextActions.map { it.action }).containsExactly("근거 있는 행동")
        assertThat(sanitized.limitations).contains(CoachingStructuredResultSanitizer.SANITIZED_LIMITATION)
    }

    @Test
    fun `strips unknown citation ids and drops items left without citations`() {
        val result = baseResult().copy(
            observations = listOf(CoachingObservation("관찰", "내용", listOf("doc-1", "ghost"))),
            recommendations = listOf(
                recommendation("혼합 인용 조언", listOf("doc-1", "ghost")),
                recommendation("환각 인용 조언", listOf("ghost"))
            ),
            citations = listOf(
                CoachingCitationRef("doc-1", "실제 문서", RagSourceType.TECH_DOCUMENT),
                CoachingCitationRef("ghost", "환각 문서", RagSourceType.TECH_DOCUMENT)
            )
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized.observations.single().citationIds).containsExactly("doc-1")
        assertThat(sanitized.recommendations.map { it.action }).containsExactly("혼합 인용 조언")
        assertThat(sanitized.recommendations.single().citationIds).containsExactly("doc-1")
        assertThat(sanitized.citations.map { it.chunkId }).containsExactly("doc-1")
    }

    @Test
    fun `returns same instance when nothing to sanitize`() {
        val result = baseResult().copy(
            recommendations = listOf(recommendation("근거 있는 조언", listOf("doc-1")))
        )

        val sanitized = sanitizer.sanitize(result, setOf("doc-1"))

        assertThat(sanitized).isSameAs(result)
    }

    private fun baseResult(): CoachingStructuredResult {
        return CoachingStructuredResult(
            summary = "요약",
            riskLevel = CoachingRiskLevel.MEDIUM,
            confidence = 0.7,
            observations = emptyList(),
            diagnosis = "진단",
            recommendations = emptyList(),
            nextActions = emptyList(),
            followUpQuestions = emptyList(),
            citations = emptyList()
        )
    }

    private fun recommendation(action: String, citationIds: List<String>): CoachingRecommendation {
        return CoachingRecommendation(CoachingPriority.MEDIUM, action, "이유", null, citationIds)
    }

    private fun nextAction(action: String, citationIds: List<String>): CoachingNextAction {
        return CoachingNextAction(CoachingActionDue.TODAY, action, citationIds)
    }
}
```

- [ ] **Step 6: 정화기 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.common.CoachingStructuredResultSanitizerTest"`
Expected: FAIL — 컴파일 오류 (클래스 없음)

- [ ] **Step 7: 정화기 구현 (신규 파일)**

`backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizer.kt`:

```kotlin
package com.chamchamcham.application.coaching.rag.common

import org.springframework.stereotype.Component

@Component
class CoachingStructuredResultSanitizer {
    fun sanitize(
        result: CoachingStructuredResult,
        allowedCitationIds: Set<String>
    ): CoachingStructuredResult {
        val observations = result.observations.map {
            it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains))
        }
        val recommendations = result.recommendations
            .map { it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains)) }
            .filter { it.citationIds.isNotEmpty() }
        val nextActions = result.nextActions
            .map { it.copy(citationIds = it.citationIds.filter(allowedCitationIds::contains)) }
            .filter { it.citationIds.isNotEmpty() }
        val citations = result.citations.filter { it.chunkId in allowedCitationIds }

        val unchanged = observations == result.observations &&
            recommendations == result.recommendations &&
            nextActions == result.nextActions &&
            citations == result.citations
        if (unchanged) {
            return result
        }

        return result.copy(
            observations = observations,
            recommendations = recommendations,
            nextActions = nextActions,
            citations = citations,
            limitations = result.limitations + SANITIZED_LIMITATION
        )
    }

    companion object {
        const val SANITIZED_LIMITATION = "일부 조언이 근거 검증을 통과하지 못해 제외되었습니다."
    }
}
```

- [ ] **Step 8: 정화기 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.common.CoachingStructuredResultSanitizerTest"`
Expected: PASS

- [ ] **Step 9: 서비스 통합 실패 테스트 작성**

`TodayRecordFeedbackServiceTest.kt`에 헬퍼와 테스트 추가:

```kotlin
private fun structuredResultWithUncitedRecommendation(citationId: String): CoachingStructuredResult {
    return structuredResult(citationId).copy(
        recommendations = listOf(
            CoachingRecommendation(CoachingPriority.MEDIUM, "다음 관수 전 토양 수분 확인", "건조 조건", null, listOf(citationId)),
            CoachingRecommendation(CoachingPriority.LOW, "무근거 조언", "이유 없음", null, emptyList())
        )
    )
}

@Test
fun `generate strips uncited recommendations and downgrades audit to warn`() {
    val chatClient = FakeChatClient(structuredResultWithUncitedRecommendation("doc-1"))
    val result = service(vectorStore = FakeVectorStore(listOf(officialDocument("doc-1"))), chatClient = chatClient)
        .generate(readFixture("today-record-feedback-watering.json"), topK = 2)

    assertThat(result.result.recommendations.map { it.action })
        .containsExactly("다음 관수 전 토양 수분 확인")
    assertThat(result.audit.status).isEqualTo(RagAuditStatus.WARN)
    assertThat(result.audit.warnings).contains("sanitized_output")
    assertThat(result.result.limitations)
        .contains(CoachingStructuredResultSanitizer.SANITIZED_LIMITATION)
}
```

서비스 헬퍼 `service(...)`의 생성자 호출에 `sanitizer = CoachingStructuredResultSanitizer()` 추가 (Step 11에서 생성자가 바뀌면 함께 컴파일됨). import에 `CoachingStructuredResultSanitizer` 추가.

- [ ] **Step 10: 서비스 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: FAIL — 컴파일 오류(sanitizer 파라미터 없음) 또는 audit FAIL 그대로 반환

- [ ] **Step 11: 서비스 통합 구현**

`TodayRecordFeedbackService.kt` 생성자에 sanitizer 추가:

```kotlin
@Service
class TodayRecordFeedbackService(
    private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val contextValidator: TodayRecordFeedbackContextValidator,
    private val queryPlanner: RecordFeedbackRetrievalQueryPlanner,
    private val promptBuilder: RecordFeedbackPromptBuilder,
    private val outputValidator: CoachingStructuredOutputValidator,
    private val sanitizer: CoachingStructuredResultSanitizer,
    private val ragProperties: RagProperties
) {
```

`generate()`의 audit 처리부 교체:

```kotlin
val allowedCitationIds = documents.map { it.id }.toSet() + context.recordCitationId()
val audit = outputValidator.validate(result, allowedCitationIds)
val (finalResult, finalAudit) = resolveAuditedResult(result, audit, allowedCitationIds)

return TodayRecordFeedbackResult(
    result = finalResult,
    audit = finalAudit,
    model = modelInfo(),
    contextWarnings = validation.warnings
)
```

새 private 메서드:

```kotlin
private fun resolveAuditedResult(
    result: CoachingStructuredResult,
    audit: RagAuditResult,
    allowedCitationIds: Set<String>
): Pair<CoachingStructuredResult, RagAuditResult> {
    if (audit.status != RagAuditStatus.FAIL) {
        return result to audit
    }
    val sanitized = sanitizer.sanitize(result, allowedCitationIds)
    if (sanitized === result) {
        return result to audit
    }
    val reAudit = outputValidator.validate(sanitized, allowedCitationIds)
    val status = if (reAudit.status == RagAuditStatus.PASS) RagAuditStatus.WARN else reAudit.status
    return sanitized to reAudit.copy(
        status = status,
        warnings = reAudit.warnings + "sanitized_output"
    )
}
```

import 추가: `CoachingStructuredResultSanitizer`, `RagAuditResult`(이미 있음), `RagAuditStatus`(이미 있음).

- [ ] **Step 12: 전체 관련 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.*"`
Expected: PASS

- [ ] **Step 13: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredOutputValidator.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizer.kt backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredOutputValidatorTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/common/CoachingStructuredResultSanitizerTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "fix(rag): 무인용 권고 검증 및 감사 실패 정화 추가

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: 작물 필터 적용 (검색 측, 메타데이터 계약)

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt` (retrieveDocuments)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

**Interfaces:**
- Consumes: 메타데이터 계약 — TECH_DOCUMENT 청크의 `cropName` 키 (단일 작물명 또는 `GENERAL`). 색인 측 구현은 Task 9(로컬 전용).
- Produces: filterExpression `sourceType == 'TECH_DOCUMENT' && cropName in ['<작물명>','GENERAL']`

- [ ] **Step 1: 실패하는 테스트 작성**

Task 1에서 수정한 `allSatisfy` 블록을 다시 확장:

```kotlin
assertThat(vectorStore.requests).allSatisfy {
    assertThat(it.filterExpression?.toString().orEmpty())
        .contains("sourceType")
        .contains("TECH_DOCUMENT")
        .contains("cropName")
        .contains("참당귀")
        .contains("GENERAL")
    assertThat(it.similarityThreshold).isEqualTo(0.55)
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: FAIL — filterExpression에 cropName 없음

- [ ] **Step 3: 최소 구현**

`generate()`에서 retrieveDocuments 호출에 작물명 전달, filterExpression 확장:

```kotlin
val documents = retrieveDocuments(queries, perQueryTopK, context.crop.name.trim())
```

```kotlin
private fun retrieveDocuments(
    queries: List<RecordFeedbackRetrievalQuery>,
    perQueryTopK: Int,
    cropName: String
): List<Document> {
    val safeCropName = cropName.replace("'", "")
    return queries
        .flatMap { query ->
            vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(query.query)
                    .topK(perQueryTopK)
                    .similarityThreshold(ragProperties.retrieval.lowSimilarityThreshold)
                    .filterExpression(
                        "sourceType == '${RagSourceType.TECH_DOCUMENT.name}' && " +
                            "cropName in ['$safeCropName', '$GENERAL_CROP_NAME']"
                    )
                    .build()
            )
        }
        .distinctBy { it.id }
}
```

`companion object`에 상수 추가:

```kotlin
companion object {
    const val GENERAL_CROP_NAME = "GENERAL"
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "feat(rag): 작물 필터 및 근거 메타데이터 계약 적용

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: 프롬프트 누락 컨텍스트 반영 (경력·경영형태·최저기온·마지막 작업일)

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt` (formatContext, formatForecast)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt`

**Interfaces:**
- Consumes: `RecordFeedbackMemberContext.experienceLevel/managementType`, `RecordFeedbackRecordDayWeather.minTemperatureC`, `RecordFeedbackForecastDayWeather.minTemperatureC`, `RecordFeedbackWorkTypeStatsContext.lastWorkedOnByType` (모두 기존 필드)
- Produces: user 프롬프트에 신규 라인 — 아래 형식 그대로

- [ ] **Step 1: 실패하는 테스트 작성**

`prompt includes target record weather stats recent records and evidence` 테스트에 추가:

```kotlin
assertThat(prompt.user).contains("영농 경력: 1")
assertThat(prompt.user).contains("경영 형태: NON_REGISTERED_FARMER")
assertThat(prompt.user).contains("최저 19.9C")
assertThat(prompt.user).contains("최저 21.4C")
assertThat(prompt.user).contains("유형별 마지막 작업일:")
assertThat(prompt.user).contains("WATERING=2026-06-30")
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackPromptBuilderTest"`
Expected: FAIL

- [ ] **Step 3: 최소 구현**

`formatContext()` — 농장 라인 다음에 회원 라인 추가, 당일 날씨에 최저기온 추가, 작업 통계에 마지막 작업일 추가:

```kotlin
appendLine("- 농장: ${context.farm.name} (${context.farm.address})")
appendLine(
    "- 회원: 영농 경력: ${context.member.experienceLevel ?: "미상"}, " +
        "경영 형태: ${context.member.managementType ?: "미상"}"
)
```

```kotlin
appendLine(
    "- 당일 날씨: 평균 ${recordDay.avgTemperatureC ?: "미상"}C, " +
        "최고 ${recordDay.maxTemperatureC ?: "미상"}C, " +
        "최저 ${recordDay.minTemperatureC ?: "미상"}C, " +
        "강수 ${recordDay.rainfallMm ?: "미상"}mm, " +
        "습도 ${recordDay.humidityPct ?: "미상"}%"
)
```

```kotlin
appendLine("- 주기별 작업 횟수: ${formatMap(context.workTypeStats.cycleCounts)}")
appendLine("- 유형별 마지막 작업일: ${formatMap(context.workTypeStats.lastWorkedOnByType)}")
appendLine("- 최근 30일 작업 횟수: ${formatMap(context.workTypeStats.recent30DayCounts)}")
```

`formatForecast()` — 최고 다음에 최저 추가:

```kotlin
return forecast.joinToString(" | ") {
    "${it.date} 강수 ${it.rainfallMm ?: "미상"}mm, " +
        "강수확률 ${it.rainProbabilityPct ?: "미상"}%, " +
        "최고 ${it.maxTemperatureC ?: "미상"}C, " +
        "최저 ${it.minTemperatureC ?: "미상"}C, " +
        "습도 ${it.humidityPct ?: "미상"}%, " +
        "풍속 ${it.windSpeedMs ?: "미상"}m/s, " +
        "riskFlags=${it.riskFlags.joinToString(",").ifBlank { "없음" }}"
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackPromptBuilderTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilder.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackPromptBuilderTest.kt
git commit -m "fix(rag): 프롬프트 누락 컨텍스트 반영

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: 날씨 리스크 쿼리 분기 수정 (과습 우선, 건조 오판 제거)

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt` (weatherRiskQuery)
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt`

**Interfaces:**
- Produces: 과습 조건이 건조·고온보다 우선 평가되는 `weatherRiskQuery`. 건조 판단에서 recent7Days.rainfallMm가 null이면 강수량 신호 미사용.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@Test
fun `wet week takes priority over hot days`() {
    val base = readFixture("today-record-feedback-watering.json")
    val context = base.copy(
        weather = base.weather.copy(
            recent7Days = RecordFeedbackRecentWeatherSummary(
                rainfallMm = 40.0,
                hotDaysCount = 2,
                dryDaysCount = 0
            )
        )
    )

    val reasons = planner.plan(context).map { it.reason }

    assertThat(reasons).contains("rain_wet_weather")
    assertThat(reasons).doesNotContain("dry_hot_weather")
}

@Test
fun `ordinary day without recent rainfall data does not trigger dry query`() {
    val base = readFixture("today-record-feedback-watering.json")
    val context = base.copy(
        weather = base.weather.copy(
            recordDay = RecordFeedbackRecordDayWeather(
                avgTemperatureC = 22.0,
                maxTemperatureC = 26.0,
                minTemperatureC = 17.0,
                rainfallMm = 0.0,
                humidityPct = 65.0
            ),
            recent7Days = RecordFeedbackRecentWeatherSummary(
                rainfallMm = null,
                hotDaysCount = 0,
                dryDaysCount = 0
            )
        )
    )

    val reasons = planner.plan(context).map { it.reason }

    assertThat(reasons).doesNotContain("dry_hot_weather")
    assertThat(reasons).doesNotContain("rain_wet_weather")
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"`
Expected: FAIL — 두 케이스 모두 현재 로직에서는 dry_hot_weather 생성

- [ ] **Step 3: 최소 구현**

`weatherRiskQuery()` 교체:

```kotlin
private fun weatherRiskQuery(context: TodayRecordFeedbackContext): RecordFeedbackRetrievalQuery? {
    val cropName = context.crop.name.trim()
    val recent = context.weather.recent7Days
    val recordDay = context.weather.recordDay
    val rainfall = recent.rainfallMm ?: recordDay.rainfallMm
    val dryDays = recent.dryDaysCount ?: 0
    val hotDays = recent.hotDaysCount ?: 0
    val maxTemp = recordDay.maxTemperatureC ?: 0.0

    if (rainfall != null && rainfall >= 30.0) {
        return RecordFeedbackRetrievalQuery(
            query = "$cropName 강우 과습 배수 병해충",
            reason = "rain_wet_weather"
        )
    }

    val lowRecentRainfall = recent.rainfallMm != null && recent.rainfallMm <= 5.0
    if (lowRecentRainfall || dryDays >= 4 || hotDays >= 2 || maxTemp >= 30.0) {
        return RecordFeedbackRetrievalQuery(
            query = "$cropName 고온 건조 관수 병해충",
            reason = "dry_hot_weather"
        )
    }

    return null
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.RecordFeedbackRetrievalQueryPlannerTest"`
Expected: PASS — watering 픽스처(최근 강수 4.5mm)는 여전히 dry_hot_weather 생성

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlanner.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/RecordFeedbackRetrievalQueryPlannerTest.kt
git commit -m "fix(rag): 날씨 리스크 쿼리 분기 수정

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: 구조화 출력 1회 재시도 + 근거 부족 문구 개선

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt`

**Interfaces:**
- Produces: `entity()` RuntimeException 시 1회 재시도, 총 2회 실패 시 `BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)`. insufficientEvidence summary 문구 변경.

- [ ] **Step 1: 실패하는 테스트 작성**

`FakeChatClient`를 CallResponseSpec 주입형으로 확장하고 재시도용 페이크 추가:

```kotlin
private class FakeChatClient(
    callResponseSpec: ChatClient.CallResponseSpec
) : ChatClient {
    constructor(result: CoachingStructuredResult) : this(FakeCallResponseSpec(result))

    val requestSpec = FakeRequestSpec(callResponseSpec)

    override fun prompt(): ChatClient.ChatClientRequestSpec = requestSpec
    override fun prompt(content: String): ChatClient.ChatClientRequestSpec = requestSpec
    override fun prompt(prompt: Prompt): ChatClient.ChatClientRequestSpec = requestSpec
    override fun mutate(): ChatClient.Builder = error("mutate is not used")
}

private class FlakyCallResponseSpec(
    private val result: CoachingStructuredResult,
    private var remainingFailures: Int
) : ChatClient.CallResponseSpec {
    var attempts = 0

    override fun <T : Any> entity(type: Class<T>): T {
        attempts += 1
        if (remainingFailures > 0) {
            remainingFailures -= 1
            throw IllegalStateException("structured output parse failed")
        }
        return type.cast(result)
    }

    override fun <T : Any> entity(type: ParameterizedTypeReference<T>): T = error("not used")
    override fun <T : Any> entity(structuredOutputConverter: StructuredOutputConverter<T>): T = error("not used")
    override fun chatClientResponse(): ChatClientResponse = error("not used")
    override fun chatResponse(): ChatResponse = error("not used")
    override fun content(): String = error("not used")
    override fun <T : Any> responseEntity(type: Class<T>): ResponseEntity<ChatResponse, T> = error("not used")
    override fun <T : Any> responseEntity(type: ParameterizedTypeReference<T>): ResponseEntity<ChatResponse, T> = error("not used")
    override fun <T : Any> responseEntity(structuredOutputConverter: StructuredOutputConverter<T>): ResponseEntity<ChatResponse, T> = error("not used")
}
```

테스트 3개 추가:

```kotlin
@Test
fun `generate retries structured output once on runtime failure`() {
    val flakySpec = FlakyCallResponseSpec(structuredResult("doc-1"), remainingFailures = 1)
    val result = service(
        vectorStore = FakeVectorStore(listOf(officialDocument("doc-1"))),
        chatClient = FakeChatClient(flakySpec)
    ).generate(readFixture("today-record-feedback-watering.json"), topK = 2)

    assertThat(flakySpec.attempts).isEqualTo(2)
    assertThat(result.audit.citations).contains("doc-1")
}

@Test
fun `generate fails when structured output fails twice`() {
    val flakySpec = FlakyCallResponseSpec(structuredResult("doc-1"), remainingFailures = 2)

    assertThatThrownBy {
        service(
            vectorStore = FakeVectorStore(listOf(officialDocument("doc-1"))),
            chatClient = FakeChatClient(flakySpec)
        ).generate(readFixture("today-record-feedback-watering.json"), topK = 2)
    }.isInstanceOfSatisfying(BusinessException::class.java) {
        assertThat(it.errorCode).isEqualTo(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
    }
    assertThat(flakySpec.attempts).isEqualTo(2)
}
```

기존 `generate returns insufficient evidence when official docs are not retrieved` 테스트에 문구 검증 추가:

```kotlin
assertThat(result.result.summary)
    .isEqualTo("아직 이 작물에 대한 참고 자료가 부족해 오늘 기록만으로는 판단하기 어려워요.")
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: FAIL — 재시도 없음(attempts 1에서 예외), 기존 문구 불일치

- [ ] **Step 3: 최소 구현**

`generate()`의 LLM 호출부를 재시도 메서드로 교체:

```kotlin
val result = callForStructuredResult(prompt)
```

```kotlin
private fun callForStructuredResult(prompt: RecordFeedbackPrompt): CoachingStructuredResult {
    repeat(MAX_STRUCTURED_OUTPUT_ATTEMPTS - 1) {
        try {
            return requestStructuredResult(prompt)
        } catch (exception: BusinessException) {
            throw exception
        } catch (_: RuntimeException) {
            // 1회 재시도
        }
    }
    return try {
        requestStructuredResult(prompt)
    } catch (exception: BusinessException) {
        throw exception
    } catch (_: RuntimeException) {
        throw BusinessException(ErrorCode.RAG_STRUCTURED_OUTPUT_INVALID)
    }
}

private fun requestStructuredResult(prompt: RecordFeedbackPrompt): CoachingStructuredResult {
    return chatClient.prompt()
        .system(prompt.system)
        .user(prompt.user)
        .call()
        .entity(CoachingStructuredResult::class.java)
}
```

`companion object`에 상수 추가:

```kotlin
const val MAX_STRUCTURED_OUTPUT_ATTEMPTS = 2
```

insufficientEvidence 문구 교체 (documents.isEmpty() 분기):

```kotlin
val result = CoachingStructuredResult.insufficientEvidence(
    "아직 이 작물에 대한 참고 자료가 부족해 오늘 기록만으로는 판단하기 어려워요."
).copy(
    riskLevel = CoachingRiskLevel.UNKNOWN,
    limitations = listOf("검색된 공식문서 근거가 없습니다.")
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.record.TodayRecordFeedbackServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackService.kt backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/record/TodayRecordFeedbackServiceTest.kt
git commit -m "fix(rag): 구조화 출력 재시도 및 근거 부족 문구 개선

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: 시드 메타데이터 보강 (로컬 전용 — **커밋 금지**)

> **주의:** 이 태스크의 모든 파일은 `.gitignore`로 추적 차단된 로컬 전용 코드다. **어떤 git 커밋도 하지 않는다.** 검증은 로컬 `./gradlew test`로만 한다.

**Files (전부 로컬 전용):**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/coaching/rag/seed/DevRagSeedServiceTest.kt`
- Modify: `data/rag/medicinal-plants/manifest.csv` (crop_names 열 추가)

**Interfaces:**
- Consumes: Task 5의 메타데이터 계약 — `cropName`(작물명 또는 `GENERAL`), `page`(Int, 1-base), `publisher`, `year`
- Produces: `seedPdfChunks()`가 계약 키를 채운 Document 생성. `PdfTextExtractor` 인터페이스는 변경하지 않음.

- [ ] **Step 1: manifest.csv에 crop_names 열 추가**

`data/rag/medicinal-plants/manifest.csv` 헤더에 `"crop_names"` 열을 추가하고, 현재 12개 문서는 전부 다작물 자료집이므로 빈 값(`""`)으로 채운다. 예:

```csv
"id","title","publisher","year","source_url","download_url","local_path","status","pages","bytes","sha256","notes","crop_names"
"rda_medicinal_crops_guide_2019","농업기술길잡이 007 약용작물","농촌진흥청","2019",...,"",""
```

(향후 단일 작물 문서 추가 시 `"참당귀"`처럼 채우면 해당 작물 필터에 걸린다.)

- [ ] **Step 2: 실패하는 테스트 작성 (페이지·manifest 메타데이터)**

`DevRagSeedServiceTest.kt`에 추가. 기존 `FixedPdfTextExtractor` 패턴과 임시 디렉토리 헬퍼를 그대로 활용하되, PDF 텍스트에 form feed(`\u000C`)로 페이지 경계를 넣고 seedRoot에 manifest.csv를 써 둔다:

```kotlin
@Test
fun `pdf chunks carry page and manifest metadata`() {
    // 임시 seed 디렉토리 구성 (기존 테스트의 seed 디렉토리 헬퍼 방식을 따른다)
    val page1 = "참당귀는 서늘한 기후를 좋아하는 약용작물로 배수가 잘 되는 토양에서 잘 자란다. 재배 전 토양 검정을 권장한다."
    val page2 = "관수는 토양 표면이 마르면 실시하고 과습하지 않게 배수로를 정비한다. 장마철에는 배수 관리가 특히 중요하다."
    val pdfText = page1 + "\u000C" + page2
    // seedRoot/manifest.csv 내용:
    // "id","title","publisher","year","source_url","download_url","local_path","status","pages","bytes","sha256","notes","crop_names"
    // "guide","참당귀 재배 길잡이","농촌진흥청","2019","","","","","","","","","참당귀"

    val service = service(
        pdfTextExtractor = FixedPdfTextExtractor(mapOf("guide.pdf" to pdfText)),
        seedDirectory = seedRoot.toString()
    )
    service.seed(seedRequestWithPdf("guide.pdf"))

    val documents = vectorStore.added  // 기존 테스트의 캡처용 페이크 사용
    assertThat(documents).isNotEmpty
    assertThat(documents.first().metadata["cropName"]).isEqualTo("참당귀")
    assertThat(documents.first().metadata["publisher"]).isEqualTo("농촌진흥청")
    assertThat(documents.first().metadata["year"]).isEqualTo(2019)
    assertThat(documents.first().metadata["page"]).isEqualTo(1)
    assertThat(documents.first().metadata["documentTitle"]).isEqualTo("참당귀 재배 길잡이")
    assertThat(documents.last().metadata["page"]).isEqualTo(2)
}

@Test
fun `pdf chunks default to general crop when manifest row is missing`() {
    val service = service(
        pdfTextExtractor = FixedPdfTextExtractor(mapOf("unknown.pdf" to "약용작물 일반 관리 지침. 토양과 배수 상태를 정기적으로 확인하고 기록한다. 이 문단은 80자를 넘도록 충분히 길게 작성한다.")),
        seedDirectory = seedRootWithoutManifest.toString()
    )
    service.seed(seedRequestWithPdf("unknown.pdf"))

    assertThat(vectorStore.added.first().metadata["cropName"]).isEqualTo("GENERAL")
    assertThat(vectorStore.added.first().metadata).doesNotContainKeys("publisher", "year")
}
```

(정확한 헬퍼 시그니처는 기존 `DevRagSeedServiceTest.kt`의 service()/seed 요청 빌더를 따른다 — 구현자가 파일을 열어 기존 패턴에 맞춰 조정한다. 단, 검증 대상 메타데이터 키·값은 위와 동일해야 한다.)

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.seed.DevRagSeedServiceTest"`
Expected: FAIL — cropName/page/publisher/year 메타데이터 없음

- [ ] **Step 4: manifest 로더 구현**

`DevRagSeedService.kt`에 추가:

```kotlin
private data class SeedManifestRow(
    val id: String,
    val title: String?,
    val publisher: String?,
    val year: Int?,
    val cropNames: List<String>
)

private fun loadManifest(): Map<String, SeedManifestRow> {
    if (seedDirectory.isBlank()) {
        return emptyMap()
    }
    val manifestPath = Path.of(seedDirectory).toAbsolutePath().normalize().resolve("manifest.csv")
    if (!Files.isRegularFile(manifestPath)) {
        return emptyMap()
    }
    val lines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8).filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        return emptyMap()
    }
    val header = parseCsvLine(lines.first())
    val indexOf = header.withIndex().associate { (index, name) -> name.trim() to index }
    val idIndex = indexOf["id"] ?: throw invalidSeedRequest("manifest.csv must have an id column")

    return lines.drop(1).associate { line ->
        val fields = parseCsvLine(line)
        fun field(name: String): String? =
            indexOf[name]?.let { fields.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }

        val id = fields.getOrNull(idIndex)?.trim()
            ?: throw invalidSeedRequest("manifest.csv row is missing id")
        id to SeedManifestRow(
            id = id,
            title = field("title"),
            publisher = field("publisher"),
            year = field("year")?.toIntOrNull(),
            cropNames = field("crop_names")?.split(';')?.map(String::trim)?.filter(String::isNotEmpty)
                ?: emptyList()
        )
    }
}

private fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i++
            }
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> {
                fields += current.toString()
                current.clear()
            }
            else -> current.append(ch)
        }
        i++
    }
    fields += current.toString()
    return fields
}
```

(제약: 필드 내 개행은 미지원 — 현재 manifest는 한 행 한 줄이다. 형식이 깨진 행은 `invalidSeedRequest`로 실패시킨다.)

- [ ] **Step 5: 페이지 인식 청킹 구현**

`chunkText()`를 페이지 인식 버전으로 교체 (`\u000C` = pdftotext 페이지 구분자, 기존 정규화에서 `\f` 제거하던 것을 페이지 분리에 사용):

```kotlin
private data class PageChunk(val page: Int, val content: String)

private fun chunkPages(
    rawText: String,
    maxChunkChars: Int = 1_200,
    overlapChars: Int = 160,
    maxChunks: Int
): List<PageChunk> {
    data class PagedParagraph(val page: Int, val text: String)

    val paragraphs = rawText.split('\u000C').flatMapIndexed { pageIndex, pageText ->
        pageText
            .replace('\u0000', ' ')
            .replace(Regex("[ \\t\\x0B\\r]+"), " ")
            .split(Regex("\\n{2,}"))
            .map { it.lines().joinToString(" ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 80 }
            .map { PagedParagraph(pageIndex + 1, it) }
    }

    val chunks = mutableListOf<PageChunk>()
    var current = StringBuilder()
    var currentPage = 1
    for (paragraph in paragraphs) {
        if (current.isEmpty()) {
            currentPage = paragraph.page
        }
        if (current.isNotEmpty() && current.length + paragraph.text.length + 1 > maxChunkChars) {
            chunks += PageChunk(currentPage, current.toString().trim())
            if (chunks.size >= maxChunks) {
                return chunks
            }
            val overlap = current.takeLast(overlapChars)
            current = StringBuilder(overlap.trim())
            currentPage = paragraph.page
        }
        if (current.isNotEmpty()) {
            current.append('\n')
        }
        current.append(paragraph.text)
    }
    if (current.isNotBlank() && chunks.size < maxChunks) {
        chunks += PageChunk(currentPage, current.toString().trim())
    }
    return chunks.take(maxChunks)
}
```

기존 `chunkText()` 호출부(비 PDF 경로가 있으면)는 `chunkPages(...).map { it.content }`로 대체하거나 그대로 두고, PDF 경로만 chunkPages를 쓴다.

- [ ] **Step 6: seedPdfChunks 메타데이터 연결**

```kotlin
private fun seedPdfChunks(pdfSeed: ExtractedPdfSeed, maxPdfChunks: Int): Int {
    val manifestRow = loadManifest()[pdfSeed.sourceId]
    val title = manifestRow?.title ?: pdfSeed.title
    val cropName = manifestRow?.cropNames?.singleOrNull() ?: "GENERAL"

    val chunks = chunkPages(pdfSeed.text, maxChunks = maxPdfChunks)
    val documents = chunks.mapIndexed { index, chunk ->
        Document(
            seedDocumentId(pdfSeed.sourceId, index),
            chunk.content,
            buildMap {
                put("sourceType", "TECH_DOCUMENT")
                put("sourceId", pdfSeed.sourceId)
                put("label", "$title ${index + 1}")
                put("documentTitle", title)
                put("cropName", cropName)
                put("page", chunk.page)
                manifestRow?.publisher?.let { put("publisher", it) }
                manifestRow?.year?.let { put("year", it) }
                put("pdfPath", pdfSeed.path)
                put("seedName", SEED_NAME)
                put("chunkIndex", index)
            }
        )
    }
    if (documents.isEmpty()) {
        return 0
    }
    vectorStore.add(documents)
    return documents.size
}
```

필요 import: `java.nio.charset.StandardCharsets` (이미 있음), `java.nio.file.Files`(이미 있음).

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd backend && ./gradlew :application:test --tests "com.chamchamcham.application.coaching.rag.seed.DevRagSeedServiceTest"`
Expected: PASS (기존 + 신규 전부)

- [ ] **Step 8: 커밋하지 않음 — 상태 확인만**

Run: `git status --short | grep -E "rag/seed|api/dev|data/"`
Expected: 빈 출력 (전부 ignore 상태). 커밋 단계 없음.

---

### Task 10: 전체 검증

**Files:** 없음 (검증만)

- [ ] **Step 1: 전체 백엔드 테스트**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — api 모듈의 `CoachingRagControllerTest`, `DevRecordFeedbackControllerTest`(로컬 전용) 포함 전부 통과. 실패 시 원인을 고치고 재실행 (특히 서비스 생성자 변경이 로컬 전용 `DevRecordFeedbackController`나 api 테스트에 영향을 줬는지 확인 — Spring 주입이라 컴파일 영향은 생성자 직접 호출 지점에만 있다).

- [ ] **Step 2: 커밋 코드의 로컬 전용 참조 없음 확인**

Run: `git grep -l "coaching.rag.seed" -- '*.kt'`
Expected: 빈 출력 (추적 중인 Kotlin 파일에서 seed 패키지 import 없음)

- [ ] **Step 3: 로컬 전용 파일 미추적 확인**

Run: `git status --short | grep -E "rag/seed|api/dev|^\?\? data|dev-rag-test"`
Expected: 빈 출력

- [ ] **Step 4: (선택, 수동) 실제 VectorStore smoke**

로컬 pgvector 기동 상태에서 재시드 후 `TodayRecordFeedbackVectorStoreSmokeTest` 실행. cropName 필터 도입으로 **기존 시드 데이터에는 cropName 메타데이터가 없어 재시드 전에는 검색 결과가 0건**이 된다 — 재시드(seedName 전체 삭제 후 재적재)가 선행돼야 함을 기억할 것.

## 리스크 메모 (구현자 참고)

- threshold 0.55는 bge-m3 기준 미실측 — smoke에서 과차단이 보이면 코드가 아닌 `application-local.yml`의 `rag.retrieval.low-similarity-threshold`로 조정한다.
- Task 5의 cropName 필터는 재시드 전 로컬 데이터에서 결과 0건을 만든다(위 Task 10 Step 4). dev 데모 전 반드시 재시드.
- `RecordFeedbackRecentWeatherSummary`/`RecordFeedbackRecordDayWeather` 생성자 시그니처는 `TodayRecordFeedbackContext.kt`에 정의된 순서(`rainfallMm, hotDaysCount, dryDaysCount` / `avgTemperatureC, maxTemperatureC, minTemperatureC, rainfallMm, humidityPct`)를 따른다.
