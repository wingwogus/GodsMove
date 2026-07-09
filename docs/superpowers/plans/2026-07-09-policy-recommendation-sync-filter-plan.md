# Policy Recommendation Sync Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple policy and recommendation freshness from sync job IDs, add enum-backed card categories and benefit filtering, skip unchanged policy updates, and add sync-time LLM tag extraction with rule fallback.

**Architecture:** `PolicySyncJob` becomes an execution log only. `PolicyProgram` stores the current policy state, `PolicyRecommendation` stores the current member-specific recommendation result, and recommendation listing scopes through `PolicyProgram.source/sourceYear` rather than sync jobs. LLM usage stays behind a sync-time tag-extraction boundary and never receives member data.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, Java 21, Spring Data JPA, JPQL, Jackson, Java `HttpClient`, JUnit 5, Mockito, MockMvc, H2, PostgreSQL schema SQL.

## Global Constraints

- Preserve the domain term `member`; do not introduce project-owned `userId` or `users` naming.
- Keep `api -> application -> domain` and `batch -> application -> domain`.
- Do not change the mobile card response shape.
- Do not add `benefit_category` or `eligibility_category` DB columns in the first implementation pass.
- Do not call LLMs during `GET /api/v1/policy-recommendations`.
- Do not send member profile or farm data to an LLM.
- Keep recommendation requests deterministic and free of source HTTP calls.
- Use existing dependencies first; do not add a new SDK dependency for LLM extraction.
- Runtime dev/prod DB uses `ddl-auto: none`; update the reviewed schema SQL.
- Existing application test compilation is known to be blocked by unrelated `DevRagSeedServiceTest` unresolved references. Do not hide this blocker when reporting verification.
- Existing uncommitted backend changes may be present in this branch. Do not revert unrelated user changes.

---

## References

- Design: `docs/superpowers/specs/2026-07-09-policy-recommendation-sync-filter-design.md`
- Existing MVP design: `docs/superpowers/specs/2026-07-07-policy-recommendation-nongupez-design.md`
- Backend guide: `backend/AGENTS.md`
- OpenAI Structured Outputs guide: `https://platform.openai.com/docs/guides/structured-outputs`
- OpenAI Responses API reference: `https://platform.openai.com/docs/api-reference/responses`

## File Map

### Domain

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt`
  - Remove `lastSyncedJob`.
  - Remove `lastSyncedJob` parameters from `applyListFields` and `applyDetailFields`.
  - Add changed-state helpers so sync can skip unchanged existing rows.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt`
  - Remove `sourceSyncJob`.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`
  - Replace sync-job candidate query with source/year candidate query.
  - Add source/year listing for current-source removal handling.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`
  - Replace sync-job methods with source/year methods through `policyProgram`.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
  - Scope listing by member, policy source, source year, and optional benefit label.
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`
- Modify: `docs/database/2026-07-07-policy-recommendation-schema.sql`

### Application

- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyBenefitCategory.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyEligibilityCategory.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGenerator.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGeneratorTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/sync/PolicySyncService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/sync/PolicySyncServiceTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/OpenAiPolicyTagExtractionClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionConfig.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractor.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractorLlmTest.kt`

### API

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`

---

### Task 1: Remove Sync Job Foreign Keys From Current State

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`
- Modify: `docs/database/2026-07-07-policy-recommendation-schema.sql`

**Interfaces:**
- Produces: `PolicyProgram.applyListFields(...): Boolean`
- Produces: `PolicyProgram.applyDetailFields(...): Boolean`
- Produces: `PolicyProgram.markDetailSyncFailed(rawPayload: String): Boolean`
- Produces: `PolicyProgramRepository.findRecommendableCandidates(source: PolicySource, sourceYear: String, today: LocalDate): List<PolicyProgram>`
- Produces: `PolicyProgramRepository.findBySourceAndSourceYear(source: PolicySource, sourceYear: String): List<PolicyProgram>`
- Produces: `PolicyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(memberId: UUID, source: PolicySource, sourceYear: String): List<UUID>`
- Produces: `PolicyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(memberId: UUID, source: PolicySource, sourceYear: String): LocalDateTime?`
- Produces: `PolicyRecommendationRepository.deleteByMemberIdAndPolicyProgramSourceAndSourceYear(memberId: UUID, source: PolicySource, sourceYear: String): Int`

- [ ] **Step 1: Write failing domain test for no-op field application**

Add this test to `PolicyProgramTest`:

```kotlin
@Test
fun `applying identical policy fields reports no change`() {
    val program = PolicyProgram(
        title = "친환경농업 직불 지원",
        body = "친환경농업 지원",
        region = "전국",
        targetManagementType = null
    )

    val listChanged = program.applyListFields(
        source = PolicySource.NONGUP_EZ,
        externalId = "AB000009",
        sourceYear = "2026",
        title = "친환경농업 직불 지원",
        summary = "친환경농업 지원",
        region = "전국",
        sourceUrl = "https://example.test/policy",
        agencyName = "농림축산식품부"
    )
    val detailChanged = program.applyDetailFields(
        body = "본문",
        purpose = "목적",
        eligibilityOriginal = "농업인",
        eligibilitySummary = "농업인",
        benefitOriginal = "직불금 지원",
        benefitSummary = "직불/수당",
        applyStartsOn = LocalDate.of(2026, 1, 1),
        applyEndsOn = LocalDate.of(2026, 12, 31),
        applicationPeriodLabel = "2026.01.01~12.31",
        applicationPeriodNotice = null,
        applicationMethod = "방문 신청",
        requiredDocuments = "신청서",
        selectionCriteria = "자격 확인",
        departmentName = "친환경농업과",
        onlineApplyAvailable = false,
        applicationUrl = null,
        targetTagsJson = """["REGISTERED_FARMER"]""",
        cropTagsJson = "[]",
        regionTagsJson = """["전국"]""",
        rawPayload = """{"afbzCd":"AB000009"}""",
        recommendable = true
    )

    assertThat(listChanged).isTrue()
    assertThat(detailChanged).isTrue()

    val secondListChanged = program.applyListFields(
        source = PolicySource.NONGUP_EZ,
        externalId = "AB000009",
        sourceYear = "2026",
        title = "친환경농업 직불 지원",
        summary = "친환경농업 지원",
        region = "전국",
        sourceUrl = "https://example.test/policy",
        agencyName = "농림축산식품부"
    )
    val secondDetailChanged = program.applyDetailFields(
        body = "본문",
        purpose = "목적",
        eligibilityOriginal = "농업인",
        eligibilitySummary = "농업인",
        benefitOriginal = "직불금 지원",
        benefitSummary = "직불/수당",
        applyStartsOn = LocalDate.of(2026, 1, 1),
        applyEndsOn = LocalDate.of(2026, 12, 31),
        applicationPeriodLabel = "2026.01.01~12.31",
        applicationPeriodNotice = null,
        applicationMethod = "방문 신청",
        requiredDocuments = "신청서",
        selectionCriteria = "자격 확인",
        departmentName = "친환경농업과",
        onlineApplyAvailable = false,
        applicationUrl = null,
        targetTagsJson = """["REGISTERED_FARMER"]""",
        cropTagsJson = "[]",
        regionTagsJson = """["전국"]""",
        rawPayload = """{"afbzCd":"AB000009"}""",
        recommendable = true
    )

    assertThat(secondListChanged).isFalse()
    assertThat(secondDetailChanged).isFalse()
}
```

