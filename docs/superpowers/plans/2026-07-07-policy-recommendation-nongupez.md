# NongupEZ Policy Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build stored NongupEZ policy synchronization, admin sync controls, scheduled refresh, and deterministic member policy recommendations.

**Architecture:** Keep `api -> application -> domain` and `batch -> application -> domain`. Store NongupEZ list/detail payloads in `domain`, isolate live HTTP calls behind one `application` source client, compute recommendations from stored rows only, and expose thin API DTO/controller mapping from application results.

**Tech Stack:** Spring Boot 3.5, Kotlin 1.9, Java 21, Spring Data JPA, JPQL via `EntityManager`, Jackson, Java `HttpClient`, Spring Security, Spring Scheduling, Spring Async, JUnit 5, Mockito, MockMvc, H2 for JPA tests.

## Global Constraints

- Preserve the domain term `member`; do not introduce project-owned `userId` or `users` naming.
- No new dependencies unless a concrete task below explicitly adds one; this plan adds none.
- Network calls to NongupEZ must not run in unit tests.
- The backend guarantees `eligibilitySummary`, `benefitSummary`, and `applicationPeriodLabel` are 19 Korean characters or fewer.
- `programTitle` and `agencyName` remain official source text and are not shortened by the backend.
- Recommendations use deterministic rules only; no LLM, RAG, or prompt-based summarization.
- Member-facing recommendation APIs never call NongupEZ directly.
- Attachments are exposed as source metadata and URLs only; do not download or store attachment files.
- Runtime dev/prod DB uses `ddl-auto: none`; create a reviewed schema SQL document because Flyway is not installed.
- Keep contacts, attachments, source tags, and raw payload in JSON text fields for this MVP; do not add separate target, crop, region, contact, or attachment tables.
- Existing source branch is `feat/policy-recommendation-nongupez`; leave unrelated `.claude/` changes untouched.

---

## References

- Spec: `docs/superpowers/specs/2026-07-07-policy-recommendation-nongupez-design.md`
- Backend guide: `backend/AGENTS.md`
- Existing cursor codec: `backend/application/src/main/kotlin/com/chamchamcham/application/common/OpaqueCursorCodec.kt`
- Existing cursor pattern: `backend/application/src/main/kotlin/com/chamchamcham/application/community/CommunityPostService.kt`
- Existing JPQL query repository pattern: `backend/domain/src/main/kotlin/com/chamchamcham/domain/community/CommunityPostQueryRepositoryImpl.kt`
- NongupEZ source endpoints:
  - `POST https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd`
  - `POST https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/retrieveListBizSrch`
  - `POST https://www.nongupez.go.kr/nsm/bizAply/cstBiz/findBizSrchDtl`

## File Map

### Domain

- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt`
  Add NongupEZ source identity, display fields, card summaries, detail sections, JSON tag/raw payload fields, sync flags, source job relation, and nullable `targetManagementType`.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt`
  Add `sourceSyncJob` relation.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`
  Add source identity lookup and recommendable candidate query.
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`
  Add member/sync-job stale detection and deletion methods.
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySource.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJob.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncTriggerType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicySyncJobTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`

### Application

- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/config/AsyncConfig.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationCursorPayload.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorer.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncAsyncRunner.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/TextListJsonCodec.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractor.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyCardTextGenerator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyMemberProfileReader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcher.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzHttpTransport.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/JavaNetNongupEzHttpTransport.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzPolicySourceClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzSourceModels.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicySourceClientTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyCardTextGeneratorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcherTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorerTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationServiceTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicySyncServiceTest.kt`
- Fixtures:
  - `backend/application/src/test/resources/policy/nongupez/conditions.json`
  - `backend/application/src/test/resources/policy/nongupez/list-page.json`
  - `backend/application/src/test/resources/policy/nongupez/detail-success.json`
  - `backend/application/src/test/resources/policy/nongupez/detail-missing-optional-fields.json`

### API

- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`
- Modify: `backend/api/src/main/resources/application.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/dto/PolicyResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt`

### Batch And Schema

- Modify: `backend/batch/src/main/resources/application.yml` if the file exists; create it if absent.
- Create: `backend/batch/src/main/kotlin/com/chamchamcham/batch/policy/PolicySyncScheduler.kt`
- Test: `backend/batch/src/test/kotlin/com/chamchamcham/batch/policy/PolicySyncSchedulerTest.kt`
- Create: `docs/database/2026-07-07-policy-recommendation-schema.sql`

---

### Task 1: Policy Domain Model, Schema Contract, And Query Repository

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySource.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJob.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobStatus.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncTriggerType.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`
- Create: `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`
- Create: `docs/database/2026-07-07-policy-recommendation-schema.sql`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicySyncJobTest.kt`
- Test: `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryTest.kt`

**Interfaces:**
- Produces: `PolicySource.NONGUP_EZ`
- Produces: `PolicySyncJob.succeed(...)`, `PolicySyncJob.fail(...)`
- Produces: `PolicyProgram.applyListFields(...)`, `PolicyProgram.applyDetailFields(...)`, `PolicyProgram.markDetailSyncFailed(...)`, `PolicyProgram.isOpenOn(today: LocalDate): Boolean`
- Produces: `PolicyRecommendationQueryRepository.findPage(condition: SearchCondition): SearchResult`

- [ ] **Step 1: Write failing domain behavior tests**

Create `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicyProgramTest.kt`:

```kotlin
package com.chamchamcham.domain.policy

import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class PolicyProgramTest {
    @Test
    fun `detail success makes open detailed policy recommendable`() {
        val jobId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val job = PolicySyncJob(
            id = jobId,
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.ADMIN,
            createdByMemberId = null
        )
        val program = PolicyProgram(
            title = "친환경농업직불",
            body = "목록 내용",
            region = "전국",
            targetManagementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        )

        program.applyListFields(
            source = PolicySource.NONGUP_EZ,
            externalId = "AB000009",
            sourceYear = "2026",
            title = "친환경농업직불",
            summary = "친환경농업 실천 농업인 지원",
            region = "전국",
            sourceUrl = "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
            agencyName = "농림축산식품부",
            lastSyncedJob = job
        )
        program.applyDetailFields(
            body = "상세 내용",
            purpose = "친환경농업 확산",
            eligibilityOriginal = "친환경인증을 받은 농업경영정보 등록 농업인",
            eligibilitySummary = "친환경 인증 농업인",
            benefitOriginal = "인증단계별 직불금 지급",
            benefitSummary = "인증단계별 직불금",
            applyStartsOn = LocalDate.of(2026, 3, 25),
            applyEndsOn = LocalDate.of(2026, 6, 30),
            applicationPeriodLabel = "2026.03.25~06.30",
            applicationPeriodNotice = null,
            applicationMethod = "방문 신청",
            requiredDocuments = "신청서",
            selectionCriteria = "자격 검토",
            departmentName = "친환경농업과",
            onlineApplyAvailable = false,
            applicationUrl = null,
            targetTagsJson = "[\"REGISTERED_FARMER\"]",
            cropTagsJson = "[\"ECO_FRIENDLY\"]",
            regionTagsJson = "[\"전국\"]",
            rawPayload = "{\"afbzCd\":\"AB000009\"}",
            recommendable = true,
            lastSyncedJob = job
        )

        assertThat(program.detailSynced).isTrue()
        assertThat(program.recommendable).isTrue()
        assertThat(program.isOpenOn(LocalDate.of(2026, 6, 1))).isTrue()
        assertThat(program.isOpenOn(LocalDate.of(2026, 7, 1))).isFalse()
        assertThat(program.targetManagementType).isEqualTo(ManagementType.AGRICULTURAL_INDIVIDUAL)
    }

    @Test
    fun `detail failure keeps list fields but blocks recommendation`() {
        val program = PolicyProgram(
            title = "청년농 지원",
            body = "목록 내용",
            region = "전국",
            targetManagementType = null
        )

        program.markDetailSyncFailed(rawPayload = "{\"listOnly\":true}")

        assertThat(program.detailSynced).isFalse()
        assertThat(program.recommendable).isFalse()
        assertThat(program.rawPayload).isEqualTo("{\"listOnly\":true}")
    }
}
```

Create `backend/domain/src/test/kotlin/com/chamchamcham/domain/policy/PolicySyncJobTest.kt`:

```kotlin
package com.chamchamcham.domain.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolicySyncJobTest {
    @Test
    fun `succeed records counters and finished timestamp`() {
        val job = PolicySyncJob(
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.SCHEDULED,
            createdByMemberId = null
        )

        job.succeed(totalCount = 372, syncedCount = 372, detailSuccessCount = 360, detailFailureCount = 12)

        assertThat(job.status).isEqualTo(PolicySyncJobStatus.SUCCEEDED)
        assertThat(job.totalCount).isEqualTo(372)
        assertThat(job.detailFailureCount).isEqualTo(12)
        assertThat(job.finishedAt).isNotNull()
    }

    @Test
    fun `fail records bounded error message`() {
        val job = PolicySyncJob(
            source = PolicySource.NONGUP_EZ,
            targetYear = "2026",
            triggerType = PolicySyncTriggerType.ADMIN,
            createdByMemberId = null
        )

        job.fail("x".repeat(1200))

        assertThat(job.status).isEqualTo(PolicySyncJobStatus.FAILED)
        assertThat(job.errorMessage).hasSize(1000)
        assertThat(job.finishedAt).isNotNull()
    }
}
```

- [ ] **Step 2: Run domain tests and verify they fail**

Run:

```bash
cd backend
./gradlew :domain:test --tests "com.chamchamcham.domain.policy.PolicyProgramTest" --tests "com.chamchamcham.domain.policy.PolicySyncJobTest"
```

Expected: FAIL because the new enums, entity fields, and methods do not exist.

- [ ] **Step 3: Add policy enums and sync job entity**

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySource.kt`:

```kotlin
package com.chamchamcham.domain.policy

enum class PolicySource {
    NONGUP_EZ
}
```

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobStatus.kt`:

```kotlin
package com.chamchamcham.domain.policy

enum class PolicySyncJobStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
```

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncTriggerType.kt`:

