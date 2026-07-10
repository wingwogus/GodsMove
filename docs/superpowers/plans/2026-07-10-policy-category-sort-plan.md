# Policy Category and Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace policy support categories with the product's ten canonical categories and add cursor-safe latest-by-policy-start-date sorting without changing the default recommendation order.

**Architecture:** Keep category extraction in the application support package and define the list sort as a domain query concept. Pass the selected sort through API → application → domain query, bind it into the opaque cursor, and select one of two JPQL predicates/order clauses inside the existing repository implementation.

**Tech Stack:** Kotlin, Spring Boot 3, Spring MVC, Spring Data JPA/JPQL, Jackson cursor codec, JUnit 5, AssertJ, Mockito, Gradle

## Global Constraints

- Branch from `dev` as `fix/policy-category-sort`.
- `sort` accepts `RECOMMENDED` and `LATEST`; omission means `RECOMMENDED`.
- Latest order is `applyStartsOn desc nulls last, id asc`.
- Recommended order remains `score desc, applyEndsOn asc nulls last, id asc`.
- Category labels are exactly `지원금`, `융자·금융`, `시설·장비`, `교육`, `복지`, `인증`, `판로`, `창업`, `환경·인프라`, `기타`.
- No DB schema change, dependency addition, frontend work, score change, or missing-date backfill.
- Follow red-green-refactor: every production behavior change starts with a failing test.

---

### Task 0: Repair the inherited policy service test baseline

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt`

**Interfaces:**
- Consumes: the exact `PolicyRecommendationQueryRepository.SearchCondition` produced by the existing service.
- Produces: stable Mockito stubs without passing matcher-generated null into a Kotlin non-null parameter.

- [ ] **Step 1: Reproduce the inherited failure**

```bash
cd backend
./gradlew :application:test --tests '*PolicyRecommendationServiceTest'
```

Expected on unmodified `dev`: `any(...) must not be null` at each `findPage(any())`, followed by Mockito matcher-state failures.

- [ ] **Step 2: Replace unsafe Kotlin-interface matchers**

Use an exact data-class condition:

```kotlin
private fun recommendationSearchCondition(): PolicyRecommendationQueryRepository.SearchCondition =
    PolicyRecommendationQueryRepository.SearchCondition(
        memberId = memberId,
        source = PolicySource.NONGUP_EZ,
        sourceYear = "2026",
        benefitSummary = null,
        cursor = null,
        size = 21
    )
```

Stub `findPage(recommendationSearchCondition())` and use `verifyNoInteractions(policyRecommendationQueryRepository)` in early-rejection tests.

- [ ] **Step 3: Verify the baseline repair**

Run the Step 1 command. Expected: all 10 `PolicyRecommendationServiceTest` tests pass.

- [ ] **Step 4: Commit the isolated baseline fix**

```bash
git add backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt
git commit -m "test(policy): Kotlin 저장소 matcher로 인한 기준선 실패 제거"
```

---

### Task 1: Canonical policy benefit categories

**Files:**
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGeneratorTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyBenefitCategory.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGenerator.kt`

**Interfaces:**
- Consumes: policy support source text as `String?`.
- Produces: `PolicyBenefitCategory.fromKey(String)` and `benefitCategory(String?)` using the ten canonical enum keys and labels.

- [ ] **Step 1: Write failing category contract and mapping tests**

Replace the existing benefit-category assertions with:

```kotlin
@Test
fun `benefit categories expose canonical keys and labels`() {
    assertThat(PolicyBenefitCategory.entries.map { it.name to it.label }).containsExactly(
        "GRANT" to "지원금",
        "FINANCE" to "융자·금융",
        "FACILITY_EQUIPMENT" to "시설·장비",
        "EDUCATION" to "교육",
        "WELFARE" to "복지",
        "CERTIFICATION" to "인증",
        "MARKET" to "판로",
        "STARTUP" to "창업",
        "ENVIRONMENT_INFRA" to "환경·인프라",
        "ETC" to "기타"
    )
    assertThat(PolicyBenefitCategory.fromKey("FINANCE")).isEqualTo(PolicyBenefitCategory.FINANCE)
    assertThat(PolicyBenefitCategory.fromKey("bad")).isNull()
}

@Test
fun `benefit summary maps source text to canonical support categories`() {
    val cases = mapOf(
        "청년농 영농정착지원금 지급" to "지원금",
        "정책자금 융자 및 이자 지원" to "융자·금융",
        "농기계 장비 설치 및 개보수" to "시설·장비",
        "농업인 교육과 컨설팅" to "교육",
        "농작물재해보험 보험료 지원" to "복지",
        "GAP 인증 및 품질 검정" to "인증",
        "수출 박람회 참가와 판로 홍보" to "판로",
        "청년 농업인 창업 사업화" to "창업",
        "농업용수 환경 인프라 개선" to "환경·인프라",
        "세부 내용은 공고문 참조" to "기타"
    )

    cases.forEach { (source, expected) ->
        assertThat(generator.benefitSummary(source)).isEqualTo(expected)
    }
    assertThat(generator.benefitCategory("농업 데이터 제공")).isEqualTo(PolicyBenefitCategory.ETC)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd backend
./gradlew :application:test --tests '*PolicyCardTextGeneratorTest'
```