- [ ] **Step 2: Run domain test to verify it fails**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :domain:test --tests com.chamchamcham.domain.policy.PolicyProgramTest
```

Expected: compile failure because `applyListFields` and `applyDetailFields` do not yet return `Boolean` and still require `lastSyncedJob`.

- [ ] **Step 3: Update `PolicyProgram` signatures and remove `lastSyncedJob`**

Modify `PolicyProgram`:

```kotlin
fun applyListFields(
    source: PolicySource,
    externalId: String,
    sourceYear: String,
    title: String,
    summary: String?,
    region: String,
    sourceUrl: String?,
    agencyName: String
): Boolean {
    var changed = false
    fun <T> setIfChanged(current: T, next: T, setter: (T) -> Unit) {
        if (current != next) {
            setter(next)
            changed = true
        }
    }
    setIfChanged(this.source, source) { this.source = it }
    setIfChanged(this.externalId, externalId) { this.externalId = it }
    setIfChanged(this.sourceYear, sourceYear) { this.sourceYear = it }
    setIfChanged(this.title, title) { this.title = it }
    setIfChanged(this.summary, summary) { this.summary = it }
    setIfChanged(this.body, summary ?: body) { this.body = it }
    setIfChanged(this.region, region) { this.region = it }
    setIfChanged(this.sourceUrl, sourceUrl) { this.sourceUrl = it }
    setIfChanged(this.agencyName, agencyName) { this.agencyName = it }
    return changed
}
```

Update `applyDetailFields(...)` with the same `changed` flag and `setIfChanged` helper. The method must return `true` when any of these fields changes and `false` when all values are identical:

```kotlin
body
purpose
eligibilityOriginal
eligibilitySummary
benefitOriginal
benefitSummary
applyStartsOn
applyEndsOn
applicationPeriodLabel
applicationPeriodNotice
applicationMethod
requiredDocuments
selectionCriteria
departmentName
onlineApplyAvailable
applicationUrl
targetTagsJson
cropTagsJson
regionTagsJson
rawPayload
detailSynced
recommendable
```

Change `markDetailSyncFailed(rawPayload: String)` to:

```kotlin
fun markDetailSyncFailed(rawPayload: String): Boolean {
    var changed = false
    if (detailSynced) {
        detailSynced = false
        changed = true
    }
    if (recommendable) {
        recommendable = false
        changed = true
    }
    if (this.rawPayload != rawPayload) {
        this.rawPayload = rawPayload
        changed = true
    }
    return changed
}
```

- [ ] **Step 4: Remove `sourceSyncJob` from `PolicyRecommendation` constructor**

Delete this property:

```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "source_sync_job_id", nullable = false)
val sourceSyncJob: PolicySyncJob,
```

Keep only `member`, `policyProgram`, `score`, and `reason`.

- [ ] **Step 5: Replace repository methods**

In `PolicyProgramRepository`, replace the candidate query with:

```kotlin
@Query(
    """
    select p
    from PolicyProgram p
    where p.source = :source
      and p.sourceYear = :sourceYear
      and p.detailSynced = true
      and p.recommendable = true
      and (p.applyEndsOn is null or p.applyEndsOn >= :today)
    """
)
fun findRecommendableCandidates(
    @Param("source") source: PolicySource,
    @Param("sourceYear") sourceYear: String,
    @Param("today") today: LocalDate
): List<PolicyProgram>

fun findBySourceAndSourceYear(source: PolicySource, sourceYear: String): List<PolicyProgram>
```

In `PolicyRecommendationRepository`, replace sync-job methods with:

```kotlin
@Query(
    """
    select r.policyProgram.id
    from PolicyRecommendation r
    where r.member.id = :memberId
      and r.policyProgram.source = :source
      and r.policyProgram.sourceYear = :sourceYear
    """
)
fun findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
    @Param("memberId") memberId: UUID,
    @Param("source") source: PolicySource,
    @Param("sourceYear") sourceYear: String
): List<UUID>

@Query(
    """
    select max(r.createdAt)
    from PolicyRecommendation r
    where r.member.id = :memberId
      and r.policyProgram.source = :source
      and r.policyProgram.sourceYear = :sourceYear
    """
)
fun findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
    @Param("memberId") memberId: UUID,
    @Param("source") source: PolicySource,
    @Param("sourceYear") sourceYear: String
): LocalDateTime?

@Modifying
@Query(
    """
    delete from PolicyRecommendation r
    where r.member.id = :memberId
      and r.policyProgram.source = :source
      and r.policyProgram.sourceYear = :sourceYear
    """
)
fun deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
    @Param("memberId") memberId: UUID,
    @Param("source") source: PolicySource,
    @Param("sourceYear") sourceYear: String
): Int
```

Add imports for `Modifying`, `LocalDateTime`, and `PolicySource`.

- [ ] **Step 6: Update schema SQL**

Modify `docs/database/2026-07-07-policy-recommendation-schema.sql`:

```sql
alter table policy_program drop column if exists last_synced_job_id;

drop index if exists ix_policy_recommendation_member_sync_score;
alter table policy_recommendation drop column if exists source_sync_job_id;

create index if not exists ix_policy_recommendation_member_policy_score
    on policy_recommendation(member_id, policy_program_id, score desc, id asc);
```

Remove the legacy sync-job backfill block that exists only to populate `source_sync_job_id`.

- [ ] **Step 7: Run domain tests**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :domain:test --tests com.chamchamcham.domain.policy.PolicyProgramTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt \
  docs/database/2026-07-07-policy-recommendation-schema.sql
git commit -m "refactor(policy): sync job 결합 제거" \
  -m "정책과 추천 현재 상태 판단에서 sync job FK를 제거하고 source/sourceYear 기준으로 조회한다." \
  -m "Constraint: PolicySyncJob은 실행 로그로만 유지" \
  -m "Rejected: sync job 감사 포인터 유지 | no-op sync update 재발 가능" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :domain:test --tests com.chamchamcham.domain.policy.PolicyProgramTest"
```

