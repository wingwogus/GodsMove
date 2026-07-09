# Policy Recommendation Sync, Category, and LLM Tagging Design

## Context

The first NongupEZ policy recommendation MVP stores policy programs, creates
member-specific recommendations, and shows fixed policy cards. The next change
improves three weak points found during review:

- repeated sync jobs update unchanged policy rows only to move sync-job
  foreign keys
- card summaries are currently string literals, even though they represent a
  closed set of categories
- recommendation tags are extracted with broad keyword rules, which is not
  reliable enough for policy eligibility matching

This design keeps the member-facing card shape stable. It improves internal
classification and recommendation filtering without adding a broad new policy
schema.

## Goals

- Stop issuing updates for unchanged NongupEZ policy rows.
- Keep sync jobs as execution logs, not as the definition of the active policy
  set.
- Remove sync-job foreign keys from policy and recommendation current-state
  decisions.
- Add support-item filtering to recommendation listing by benefit category.
- Represent card summary categories as Kotlin enums internally.
- Use LLM extraction for policy recommendation tags at sync time only.
- Keep recommendation requests deterministic and free of LLM calls.
- Preserve rule-based fallback when LLM extraction fails or returns invalid
  tags.

## Non-Goals

- Changing the mobile card response shape.
- Adding `benefit_category` or `eligibility_category` DB columns in the first
  implementation pass.
- Calling LLMs during `GET /api/v1/policy-recommendations`.
- Sending member profile or farm data to an LLM.
- Building a full policy target/crop/region relational model.
- Solving every unsupported eligibility condition, such as income, farm area,
  or certification ownership, before the member profile has matching fields.

## Current State

`PolicyProgram` already has a unique source identity:

```text
source + externalId + sourceYear
```

The current model lets policy and recommendation freshness depend on sync-job
foreign keys:

```text
PolicyProgram.lastSyncedJob
PolicyRecommendation.sourceSyncJob
```

That makes `PolicySyncJob` do two jobs at once: execution logging and active
data selection. As a result, every successful sync can touch unchanged policy
rows just to point them at the newest job.

Recommendation cards expose:

```text
programTitle
eligibilitySummary
benefitSummary
applicationPeriodLabel
agencyName
score
reason
```

This shape remains unchanged.

## Proposed Design

### 1. Sync Job as Execution Log Only

`PolicySyncJob` should remain, but only as a record of a sync execution:

```text
source
targetYear
triggerType
status
startedAt
finishedAt
totalCount
syncedCount
detailSuccessCount
detailFailureCount
errorMessage
createdByMemberId
```

It should not be referenced by the current policy or recommendation model.

Remove these relationships from the domain model and schema:

```text
PolicyProgram.lastSyncedJob
PolicyRecommendation.sourceSyncJob
```

After this change, current policy visibility is derived from `PolicyProgram`
state, not from the latest sync job ID. Sync jobs answer "what happened during
an ingestion run"; they do not answer "which policies are currently
recommendable".

### 2. Card Category Enums

Introduce internal enums for card summary classification.

`PolicyBenefitCategory`:

```text
DIRECT_PAYMENT        -> "직불/수당"
FINANCE               -> "융자/금융"
FACILITY_EQUIPMENT    -> "시설/장비"
EDUCATION_CONSULTING  -> "교육/컨설팅"
INSURANCE_WELFARE     -> "보험/복지"
CERTIFICATION_QUALITY -> "인증/품질"
MARKETING             -> "판로/마케팅"
STARTUP_BUSINESS      -> "창업/사업화"
ENVIRONMENT_INFRA     -> "환경/인프라"
ETC                   -> "기타"
```

`PolicyEligibilityCategory`:

```text
YOUNG_FARMER          -> "청년 농업인"
RETURNING_FARMER      -> "귀농·귀촌인"
FEMALE_FARMER         -> "여성 농업인"
SENIOR_RETIRED_FARMER -> "고령·은퇴 농업인"
SPECIAL_TARGET        -> "특수 대상자"
DAMAGED_FARM          -> "피해 농가"
CERTIFIED_FARMER      -> "인증 보유 농업인"
LIVESTOCK_FARM        -> "축산 농가"
EXPORT_BUSINESS       -> "수출 농가·기업"
FOOD_AGRI_COMPANY     -> "농식품 기업"
AGRI_CORPORATION      -> "농업법인·단체"
REGISTERED_FARMER     -> "경영체 등록 농업인"
FARMER                -> "농업인"
UNKNOWN               -> "상세 자격 확인"
```

`PolicyCardTextGenerator` should classify to enum first, then store the enum
label in the existing `benefitSummary` and `eligibilitySummary` string fields.
The enums give the code and tests stable identifiers while keeping the API
response unchanged.

### 3. Recommendation Benefit Filter

Add an optional query parameter:

```http
GET /api/v1/policy-recommendations?benefitCategory=FINANCE
```

Validation:

- absent value means no benefit filter
- unknown enum key returns `INVALID_INPUT`
- cursor payload should include the active benefit category so a cursor from one
  filter cannot be reused against another filter

Application flow:

```text
controller parses benefitCategory
-> service receives PolicyBenefitCategory?
-> query repository filters by policyProgram.benefitSummary = category.label
```

The response still returns only `benefitSummary`, not a new
`benefitCategory` field. A future API can expose both enum key and label if the
frontend needs stable keys in the payload.

### 4. Sync Without Unchanged Row Updates

Policy sync should separate "source execution log" from "policy content state".

Per list item:

```text
no existing policy
  -> insert

existing policy and normalized content changed
  -> update

existing policy and normalized content unchanged
  -> skip save
```

The content comparison should include normalized source fields that affect
display or recommendation:

```text
title
summary
body
sourceUrl
agencyName
departmentName
applyStartsOn
applyEndsOn
applicationPeriodLabel
applicationPeriodNotice
eligibilityOriginal
eligibilitySummary
benefitOriginal
benefitSummary
purpose
applicationMethod
requiredDocuments
selectionCriteria
onlineApplyAvailable
applicationUrl
targetTagsJson
cropTagsJson
regionTagsJson
rawPayload
detailSynced
recommendable
```

Sync-job relationships are not part of `PolicyProgram`, so they cannot be the
reason to update an unchanged policy. A row is updated only when its normalized
policy state changes.

To avoid keeping removed policies active forever, after fetching the current
source list the sync service should compare current source identities against
stored policies for the same `source + sourceYear`. Stored policies missing
from the current source list should be marked `recommendable=false`. Those are
real availability changes and should be updated.

### 5. Candidate Query and Recommendation Staleness

Candidate lookup should not filter by sync-job ID.

Use:

```text
source = NONGUP_EZ
sourceYear = latest successful targetYear
detailSynced = true
recommendable = true
applyEndsOn is null or applyEndsOn >= today
```

Recommendation query should not filter by `sourceSyncJobId`; that field is
removed. It should scope recommendations through the linked policy program:

```text
recommendation.member.id = memberId
recommendation.policyProgram.source = NONGUP_EZ
recommendation.policyProgram.sourceYear = activeSourceYear
```

Staleness should be based on the current candidate set and content freshness:

- regenerate if the member has no recommendations for the active source year
- regenerate if any stored recommendation policy ID is no longer in the current
  candidate set
- regenerate if any active candidate policy was inserted or updated after the
  member's newest recommendation row was created
- regenerate if the member profile, member crop rows, or farm rows used for
  matching were updated after the member's newest recommendation row was
  created

This preserves recommendations across no-op sync jobs while still recalculating
when policy content, tags, source availability, or relevant member profile data
changes.

`updatedAt` is sufficient for the first implementation because unchanged sync
items are skipped. A future `contentHash` field can replace or supplement
`updatedAt` if explicit content-version comparison becomes necessary.

### 6. LLM Policy Tag Extraction

LLM usage is limited to sync-time policy tag extraction. It should not rank
policies for a member and should not receive member data.

Input:

```json
{
  "title": "...",
  "summary": "...",
  "eligibility": "...",
  "benefit": "...",
  "agencyName": "..."
}
```

Output must be strict JSON using allowed enums only:

```json
{
  "targetTags": ["YOUNG_FARMER", "REGISTERED_FARMER"],
  "cropTags": ["MEDICINAL_CROP"],
  "regionTags": ["충청북도"],
  "confidence": 0.86,
  "evidence": [
    {
      "tag": "YOUNG_FARMER",
      "text": "청년후계농업경영인"
    }
  ]
}
```

Allowed first-pass recommendation tags:

```text
targetTags:
YOUNG_FARMER
REGISTERED_FARMER
AGRICULTURAL_CORPORATION
RETURNING_FARMER

cropTags:
MEDICINAL_CROP
SPECIAL_CROP

regionTags:
전국 or official province names
```

Validation rules:

- reject unknown tag strings
- reject invalid JSON
- reject region values outside the supported region vocabulary
- use rule-based fallback on validation failure or LLM call failure
- store fallback results in the same JSON fields

The deterministic scorer remains responsible for member-specific eligibility,
score, and reason generation.

## Data Flow

```text
NongupEZ list/detail
-> normalize detail fields
-> classify card categories by enum rules
-> extract recommendation tags by LLM, fallback to rules
-> compare normalized content with existing policy
-> insert/update/skip
-> mark policies absent from latest source list as not recommendable
-> record sync job counts and status only
```

Recommendation listing:

```text
member request with optional benefitCategory
-> load active source year
-> load active candidates independent of sync job ID
-> evaluate stale state from recommendation rows, policy updatedAt, and profile updatedAt
-> regenerate only when missing/stale
-> query member recommendations with optional benefitSummary label filter
-> return existing card shape
```

## Error Handling

- Invalid `benefitCategory` query value returns `INVALID_INPUT`.
- Invalid cursor or cursor/filter mismatch returns `INVALID_INPUT`.
- LLM extraction failure does not fail the policy sync item; the service falls
  back to deterministic rule extraction.
- A whole-source failure still fails the sync job.
- A detail fetch failure still marks that policy detail as not synchronized only
  when the existing row must be changed; unchanged previous successful rows
  should remain available unless the current source list indicates removal.
- Removing sync-job foreign keys means a failed sync job does not hide or
  invalidate the last known good policy state.

## Testing Strategy

- Unit-test each card category enum rule and label length.
- Controller test for `benefitCategory` parsing and invalid enum handling.
- Query repository test for benefit-category filtering and cursor ordering.
- Domain/schema test or migration review confirming `PolicyProgram` no longer
  maps `lastSyncedJob` and `PolicyRecommendation` no longer maps
  `sourceSyncJob`.
- Sync service test for unchanged existing policy rows skipping repository save
  or dirty update paths as far as the repository abstraction allows.
- Sync service test for changed policy rows updating normalized fields.
- Sync service test for current-source missing policies becoming
  `recommendable=false`.
- Recommendation service test showing a no-op sync job does not regenerate
  recommendations only because the job ID changed.
- Recommendation service test showing policy content updates make
  recommendations stale.
- Recommendation service test showing member, member crop, or farm updates make
  recommendations stale.
- Recommendation query test scoping rows by `policyProgram.source` and
  `policyProgram.sourceYear`, not sync job ID.
- LLM extractor tests with a fake client: valid JSON, unknown enum, invalid JSON,
  call failure, and fallback behavior.

## Implementation Phasing

Phase 1:

- add category enums
- update rule-based card summary generation to return enum labels
- add recommendation `benefitCategory` filter
- remove `PolicyProgram.lastSyncedJob`
- remove `PolicyRecommendation.sourceSyncJob`
- remove latest-job coupling from candidate and recommendation queries
- skip unchanged policy updates during sync

Phase 2:

- add LLM tag extractor boundary
- validate strict enum output
- call LLM for all synchronized policies
- keep rule fallback for failures

This split keeps the deterministic behavior changes reviewable before adding
external LLM configuration and failure modes.