Expected: compilation fails because `GRANT`, `EDUCATION`, `WELFARE`, `CERTIFICATION`, `MARKET`, and `STARTUP` do not exist.

- [ ] **Step 3: Implement the ten-category enum and deterministic keywords**

Make `PolicyBenefitCategory` exactly:

```kotlin
enum class PolicyBenefitCategory(val label: String) {
    GRANT("지원금"),
    FINANCE("융자·금융"),
    FACILITY_EQUIPMENT("시설·장비"),
    EDUCATION("교육"),
    WELFARE("복지"),
    CERTIFICATION("인증"),
    MARKET("판로"),
    STARTUP("창업"),
    ENVIRONMENT_INFRA("환경·인프라"),
    ETC("기타");

    companion object {
        fun fromKey(key: String): PolicyBenefitCategory? = entries.firstOrNull { it.name == key }
    }
}
```

Change `benefitCategory` branches to:

```kotlin
return when {
    source.containsAny("직불금", "지원금", "보조금", "장려금", "수당", "바우처") -> PolicyBenefitCategory.GRANT
    source.containsAny("융자", "정책자금", "대출", "금리", "이자") -> PolicyBenefitCategory.FINANCE
    source.containsAny("시설", "장비", "농기계", "설치", "개보수") -> PolicyBenefitCategory.FACILITY_EQUIPMENT
    source.containsAny("교육", "컨설팅", "상담", "연수") -> PolicyBenefitCategory.EDUCATION
    source.containsAny("보험", "보험료", "연금", "건강", "복지") -> PolicyBenefitCategory.WELFARE
    source.containsAny("인증", "검정", "품질", "무병묘", "저탄소") -> PolicyBenefitCategory.CERTIFICATION
    source.containsAny("박람회", "수출", "판로", "홍보", "브랜드") -> PolicyBenefitCategory.MARKET
    source.containsAny("창업", "사업화", "벤처", "R&D") -> PolicyBenefitCategory.STARTUP
    source.containsAny("수질", "용수", "저수지", "가뭄", "환경", "인프라") -> PolicyBenefitCategory.ENVIRONMENT_INFRA
    else -> PolicyBenefitCategory.ETC
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL` and all `PolicyCardTextGeneratorTest` tests pass.

- [ ] **Step 5: Commit the category contract**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyBenefitCategory.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGenerator.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGeneratorTest.kt
git commit -m "fix(policy): 지원 카테고리 계약을 제품 분류와 일치"
```

---

### Task 2: Cursor-safe latest sorting in the domain query

**Files:**
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationSort.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`

**Interfaces:**
- Produces: `PolicyRecommendationSort.RECOMMENDED|LATEST`, `SearchCondition.sort`, and a cursor with nullable sort-specific fields.
- Cursor signature: `Cursor(score: BigDecimal?, applyStartsOn: LocalDate?, applyEndsOn: LocalDate?, id: UUID)`.

- [ ] **Step 1: Write failing repository tests for recommended regression and latest pagination**

Extend the persistence helper with `applyStartsOn: LocalDate? = null`, pass it to `applyDetailFields`, and add:

```kotlin
@Test
fun `find page orders latest policies by start date with null last and cursor`() {
    persistRecommendation("최근", score = "0.7000", applyStartsOn = LocalDate.of(2026, 7, 1), applyEndsOn = null)
    persistRecommendation("중간", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
    persistRecommendation("과거", score = "0.9500", applyStartsOn = LocalDate.of(2026, 5, 1), applyEndsOn = null)
    persistRecommendation("시작일 없음", score = "0.9900", applyStartsOn = null, applyEndsOn = null)
    entityManager.flush()
    entityManager.clear()

    val firstPage = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST, size = 2))
    val cursorRow = firstPage.rows.last()
    val secondPage = queryRepository.findPage(
        condition(
            sort = PolicyRecommendationSort.LATEST,
            cursor = PolicyRecommendationQueryRepository.Cursor(
                score = null,
                applyStartsOn = cursorRow.policyProgram.applyStartsOn,
                applyEndsOn = null,
                id = requireNotNull(cursorRow.id)
            )
        )
    )

    assertThat(firstPage.rows.map { it.policyProgram.title }).containsExactly("최근", "중간")
    assertThat(secondPage.rows.map { it.policyProgram.title }).containsExactly("과거", "시작일 없음")
}

@Test
fun `find page orders equal latest dates by id`() {
    persistRecommendation("동일일 A", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
    persistRecommendation("동일일 B", score = "0.9000", applyStartsOn = LocalDate.of(2026, 6, 1), applyEndsOn = null)
    entityManager.flush()
    entityManager.clear()

    val result = queryRepository.findPage(condition(sort = PolicyRecommendationSort.LATEST))

    assertThat(result.rows.map { requireNotNull(it.id) }).isSorted
}
```