---

### Task 2: Add Enum-Backed Card Summary Classification

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyBenefitCategory.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyEligibilityCategory.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGenerator.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGeneratorTest.kt`

**Interfaces:**
- Produces: `PolicyBenefitCategory.label: String`
- Produces: `PolicyBenefitCategory.fromKey(key: String): PolicyBenefitCategory?`
- Produces: `PolicyCardTextGenerator.benefitCategory(text: String?): PolicyBenefitCategory`
- Produces: `PolicyCardTextGenerator.eligibilityCategory(text: String?): PolicyEligibilityCategory`

- [ ] **Step 1: Write failing category tests**

Add assertions to `PolicyCardTextGeneratorTest`:

```kotlin
@Test
fun `benefit category exposes stable enum key and card label`() {
    val category = generator.benefitCategory("정책자금 융자 및 금리 지원")

    assertThat(category).isEqualTo(PolicyBenefitCategory.FINANCE)
    assertThat(category.label).isEqualTo("융자/금융")
    assertThat(generator.benefitSummary("정책자금 융자 및 금리 지원")).isEqualTo("융자/금융")
}

@Test
fun `eligibility category exposes stable enum key and card label`() {
    val category = generator.eligibilityCategory("청년후계농업경영인")

    assertThat(category).isEqualTo(PolicyEligibilityCategory.YOUNG_FARMER)
    assertThat(category.label).isEqualTo("청년 농업인")
    assertThat(generator.eligibilitySummary("청년후계농업경영인")).isEqualTo("청년 농업인")
}