```kotlin
package com.chamchamcham.domain.policy

enum class PolicySyncTriggerType {
    SCHEDULED,
    ADMIN
}
```

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJob.kt`:

```kotlin
package com.chamchamcham.domain.policy

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "policy_sync_job")
class PolicySyncJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val source: PolicySource,

    @Column(name = "target_year", nullable = false, length = 4)
    val targetYear: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    val triggerType: PolicySyncTriggerType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PolicySyncJobStatus = PolicySyncJobStatus.RUNNING,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0,

    @Column(name = "synced_count", nullable = false)
    var syncedCount: Int = 0,

    @Column(name = "detail_success_count", nullable = false)
    var detailSuccessCount: Int = 0,

    @Column(name = "detail_failure_count", nullable = false)
    var detailFailureCount: Int = 0,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,

    @Column(name = "created_by_member_id", columnDefinition = "uuid")
    val createdByMemberId: UUID? = null,
) : BaseTimeEntity() {
    fun succeed(totalCount: Int, syncedCount: Int, detailSuccessCount: Int, detailFailureCount: Int) {
        this.status = PolicySyncJobStatus.SUCCEEDED
        this.totalCount = totalCount
        this.syncedCount = syncedCount
        this.detailSuccessCount = detailSuccessCount
        this.detailFailureCount = detailFailureCount
        this.errorMessage = null
        this.finishedAt = LocalDateTime.now()
    }

    fun fail(message: String) {
        this.status = PolicySyncJobStatus.FAILED
        this.errorMessage = message.take(1000)
        this.finishedAt = LocalDateTime.now()
    }
}
```

- [ ] **Step 4: Extend policy program and recommendation entities**

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgram.kt` so the constructor and methods include these fields and methods:

```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 32)
var source: PolicySource = PolicySource.NONGUP_EZ

@Column(name = "external_id", nullable = false, length = 64)
var externalId: String = ""

@Column(name = "source_year", nullable = false, length = 4)
var sourceYear: String = ""

@Column(name = "summary", columnDefinition = "text")
var summary: String? = null

@Column(name = "agency_name", nullable = false, length = 255)
var agencyName: String = ""

@Column(name = "department_name", length = 255)
var departmentName: String? = null

@Column(name = "online_apply_available", nullable = false)
var onlineApplyAvailable: Boolean = false

@Column(name = "application_url", length = 2048)
var applicationUrl: String? = null

@Column(name = "application_period_label", nullable = false, length = 19)
var applicationPeriodLabel: String = "접수기관문의"

@Column(name = "application_period_notice", length = 255)
var applicationPeriodNotice: String? = null

@Column(name = "eligibility_original", columnDefinition = "text")
var eligibilityOriginal: String? = null

@Column(name = "eligibility_summary", nullable = false, length = 19)
var eligibilitySummary: String = "상세 자격 확인"

@Column(name = "benefit_original", columnDefinition = "text")
var benefitOriginal: String? = null

@Column(name = "benefit_summary", nullable = false, length = 19)
var benefitSummary: String = "상세 지원 확인"

@Column(name = "purpose", columnDefinition = "text")
var purpose: String? = null

@Column(name = "application_method", columnDefinition = "text")
var applicationMethod: String? = null

@Column(name = "required_documents", columnDefinition = "text")
var requiredDocuments: String? = null

@Column(name = "selection_criteria", columnDefinition = "text")
var selectionCriteria: String? = null

@Column(name = "detail_synced", nullable = false)
var detailSynced: Boolean = false

@Column(nullable = false)
var recommendable: Boolean = false

@Column(name = "target_tags_json", nullable = false, columnDefinition = "text")
var targetTagsJson: String = "[]"

@Column(name = "crop_tags_json", nullable = false, columnDefinition = "text")
var cropTagsJson: String = "[]"

@Column(name = "region_tags_json", nullable = false, columnDefinition = "text")
var regionTagsJson: String = "[]"

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "last_synced_job_id")
var lastSyncedJob: PolicySyncJob? = null

@Column(name = "raw_payload", nullable = false, columnDefinition = "text")
var rawPayload: String = "{}"
```

Change the existing `targetManagementType` column to nullable:

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "target_management_type", length = 32)
var targetManagementType: ManagementType? = targetManagementType
```

Add these methods to `PolicyProgram`:

```kotlin
fun applyListFields(
    source: PolicySource,
    externalId: String,
    sourceYear: String,
    title: String,
    summary: String?,
    region: String,
    sourceUrl: String?,
    agencyName: String,
    lastSyncedJob: PolicySyncJob
) {
    this.source = source
    this.externalId = externalId
    this.sourceYear = sourceYear
    this.title = title
    this.summary = summary
    this.body = summary ?: body
    this.region = region
    this.sourceUrl = sourceUrl
    this.agencyName = agencyName
    this.lastSyncedJob = lastSyncedJob
}

fun applyDetailFields(
    body: String,
    purpose: String?,
    eligibilityOriginal: String?,
    eligibilitySummary: String,
    benefitOriginal: String?,
    benefitSummary: String,
    applyStartsOn: LocalDate?,
    applyEndsOn: LocalDate?,
    applicationPeriodLabel: String,
    applicationPeriodNotice: String?,
    applicationMethod: String?,
    requiredDocuments: String?,
    selectionCriteria: String?,
    departmentName: String?,
    onlineApplyAvailable: Boolean,
    applicationUrl: String?,
    targetTagsJson: String,
    cropTagsJson: String,
    regionTagsJson: String,
    rawPayload: String,
    recommendable: Boolean,
    lastSyncedJob: PolicySyncJob
) {
    require(eligibilitySummary.length <= 19)
    require(benefitSummary.length <= 19)
    require(applicationPeriodLabel.length <= 19)
    this.body = body
    this.purpose = purpose
    this.eligibilityOriginal = eligibilityOriginal
    this.eligibilitySummary = eligibilitySummary
    this.benefitOriginal = benefitOriginal
    this.benefitSummary = benefitSummary
    this.applyStartsOn = applyStartsOn
    this.applyEndsOn = applyEndsOn
    this.applicationPeriodLabel = applicationPeriodLabel
    this.applicationPeriodNotice = applicationPeriodNotice
    this.applicationMethod = applicationMethod
    this.requiredDocuments = requiredDocuments
    this.selectionCriteria = selectionCriteria
    this.departmentName = departmentName
    this.onlineApplyAvailable = onlineApplyAvailable
    this.applicationUrl = applicationUrl
    this.targetTagsJson = targetTagsJson
    this.cropTagsJson = cropTagsJson
    this.regionTagsJson = regionTagsJson
    this.rawPayload = rawPayload
    this.detailSynced = true
    this.recommendable = recommendable
    this.lastSyncedJob = lastSyncedJob
}

fun markDetailSyncFailed(rawPayload: String) {
    this.detailSynced = false
    this.recommendable = false
    this.rawPayload = rawPayload
}

fun isOpenOn(today: LocalDate): Boolean {
    return applyEndsOn == null || !applyEndsOn!!.isBefore(today)
}
```

Because the existing `title`, `body`, `region`, `applyStartsOn`, `applyEndsOn`, and `sourceUrl` properties are immutable `val`s today, change those properties to `var` in the same file.

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendation.kt` by adding:

```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "source_sync_job_id", nullable = false)
val sourceSyncJob: PolicySyncJob,
```

- [ ] **Step 5: Add repositories and farm lookup**

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicySyncJobRepository.kt`:

```kotlin
package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicySyncJobRepository : JpaRepository<PolicySyncJob, UUID> {
    fun findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
        source: PolicySource,
        status: PolicySyncJobStatus
    ): PolicySyncJob?
}
```

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyProgramRepository.kt`:

```kotlin
package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface PolicyProgramRepository : JpaRepository<PolicyProgram, UUID> {
    fun findBySourceAndExternalIdAndSourceYear(
        source: PolicySource,
        externalId: String,
        sourceYear: String
    ): PolicyProgram?

    @Query(
        """
        select p
        from PolicyProgram p
        where p.lastSyncedJob.id = :syncJobId
          and p.sourceYear = :sourceYear
          and p.detailSynced = true
          and p.recommendable = true
          and (p.applyEndsOn is null or p.applyEndsOn >= :today)
        """
    )
    fun findRecommendableCandidates(
        @Param("syncJobId") syncJobId: UUID,
        @Param("sourceYear") sourceYear: String,
        @Param("today") today: LocalDate
    ): List<PolicyProgram>

    fun findByIdAndDetailSyncedTrueAndRecommendableTrue(id: UUID): PolicyProgram?
}
```

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationRepository.kt`:

```kotlin
package com.chamchamcham.domain.policy

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRecommendationRepository : JpaRepository<PolicyRecommendation, UUID> {
    fun existsByMember_IdAndSourceSyncJob_Id(memberId: UUID, sourceSyncJobId: UUID): Boolean
    fun deleteByMember_Id(memberId: UUID)
}
```

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`:

```kotlin
interface FarmRepository : JpaRepository<Farm, UUID> {
    fun findByOwner_Id(ownerId: UUID): List<Farm>
}
```

- [ ] **Step 6: Add recommendation query repository**

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepository.kt`:

```kotlin
package com.chamchamcham.domain.policy

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface PolicyRecommendationQueryRepository {
    fun findPage(condition: SearchCondition): SearchResult

    data class SearchCondition(
        val memberId: UUID,
        val sourceSyncJobId: UUID,
        val cursor: Cursor?,
        val size: Int
    )

    data class Cursor(
        val score: BigDecimal,
        val applyEndsOn: LocalDate?,
        val id: UUID
    )

    data class SearchResult(
        val rows: List<PolicyRecommendation>
    )
}
```

Create `backend/domain/src/main/kotlin/com/chamchamcham/domain/policy/PolicyRecommendationQueryRepositoryImpl.kt`:

```kotlin
package com.chamchamcham.domain.policy

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PolicyRecommendationQueryRepositoryImpl(
    private val entityManager: EntityManager
) : PolicyRecommendationQueryRepository {
    override fun findPage(
        condition: PolicyRecommendationQueryRepository.SearchCondition
    ): PolicyRecommendationQueryRepository.SearchResult {
        val maxDate = LocalDate.of(9999, 12, 31)
        val where = mutableListOf("r.member.id = :memberId", "r.sourceSyncJob.id = :sourceSyncJobId")
        val params = linkedMapOf<String, Any>(
            "memberId" to condition.memberId,
            "sourceSyncJobId" to condition.sourceSyncJobId,
            "maxDate" to maxDate
        )

        condition.cursor?.let { cursor ->
            where += """
                (
                    r.score < :cursorScore
                    or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) > :cursorApplyEndsOn)
                    or (r.score = :cursorScore and coalesce(r.policyProgram.applyEndsOn, :maxDate) = :cursorApplyEndsOn and r.id > :cursorId)
                )
            """.trimIndent()
            params["cursorScore"] = cursor.score
            params["cursorApplyEndsOn"] = cursor.applyEndsOn ?: maxDate
            params["cursorId"] = cursor.id
        }

        val query = entityManager.createQuery(
            """
            select r
            from PolicyRecommendation r
            join fetch r.policyProgram p
            where ${where.joinToString(" and ")}
            order by r.score desc, coalesce(p.applyEndsOn, :maxDate) asc, r.id asc
            """.trimIndent(),
            PolicyRecommendation::class.java
        )
        params.forEach(query::setParameter)
        query.maxResults = condition.size

        return PolicyRecommendationQueryRepository.SearchResult(query.resultList)
    }
}
```

- [ ] **Step 7: Add schema SQL document**

Create `docs/database/2026-07-07-policy-recommendation-schema.sql` with PostgreSQL DDL for review:

```sql
create table if not exists policy_sync_job (
    id uuid primary key,
    source varchar(32) not null,
    target_year varchar(4) not null,
    trigger_type varchar(32) not null,
    status varchar(32) not null,
    started_at timestamp not null,
    finished_at timestamp,
    total_count integer not null default 0,
    synced_count integer not null default 0,
    detail_success_count integer not null default 0,
    detail_failure_count integer not null default 0,
    error_message varchar(1000),
    created_by_member_id uuid,
    created_at timestamp not null,
    updated_at timestamp not null
);