Update the existing recommended cursor construction with `applyStartsOn = null`, and make `condition` accept `sort: PolicyRecommendationSort = PolicyRecommendationSort.RECOMMENDED`.

- [ ] **Step 2: Run repository tests and verify RED**

```bash
cd backend
./gradlew :domain:test --tests '*PolicyRecommendationQueryRepositoryTest'
```

Expected: compilation fails because `PolicyRecommendationSort`, `SearchCondition.sort`, and `Cursor.applyStartsOn` do not exist.

- [ ] **Step 3: Add the sort enum and query contracts**

Create:

```kotlin
package com.chamchamcham.domain.policy

enum class PolicyRecommendationSort {
    RECOMMENDED,
    LATEST;

    companion object {
        fun fromKey(key: String): PolicyRecommendationSort? = entries.firstOrNull { it.name == key }
    }
}
```

Change repository DTOs to:

```kotlin
data class SearchCondition(
    val memberId: UUID,
    val source: PolicySource,
    val sourceYear: String,
    val benefitSummary: String?,
    val sort: PolicyRecommendationSort,
    val cursor: Cursor?,
    val size: Int
)

data class Cursor(
    val score: BigDecimal?,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
```

- [ ] **Step 4: Implement sort-specific predicates and order clauses**

Keep common member/source/year/category predicates. For `RECOMMENDED`, require a non-null cursor score and retain the current score/deadline predicate. For `LATEST`, add this cursor logic:

```kotlin
if (cursor.applyStartsOn == null) {
    where += "p.applyStartsOn is null and r.id > :cursorId"
} else {
    where += """
        (
            p.applyStartsOn < :cursorApplyStartsOn
            or (p.applyStartsOn = :cursorApplyStartsOn and r.id > :cursorId)
            or p.applyStartsOn is null
        )
    """.trimIndent()
    params["cursorApplyStartsOn"] = cursor.applyStartsOn
}
params["cursorId"] = cursor.id
```

Select the order clause with:

```kotlin
val orderBy = when (condition.sort) {
    PolicyRecommendationSort.RECOMMENDED ->
        "r.score desc, coalesce(p.applyEndsOn, :maxDate) asc, r.id asc"
    PolicyRecommendationSort.LATEST ->
        "case when p.applyStartsOn is null then 1 else 0 end asc, p.applyStartsOn desc, r.id asc"
}
```

- [ ] **Step 5: Run repository tests and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`; recommended, category filter, latest, null-last, and cursor tests all pass.

- [ ] **Step 6: Commit the domain sort**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/policy backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt
git commit -m "feat(policy): 정책 시작일 최신순 조회 지원"
```

---