@Test
fun `benefit category key parser rejects unknown values`() {
    assertThat(PolicyBenefitCategory.fromKey("FINANCE")).isEqualTo(PolicyBenefitCategory.FINANCE)
    assertThat(PolicyBenefitCategory.fromKey("bad")).isNull()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:test --tests com.chamchamcham.application.policy.support.PolicyCardTextGeneratorTest
```

Expected: compile failure for missing `PolicyBenefitCategory`, `PolicyEligibilityCategory`, `benefitCategory`, and `eligibilityCategory`. If `DevRagSeedServiceTest` blocks `compileTestKotlin` first, record that blocker and continue with `:application:compileKotlin` after implementation.

- [ ] **Step 3: Add `PolicyBenefitCategory`**

Create:

```kotlin
package com.chamchamcham.application.policy.support

enum class PolicyBenefitCategory(val label: String) {
    DIRECT_PAYMENT("직불/수당"),
    FINANCE("융자/금융"),
    FACILITY_EQUIPMENT("시설/장비"),
    EDUCATION_CONSULTING("교육/컨설팅"),
    INSURANCE_WELFARE("보험/복지"),
    CERTIFICATION_QUALITY("인증/품질"),
    MARKETING("판로/마케팅"),
    STARTUP_BUSINESS("창업/사업화"),
    ENVIRONMENT_INFRA("환경/인프라"),
    ETC("기타");

    companion object {
        fun fromKey(key: String): PolicyBenefitCategory? =
            entries.firstOrNull { it.name == key }
    }
}
```

- [ ] **Step 4: Add `PolicyEligibilityCategory`**

Create:

```kotlin
package com.chamchamcham.application.policy.support

enum class PolicyEligibilityCategory(val label: String) {
    YOUNG_FARMER("청년 농업인"),
    RETURNING_FARMER("귀농·귀촌인"),
    FEMALE_FARMER("여성 농업인"),
    SENIOR_RETIRED_FARMER("고령·은퇴 농업인"),
    SPECIAL_TARGET("특수 대상자"),
    DAMAGED_FARM("피해 농가"),
    CERTIFIED_FARMER("인증 보유 농업인"),
    LIVESTOCK_FARM("축산 농가"),
    EXPORT_BUSINESS("수출 농가·기업"),
    FOOD_AGRI_COMPANY("농식품 기업"),
    AGRI_CORPORATION("농업법인·단체"),
    REGISTERED_FARMER("경영체 등록 농업인"),
    FARMER("농업인"),
    UNKNOWN("상세 자격 확인")
}
```

- [ ] **Step 5: Update `PolicyCardTextGenerator` to classify enum first**

Change `benefitSummary` and `eligibilitySummary`:

```kotlin
fun eligibilitySummary(text: String?): String = eligibilityCategory(text).label

fun eligibilityCategory(text: String?): PolicyEligibilityCategory {
    val source = text.orEmpty()
    return when {
        source.containsAny("청년", "청년농", "청년후계", "만 40세") -> PolicyEligibilityCategory.YOUNG_FARMER
        source.containsAny("귀농", "귀촌") -> PolicyEligibilityCategory.RETURNING_FARMER
        source.containsAny("여성농업인", "결혼이민여성") -> PolicyEligibilityCategory.FEMALE_FARMER
        source.containsAny("60세", "65세", "고령", "은퇴", "농지이양") -> PolicyEligibilityCategory.SENIOR_RETIRED_FARMER
        source.containsAny("임산부", "학생", "취약계층", "기초생활", "차상위") -> PolicyEligibilityCategory.SPECIAL_TARGET
        source.containsAny("재해", "피해", "재난", "전염병", "질병") -> PolicyEligibilityCategory.DAMAGED_FARM
        source.containsAny("친환경인증", "친환경 인증", "GAP", "HACCP", "인증농가", "인증을 받은") -> PolicyEligibilityCategory.CERTIFIED_FARMER
        source.containsAny("축산", "가축", "한우", "젖소", "돼지", "양돈", "낙농", "송아지", "암소") -> PolicyEligibilityCategory.LIVESTOCK_FARM
        source.containsAny("수출농가", "수출업체", "수출기업", "해외", "박람회") -> PolicyEligibilityCategory.EXPORT_BUSINESS
        source.containsAny("농식품 기업", "농식품기업", "식품기업", "외식기업", "벤처", "창업기업") -> PolicyEligibilityCategory.FOOD_AGRI_COMPANY
        source.containsAny("농업법인", "영농조합", "농업회사법인", "생산자단체", "농협", "협동조합") -> PolicyEligibilityCategory.AGRI_CORPORATION
        source.containsAny("농업경영체", "농업경영정보", "경영체 등록") -> PolicyEligibilityCategory.REGISTERED_FARMER
        source.containsAny("농업인", "임업인", "농가") -> PolicyEligibilityCategory.FARMER
        else -> PolicyEligibilityCategory.UNKNOWN
    }
}

fun benefitSummary(text: String?): String = benefitCategory(text).label

fun benefitCategory(text: String?): PolicyBenefitCategory {
    val source = text.orEmpty()
    return when {
        source.containsAny("직불금", "영농정착지원금", "장려금") -> PolicyBenefitCategory.DIRECT_PAYMENT
        source.containsAny("융자", "정책자금", "대출", "금리") -> PolicyBenefitCategory.FINANCE
        source.containsAny("시설", "장비", "농기계", "설치", "개보수") -> PolicyBenefitCategory.FACILITY_EQUIPMENT
        source.containsAny("교육", "컨설팅", "상담", "연수") -> PolicyBenefitCategory.EDUCATION_CONSULTING
        source.containsAny("보험", "보험료", "연금", "건강보험") -> PolicyBenefitCategory.INSURANCE_WELFARE
        source.containsAny("인증", "검정", "품질", "무병묘", "저탄소") -> PolicyBenefitCategory.CERTIFICATION_QUALITY
        source.containsAny("박람회", "수출", "판로", "홍보", "브랜드") -> PolicyBenefitCategory.MARKETING
        source.containsAny("창업", "사업화", "벤처", "데이터", "R&D") -> PolicyBenefitCategory.STARTUP_BUSINESS
        source.containsAny("수질", "용수", "저수지", "가뭄", "환경") -> PolicyBenefitCategory.ENVIRONMENT_INFRA
        else -> PolicyBenefitCategory.ETC
    }
}
```

- [ ] **Step 6: Run compile and available tests**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:compileKotlin
./gradlew :application:test --tests com.chamchamcham.application.policy.support.PolicyCardTextGeneratorTest
```

Expected: `compileKotlin` succeeds. The targeted test succeeds only if the unrelated application test compile blocker has been resolved.

- [ ] **Step 7: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyBenefitCategory.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyEligibilityCategory.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGenerator.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/PolicyCardTextGeneratorTest.kt
git commit -m "feat(policy): 카드 요약 카테고리 enum화" \
  -m "카드 표시용 지원 항목과 자격 요약을 enum 기준으로 분류한 뒤 기존 문자열 label에 저장한다." \
  -m "Constraint: 카드 API 응답 필드는 변경하지 않음" \
  -m "Rejected: benefit_category 컬럼 추가 | 1차 필터는 기존 label로 충분함" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :application:compileKotlin"
```

---

### Task 3: Skip Unchanged Sync Updates and Mark Removed Source Policies

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/sync/PolicySyncService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/sync/PolicySyncServiceTest.kt`
- Uses: `PolicyProgram.applyListFields(...): Boolean`
- Uses: `PolicyProgram.applyDetailFields(...): Boolean`
- Uses: `PolicyProgramRepository.findBySourceAndSourceYear(...)`

**Interfaces:**
- Produces: sync behavior where unchanged existing policy rows are not saved.
- Produces: sync behavior where policies absent from current source list become `recommendable=false`.

- [ ] **Step 1: Write failing sync no-op test**

Add to `PolicySyncServiceTest`:

```kotlin
@Test
fun `unchanged existing policy is not saved again`() {
    val existing = existingSyncedProgram()
    `when`(sourceClient.detectLatestYear()).thenReturn("2026")
    `when`(sourceClient.fetchPrograms("2026")).thenReturn(listOf(listItem()))
    `when`(sourceClient.fetchDetail("AB000009", "2026")).thenReturn(detail())
    `when`(
        policyProgramRepository.findBySourceAndExternalIdAndSourceYear(
            PolicySource.NONGUP_EZ,
            "AB000009",
            "2026"
        )
    ).thenReturn(existing)
    `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
        .thenReturn(listOf(existing))
    stubJobSaveAndFind()

    service.runScheduledSync()

    verify(policyProgramRepository, never()).save(existing)
}
```

Add helper:

```kotlin
private fun existingSyncedProgram(): PolicyProgram =
    PolicyProgram(
        title = "친환경농업 직불 지원",
        body = "친환경농업 확산\n\n친환경 인증 농업인 지원\n\n농업경영정보를 등록하고 친환경인증을 받은 농업인\n\n인증단계별 직불금 지원",
        region = "전국",
        targetManagementType = null
    ).also {
        it.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            title = "친환경농업 직불 지원",
            summary = "친환경 인증 농업인에게 직불금을 지원합니다.",
            region = "전국",
            sourceUrl = "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
            agencyName = "농림축산식품부"
        )
        it.applyDetailFields(
            body = "친환경농업 확산\n\n친환경 인증 농업인 지원\n\n농업경영정보를 등록하고 친환경인증을 받은 농업인\n\n인증단계별 직불금 지원",
            purpose = "친환경농업 확산",
            eligibilityOriginal = "농업경영정보를 등록하고 친환경인증을 받은 농업인",
            eligibilitySummary = "인증 보유 농업인",
            benefitOriginal = "인증단계별 직불금 지원",
            benefitSummary = "직불/수당",
            applyStartsOn = LocalDate.of(2026, 2, 1),
            applyEndsOn = LocalDate.of(2026, 3, 31),
            applicationPeriodLabel = "2026.02.01~03.31",
            applicationPeriodNotice = null,
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 확인",
            departmentName = "친환경농업과",
            onlineApplyAvailable = false,
            applicationUrl = null,
            targetTagsJson = """["REGISTERED_FARMER"]""",
            cropTagsJson = """["ECO_FRIENDLY"]""",
            regionTagsJson = """["전국"]""",
            rawPayload = """{"afbzCd":"AB000009","bizYr":"2026"}""",
            recommendable = true
        )
    }
```

- [ ] **Step 2: Write failing removed-policy test**

Add:

```kotlin
@Test
fun `policy missing from current source list becomes not recommendable`() {
    val missing = existingSyncedProgram()
    `when`(sourceClient.detectLatestYear()).thenReturn("2026")
    `when`(sourceClient.fetchPrograms("2026")).thenReturn(emptyList())
    `when`(policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, "2026"))
        .thenReturn(listOf(missing))
    stubJobSaveAndFind()

    service.runScheduledSync()

    assertFalse(missing.recommendable)
    verify(policyProgramRepository).save(missing)
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:test --tests com.chamchamcham.application.policy.sync.PolicySyncServiceTest
```

Expected: compile/test failure because sync still requires `lastSyncedJob`, always saves, and does not mark missing source rows.

- [ ] **Step 4: Update `PolicySyncService.runExistingJob` to distinct source identities**

Inside `runExistingJob`, replace direct `listItems.forEach` with:

```kotlin
val listItems = sourceClient.fetchPrograms(targetYear)
    .distinctBy { item -> SourceIdentity(item.externalId, item.sourceYear) }