alter table policy_program add column if not exists source varchar(32) not null default 'NONGUP_EZ';
alter table policy_program add column if not exists external_id varchar(64) not null default '';
alter table policy_program add column if not exists source_year varchar(4) not null default '';
alter table policy_program add column if not exists summary text;
alter table policy_program alter column target_management_type drop not null;
alter table policy_program add column if not exists agency_name varchar(255) not null default '';
alter table policy_program add column if not exists department_name varchar(255);
alter table policy_program add column if not exists online_apply_available boolean not null default false;
alter table policy_program add column if not exists application_url varchar(2048);
alter table policy_program add column if not exists application_period_label varchar(19) not null default '접수기관문의';
alter table policy_program add column if not exists application_period_notice varchar(255);
alter table policy_program add column if not exists eligibility_original text;
alter table policy_program add column if not exists eligibility_summary varchar(19) not null default '상세 자격 확인';
alter table policy_program add column if not exists benefit_original text;
alter table policy_program add column if not exists benefit_summary varchar(19) not null default '상세 지원 확인';
alter table policy_program add column if not exists purpose text;
alter table policy_program add column if not exists application_method text;
alter table policy_program add column if not exists required_documents text;
alter table policy_program add column if not exists selection_criteria text;
alter table policy_program add column if not exists detail_synced boolean not null default false;
alter table policy_program add column if not exists recommendable boolean not null default false;
alter table policy_program add column if not exists target_tags_json text not null default '[]';
alter table policy_program add column if not exists crop_tags_json text not null default '[]';
alter table policy_program add column if not exists region_tags_json text not null default '[]';
alter table policy_program add column if not exists last_synced_job_id uuid references policy_sync_job(id);
alter table policy_program add column if not exists raw_payload text not null default '{}';

create unique index if not exists uk_policy_program_source_external_year
    on policy_program(source, external_id, source_year);

alter table policy_recommendation add column if not exists source_sync_job_id uuid references policy_sync_job(id);
create index if not exists ix_policy_recommendation_member_sync_score
    on policy_recommendation(member_id, source_sync_job_id, score desc, id asc);
```

- [ ] **Step 8: Run domain tests and compile**

Run:

```bash
cd backend
./gradlew :domain:test
```

Expected: PASS.

- [ ] **Step 9: Commit domain task**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/policy backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt backend/domain/src/test/kotlin/com/chamchamcham/domain/policy docs/database/2026-07-07-policy-recommendation-schema.sql
git commit -m "feat(policy): 농업e지 정책 저장 모델 확장" \
  -m "농업e지 원문, 동기화 상태, 카드 요약, 추천 최신성 판단에 필요한 도메인 모델과 검토용 스키마 문서를 추가한다." \
  -m "Constraint: Flyway가 없어 dev/prod에는 별도 스키마 적용이 필요하다" \
  -m "Rejected: 대상/품목/지역 별도 테이블 분리 | MVP에서는 JSON 텍스트 필드로 충분하다" \
  -m "Confidence: high" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :domain:test"
```

---

### Task 2: NongupEZ Source Client And Fixture Parser Tests

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzHttpTransport.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/JavaNetNongupEzHttpTransport.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzPolicySourceClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzSourceModels.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicySourceClientTest.kt`
- Fixtures:
  - `backend/application/src/test/resources/policy/nongupez/conditions.json`
  - `backend/application/src/test/resources/policy/nongupez/list-page.json`
  - `backend/application/src/test/resources/policy/nongupez/detail-success.json`
  - `backend/application/src/test/resources/policy/nongupez/detail-missing-optional-fields.json`

**Interfaces:**
- Produces: `NongupEzPolicySourceClient.detectLatestYear(): String`
- Produces: `NongupEzPolicySourceClient.fetchPrograms(year: String): List<NongupEzPolicyListItem>`
- Produces: `NongupEzPolicySourceClient.fetchDetail(externalId: String, year: String): NongupEzPolicyDetail`

- [ ] **Step 1: Add source fixtures**

Create `backend/application/src/test/resources/policy/nongupez/conditions.json`:

```json
{
  "result": {
    "SRCH_BIZ_YR": [
      { "cd": "0000", "cdNm": "전체" },
      { "cd": "2025", "cdNm": "2025년" },
      { "cd": "2026", "cdNm": "2026년" }
    ]
  }
}
```

Create `backend/application/src/test/resources/policy/nongupez/list-page.json`:

```json
{
  "result": {
    "totalCount": 1,
    "list": [
      {
        "afbzCd": "AB000009",
        "bizYr": "2026",
        "afbzNm": "친환경농업직불",
        "bizCn": "친환경농업 실천 농업인에게 직불금을 지원합니다.",
        "bizTkcgInstNm": "농림축산식품부",
        "bizAplyBgngYmd": "20260325",
        "bizAplyEndYmd": "20260630"
      }
    ]
  }
}
```

Create `backend/application/src/test/resources/policy/nongupez/detail-success.json`:

```json
{
  "result": {
    "afbzCd": "AB000009",
    "bizYr": "2026",
    "afbzNm": "친환경농업직불",
    "bizPrpsCn": "친환경농업 확산",
    "bizCn": "친환경농업 실천 농업인에게 직불금을 지원합니다.",
    "bizSprtQlfcCn": "농업경영정보를 등록한 농업·임업인·법인으로 친환경인증을 받은 자",
    "bizSprtCn": "인증단계 및 품목에 따라 직불금을 지급",
    "bizAplyBgngYmd": "20260325",
    "bizAplyEndYmd": "20260630",
    "bizAplyMthdCn": "읍면동 방문 신청",
    "bizRqdcCn": "사업 신청서",
    "bizSlctnCrtrCn": "자격 요건 검토",
    "bizTkcgInstNm": "농림축산식품부",
    "bizPicList": [
      {
        "instNm": "농림축산식품부",
        "deptNm": "친환경농업과",
        "telno": "044-201-2434"
      }
    ],
    "bizAtchFileList": [
      {
        "atchFileNm": "2026년 친환경농업직불 시행지침.pdf",
        "fileExtnNm": "pdf",
        "fileSz": 1537272,
        "atchFileId": "FILE-1"
      }
    ]
  }
}
```

Create `backend/application/src/test/resources/policy/nongupez/detail-missing-optional-fields.json`:

```json
{
  "result": {
    "afbzCd": "AB000010",
    "bizYr": "2026",
    "afbzNm": "청년농 영농정착지원",
    "bizSprtQlfcCn": null,
    "bizSprtCn": null,
    "bizTkcgInstNm": null
  }
}
```

- [ ] **Step 2: Write failing source client tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicySourceClientTest.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.application.policy.source.NongupEzHttpTransport
import com.chamchamcham.application.policy.source.NongupEzPolicySourceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class NongupEzPolicySourceClientTest {
    @Test
    fun `detectLatestYear ignores all option and selects max numeric year`() {
        val client = NongupEzPolicySourceClient(fakeTransport("conditions" to fixture("conditions.json")))

        val latestYear = client.detectLatestYear()

        assertThat(latestYear).isEqualTo("2026")
    }

    @Test
    fun `fetchPrograms parses list page into source list item`() {
        val client = NongupEzPolicySourceClient(fakeTransport("retrieveListBizSrch" to fixture("list-page.json")))

        val programs = client.fetchPrograms("2026")

        assertThat(programs).hasSize(1)
        assertThat(programs.first().externalId).isEqualTo("AB000009")
        assertThat(programs.first().title).isEqualTo("친환경농업직불")
        assertThat(programs.first().applyEndsOn?.toString()).isEqualTo("2026-06-30")
    }

    @Test
    fun `fetchDetail parses long text contacts and attachments`() {
        val client = NongupEzPolicySourceClient(fakeTransport("findBizSrchDtl" to fixture("detail-success.json")))

        val detail = client.fetchDetail("AB000009", "2026")

        assertThat(detail.eligibility).contains("농업경영정보")
        assertThat(detail.contacts.first().departmentName).isEqualTo("친환경농업과")
        assertThat(detail.attachments.first().fileName).contains("시행지침")
    }

    @Test
    fun `fetchDetail tolerates null optional fields`() {
        val client = NongupEzPolicySourceClient(
            fakeTransport("findBizSrchDtl" to fixture("detail-missing-optional-fields.json"))
        )

        val detail = client.fetchDetail("AB000010", "2026")

        assertThat(detail.agencyName).isEqualTo("농업e지")
        assertThat(detail.eligibility).isNull()
        assertThat(detail.benefit).isNull()
    }

    private fun fakeTransport(vararg responses: Pair<String, String>): NongupEzHttpTransport {
        val responseMap = responses.toMap()
        return NongupEzHttpTransport { path, _ ->
            responseMap.entries.firstOrNull { (key) -> path.contains(key) }?.value
                ?: error("No fixture response for $path")
        }
    }

    private fun fixture(name: String): String {
        return javaClass.classLoader
            .getResourceAsStream("policy/nongupez/$name")!!
            .readAllBytes()
            .toString(StandardCharsets.UTF_8)
    }
}
```

- [ ] **Step 3: Run source client tests and verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.NongupEzPolicySourceClientTest"
```

Expected: FAIL because `NongupEzPolicySourceClient` does not exist.

- [ ] **Step 4: Add source models and transport boundary**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzHttpTransport.kt`:

```kotlin
package com.chamchamcham.application.policy.source

fun interface NongupEzHttpTransport {
    fun post(path: String, form: Map<String, String>): String
}
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzSourceModels.kt` with application-friendly models:

```kotlin
package com.chamchamcham.application.policy.source

import java.time.LocalDate

data class NongupEzPolicyListItem(
    val externalId: String,
    val sourceYear: String,
    val title: String,
    val summary: String?,
    val agencyName: String,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val rawJson: String
)

data class NongupEzPolicyDetail(
    val externalId: String,
    val sourceYear: String,
    val title: String,
    val purpose: String?,
    val summary: String?,
    val eligibility: String?,
    val benefit: String?,
    val applyStartsOn: LocalDate?,
    val applyEndsOn: LocalDate?,
    val applicationMethod: String?,
    val requiredDocuments: String?,
    val selectionCriteria: String?,
    val agencyName: String,
    val contacts: List<NongupEzPolicyContact>,
    val attachments: List<NongupEzPolicyAttachment>,
    val rawJson: String
)

data class NongupEzPolicyContact(
    val agencyName: String?,
    val departmentName: String?,
    val phoneNumber: String?
)

data class NongupEzPolicyAttachment(
    val fileName: String?,
    val extension: String?,
    val sizeBytes: Long?,
    val url: String?
)
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/JavaNetNongupEzHttpTransport.kt`:

```kotlin
package com.chamchamcham.application.policy.source

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class JavaNetNongupEzHttpTransport(
    @Value("\${policy.nongup-ez.base-url:https://www.nongupez.go.kr}") private val baseUrl: String,
    @Value("\${policy.nongup-ez.timeout-millis:10000}") private val timeoutMillis: Long
) : NongupEzHttpTransport {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .build()

    override fun post(path: String, form: Map<String, String>): String {
        val encodedForm = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(encodedForm))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("NongupEZ HTTP ${response.statusCode()} for $path")
        }
        return response.body()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
```

- [ ] **Step 5: Implement source client parser**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/source/NongupEzPolicySourceClient.kt`:

```kotlin
package com.chamchamcham.application.policy.source

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class NongupEzPolicySourceClient(
    private val transport: NongupEzHttpTransport
) {
    private val objectMapper = jacksonObjectMapper()
    private val basicDate = DateTimeFormatter.BASIC_ISO_DATE

    fun detectLatestYear(): String {
        val json = transport.post("/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd", emptyMap())
        val root = objectMapper.readTree(json)
        return root.path("result").path("SRCH_BIZ_YR")
            .mapNotNull { node -> node.path("cd").asText(null) }
            .filter { it != "0000" && it.length == 4 && it.all(Char::isDigit) }
            .maxOrNull()
            ?: error("NongupEZ business year condition is empty")
    }

    fun fetchPrograms(year: String): List<NongupEzPolicyListItem> {
        val pageUnit = 1000
        val items = mutableListOf<NongupEzPolicyListItem>()
        var pageIndex = 1
        var totalCount = Int.MAX_VALUE
        while (items.size < totalCount) {
            val json = transport.post(
                "/nsm/bizAply/wholeBiz/retrieveListBizSrch",
                mapOf("bizYr" to year, "pageIndex" to pageIndex.toString(), "pageUnit" to pageUnit.toString())
            )
            val root = objectMapper.readTree(json)
            totalCount = root.path("result").path("totalCount").asInt(root.path("result").path("list").size())
            val pageItems = root.path("result").path("list").map { item -> toListItem(item, year) }
            if (pageItems.isEmpty()) {
                break
            }
            items += pageItems
            pageIndex += 1
        }
        return items
    }

    private fun toListItem(item: JsonNode, year: String): NongupEzPolicyListItem {
        return NongupEzPolicyListItem(
            externalId = item.text("afbzCd"),
            sourceYear = item.text("bizYr").ifBlank { year },
            title = item.text("afbzNm"),
            summary = item.nullableText("bizCn"),
            agencyName = item.nullableText("bizTkcgInstNm") ?: "농업e지",
            applyStartsOn = item.localDate("bizAplyBgngYmd"),
            applyEndsOn = item.localDate("bizAplyEndYmd"),
            rawJson = objectMapper.writeValueAsString(item)
        )
    }

    fun fetchDetail(externalId: String, year: String): NongupEzPolicyDetail {
        val json = transport.post(
            "/nsm/bizAply/cstBiz/findBizSrchDtl",
            mapOf("afbzCd" to externalId, "bizYr" to year)
        )
        val detail = objectMapper.readTree(json).path("result")
        return NongupEzPolicyDetail(
            externalId = detail.text("afbzCd").ifBlank { externalId },
            sourceYear = detail.text("bizYr").ifBlank { year },
            title = detail.text("afbzNm"),
            purpose = detail.nullableText("bizPrpsCn"),
            summary = detail.nullableText("bizCn"),
            eligibility = detail.nullableText("bizSprtQlfcCn"),
            benefit = detail.nullableText("bizSprtCn"),
            applyStartsOn = detail.localDate("bizAplyBgngYmd"),
            applyEndsOn = detail.localDate("bizAplyEndYmd"),
            applicationMethod = detail.nullableText("bizAplyMthdCn"),
            requiredDocuments = detail.nullableText("bizRqdcCn"),
            selectionCriteria = detail.nullableText("bizSlctnCrtrCn"),
            agencyName = detail.nullableText("bizTkcgInstNm") ?: "농업e지",
            contacts = detail.path("bizPicList").map { contact ->
                NongupEzPolicyContact(
                    agencyName = contact.nullableText("instNm"),
                    departmentName = contact.nullableText("deptNm"),
                    phoneNumber = contact.nullableText("telno")
                )
            },
            attachments = detail.path("bizAtchFileList").map { file ->
                NongupEzPolicyAttachment(
                    fileName = file.nullableText("atchFileNm"),
                    extension = file.nullableText("fileExtnNm"),
                    sizeBytes = file.path("fileSz").takeIf { it.isNumber }?.asLong(),
                    url = file.nullableText("url")
                )
            },
            rawJson = objectMapper.writeValueAsString(detail)
        )
    }

    private fun JsonNode.text(field: String): String =
        nullableText(field) ?: ""

    private fun JsonNode.nullableText(field: String): String? =
        path(field).takeUnless { it.isMissingNode || it.isNull }?.asText()?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonNode.localDate(field: String): LocalDate? =
        nullableText(field)?.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let {
            LocalDate.parse(it, basicDate)
        }
}
```

- [ ] **Step 6: Run source client tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.NongupEzPolicySourceClientTest"
```

Expected: PASS.

- [ ] **Step 7: Commit source client task**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy/source backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicySourceClientTest.kt backend/application/src/test/resources/policy/nongupez
git commit -m "feat(policy): 농업e지 수집 클라이언트 추가" \
  -m "농업e지 공개 JSON 호출과 파싱을 application 경계 안에 격리하고 단위 테스트는 fixture만 사용한다." \
  -m "Constraint: NongupEZ has no separately documented public Open API for this payload shape" \
  -m "Rejected: Unit tests with live NongupEZ calls | source availability would make tests flaky" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.policy.NongupEzPolicySourceClientTest\""
```

---

### Task 3: Card Text, Tag Extraction, And Member Matching Signals

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/TextListJsonCodec.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractor.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyCardTextGenerator.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcher.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyCardTextGeneratorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractorTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcherTest.kt`

**Interfaces:**
- Produces: `PolicyCardTextGenerator.eligibilitySummary(text: String?): String`
- Produces: `PolicyCardTextGenerator.benefitSummary(text: String?): String`
- Produces: `PolicyCardTextGenerator.periodLabel(start: LocalDate?, end: LocalDate?, notice: String?): String`
- Produces: `NongupEzPolicyTagExtractor.extract(detail: NongupEzPolicyDetail): ExtractedPolicyTags`
- Produces: `PolicyRegionMatcher.extractRegionTokens(addresses: Collection<String?>): Set<String>`

