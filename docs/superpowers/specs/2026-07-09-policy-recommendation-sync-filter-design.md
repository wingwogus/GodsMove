# Policy Recommendation Sync, Category, and LLM Tagging Design

## Context

The first NongupEZ policy recommendation MVP stores policy programs, creates
member-specific recommendations, and shows fixed policy cards. The next change
improves three weak points found during review:

- repeated sync jobs update unchanged policy rows only to move
  `lastSyncedJob`
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

The current candidate query is tied to `lastSyncedJob.id`, so every successful
sync must touch each policy row to make it visible in the latest recommendation
run. That creates unnecessary update queries when source content has not
changed.

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

### 1. Card Category Enums

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

### 2. Recommendation Benefit Filter

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

### 3. Sync Without Unchanged Row Updates

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

`lastSyncedJob` should not be the reason to update an unchanged policy. It can
remain as an audit pointer when a row is inserted or materially changed.

To avoid keeping removed policies active forever, after fetching the current
source list the sync service should compare current source identities against
stored policies for the same `source + sourceYear`. Stored policies missing
from the current source list should be marked `recommendable=false`. Those are
real availability changes and should be updated.

### 4. Candidate Query and Recommendation Staleness

Candidate lookup should no longer filter by `lastSyncedJob.id`.

Use:

```text
source = NONGUP_EZ
sourceYear = latest successful targetYear
detailSynced = true
recommendable = true
applyEndsOn is null or applyEndsOn >= today
```

Recommendation query should not require `sourceSyncJobId` to equal the latest
job. `sourceSyncJob` remains useful as an audit field showing which sync job
created the recommendation row.

Staleness should be based on the current candidate set and content freshness:

- regenerate if the member has no recommendations for the active source year
- regenerate if stored recommendation policy IDs are not the same as the
  current eligible recommendation policy IDs
- regenerate if any candidate policy was updated after the member's newest
  recommendation row was created

This preserves recommendations across no-op sync jobs while still recalculating
when policy content or tags actually change.

### 5. LLM Policy Tag Extraction

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
```

Recommendation listing:

```text
member request with optional benefitCategory
-> load active source year
-> load active candidates independent of latestJobId
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

## Testing Strategy

- Unit-test each card category enum rule and label length.
- Controller test for `benefitCategory` parsing and invalid enum handling.
- Query repository test for benefit-category filtering and cursor ordering.
- Sync service test for unchanged existing policy rows skipping repository save
  or dirty update paths as far as the repository abstraction allows.
- Sync service test for changed policy rows updating normalized fields.
- Sync service test for current-source missing policies becoming
  `recommendable=false`.
- Recommendation service test showing a no-op sync job does not regenerate
  recommendations only because the job ID changed.
- Recommendation service test showing policy content updates make
  recommendations stale.
- LLM extractor tests with a fake client: valid JSON, unknown enum, invalid JSON,
  call failure, and fallback behavior.

## Implementation Phasing

Phase 1:

- add category enums
- update rule-based card summary generation to return enum labels
- add recommendation `benefitCategory` filter
- remove latest-job coupling from candidate and recommendation queries
- skip unchanged policy updates during sync

Phase 2:

- add LLM tag extractor boundary
- validate strict enum output
- call LLM for all synchronized policies
- keep rule fallback for failures

This split keeps the deterministic behavior changes reviewable before adding
external LLM configuration and failure modes.