val currentIdentities = listItems.map { SourceIdentity(it.externalId, it.sourceYear) }.toSet()
```

Add private data class:

```kotlin
private data class SourceIdentity(
    val externalId: String,
    val sourceYear: String
)
```

- [ ] **Step 5: Remove job parameter from `upsertProgram`**

Change signature:

```kotlin
private fun upsertProgram(
    item: NongupEzPolicyListItem,
    detailFields: DetailFields?
): Boolean
```

Return `true` only when a new row is saved or an existing row changes. Use:

```kotlin
val existing = policyProgramRepository.findBySourceAndExternalIdAndSourceYear(
    PolicySource.NONGUP_EZ,
    item.externalId,
    item.sourceYear
)
val isNew = existing == null
val program = existing ?: PolicyProgram(
    title = item.title,
    body = item.summary ?: item.title,
    region = DEFAULT_REGION,
    targetManagementType = null
)
var changed = isNew
changed = program.applyListFields(
    source = PolicySource.NONGUP_EZ,
    externalId = item.externalId,
    sourceYear = item.sourceYear,
    title = item.title,
    summary = item.summary,
    region = DEFAULT_REGION,
    sourceUrl = detailUrl(item.externalId, item.sourceYear),
    agencyName = item.agencyName
) || changed
```

For detail failure:

```kotlin
if (detailFields == null) {
    if (isNew || !program.detailSynced) {
        changed = program.markDetailSyncFailed(rawPayload = item.rawJson) || changed
    }
} else {
    changed = program.applyDetailFields(
        body = detailFields.body,
        purpose = detailFields.purpose,
        eligibilityOriginal = detailFields.eligibilityOriginal,
        eligibilitySummary = detailFields.eligibilitySummary,
        benefitOriginal = detailFields.benefitOriginal,
        benefitSummary = detailFields.benefitSummary,
        applyStartsOn = detailFields.applyStartsOn,
        applyEndsOn = detailFields.applyEndsOn,
        applicationPeriodLabel = detailFields.applicationPeriodLabel,
        applicationPeriodNotice = detailFields.applicationPeriodNotice,
        applicationMethod = detailFields.applicationMethod,
        requiredDocuments = detailFields.requiredDocuments,
        selectionCriteria = detailFields.selectionCriteria,
        departmentName = detailFields.departmentName,
        onlineApplyAvailable = detailFields.onlineApplyAvailable,
        applicationUrl = detailFields.applicationUrl,
        targetTagsJson = detailFields.targetTagsJson,
        cropTagsJson = detailFields.cropTagsJson,
        regionTagsJson = detailFields.regionTagsJson,
        rawPayload = detailFields.rawPayload,
        recommendable = true
    ) || changed
}
if (changed) {
    policyProgramRepository.save(program)
}
return changed
```

- [ ] **Step 6: Add missing-source deactivation**

After processing list items and before `succeedJob`, call:

```kotlin
deactivateMissingSourcePolicies(targetYear, currentIdentities)
```

Implement:

```kotlin
private fun deactivateMissingSourcePolicies(
    sourceYear: String,
    currentIdentities: Set<SourceIdentity>
) {
    transactionTemplate.executeWithoutResult {
        policyProgramRepository.findBySourceAndSourceYear(PolicySource.NONGUP_EZ, sourceYear)
            .filter { program ->
                SourceIdentity(program.externalId, program.sourceYear) !in currentIdentities && program.recommendable
            }
            .forEach { program ->
                program.applyDetailFields(
                    body = program.body,
                    purpose = program.purpose,
                    eligibilityOriginal = program.eligibilityOriginal,
                    eligibilitySummary = program.eligibilitySummary,
                    benefitOriginal = program.benefitOriginal,
                    benefitSummary = program.benefitSummary,
                    applyStartsOn = program.applyStartsOn,
                    applyEndsOn = program.applyEndsOn,
                    applicationPeriodLabel = program.applicationPeriodLabel,
                    applicationPeriodNotice = program.applicationPeriodNotice,
                    applicationMethod = program.applicationMethod,
                    requiredDocuments = program.requiredDocuments,
                    selectionCriteria = program.selectionCriteria,
                    departmentName = program.departmentName,
                    onlineApplyAvailable = program.onlineApplyAvailable,
                    applicationUrl = program.applicationUrl,
                    targetTagsJson = program.targetTagsJson,
                    cropTagsJson = program.cropTagsJson,
                    regionTagsJson = program.regionTagsJson,
                    rawPayload = program.rawPayload,
                    recommendable = false
                )
                policyProgramRepository.save(program)
            }
    }
}
```

- [ ] **Step 7: Run compile and sync tests**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:compileKotlin
./gradlew :application:test --tests com.chamchamcham.application.policy.sync.PolicySyncServiceTest
```

Expected: `compileKotlin` succeeds. Targeted test execution may still be blocked by unrelated application test compilation.

- [ ] **Step 8: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/sync/PolicySyncService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/sync/PolicySyncServiceTest.kt
git commit -m "fix(policy): 동일 정책 sync update 생략" \
  -m "정책 내용이 변하지 않은 sync 항목은 저장하지 않고, 원본 목록에서 빠진 정책만 recommendable=false로 갱신한다." \
  -m "Constraint: source identity는 source/externalId/sourceYear" \
  -m "Rejected: lastSyncedJob 갱신으로 활성 정책 표시 | no-op update 유발" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:compileKotlin"
```

---

### Task 4: Rework Recommendation Staleness Without Sync Job IDs

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationCursorPayload.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt`
- Modify: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 repository source/year methods.
- Produces: `PolicyRecommendationService.listRecommendations(memberId: UUID, cursor: String?, size: Int, benefitCategory: PolicyBenefitCategory? = null)`
- Produces: `PolicyRecommendationQueryRepository.SearchCondition(source: PolicySource, sourceYear: String, benefitSummary: String?)`
- Produces: cursor payload with `source`, `sourceYear`, and `benefitCategory`.

- [ ] **Step 1: Write failing service test for no-op sync job reuse**

Update `PolicyRecommendationServiceTest` to assert the service does not regenerate only because latest job changed. Use repository stubs:

```kotlin
`when`(
    policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
        memberId,
        PolicySource.NONGUP_EZ,
        "2026"
    )
).thenReturn(LocalDateTime.of(2026, 7, 1, 10, 0))
`when`(
    policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
        memberId,
        PolicySource.NONGUP_EZ,
        "2026"
    )
).thenReturn(listOf(policyId))
```

Assert:

```kotlin
verify(policyRecommendationRepository, never())
    .deleteByMemberIdAndPolicyProgramSourceAndSourceYear(memberId, PolicySource.NONGUP_EZ, "2026")
```

- [ ] **Step 2: Write failing query repository test for source/year scope**

In `PolicyRecommendationQueryRepositoryTest`, create one recommendation for `NONGUP_EZ/2026`, one for `NONGUP_EZ/2025`, and one for another member. Query:

```kotlin
val result = repository.findPage(
    PolicyRecommendationQueryRepository.SearchCondition(
        memberId = member.id!!,
        source = PolicySource.NONGUP_EZ,
        sourceYear = "2026",
        benefitSummary = null,
        cursor = null,
        size = 20
    )
)
```

Assert:

```kotlin
assertThat(result.rows).extracting<String> { it.policyProgram.sourceYear }
    .containsExactly("2026")
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :domain:test --tests com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest
./gradlew :application:test --tests com.chamchamcham.application.policy.recommendation.PolicyRecommendationServiceTest
```

Expected: compile failures where sync-job fields remain in conditions, cursor payload, recommendation constructor, and service calls.

- [ ] **Step 4: Update query condition and JPQL**

Change `SearchCondition`:

```kotlin
data class SearchCondition(
    val memberId: UUID,
    val source: PolicySource,
    val sourceYear: String,
    val benefitSummary: String?,
    val cursor: Cursor?,
    val size: Int
)
```

Update `PolicyRecommendationQueryRepositoryImpl`:

```kotlin
val where = mutableListOf(
    "r.member.id = :memberId",
    "p.source = :source",
    "p.sourceYear = :sourceYear"
)
val params = linkedMapOf<String, Any>(
    "memberId" to condition.memberId,
    "source" to condition.source,
    "sourceYear" to condition.sourceYear,
    "maxDate" to maxDate
)
condition.benefitSummary?.let {
    where += "p.benefitSummary = :benefitSummary"
    params["benefitSummary"] = it
}
```

Keep ordering:

```sql
order by r.score desc, coalesce(p.applyEndsOn, :maxDate) asc, r.id asc
```

- [ ] **Step 5: Update cursor payload**

Change `PolicyRecommendationCursorPayload`:

```kotlin
data class PolicyRecommendationCursorPayload(
    val source: PolicySource,
    val sourceYear: String,
    val benefitCategory: String?,
    val score: BigDecimal,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
```

In `decodeCursor`, reject cursor when any of `source`, `sourceYear`, or `benefitCategory` mismatches the current request.

- [ ] **Step 6: Update recommendation service flow**

Change signature:

```kotlin
fun listRecommendations(
    memberId: UUID,
    cursor: String?,
    size: Int,
    benefitCategory: PolicyBenefitCategory? = null
): PolicyRecommendationResult.Page
```

Load candidates:

```kotlin
val candidates = policyProgramRepository.findRecommendableCandidates(
    PolicySource.NONGUP_EZ,
    latestJob.targetYear,
    today
)
```

Stale check should use:

```kotlin
private fun recommendationsAreStale(
    memberId: UUID,
    sourceYear: String,
    candidates: List<PolicyProgram>,
    member: Member,
    memberCrops: List<MemberCrop>,
    farms: List<Farm>
): Boolean
```

Rules:

```kotlin
val newestRecommendationCreatedAt =
    policyRecommendationRepository.findNewestCreatedAtByMemberIdAndPolicyProgramSourceAndSourceYear(
        memberId,
        PolicySource.NONGUP_EZ,
        sourceYear
    ) ?: return true

val storedPolicyIds =
    policyRecommendationRepository.findPolicyProgramIdsByMemberIdAndPolicyProgramSourceAndSourceYear(
        memberId,
        PolicySource.NONGUP_EZ,
        sourceYear
    ).toSet()

if (storedPolicyIds.any { it !in candidates.map { candidate -> requireNotNull(candidate.id) }.toSet() }) {
    return true
}
if (candidates.any { it.updatedAfter(newestRecommendationCreatedAt) }) {
    return true
}
if (member.updatedAfter(newestRecommendationCreatedAt)) {
    return true
}
if (memberCrops.any { it.updatedAfter(newestRecommendationCreatedAt) }) {
    return true
}
if (farms.any { it.updatedAfter(newestRecommendationCreatedAt) }) {
    return true
}
return false
```

Add helper:

```kotlin
private fun BaseTimeEntity.updatedAfter(threshold: LocalDateTime): Boolean =
    runCatching { updatedAt.isAfter(threshold) }.getOrDefault(false)
```

Add import:

```kotlin
import com.chamchamcham.domain.common.BaseTimeEntity
```

- [ ] **Step 7: Update regeneration to delete only active source/year recommendations**

Replace:

```kotlin
policyRecommendationRepository.deleteByMember_Id(memberId)
```

with:

```kotlin
policyRecommendationRepository.deleteByMemberIdAndPolicyProgramSourceAndSourceYear(
    memberId,
    PolicySource.NONGUP_EZ,
    latestJob.targetYear
)
```

Construct `PolicyRecommendation` without `sourceSyncJob`.

- [ ] **Step 8: Run compile and tests**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :domain:test --tests com.chamchamcham.domain.policy.PolicyRecommendationQueryRepositoryTest
./gradlew :application:compileKotlin
./gradlew :application:test --tests com.chamchamcham.application.policy.recommendation.PolicyRecommendationServiceTest
```

Expected: domain query test succeeds. Application compile succeeds. Application test may be blocked by unrelated application test compile errors.

- [ ] **Step 9: Commit**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt \
  backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationCursorPayload.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationServiceTest.kt
git commit -m "refactor(policy): 추천 조회를 source year 기준으로 변경" \
  -m "추천 후보와 저장된 추천 조회를 sync job ID 대신 policyProgram source/sourceYear 기준으로 묶는다." \
  -m "Constraint: no-op sync job은 추천 재생성을 유발하지 않아야 함" \
  -m "Rejected: 전체 member 추천 삭제 | 다른 source/year 추천까지 지워짐" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:compileKotlin"
```

---

### Task 5: Add Recommendation Benefit Category Filter API

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Modify: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`

**Interfaces:**
- Consumes: `PolicyBenefitCategory.fromKey(key: String): PolicyBenefitCategory?`
- Produces: `GET /api/v1/policy-recommendations?benefitCategory=FINANCE`

- [ ] **Step 1: Write failing controller test**

Add to `PolicyControllerTest`:

```kotlin
@Test
fun `list recommendations passes benefit category filter to service`() {
    `when`(
        policyRecommendationService.listRecommendations(
            memberId,
            null,
            20,
            PolicyBenefitCategory.FINANCE
        )
    ).thenReturn(PolicyRecommendationResult.Page(emptyList(), null))

    mockMvc.perform(
        get("/api/v1/policy-recommendations")
            .param("benefitCategory", "FINANCE")
            .principal { memberId.toString() }
    )
        .andExpect(status().isOk)

    verify(policyRecommendationService).listRecommendations(
        memberId,
        null,
        20,
        PolicyBenefitCategory.FINANCE
    )
}
```

Add invalid enum test:

```kotlin
@Test
fun `list recommendations rejects unknown benefit category`() {
    mockMvc.perform(
        get("/api/v1/policy-recommendations")
            .param("benefitCategory", "BAD")
            .principal { memberId.toString() }
    )
        .andExpect(status().isBadRequest)
}
```

- [ ] **Step 2: Run API test to verify failure**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :api:test --tests com.chamchamcham.api.policy.controller.PolicyControllerTest
```