- [ ] **Step 1: Write failing text and tag tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyCardTextGeneratorTest.kt`:

```kotlin
package com.chamchamcham.application.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PolicyCardTextGeneratorTest {
    private val generator = PolicyCardTextGenerator()

    @Test
    fun `eligibility summary is deterministic and within nineteen characters`() {
        val summary = generator.eligibilitySummary("농업경영정보를 등록한 농업·임업인·법인으로 친환경인증을 받은 자")

        assertThat(summary).isEqualTo("친환경 인증 농업인")
        assertThat(summary.length).isLessThanOrEqualTo(19)
    }

    @Test
    fun `benefit summary uses fallback for blank source text`() {
        val summary = generator.benefitSummary("   ")

        assertThat(summary).isEqualTo("상세 지원 확인")
        assertThat(summary.length).isLessThanOrEqualTo(19)
    }

    @Test
    fun `period label is nineteen characters or fewer`() {
        val label = generator.periodLabel(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 6, 30), null)

        assertThat(label).isEqualTo("2026.03.25~06.30")
        assertThat(label.length).isLessThanOrEqualTo(19)
    }

    @Test
    fun `period label preserves agency inquiry notice`() {
        val label = generator.periodLabel(null, null, "접수기관문의")

        assertThat(label).isEqualTo("접수기관문의")
    }
}
```

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractorTest.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.application.policy.source.NongupEzPolicyDetail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NongupEzPolicyTagExtractorTest {
    private val extractor = NongupEzPolicyTagExtractor()

    @Test
    fun `extracts young farmer return farmer medicinal and national tags`() {
        val detail = detail(
            title = "청년농 귀농 약용작물 지원",
            eligibility = "만 40세 미만 청년농업인 및 귀농인",
            benefit = "약용작물 재배시설 지원",
            agencyName = "농림축산식품부"
        )

        val tags = extractor.extract(detail)

        assertThat(tags.targetTags).contains("YOUNG_FARMER", "RETURNING_FARMER")
        assertThat(tags.cropTags).contains("MEDICINAL_CROP")
        assertThat(tags.regionTags).contains("전국")
    }

    @Test
    fun `extracts regional tag from agency name`() {
        val detail = detail(
            title = "충청북도 특용작물 지원",
            eligibility = "충청북도 농업인",
            benefit = "특용작물 생산 지원",
            agencyName = "충청북도"
        )

        val tags = extractor.extract(detail)

        assertThat(tags.cropTags).contains("SPECIAL_CROP")
        assertThat(tags.regionTags).contains("충청북도")
    }

    private fun detail(title: String, eligibility: String, benefit: String, agencyName: String): NongupEzPolicyDetail =
        NongupEzPolicyDetail(
            externalId = "AB",
            sourceYear = "2026",
            title = title,
            purpose = null,
            summary = null,
            eligibility = eligibility,
            benefit = benefit,
            applyStartsOn = null,
            applyEndsOn = null,
            applicationMethod = null,
            requiredDocuments = null,
            selectionCriteria = null,
            agencyName = agencyName,
            contacts = emptyList(),
            attachments = emptyList(),
            rawJson = "{}"
        )
}
```

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcherTest.kt`:

```kotlin
package com.chamchamcham.application.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolicyRegionMatcherTest {
    private val matcher = PolicyRegionMatcher()

    @Test
    fun `extracts province and city tokens from farm addresses`() {
        val tokens = matcher.extractRegionTokens(
            listOf("충청북도 제천시 봉양읍 의암로 1", "충북 제천시 봉양읍 1")
        )

        assertThat(tokens).contains("충청북도", "제천시")
    }

    @Test
    fun `national policy matches any farm region`() {
        assertThat(matcher.matches(setOf("충청북도", "제천시"), setOf("전국"))).isTrue()
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicyCardTextGeneratorTest" --tests "com.chamchamcham.application.policy.NongupEzPolicyTagExtractorTest" --tests "com.chamchamcham.application.policy.PolicyRegionMatcherTest"
```

Expected: FAIL because text, tag, and matcher classes do not exist.

- [ ] **Step 3: Add JSON list codec**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/TextListJsonCodec.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component

@Component
class TextListJsonCodec {
    private val objectMapper = jacksonObjectMapper()

    fun encode(values: Collection<String>): String {
        return objectMapper.writeValueAsString(values.map(String::trim).filter(String::isNotEmpty).distinct())
    }

    fun decode(json: String?): Set<String> {
        if (json.isNullOrBlank()) {
            return emptySet()
        }
        return runCatching { objectMapper.readValue<List<String>>(json).toSet() }.getOrDefault(emptySet())
    }
}
```

- [ ] **Step 4: Add card text generator**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyCardTextGenerator.kt`:

```kotlin
package com.chamchamcham.application.policy

import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PolicyCardTextGenerator {
    fun eligibilitySummary(text: String?): String {
        val source = text.orEmpty()
        return when {
            source.contains("친환경인증") || source.contains("친환경 인증") -> "친환경 인증 농업인"
            source.contains("청년") -> "청년 농업인"
            source.contains("귀농") -> "귀농 예정 농업인"
            source.contains("농업경영정보") -> "경영정보 등록 농업인"
            source.isBlank() -> "상세 자격 확인"
            else -> compact(source, "상세 자격 확인")
        }
    }

    fun benefitSummary(text: String?): String {
        val source = text.orEmpty()
        return when {
            source.contains("직불금") -> "인증단계별 직불금"
            source.contains("시설") -> "재배시설 지원"
            source.contains("교육") -> "교육 프로그램 지원"
            source.contains("융자") -> "정책자금 융자"
            source.isBlank() -> "상세 지원 확인"
            else -> compact(source, "상세 지원 확인")
        }
    }

    fun periodLabel(start: LocalDate?, end: LocalDate?, notice: String?): String {
        val sourceNotice = notice?.trim()?.takeIf(String::isNotEmpty)
        val label = when {
            start != null && end != null -> "${start.formatFull()}~${end.formatMonthDay()}"
            sourceNotice != null -> sourceNotice
            end != null -> "${end.formatFull()}까지"
            else -> "접수기관문의"
        }
        return compact(label, "접수기관문의")
    }

    private fun compact(text: String, fallback: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) {
            return fallback
        }
        return normalized.take(MAX_CARD_CHARS)
    }

    private fun LocalDate.formatFull(): String = "%04d.%02d.%02d".format(year, monthValue, dayOfMonth)
    private fun LocalDate.formatMonthDay(): String = "%02d.%02d".format(monthValue, dayOfMonth)

    private companion object {
        const val MAX_CARD_CHARS = 19
    }
}
```

- [ ] **Step 5: Add tag extractor and region matcher**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractor.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.application.policy.source.NongupEzPolicyDetail
import org.springframework.stereotype.Component

@Component
class NongupEzPolicyTagExtractor {
    fun extract(detail: NongupEzPolicyDetail): ExtractedPolicyTags {
        val text = listOfNotNull(detail.title, detail.summary, detail.eligibility, detail.benefit, detail.agencyName)
            .joinToString(" ")
        val targetTags = linkedSetOf<String>()
        val cropTags = linkedSetOf<String>()
        val regionTags = linkedSetOf<String>()

        if (text.contains("청년")) targetTags += "YOUNG_FARMER"
        if (text.contains("귀농") || text.contains("귀촌")) targetTags += "RETURNING_FARMER"
        if (text.contains("농업경영정보") || text.contains("농업경영체")) targetTags += "REGISTERED_FARMER"
        if (text.contains("법인")) targetTags += "AGRICULTURAL_CORPORATION"
        if (text.contains("약용작물")) cropTags += "MEDICINAL_CROP"
        if (text.contains("특용작물")) cropTags += "SPECIAL_CROP"
        if (text.contains("친환경")) cropTags += "ECO_FRIENDLY"

        regionTags += provinceNames.filter { text.contains(it) }
        if (regionTags.isEmpty() && detail.agencyName.contains("농림축산식품부")) {
            regionTags += "전국"
        }

        return ExtractedPolicyTags(
            targetTags = targetTags,
            cropTags = cropTags,
            regionTags = regionTags.ifEmpty { linkedSetOf("전국") }
        )
    }

    private val provinceNames = listOf(
        "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시",
        "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도",
        "전라남도", "경상북도", "경상남도", "제주특별자치도"
    )
}

data class ExtractedPolicyTags(
    val targetTags: Set<String>,
    val cropTags: Set<String>,
    val regionTags: Set<String>
)
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcher.kt`:

```kotlin
package com.chamchamcham.application.policy

import org.springframework.stereotype.Component

@Component
class PolicyRegionMatcher {
    fun extractRegionTokens(addresses: Collection<String?>): Set<String> {
        val joined = addresses.filterNotNull().joinToString(" ")
        val tokens = linkedSetOf<String>()
        provinceAliases.forEach { (canonical, aliases) ->
            if (aliases.any(joined::contains)) {
                tokens += canonical
            }
        }
        cityPattern.findAll(joined).forEach { match -> tokens += match.value }
        return tokens
    }

    fun matches(memberRegions: Set<String>, policyRegions: Set<String>): Boolean {
        return policyRegions.contains("전국") || memberRegions.any(policyRegions::contains)
    }

    private val provinceAliases = linkedMapOf(
        "충청북도" to listOf("충청북도", "충북"),
        "충청남도" to listOf("충청남도", "충남"),
        "경기도" to listOf("경기도", "경기"),
        "강원특별자치도" to listOf("강원특별자치도", "강원"),
        "전북특별자치도" to listOf("전북특별자치도", "전북"),
        "전라남도" to listOf("전라남도", "전남"),
        "경상북도" to listOf("경상북도", "경북"),
        "경상남도" to listOf("경상남도", "경남"),
        "제주특별자치도" to listOf("제주특별자치도", "제주")
    )
    private val cityPattern = Regex("[가-힣]+(?:시|군|구)")
}
```

`PolicyMemberProfileReader.kt` is owned by Task 5, where repository dependencies are assembled.

- [ ] **Step 6: Run text and tag tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicyCardTextGeneratorTest" --tests "com.chamchamcham.application.policy.NongupEzPolicyTagExtractorTest" --tests "com.chamchamcham.application.policy.PolicyRegionMatcherTest"
```

Expected: PASS.

- [ ] **Step 7: Commit text and tag task**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/policy backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyCardTextGeneratorTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/NongupEzPolicyTagExtractorTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRegionMatcherTest.kt
git commit -m "feat(policy): 정책 카드 문구와 추천 태그 추출 추가" \
  -m "농업e지 원문에서 카드 요약과 추천용 태그를 규칙 기반으로 생성한다." \
  -m "Constraint: 카드 요약 세 필드는 19자 이하를 보장해야 한다" \
  -m "Rejected: LLM 요약 | 비용과 비결정성이 MVP 요구에 맞지 않는다" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.policy.PolicyCardTextGeneratorTest\" --tests \"com.chamchamcham.application.policy.NongupEzPolicyTagExtractorTest\" --tests \"com.chamchamcham.application.policy.PolicyRegionMatcherTest\""
```

---

### Task 4: Policy Synchronization Service And Async Admin Runner

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/config/AsyncConfig.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncService.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncAsyncRunner.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicySyncServiceTest.kt`

**Interfaces:**
- Consumes: `NongupEzPolicySourceClient`
- Consumes: `PolicyCardTextGenerator`, `NongupEzPolicyTagExtractor`, `TextListJsonCodec`
- Produces: `PolicySyncService.createAdminSyncJob(adminMemberId: UUID): PolicySyncResult.JobSummary`
- Produces: `PolicySyncService.runScheduledSync(): PolicySyncResult.JobSummary`
- Produces: `PolicySyncService.getJob(jobId: UUID): PolicySyncResult.JobDetail`
- Produces: `PolicySyncAsyncRunner.run(jobId: UUID): Unit`

- [ ] **Step 1: Write failing sync service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicySyncServiceTest.kt` using Mockito mocks for repositories and source client. The test file must contain these three test methods:

```kotlin
@Test
fun `runSync upserts list and detail success into recommendable policy`()

@Test
fun `detail failure keeps job succeeded and policy non recommendable`()

@Test
fun `whole source failure records failed job and keeps previous policies`()
```

The first test stubs `detectLatestYear()` as `"2026"`, stubs one `NongupEzPolicyListItem`, stubs one successful `NongupEzPolicyDetail`, captures the saved `PolicyProgram`, and asserts `detailSynced=true`, `recommendable=true`, and `eligibilitySummary.length <= 19`.

The second test stubs one list item and makes `fetchDetail("AB000009", "2026")` throw `RuntimeException("detail down")`; it asserts the job ends as `SUCCEEDED`, `detailFailureCount=1`, and the saved program has `detailSynced=false`.

The third test stubs `detectLatestYear()` to throw `RuntimeException("condition down")`; it asserts `createAdminSyncJob(adminId)` returns `FAILED` and `policyProgramRepository.deleteAll()` is never called.

Use real `PolicyCardTextGenerator`, `NongupEzPolicyTagExtractor`, and `TextListJsonCodec` in the test setup so 19-character behavior is covered through the sync path.

- [ ] **Step 2: Run sync service tests and verify they fail**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicySyncServiceTest"
```

Expected: FAIL because sync service classes do not exist.

- [ ] **Step 3: Add async config and result models**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/config/AsyncConfig.kt`:

```kotlin
package com.chamchamcham.application.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
class AsyncConfig
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncResult.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.domain.policy.PolicySource
import com.chamchamcham.domain.policy.PolicySyncJob
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import com.chamchamcham.domain.policy.PolicySyncTriggerType
import java.time.LocalDateTime
import java.util.UUID

object PolicySyncResult {
    data class JobSummary(
        val jobId: UUID,
        val status: PolicySyncJobStatus,
        val targetYear: String
    ) {
        companion object {
            fun from(job: PolicySyncJob): JobSummary =
                JobSummary(requireNotNull(job.id), job.status, job.targetYear)
        }
    }

    data class JobDetail(
        val jobId: UUID,
        val source: PolicySource,
        val targetYear: String,
        val triggerType: PolicySyncTriggerType,
        val status: PolicySyncJobStatus,
        val totalCount: Int,
        val syncedCount: Int,
        val detailSuccessCount: Int,
        val detailFailureCount: Int,
        val errorMessage: String?,
        val startedAt: LocalDateTime,
        val finishedAt: LocalDateTime?
    ) {
        companion object {
            fun from(job: PolicySyncJob): JobDetail =
                JobDetail(
                    jobId = requireNotNull(job.id),
                    source = job.source,
                    targetYear = job.targetYear,
                    triggerType = job.triggerType,
                    status = job.status,
                    totalCount = job.totalCount,
                    syncedCount = job.syncedCount,
                    detailSuccessCount = job.detailSuccessCount,
                    detailFailureCount = job.detailFailureCount,
                    errorMessage = job.errorMessage,
                    startedAt = job.startedAt,
                    finishedAt = job.finishedAt
                )
        }
    }
}
```

- [ ] **Step 4: Implement sync service**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncService.kt` with these public methods:

```kotlin
import java.time.Clock
import java.time.Year

@Service
@Transactional
class PolicySyncService(
    private val sourceClient: NongupEzPolicySourceClient,
    private val policyProgramRepository: PolicyProgramRepository,
    private val policySyncJobRepository: PolicySyncJobRepository,
    private val cardTextGenerator: PolicyCardTextGenerator,
    private val tagExtractor: NongupEzPolicyTagExtractor,
    private val textListJsonCodec: TextListJsonCodec,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun createAdminSyncJob(adminMemberId: UUID): PolicySyncResult.JobSummary {
        val job = createJobWithDetectedYear(
            triggerType = PolicySyncTriggerType.ADMIN,
            createdByMemberId = adminMemberId
        )
        return PolicySyncResult.JobSummary.from(job)
    }

    fun runScheduledSync(): PolicySyncResult.JobSummary {
        val job = createJobWithDetectedYear(
            triggerType = PolicySyncTriggerType.SCHEDULED,
            createdByMemberId = null
        )
        if (job.status == PolicySyncJobStatus.RUNNING) {
            runExistingJob(requireNotNull(job.id))
        }
        return PolicySyncResult.JobSummary.from(job)
    }

    fun runExistingJob(jobId: UUID) {
        val job = policySyncJobRepository.findById(jobId).orElseThrow()
        try {
            val listItems = sourceClient.fetchPrograms(job.targetYear)
            var synced = 0
            var detailSuccess = 0
            var detailFailure = 0

            listItems.forEach { item ->
                val program = policyProgramRepository.findBySourceAndExternalIdAndSourceYear(
                    PolicySource.NONGUP_EZ,
                    item.externalId,
                    item.sourceYear
                ) ?: PolicyProgram(
                    title = item.title,
                    body = item.summary ?: item.title,
                    region = "전국",
                    targetManagementType = null
                )
                program.applyListFields(
                    source = PolicySource.NONGUP_EZ,
                    externalId = item.externalId,
                    sourceYear = item.sourceYear,
                    title = item.title,
                    summary = item.summary,
                    region = "전국",
                    sourceUrl = detailUrl(item.externalId, item.sourceYear),
                    agencyName = item.agencyName,
                    lastSyncedJob = job
                )

                try {
                    val detail = sourceClient.fetchDetail(item.externalId, item.sourceYear)
                    val tags = tagExtractor.extract(detail)
                    val periodLabel = cardTextGenerator.periodLabel(
                        detail.applyStartsOn,
                        detail.applyEndsOn,
                        null
                    )
                    program.applyDetailFields(
                        body = listOfNotNull(detail.purpose, detail.summary, detail.eligibility, detail.benefit)
                            .joinToString("\n\n")
                            .ifBlank { item.summary ?: item.title },
                        purpose = detail.purpose,
                        eligibilityOriginal = detail.eligibility,
                        eligibilitySummary = cardTextGenerator.eligibilitySummary(detail.eligibility),
                        benefitOriginal = detail.benefit,
                        benefitSummary = cardTextGenerator.benefitSummary(detail.benefit),
                        applyStartsOn = detail.applyStartsOn,
                        applyEndsOn = detail.applyEndsOn,
                        applicationPeriodLabel = periodLabel,
                        applicationPeriodNotice = if (periodLabel == "접수기관문의") periodLabel else null,
                        applicationMethod = detail.applicationMethod,
                        requiredDocuments = detail.requiredDocuments,
                        selectionCriteria = detail.selectionCriteria,
                        departmentName = detail.contacts.firstOrNull()?.departmentName,
                        onlineApplyAvailable = false,
                        applicationUrl = null,
                        targetTagsJson = textListJsonCodec.encode(tags.targetTags),
                        cropTagsJson = textListJsonCodec.encode(tags.cropTags),
                        regionTagsJson = textListJsonCodec.encode(tags.regionTags),
                        rawPayload = detail.rawJson,
                        recommendable = true,
                        lastSyncedJob = job
                    )
                    detailSuccess += 1
                } catch (exception: Exception) {
                    program.markDetailSyncFailed(rawPayload = item.rawJson)
                    detailFailure += 1
                }

                policyProgramRepository.save(program)
                synced += 1
            }

            job.succeed(
                totalCount = listItems.size,
                syncedCount = synced,
                detailSuccessCount = detailSuccess,
                detailFailureCount = detailFailure
            )
        } catch (exception: Exception) {
            job.fail(exception.message ?: exception.javaClass.simpleName)
        }
    }

    @Transactional(readOnly = true)
    fun getJob(jobId: UUID): PolicySyncResult.JobDetail {
        val job = policySyncJobRepository.findById(jobId).orElseThrow()
        return PolicySyncResult.JobDetail.from(job)
    }

    private fun detailUrl(externalId: String, year: String): String =
        "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=$externalId&bizYr=$year"

    private fun createJobWithDetectedYear(
        triggerType: PolicySyncTriggerType,
        createdByMemberId: UUID?
    ): PolicySyncJob {
        return try {
            policySyncJobRepository.save(
                PolicySyncJob(
                    source = PolicySource.NONGUP_EZ,
                    targetYear = sourceClient.detectLatestYear(),
                    triggerType = triggerType,
                    createdByMemberId = createdByMemberId
                )
            )
        } catch (exception: Exception) {
            val failedJob = PolicySyncJob(
                source = PolicySource.NONGUP_EZ,
                targetYear = Year.now(clock).value.toString(),
                triggerType = triggerType,
                createdByMemberId = createdByMemberId
            )
            failedJob.fail(exception.message ?: exception.javaClass.simpleName)
            policySyncJobRepository.save(failedJob)
        }
    }
}
```

- [ ] **Step 5: Add async runner**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncAsyncRunner.kt`:

```kotlin
package com.chamchamcham.application.policy

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PolicySyncAsyncRunner(
    private val policySyncService: PolicySyncService
) {
    @Async
    fun run(jobId: UUID) {
        policySyncService.runExistingJob(jobId)
    }
}
```

- [ ] **Step 6: Run sync service tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicySyncServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit sync task**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/config/AsyncConfig.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncResult.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncService.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicySyncAsyncRunner.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicySyncServiceTest.kt
git commit -m "feat(policy): 농업e지 정책 동기화 유스케이스 추가" \
  -m "관리자 비동기 동기화와 배치 동기화가 같은 application 서비스를 사용하도록 정책 동기화 흐름을 추가한다." \
  -m "Constraint: 부분 상세 실패는 전체 job 실패로 보지 않는다" \
  -m "Rejected: 요청 시 실시간 NongupEZ 조회 | 추천 API 응답성과 장애 격리를 해친다" \
  -m "Confidence: medium" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.policy.PolicySyncServiceTest\""
```

---

### Task 5: Recommendation Scoring, Regeneration, Cursor Paging, And Detail Read

**Files:**
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationCursorPayload.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorer.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyMemberProfileReader.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorerTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationServiceTest.kt`

**Interfaces:**
- Consumes: Task 1 query repository and Task 3 tag/region helpers.
- Produces: `PolicyRecommendationService.listRecommendations(memberId: UUID, cursor: String?, size: Int): PolicyRecommendationResult.Page`
- Produces: `PolicyRecommendationService.getProgramDetail(memberId: UUID, policyProgramId: UUID): PolicyRecommendationResult.Detail`

- [ ] **Step 1: Add policy error codes**

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt` by adding:

```kotlin
POLICY_PROGRAM_NOT_FOUND("POLICY_001", "error.policy_program_not_found", 404),
POLICY_SYNC_JOB_NOT_FOUND("POLICY_002", "error.policy_sync_job_not_found", 404),
```

- [ ] **Step 2: Write failing scorer tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorerTest.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.domain.member.ManagementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PolicyRecommendationScorerTest {
    private val scorer = PolicyRecommendationScorer(PolicyRegionMatcher())

    @Test
    fun `scores young farmer medicinal crop and region matches above generic policy`() {
        val profile = PolicyMemberProfile(
            birthDate = LocalDate.of(1995, 1, 1),
            experienceLevel = 2,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            cropNames = setOf("참당귀"),
            cropUsePartCategories = setOf("ROOT_BARK"),
            farmRegionTokens = setOf("충청북도", "제천시")
        )

        val scored = scorer.score(
            profile = profile,
            policyTargetTags = setOf("YOUNG_FARMER", "REGISTERED_FARMER"),
            policyCropTags = setOf("MEDICINAL_CROP"),
            policyRegionTags = setOf("충청북도"),
            onlineApplyAvailable = true,
            today = LocalDate.of(2026, 4, 1)
        )

        assertThat(scored.score.toDouble()).isGreaterThanOrEqualTo(70.0)
        assertThat(scored.reason).isEqualTo("청년농 대상이고 재배 품목이 맞아요.")
    }
}
```

- [ ] **Step 3: Add profile and scorer**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorer.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.domain.member.ManagementType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Period

data class PolicyMemberProfile(
    val birthDate: LocalDate?,
    val experienceLevel: Int?,
    val managementType: ManagementType?,
    val cropNames: Set<String>,
    val cropUsePartCategories: Set<String>,
    val farmRegionTokens: Set<String>
)

data class PolicyScore(
    val score: BigDecimal,
    val reason: String
)

@Component
class PolicyRecommendationScorer(
    private val regionMatcher: PolicyRegionMatcher
) {
    fun score(
        profile: PolicyMemberProfile,
        policyTargetTags: Set<String>,
        policyCropTags: Set<String>,
        policyRegionTags: Set<String>,
        onlineApplyAvailable: Boolean,
        today: LocalDate
    ): PolicyScore {
        var score = 30
        val signals = mutableListOf<String>()

        if (policyTargetTags.contains("YOUNG_FARMER") && profile.birthDate?.let { Period.between(it, today).years < 40 } == true) {
            score += 20
            signals += "청년농 대상"
        }
        if (policyTargetTags.contains("REGISTERED_FARMER") && profile.managementType != ManagementType.NON_REGISTERED_FARMER) {
            score += 15
            signals += "경영체 유형"
        }
        if (policyTargetTags.contains("AGRICULTURAL_CORPORATION") && profile.managementType == ManagementType.AGRICULTURAL_CORPORATION) {
            score += 15
            signals += "법인 유형"
        }
        if (policyTargetTags.contains("RETURNING_FARMER") && (profile.experienceLevel ?: 99) <= 3) {
            score += 12
            signals += "영농경력"
        }
        if (policyCropTags.contains("MEDICINAL_CROP") && profile.cropUsePartCategories.contains("ROOT_BARK")) {
            score += 18
            signals += "재배 품목"
        }
        if (policyCropTags.contains("SPECIAL_CROP") && profile.cropNames.any { it.contains("작물") || it.contains("당귀") || it.contains("작약") }) {
            score += 10
            signals += "작물 분야"
        }
        if (regionMatcher.matches(profile.farmRegionTokens, policyRegionTags)) {
            score += 10
            signals += "농장 지역"
        }
        if (onlineApplyAvailable) {
            score += 3
            signals += "온라인 신청"
        }

        val reason = when {
            signals.contains("청년농 대상") && signals.contains("재배 품목") -> "청년농 대상이고 재배 품목이 맞아요."
            signals.contains("경영체 유형") && signals.contains("재배 품목") -> "경영체 유형과 재배 품목이 맞아요."
            signals.contains("농장 지역") -> "신청 가능한 정책이고 농장 지역이 맞아요."
            signals.isNotEmpty() -> "${signals.take(2).joinToString("과 ")}이 맞아요."
            else -> "신청 가능한 정책이에요."
        }

        return PolicyScore(BigDecimal.valueOf(score.toLong()), reason)
    }
}
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyMemberProfileReader.kt`:

```kotlin
package com.chamchamcham.application.policy

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PolicyMemberProfileReader(
    private val memberRepository: MemberRepository,
    private val memberCropRepository: MemberCropRepository,
    private val farmRepository: FarmRepository,
    private val regionMatcher: PolicyRegionMatcher
) {
    fun read(memberId: UUID): PolicyMemberProfile {
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val memberCrops = memberCropRepository.findByMember_Id(memberId)
        val farms = farmRepository.findByOwner_Id(memberId)
        return PolicyMemberProfile(
            birthDate = member.birthDate,
            experienceLevel = member.experienceLevel,
            managementType = member.managementType,
            cropNames = memberCrops.map { it.crop.name }.toSet(),
            cropUsePartCategories = memberCrops.map { it.crop.usePartCategory.name }.toSet(),
            farmRegionTokens = regionMatcher.extractRegionTokens(farms.flatMap { listOf(it.roadAddress, it.jibunAddress) })
        )
    }
}
```

- [ ] **Step 4: Add recommendation result and cursor payload**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationCursorPayload.kt`:

```kotlin
package com.chamchamcham.application.policy

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class PolicyRecommendationCursorPayload(
    val sourceSyncJobId: UUID,
    val score: BigDecimal,
    val applyEndsOn: LocalDate?,
    val id: UUID
)
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationResult.kt` with page, card, detail, contact, and attachment models matching the API contract in the spec. The `Card` model must contain:

```kotlin
data class Card(
    val recommendationId: UUID,
    val policyProgramId: UUID,
    val programTitle: String,
    val eligibilitySummary: String,
    val benefitSummary: String,
    val applicationPeriodLabel: String,
    val agencyName: String,
    val score: BigDecimal,
    val reason: String
)
```

- [ ] **Step 5: Implement recommendation service**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationService.kt` with this flow:

```kotlin
@Service
@Transactional
class PolicyRecommendationService(
    private val policySyncJobRepository: PolicySyncJobRepository,
    private val policyProgramRepository: PolicyProgramRepository,
    private val policyRecommendationRepository: PolicyRecommendationRepository,
    private val policyRecommendationQueryRepository: PolicyRecommendationQueryRepository,
    private val memberRepository: MemberRepository,
    private val memberProfileReader: PolicyMemberProfileReader,
    private val scorer: PolicyRecommendationScorer,
    private val textListJsonCodec: TextListJsonCodec,
    private val cursorCodec: OpaqueCursorCodec,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun listRecommendations(memberId: UUID, cursor: String?, size: Int): PolicyRecommendationResult.Page {
        validateSize(size)
        val latestJob = policySyncJobRepository.findFirstBySourceAndStatusOrderByTargetYearDescFinishedAtDesc(
            PolicySource.NONGUP_EZ,
            PolicySyncJobStatus.SUCCEEDED
        ) ?: return PolicyRecommendationResult.Page(emptyList(), null)
        val latestJobId = requireNotNull(latestJob.id)
        if (!policyRecommendationRepository.existsByMember_IdAndSourceSyncJob_Id(memberId, latestJobId)) {
            regenerate(memberId, latestJob)
        }
        val decodedCursor = decodeCursor(cursor, latestJobId)
        val result = policyRecommendationQueryRepository.findPage(
            PolicyRecommendationQueryRepository.SearchCondition(
                memberId = memberId,
                sourceSyncJobId = latestJobId,
                cursor = decodedCursor,
                size = size + 1
            )
        )
        val visible = result.rows.take(size)
        val nextCursor = if (result.rows.size > size) visible.lastOrNull()?.let(::encodeCursor) else null
        return PolicyRecommendationResult.Page(visible.map(::toCard), nextCursor)
    }

    private fun regenerate(memberId: UUID, latestJob: PolicySyncJob) {
        val member = memberRepository.findById(memberId).orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
        val profile = memberProfileReader.read(memberId)
        policyRecommendationRepository.deleteByMember_Id(memberId)
        val today = LocalDate.now(clock)
        val candidates = policyProgramRepository.findRecommendableCandidates(
            requireNotNull(latestJob.id),
            latestJob.targetYear,
            today
        )
        val recommendations = candidates.map { program ->
            val score = scorer.score(
                profile = profile,
                policyTargetTags = textListJsonCodec.decode(program.targetTagsJson),
                policyCropTags = textListJsonCodec.decode(program.cropTagsJson),
                policyRegionTags = textListJsonCodec.decode(program.regionTagsJson),
                onlineApplyAvailable = program.onlineApplyAvailable,
                today = today
            )
            PolicyRecommendation(
                member = member,
                policyProgram = program,
                sourceSyncJob = latestJob,
                score = score.score,
                reason = score.reason
            )
        }.filter { it.score > BigDecimal.ZERO }
        policyRecommendationRepository.saveAll(recommendations)
    }
}
```

Add these private methods and detail mapping behavior to the same service:
- `validateSize(size)` throwing `BusinessException(ErrorCode.INVALID_INPUT)` unless `size` is `1..50`.
- `decodeCursor(cursor, latestJobId)` returning null for blank cursor and throwing `BusinessException(ErrorCode.INVALID_INPUT)` if payload `sourceSyncJobId` differs from latest job id.
- `encodeCursor(row)` encoding `sourceSyncJobId`, `score`, `policyProgram.applyEndsOn`, and recommendation id.
- `getProgramDetail(memberId, policyProgramId)` returning only `detailSynced=true` and `recommendable=true` policies, else `POLICY_PROGRAM_NOT_FOUND`.
- Detail contact/attachment parsing from `rawPayload` via Jackson without separate tables.

- [ ] **Step 6: Run recommendation tests**

Run:

```bash
cd backend
./gradlew :application:test --tests "com.chamchamcham.application.policy.PolicyRecommendationScorerTest" --tests "com.chamchamcham.application.policy.PolicyRecommendationServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit recommendation task**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationCursorPayload.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationResult.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorer.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyMemberProfileReader.kt backend/application/src/main/kotlin/com/chamchamcham/application/policy/PolicyRecommendationService.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationScorerTest.kt backend/application/src/test/kotlin/com/chamchamcham/application/policy/PolicyRecommendationServiceTest.kt
git commit -m "feat(policy): 회원 정책 추천 계산 추가" \
  -m "저장된 농업e지 정책과 회원 프로필을 규칙 기반으로 매칭하고 stale 추천은 최신 sync job 기준으로 재생성한다." \
  -m "Constraint: 추천 조회 중 외부 NongupEZ 호출은 금지한다" \
  -m "Rejected: 추천 요청마다 실시간 점수 계산만 수행 | 커서 안정성과 조회 비용이 불리하다" \
  -m "Confidence: medium" \
  -m "Scope-risk: broad" \
  -m "Tested: ./gradlew :application:test --tests \"com.chamchamcham.application.policy.PolicyRecommendationScorerTest\" --tests \"com.chamchamcham.application.policy.PolicyRecommendationServiceTest\""
```

---

### Task 6: Member And Admin Policy APIs With Role Protection

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`
- Modify: `backend/api/src/main/resources/application.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/policy/dto/PolicyResponses.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/PolicyControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncControllerTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt`

**Interfaces:**
- Consumes: `PolicyRecommendationService`
- Consumes: `PolicySyncService`
- Consumes: `PolicySyncAsyncRunner`
- Produces: `GET /api/v1/policy-recommendations`
- Produces: `GET /api/v1/policy-programs/{policyProgramId}`
- Produces: `POST /api/v1/admin/policy-sync-jobs`
- Produces: `GET /api/v1/admin/policy-sync-jobs/{jobId}`

- [ ] **Step 1: Write failing controller tests**

Create controller tests with `@WebMvcTest`, `@AutoConfigureMockMvc(addFilters = false)`, and mocked application services:
- `GET /api/v1/policy-recommendations?size=20` maps authenticated principal to UUID and returns `items` plus `nextCursor`.
- `GET /api/v1/policy-programs/{id}` returns detail sections, contacts, and attachments.
- `POST /api/v1/admin/policy-sync-jobs` calls `PolicySyncService.createAdminSyncJob` and then `PolicySyncAsyncRunner.run(jobId)`.
- `GET /api/v1/admin/policy-sync-jobs/{jobId}` returns counters and status.

Use this principal parsing behavior in both controllers:

```kotlin
private fun parseMemberId(memberId: String?): UUID {
    if (memberId.isNullOrBlank()) {
        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
    return runCatching { UUID.fromString(memberId) }
        .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }
}
```

- [ ] **Step 2: Write security test for admin role**

Create `backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt` to verify:
- unauthenticated admin request returns 401
- authenticated `ROLE_USER` admin request returns 403
- authenticated `ROLE_ADMIN` admin request reaches controller

Use `spring-security-test` `@WithMockUser(roles = ["USER"])` and `@WithMockUser(roles = ["ADMIN"])`.

- [ ] **Step 3: Run API tests and verify they fail**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.policy.controller.PolicyControllerTest" --tests "com.chamchamcham.api.policy.controller.AdminPolicySyncControllerTest" --tests "com.chamchamcham.config.SecurityConfigAdminTest"
```

Expected: FAIL because controllers, DTOs, and admin role matcher do not exist.

- [ ] **Step 4: Add API DTOs**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/policy/dto/PolicyResponses.kt` with response classes mirroring `PolicyRecommendationResult`. Keep field names exactly:
- `recommendationId`
- `policyProgramId`
- `programTitle`
- `eligibilitySummary`
- `benefitSummary`
- `applicationPeriodLabel`
- `agencyName`
- `score`
- `reason`
- `items`
- `nextCursor`
- `contacts`
- `attachments`

Mapping functions must be companion `from(result: ...)` functions, matching existing DTO style.

- [ ] **Step 5: Add member policy controller**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/PolicyController.kt`:

```kotlin
package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.policy.dto.PolicyResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.PolicyRecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PolicyController(
    private val policyRecommendationService: PolicyRecommendationService
) {
    @GetMapping("/policy-recommendations")
    fun listRecommendations(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PolicyResponses.RecommendationPageResponse>> {
        val result = policyRecommendationService.listRecommendations(parseMemberId(memberId), cursor, size)
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.RecommendationPageResponse.from(result)))
    }

    @GetMapping("/policy-programs/{policyProgramId}")
    fun getProgramDetail(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable policyProgramId: UUID
    ): ResponseEntity<ApiResponse<PolicyResponses.PolicyDetailResponse>> {
        val result = policyRecommendationService.getProgramDetail(parseMemberId(memberId), policyProgramId)
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.PolicyDetailResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return runCatching { UUID.fromString(memberId) }
            .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }
    }
}
```

- [ ] **Step 6: Add admin sync controller**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/policy/controller/AdminPolicySyncController.kt`:

```kotlin
package com.chamchamcham.api.policy.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.policy.dto.PolicyResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.policy.PolicySyncAsyncRunner
import com.chamchamcham.application.policy.PolicySyncService
import com.chamchamcham.domain.policy.PolicySyncJobStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/policy-sync-jobs")
class AdminPolicySyncController(
    private val policySyncService: PolicySyncService,
    private val policySyncAsyncRunner: PolicySyncAsyncRunner
) {
    @PostMapping
    fun createJob(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<PolicyResponses.PolicySyncJobSummaryResponse>> {
        val result = policySyncService.createAdminSyncJob(parseMemberId(memberId))
        if (result.status == PolicySyncJobStatus.RUNNING) {
            policySyncAsyncRunner.run(result.jobId)
        }
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.PolicySyncJobSummaryResponse.from(result)))
    }

    @GetMapping("/{jobId}")
    fun getJob(
        @PathVariable jobId: UUID
    ): ResponseEntity<ApiResponse<PolicyResponses.PolicySyncJobDetailResponse>> {
        val result = policySyncService.getJob(jobId)
        return ResponseEntity.ok(ApiResponse.ok(PolicyResponses.PolicySyncJobDetailResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        return runCatching { UUID.fromString(memberId) }
            .getOrElse { throw BusinessException(ErrorCode.UNAUTHORIZED) }
    }
}
```