### Task 3: Bind sort and cursor invariants in the application service

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt`

**Interfaces:**
- Consumes: `PolicyRecommendationSort` selected by the controller.
- Produces: `listRecommendations(memberId, cursor, size, benefitCategory, sort)` and a cursor payload bound to source/year/category/sort.

- [ ] **Step 1: Write failing service tests for sort propagation and cursor binding**

Add a test that captures `SearchCondition` after calling:

```kotlin
service.listRecommendations(
    memberId = memberId,
    cursor = null,
    size = 20,
    benefitCategory = PolicyBenefitCategory.FINANCE,
    sort = PolicyRecommendationSort.LATEST
)
```

Assert:

```kotlin
assertThat(captor.value.benefitSummary).isEqualTo("융자·금융")
assertThat(captor.value.sort).isEqualTo(PolicyRecommendationSort.LATEST)
```

Add a mismatched-cursor test using:

```kotlin
PolicyRecommendationCursorPayload(
    source = PolicySource.NONGUP_EZ,
    sourceYear = "2026",
    benefitCategory = null,
    sort = PolicyRecommendationSort.RECOMMENDED,
    score = BigDecimal("80.0"),
    applyStartsOn = null,
    applyEndsOn = LocalDate.of(2026, 6, 30),
    id = recommendationId
)
```

Call the service with `sort = PolicyRecommendationSort.LATEST` and assert `BusinessException(ErrorCode.INVALID_INPUT)` before repository access.

- [ ] **Step 2: Run service tests and verify RED**

```bash
cd backend
./gradlew :application:test --tests '*PolicyRecommendationServiceTest'
```

Expected: compilation fails because service and cursor payload do not yet accept `sort` or `applyStartsOn`.

- [ ] **Step 3: Extend cursor payload and service signature**

Use:

```kotlin
data class PolicyRecommendationCursorPayload(
    val source: PolicySource,
    val sourceYear: String,
    val benefitCategory: String?,
    val sort: PolicyRecommendationSort,
    val score: BigDecimal?,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
```

Make the service signature:

```kotlin
fun listRecommendations(
    memberId: UUID,
    cursor: String?,
    size: Int,
    benefitCategory: PolicyBenefitCategory? = null,
    sort: PolicyRecommendationSort = PolicyRecommendationSort.RECOMMENDED
): PolicyRecommendationResult.Page
```

Pass `sort` to `SearchCondition`. In `decodeCursor`, reject payloads whose sort differs and validate that recommended cursors contain `score`, while latest cursors use `applyStartsOn` (nullable by contract). In `encodeCursor`, set only the selected sort's fields and always include `id`.

- [ ] **Step 4: Run service tests and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`; existing stale/malformed cursor tests and new sort tests pass.

- [ ] **Step 5: Commit application cursor behavior**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt
git commit -m "feat(policy): 정렬 조건을 추천 커서에 결합"
```

---

### Task 4: Expose the sort contract through the policy API

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`

**Interfaces:**
- Consumes: optional query string `sort`, defaulting to `RECOMMENDED`.
- Produces: the existing page response; invalid sort/category values return HTTP 400 via `INVALID_INPUT`.

- [ ] **Step 1: Write failing controller tests**

Update existing Mockito calls to include `PolicyRecommendationSort.RECOMMENDED`. Add:

```kotlin
@Test
fun `list recommendations passes latest sort to service`() {
    `when`(
        policyRecommendationService.listRecommendations(
            memberId, null, 20, null, PolicyRecommendationSort.LATEST
        )
    ).thenReturn(PolicyRecommendationResult.Page(emptyList(), null))

    mockMvc.perform(
        get("/api/v1/policy-recommendations")
            .param("sort", "LATEST")
            .with(authenticatedMember(memberId.toString()))
    ).andExpect(status().isOk)

    verify(policyRecommendationService).listRecommendations(
        memberId, null, 20, null, PolicyRecommendationSort.LATEST
    )
}

@Test
fun `list recommendations rejects unknown sort`() {
    mockMvc.perform(
        get("/api/v1/policy-recommendations")
            .param("sort", "BAD")
            .with(authenticatedMember(memberId.toString()))
    ).andExpect(status().isBadRequest)
}
```

- [ ] **Step 2: Run controller tests and verify RED**

```bash
cd backend
./gradlew :api:test --tests '*PolicyControllerTest'
```

Expected: latest-sort verification fails because the controller does not accept or forward `sort`.

- [ ] **Step 3: Parse and forward the sort**

Add `@RequestParam(defaultValue = "RECOMMENDED") sort: String`, then:

```kotlin
val parsedSort = PolicyRecommendationSort.fromKey(sort)
    ?: throw BusinessException(ErrorCode.INVALID_INPUT)
```

Call `listRecommendations` with named arguments including `benefitCategory = parsedBenefitCategory` and `sort = parsedSort`.

- [ ] **Step 4: Run controller tests and verify GREEN**

Run the Step 2 command. Expected: `BUILD SUCCESSFUL`; default, latest, category, and invalid-input tests pass.

- [ ] **Step 5: Commit the API contract**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt
git commit -m "feat(policy): 추천 목록 최신순 파라미터 노출"
```

---

### Task 5: Full verification and branch hygiene

**Files:**
- Verify: all changed Kotlin and documentation files

**Interfaces:**
- Produces: a clean `fix/policy-category-sort` branch with fresh test evidence.

- [ ] **Step 1: Run focused policy tests together**

```bash
cd backend
./gradlew :application:test :domain:test :api:test
```

Expected: `BUILD SUCCESSFUL` with zero test failures.

- [ ] **Step 2: Run the full backend test suite**

```bash
cd backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with zero test failures.

- [ ] **Step 3: Inspect the final diff and repository state**

```bash
git diff dev...HEAD --check
git status --short --branch
git log --oneline --decorate dev..HEAD
```

Expected: no whitespace errors, no uncommitted implementation files, and only focused policy/design commits on the fix branch.

- [ ] **Step 4: Verify requirements explicitly**

Confirm from tests and diff:

- all ten labels exactly match the requested copy;
- omitted sort is recommended order;
- latest order is start date descending, null last, id ascending;
- category and sort are bound into cursor validation;
- no frontend, schema, dependency, or score changes exist.