Expected: compile failure because controller and service signature do not accept `benefitCategory`.

- [ ] **Step 3: Update controller parameter parsing**

Change controller:

```kotlin
fun listRecommendations(
    @AuthenticationPrincipal principal: Any?,
    @RequestParam(required = false) cursor: String?,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(required = false) benefitCategory: String?
): ResponseEntity<ApiResponse<PolicyRecommendationPageResponse>> {
    val parsedBenefitCategory = benefitCategory?.let {
        PolicyBenefitCategory.fromKey(it) ?: throw BusinessException(ErrorCode.INVALID_INPUT)
    }
    val result = policyRecommendationService.listRecommendations(
        parseMemberId(principal),
        cursor,
        size,
        parsedBenefitCategory
    )
    return ResponseEntity.ok(ApiResponse.ok(PolicyRecommendationPageResponse.from(result)))
}
```

Add import:

```kotlin
import com.chamchamcham.application.policy.support.PolicyBenefitCategory
```

- [ ] **Step 4: Wire query filter label**

In `PolicyRecommendationService.findPage` call, pass:

```kotlin
benefitSummary = benefitCategory?.label
```

In cursor encode/decode, use:

```kotlin
benefitCategory = benefitCategory?.name
```

- [ ] **Step 5: Run API test and compile**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :api:test --tests com.chamchamcham.api.policy.controller.PolicyControllerTest
./gradlew :application:compileKotlin
```

Expected: API test succeeds if lower modules compile; application compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationService.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/recommendation/PolicyRecommendationCursorPayload.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt
git commit -m "feat(policy): 추천 지원 항목 필터 추가" \
  -m "추천 목록 API가 benefitCategory enum key를 받아 기존 benefitSummary label 기준으로 필터링한다." \
  -m "Constraint: 카드 응답에는 benefitCategory 필드를 추가하지 않음" \
  -m "Rejected: label query param 사용 | 프론트 필터 key 안정성이 낮음" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :api:test --tests com.chamchamcham.api.policy.controller.PolicyControllerTest"
```

---

### Task 6: Add Sync-Time LLM Tag Extraction With Rule Fallback

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/OpenAiPolicyTagExtractionClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionConfig.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractor.kt`
- Create: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractorLlmTest.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractorTest.kt`
- Modify: `backend/api/src/main/resources/application.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`

**Interfaces:**
- Produces: `PolicyTagExtractionClient.extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult`
- Produces: `NongupEzPolicyTagExtractor.extract(detail: NongupEzPolicyDetail): ExtractedPolicyTags` using LLM-first, rule-fallback.

- [ ] **Step 1: Write failing LLM fallback tests**

Create `NongupEzPolicyTagExtractorLlmTest`:

```kotlin
class NongupEzPolicyTagExtractorLlmTest {
    @Test
    fun `valid llm tags are used when schema and enums are valid`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(
                PolicyTagExtractionClientResult.Success(
                    targetTags = setOf("YOUNG_FARMER"),
                    cropTags = setOf("MEDICINAL_CROP"),
                    regionTags = setOf("충청북도"),
                    confidence = 0.88
                )
            )
        )

        val tags = extractor.extract(detail(title = "청년 약용작물 지원"))

        assertThat(tags.targetTags).containsExactly("YOUNG_FARMER")
        assertThat(tags.cropTags).containsExactly("MEDICINAL_CROP")
        assertThat(tags.regionTags).containsExactly("충청북도")
    }

    @Test
    fun `invalid llm tags fall back to rules`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(
                PolicyTagExtractionClientResult.Success(
                    targetTags = setOf("UNKNOWN_TARGET"),
                    cropTags = emptySet(),
                    regionTags = emptySet(),
                    confidence = 0.90
                )
            )
        )

        val tags = extractor.extract(detail(title = "청년 귀농 약용작물 지원"))

        assertThat(tags.targetTags).contains("YOUNG_FARMER", "RETURNING_FARMER")
        assertThat(tags.cropTags).contains("MEDICINAL_CROP")
    }

    @Test
    fun `llm failure falls back to rules`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(PolicyTagExtractionClientResult.Failure)
        )

        val tags = extractor.extract(detail(title = "특용작물 지원"))

        assertThat(tags.cropTags).contains("SPECIAL_CROP")
    }
}
```

Add local fake:

```kotlin
private class FakePolicyTagExtractionClient(
    private val result: PolicyTagExtractionClientResult
) : PolicyTagExtractionClient {
    override fun extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult = result
}
```

Add local detail helper:

```kotlin
private fun detail(title: String): NongupEzPolicyDetail =
    NongupEzPolicyDetail(
        externalId = "AB000009",
        sourceYear = "2026",
        title = title,
        purpose = null,
        summary = title,
        eligibility = title,
        benefit = title,
        applyStartsOn = null,
        applyEndsOn = null,
        applicationMethod = null,
        requiredDocuments = null,
        selectionCriteria = null,
        agencyName = "농림축산식품부",
        contacts = emptyList(),
        attachments = emptyList(),
        rawJson = "{}"
    )
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:test --tests com.chamchamcham.application.policy.support.NongupEzPolicyTagExtractorLlmTest
```

Expected: compile failure for missing client/result types and constructor parameter.

- [ ] **Step 3: Add client contract**

Create:

```kotlin
package com.chamchamcham.application.policy.support

data class PolicyTagExtractionRequest(
    val title: String,
    val summary: String?,
    val eligibility: String?,
    val benefit: String?,
    val agencyName: String
)

sealed interface PolicyTagExtractionClientResult {
    data class Success(
        val targetTags: Set<String>,
        val cropTags: Set<String>,
        val regionTags: Set<String>,
        val confidence: Double
    ) : PolicyTagExtractionClientResult

    data object Failure : PolicyTagExtractionClientResult
}

fun interface PolicyTagExtractionClient {
    fun extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult
}
```

- [ ] **Step 4: Update `NongupEzPolicyTagExtractor`**

Change constructor:

```kotlin
@Component
class NongupEzPolicyTagExtractor(
    private val llmClient: PolicyTagExtractionClient
) {
```

In `extract`, build request and validate LLM result first:

```kotlin
val llmTags = llmClient.extract(
    PolicyTagExtractionRequest(
        title = detail.title,
        summary = detail.summary,
        eligibility = detail.eligibility,
        benefit = detail.benefit,
        agencyName = detail.agencyName
    )
).takeIfValid()

if (llmTags != null) {
    return llmTags
}

return extractByRules(detail)
```

Add validation:

```kotlin
private fun PolicyTagExtractionClientResult.takeIfValid(): ExtractedPolicyTags? {
    val success = this as? PolicyTagExtractionClientResult.Success ?: return null
    if (!allowedTargetTags.containsAll(success.targetTags)) return null
    if (!allowedCropTags.containsAll(success.cropTags)) return null
    if (!allowedRegionTags.containsAll(success.regionTags)) return null
    return ExtractedPolicyTags(
        targetTags = success.targetTags,
        cropTags = success.cropTags,
        regionTags = success.regionTags.ifEmpty { setOf("전국") }
    )
}
```

Allowed sets:

```kotlin
private val allowedTargetTags = setOf(
    "YOUNG_FARMER",
    "REGISTERED_FARMER",
    "AGRICULTURAL_CORPORATION",
    "RETURNING_FARMER"
)
private val allowedCropTags = setOf("MEDICINAL_CROP", "SPECIAL_CROP")
private val allowedRegionTags = provinceNames.toSet() + "전국"
```

- [ ] **Step 5: Add OpenAI-compatible HTTP client**

Create `OpenAiPolicyTagExtractionClient` using Java `HttpClient` and Jackson. Use OpenAI Responses API with structured JSON output. Keep this class disabled when `policy.tag-extraction.openai.api-key` is blank.

Constructor:

```kotlin
class OpenAiPolicyTagExtractionClient(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val apiKey: String,
    private val model: String = "gpt-5.5",
    private val endpoint: URI = URI.create("https://api.openai.com/v1/responses")
) : PolicyTagExtractionClient
```

Request body must include:

```json
{
  "model": "gpt-5.5",
  "input": "Extract policy tags from this NongupEZ policy...",
  "text": {
    "format": {
      "type": "json_schema",
      "name": "policy_tags",
      "strict": true,
      "schema": {
        "type": "object",
        "additionalProperties": false,
        "required": ["targetTags", "cropTags", "regionTags", "confidence"],
        "properties": {
          "targetTags": {
            "type": "array",
            "items": {
              "type": "string",
              "enum": ["YOUNG_FARMER", "REGISTERED_FARMER", "AGRICULTURAL_CORPORATION", "RETURNING_FARMER"]
            }
          },
          "cropTags": {
            "type": "array",
            "items": {
              "type": "string",
              "enum": ["MEDICINAL_CROP", "SPECIAL_CROP"]
            }
          },
          "regionTags": {
            "type": "array",
            "items": {
              "type": "string",
              "enum": ["전국", "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도", "전라남도", "경상북도", "경상남도", "제주특별자치도"]
            }
          },
          "confidence": {
            "type": "number",
            "minimum": 0,
            "maximum": 1
          }
        }
      }
    }
  }
}
```

On HTTP error, timeout, invalid response shape, or parse failure, return `PolicyTagExtractionClientResult.Failure`.

- [ ] **Step 6: Add Spring bean configuration**

Create `PolicyTagExtractionConfig`:

```kotlin
package com.chamchamcham.application.policy.support

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient

@Configuration
class PolicyTagExtractionConfig {
    @Bean
    @ConditionalOnProperty(
        prefix = "policy.tag-extraction.openai",
        name = ["enabled"],
        havingValue = "true"
    )
    fun openAiPolicyTagExtractionClient(
        objectMapper: ObjectMapper,
        @Value("\${policy.tag-extraction.openai.api-key}") apiKey: String,
        @Value("\${policy.tag-extraction.openai.model}") model: String
    ): PolicyTagExtractionClient =
        OpenAiPolicyTagExtractionClient(
            httpClient = HttpClient.newHttpClient(),
            objectMapper = objectMapper,
            apiKey = apiKey,
            model = model,
            endpoint = URI.create("https://api.openai.com/v1/responses")
        )

    @Bean
    @ConditionalOnMissingBean(PolicyTagExtractionClient::class)
    fun ruleOnlyPolicyTagExtractionClient(): PolicyTagExtractionClient =
        PolicyTagExtractionClient { PolicyTagExtractionClientResult.Failure }
}
```

- [ ] **Step 7: Add configuration defaults**

Add properties:

```yaml
policy:
  tag-extraction:
    openai:
      enabled: false
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_POLICY_TAG_MODEL:gpt-5.5}
```

In test resources, keep:

```yaml
policy:
  tag-extraction:
    openai:
      enabled: false
      api-key: ""
      model: gpt-5.5
```

- [ ] **Step 8: Run compile and tag extractor tests**

Run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :application:compileKotlin
./gradlew :application:test --tests com.chamchamcham.application.policy.support.NongupEzPolicyTagExtractorLlmTest
```

Expected: `compileKotlin` succeeds. The targeted test succeeds if unrelated application test compile blockers are resolved.

- [ ] **Step 9: Commit**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionClient.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/OpenAiPolicyTagExtractionClient.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/PolicyTagExtractionConfig.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractor.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractorLlmTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/policy/support/NongupEzPolicyTagExtractorTest.kt \
  backend/api/src/main/resources/application.yml \
  backend/api/src/test/resources/application-test.yml
git commit -m "feat(policy): LLM 정책 태그 추출 경계 추가" \
  -m "농업e지 sync 시 정책 원문을 구조화 태그로 추출하되 실패하거나 검증에 실패하면 기존 룰 기반 추출로 대체한다." \
  -m "Constraint: member 데이터는 LLM 요청에 포함하지 않음" \
  -m "Constraint: 추천 조회 API는 LLM을 호출하지 않음" \
  -m "Rejected: 추천 시점 LLM rerank | 지연, 비용, 재현성 리스크 증가" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:compileKotlin"
```

---

## Final Verification

After all tasks are complete, run:

```bash
cd /Users/wingwogus/Projects/ChamChamCham/backend
./gradlew :domain:test
./gradlew :application:compileKotlin
./gradlew :api:test --tests com.chamchamcham.api.policy.controller.PolicyControllerTest
./gradlew :application:test --tests com.chamchamcham.application.policy.support.PolicyCardTextGeneratorTest
./gradlew :application:test --tests com.chamchamcham.application.policy.sync.PolicySyncServiceTest
./gradlew :application:test --tests com.chamchamcham.application.policy.recommendation.PolicyRecommendationServiceTest
```

If `:application:test` fails at `DevRagSeedServiceTest` compile errors, report that blocker explicitly and include the successful `:application:compileKotlin` and domain/API test results.

Run whitespace check:

```bash
git diff --check
```

Expected: no output and exit code `0`.