- [ ] **Step 7: Protect admin API by role**

Modify `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt` so the admin matcher appears before `.anyRequest().authenticated()`:

```kotlin
.authorizeHttpRequests {
    it
        .requestMatchers(*PUBLIC_ENDPOINTS.toTypedArray())
        .permitAll()
        .requestMatchers("/api/v1/admin/**")
        .hasAuthority("ROLE_ADMIN")
        .anyRequest().authenticated()
}
```

- [ ] **Step 8: Add NongupEZ config defaults**

Add to `backend/api/src/main/resources/application.yml` and `backend/api/src/test/resources/application-test.yml`:

```yaml
policy:
  nongup-ez:
    base-url: https://www.nongupez.go.kr
    timeout-millis: 10000
```

- [ ] **Step 9: Run API tests**

Run:

```bash
cd backend
./gradlew :api:test --tests "com.chamchamcham.api.policy.controller.PolicyControllerTest" --tests "com.chamchamcham.api.policy.controller.AdminPolicySyncControllerTest" --tests "com.chamchamcham.config.SecurityConfigAdminTest"
```

Expected: PASS.

- [ ] **Step 10: Commit API task**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt backend/api/src/main/resources/application.yml backend/api/src/test/resources/application-test.yml backend/api/src/main/kotlin/com/chamchamcham/api/policy backend/api/src/test/kotlin/com/chamchamcham/api/policy backend/api/src/test/kotlin/com/chamchamcham/config/SecurityConfigAdminTest.kt
git commit -m "feat(policy): 정책 추천과 관리자 동기화 API 추가" \
  -m "회원 정책 추천 조회, 정책 상세 조회, 관리자 동기화 생성과 상태 조회 API를 추가하고 admin 경로를 ROLE_ADMIN으로 보호한다." \
  -m "Constraint: JWT 권한은 ROLE_ 접두사를 가진 SimpleGrantedAuthority로 들어온다" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :api:test --tests \"com.chamchamcham.api.policy.controller.PolicyControllerTest\" --tests \"com.chamchamcham.api.policy.controller.AdminPolicySyncControllerTest\" --tests \"com.chamchamcham.config.SecurityConfigAdminTest\""
```

---

### Task 7: Batch Scheduler

**Files:**
- Create: `backend/batch/src/main/resources/application.yml` if absent.
- Create: `backend/batch/src/main/kotlin/com/chamchamcham/batch/policy/PolicySyncScheduler.kt`
- Test: `backend/batch/src/test/kotlin/com/chamchamcham/batch/policy/PolicySyncSchedulerTest.kt`

**Interfaces:**
- Consumes: `PolicySyncService.runScheduledSync()`
- Produces: one daily scheduled sync call.

- [ ] **Step 1: Write failing scheduler test**

Create `backend/batch/src/test/kotlin/com/chamchamcham/batch/policy/PolicySyncSchedulerTest.kt`:

```kotlin
package com.chamchamcham.batch.policy

import com.chamchamcham.application.policy.PolicySyncService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class PolicySyncSchedulerTest {
    @Test
    fun `scheduled sync delegates to application service`() {
        val service = mock(PolicySyncService::class.java)
        val scheduler = PolicySyncScheduler(service)

        scheduler.syncNongupEzPolicies()

        verify(service).runScheduledSync()
    }
}
```

- [ ] **Step 2: Run scheduler test and verify it fails**

Run:

```bash
cd backend
./gradlew :batch:test --tests "com.chamchamcham.batch.policy.PolicySyncSchedulerTest"
```

Expected: FAIL because scheduler class does not exist.

- [ ] **Step 3: Add scheduler**

Create `backend/batch/src/main/kotlin/com/chamchamcham/batch/policy/PolicySyncScheduler.kt`:

```kotlin
package com.chamchamcham.batch.policy

import com.chamchamcham.application.policy.PolicySyncService
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PolicySyncScheduler(
    private val policySyncService: PolicySyncService
) {
    private val log = KotlinLogging.logger {}

    @Scheduled(
        cron = "\${policy.sync.nongup-ez.cron:0 20 3 * * *}",
        zone = "\${policy.sync.nongup-ez.zone:Asia/Seoul}"
    )
    fun syncNongupEzPolicies() {
        val result = policySyncService.runScheduledSync()
        log.info { "NongupEZ policy sync finished jobId=${result.jobId} status=${result.status} targetYear=${result.targetYear}" }
    }
}
```

If `backend/batch/src/main/resources/application.yml` does not exist, create it:

```yaml
policy:
  sync:
    nongup-ez:
      cron: "0 20 3 * * *"
      zone: Asia/Seoul
  nongup-ez:
    base-url: https://www.nongupez.go.kr
    timeout-millis: 10000
```

- [ ] **Step 4: Ensure batch module has logging dependency**

If `backend/batch/build.gradle.kts` lacks Kotlin logging, add:

```kotlin
implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
```

- [ ] **Step 5: Run scheduler test**

Run:

```bash
cd backend
./gradlew :batch:test --tests "com.chamchamcham.batch.policy.PolicySyncSchedulerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit batch task**

```bash
git add backend/batch/src/main/kotlin/com/chamchamcham/batch/policy/PolicySyncScheduler.kt backend/batch/src/main/resources/application.yml backend/batch/src/test/kotlin/com/chamchamcham/batch/policy/PolicySyncSchedulerTest.kt backend/batch/build.gradle.kts
git commit -m "feat(policy): 농업e지 정책 일일 동기화 배치 추가" \
  -m "batch 모듈에서 application 정책 동기화 서비스를 하루 한 번 호출하도록 스케줄러를 추가한다." \
  -m "Constraint: batch는 application 서비스만 호출하고 수집 로직을 복제하지 않는다" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :batch:test --tests \"com.chamchamcham.batch.policy.PolicySyncSchedulerTest\""
```

---

### Task 8: End-To-End Verification And Cleanup

**Files:**
- Review all files touched in Tasks 1-7.
- No new source files in this task unless verification exposes a concrete defect.

- [ ] **Step 1: Run focused module tests**

Run:

```bash
cd backend
./gradlew :domain:test :application:test :api:test :batch:test
```

Expected: PASS.

- [ ] **Step 2: Run full backend test suite**

Run:

```bash
cd backend
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Inspect final diff**

Run:

```bash
git status --short
git diff --stat
git diff --check
```

Expected:
- `git diff --check` reports no whitespace errors.
- Only files from this plan and the existing untracked `.claude/` appear.

- [ ] **Step 4: Review spec coverage**

Confirm these acceptance points are covered by tests or code:
- latest year detection ignores `0000`
- list and detail parsing use fixtures
- sync upserts by `source + externalId + sourceYear`
- whole source failure marks job `FAILED`
- detail failure leaves old data usable and marks policy non-recommendable
- card summaries are 19 characters or fewer
- recommendations regenerate when missing or stale
- recommendation sort is `score desc, applyEndsOn asc, id asc`
- cursor contains `sourceSyncJobId`
- stale cursor returns `INVALID_INPUT`
- no successful sync returns empty recommendation page
- detail API hides list-only or non-recommendable policies
- admin sync API requires `ROLE_ADMIN`
- scheduler delegates to application sync service

- [ ] **Step 5: Route verification fixes to the owning task**

If Step 1 through Step 4 expose a source defect, return to the task that owns
the changed file, apply the smallest fix there, rerun that task's focused test,
and then rerun this verification task from Step 1. When no source change is
required, this step is complete with no commit.

---

## Execution Notes

- Start implementation from the existing branch `feat/policy-recommendation-nongupez`.
- Keep each task commit separate unless a later compile error requires combining tightly coupled files.
- Do not stage `.claude/` unless the user explicitly asks for it.
- Do not browse NongupEZ in unit tests. Live source checks belong in a manual verification note after the implementation is test-green.
- If Spring Data method names become too brittle for a query, prefer one feature-local JPQL `@Query` or one `EntityManager` repository implementation over adding a query framework.
- The first production deployment needs the SQL in `docs/database/2026-07-07-policy-recommendation-schema.sql` reviewed and applied because Flyway is not installed.
