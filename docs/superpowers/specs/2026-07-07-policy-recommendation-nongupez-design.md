# NongupEZ Policy Recommendation Design

## Context

ChamChamCham already has thin policy domain entities, but no policy application
service, API controller, scheduler, or ingestion flow. This design makes
`농업e지` the first policy source and covers the MVP through:

- daily policy synchronization
- admin-triggered asynchronous synchronization
- normalized policy storage with raw source payload preservation
- rule-based member policy recommendations
- recommendation list and policy detail APIs

Implementation must start from `dev` on a feature branch. The approved branch
for this design work is `feat/policy-recommendation-nongupez`.

## Goals

- Collect the latest available `농업e지` business year automatically.
- Store policies in the backend DB before user requests depend on them.
- Recommend only usable policies: detailed source data must be synchronized and
  the application period must not be closed.
- Generate deterministic scores and reasons without LLMs.
- Keep card copy short enough for the mobile UI.
- Preserve source payloads so later UI or matching rules can improve without
  immediate re-collection.

## Non-Goals

- LLM-based summaries, ranking, or RAG.
- Local storage of attachment files.
- Exhaustive admin UI.
- Separate policy target, crop, region, or attachment tables in the MVP.
- Real-time `농업e지` calls during member recommendation requests.
- Public API access to trigger source synchronization.

## Current Constraints

- Domain language is `member`, not `user`.
- Existing dependency direction remains:

```text
api -> application -> domain
batch -> application -> domain
```

- Runtime DB is PostgreSQL, but Flyway is not installed yet.
- `농업e지` policy data is available from public site JSON calls, not a
  separately documented public Open API. The ingestion code must keep this
  source behind a single adapter boundary.
- Existing `PolicyProgram.targetManagementType` is too narrow for `농업e지`
  because a policy can target multiple groups or broad audiences.

## Source Endpoints

The source adapter talks to these `농업e지` endpoints:

```text
POST https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/retrieveListBizSrchCnd
POST https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/retrieveListBizSrch
POST https://www.nongupez.go.kr/nsm/bizAply/cstBiz/findBizSrchDtl
```

The condition endpoint is used to detect the latest business year from
`SRCH_BIZ_YR`, ignoring `0000`. At research time the latest detected year was
`2026`.

The list endpoint is called with the latest year and no agency filter, so
`농림축산식품부`, `산림청`, and `지자체` policies are all included.

The detail endpoint is called once per `afbzCd + bizYr`. A policy is recommendable
only when its detail payload is successfully synchronized.

## Architecture

### 1. NongupEZ Source Adapter

Purpose: isolate external HTTP shape and parsing.

Responsibilities:
- detect latest source year
- fetch paged policy list
- fetch policy detail
- normalize `yyyyMMdd` dates
- construct source URLs and attachment URLs
- return application-friendly source DTOs

The adapter does not save entities and does not compute recommendations.

### 2. Policy Sync Service

Purpose: own synchronization job lifecycle and policy upsert.

Responsibilities:
- create `policy_sync_job`
- run schedule/admin sync through the same use case
- upsert `policy_program` by `source + external_id + source_year`
- update normalized fields and raw payload
- count list successes, detail successes, and detail failures
- mark job `FAILED` on whole-source failures
- keep the previous successful data when a job fails

### 3. Policy Recommendation Service

Purpose: compute deterministic member recommendations from stored policies.

Responsibilities:
- find latest successful sync job
- detect stale member recommendations by `source_sync_job_id`
- delete and recreate member recommendations when missing or stale
- score policies with rule-based signals
- generate short rule-based reason text
- return cursor-paged recommendation results

### 4. Policy API

Purpose: expose member and admin policy use cases.

Member APIs:
- `GET /api/v1/policy-recommendations`
- `GET /api/v1/policy-programs/{policyProgramId}`

Admin APIs:
- `POST /api/v1/admin/policy-sync-jobs`
- `GET /api/v1/admin/policy-sync-jobs/{jobId}`

Admin APIs must be protected by the existing Spring Security role model. The
implementation must verify whether the current code already supports
`ROLE_ADMIN`; if not, the plan must add the smallest role check compatible with
the current security setup.

### 5. Batch Scheduler

Purpose: run the same sync service once per day.

The `batch` module already enables scheduling. The scheduled job should call the
application sync service rather than duplicating ingestion logic.

## Data Model

### PolicySyncJob

New entity/table: `policy_sync_job`.

Fields:
- `id`
- `source`: `NONGUP_EZ`
- `targetYear`
- `triggerType`: `SCHEDULED` or `ADMIN`
- `status`: `RUNNING`, `SUCCEEDED`, or `FAILED`
- `startedAt`
- `finishedAt`
- `totalCount`
- `syncedCount`
- `detailSuccessCount`
- `detailFailureCount`
- `errorMessage`
- `createdByMember` nullable

No separate partial-success status is needed. A job can be `SUCCEEDED` with
`detailFailureCount > 0`.

### PolicyProgram

Extend the existing `policy_program` table. Keep it as the source-of-truth row
for policies shown to members.

Source identity:
- `source`: `NONGUP_EZ`
- `externalId`: `afbzCd`
- `sourceYear`: `bizYr`
- unique key: `source + externalId + sourceYear`

Core display:
- `title`: `afbzNm`, official title, not shortened by the backend
- `summary`: `bizCn`
- `body`: fallback combined detail text
- `sourceUrl`: `농업e지` detail URL
- `agencyName`: `bizTkcgInstNm` or first contact agency
- `departmentName` nullable
- `onlineApplyAvailable`
- `applicationUrl` nullable

Dates:
- `applyStartsOn`
- `applyEndsOn`
- `applicationPeriodLabel`: card label, 19 characters or fewer
- `applicationPeriodNotice`: source label such as `접수기관문의`

Card summaries:
- `eligibilityOriginal`: `bizSprtQlfcCn`
- `eligibilitySummary`: 19 characters or fewer
- `benefitOriginal`: `bizSprtCn`
- `benefitSummary`: 19 characters or fewer

Detail sections:
- `purpose`: `bizPrpsCn`
- `applicationMethod`: `bizAplyMthdCn`
- `requiredDocuments`: `bizRqdcCn`
- `selectionCriteria`: `bizSlctnCrtrCn`

Recommendation support:
- `detailSynced`
- `recommendable`
- `targetTagsJson`: JSON text array, for example `["YOUNG_FARMER"]`
- `cropTagsJson`: JSON text array, for example `["MEDICINAL_CROP"]`
- `regionTagsJson`: JSON text array, for example `["전국", "충청북도"]`
- `lastSyncedJobId`
- `rawPayload`: source list and detail JSON

The existing `targetManagementType` should become nullable. It can keep a coarse
single value when one is confidently inferred, but recommendation logic should
use the JSON tag fields instead because `농업e지` policies can target multiple
audiences.

Contacts and attachments stay in `rawPayload` for MVP and are mapped from JSON
when building detail responses. No separate tables are created in this design.

### PolicyRecommendation

Extend the existing `policy_recommendation` table.

Fields:
- `id`
- `member`
- `policyProgram`
- `sourceSyncJob`
- `score`
- `reason`
- `createdAt`

When a member recommendation set is missing or stale, the service deletes the
existing rows for that member and creates fresh rows for the latest successful
sync job.

## Synchronization Flow

1. Create `policy_sync_job` with `RUNNING`.
2. Detect latest source year from `retrieveListBizSrchCnd`.
3. Fetch all pages from `retrieveListBizSrch`.
4. Upsert basic list fields for each policy.
5. Fetch detail for each policy.
6. On detail success:
   - write detail fields
   - set `detailSynced = true`
   - set `recommendable = true` only if the application period is not closed
7. On detail failure:
   - keep list fields
   - set `detailSynced = false`
   - set `recommendable = false`
   - increment `detailFailureCount`
8. Mark job `SUCCEEDED` unless the whole source flow failed.
9. On whole source failure, mark job `FAILED` and keep existing successful data.

The member-facing recommendation APIs never call `농업e지` directly.

## Card Copy Rules

Member recommendation cards show only:

- `programTitle`
- `eligibilitySummary`
- `benefitSummary`
- `applicationPeriodLabel`
- `agencyName`

Backend guarantees:
- `programTitle` is the official source title and is not shortened.
- `eligibilitySummary` is 19 characters or fewer.
- `benefitSummary` is 19 characters or fewer.
- `applicationPeriodLabel` is 19 characters or fewer.
- `agencyName` is not shortened by the backend.

No LLM is used. Summary generation is rule based.

Fallbacks:
- `eligibilitySummary`: `상세 자격 확인`
- `benefitSummary`: `상세 지원 확인`

Example card response shape:

```json
{
  "recommendationId": "uuid",
  "policyProgramId": "uuid",
  "programTitle": "친환경농업직불",
  "eligibilitySummary": "친환경 인증 농업인",
  "benefitSummary": "인증단계별 직불금",
  "applicationPeriodLabel": "2026.03.25~06.30",
  "agencyName": "농림축산식품부",
  "score": 82.0,
  "reason": "경영체 유형과 재배 품목이 맞아요."
}
```

## Recommendation Rules

Only these policies are candidates:
- latest successful sync job
- latest detected source year
- `detailSynced = true`
- `recommendable = true`
- not closed by `applyEndsOn`
- source labels such as `접수기관문의` are treated as open/check-with-agency

Scoring is deterministic and member-fit first. The exact weights can be tuned
in implementation, but the initial shape is:

- target/management fit
- age or young-farmer fit
- farming experience fit
- crop/category fit
- farm region fit
- application status fit
- online application availability

Sort order:

```text
score desc, applyEndsOn asc, id asc
```

Always include `id asc` as a final tie-breaker for cursor stability.

Reason generation uses matched scoring signals, not source text summarization.
The service chooses the top two or three matched signals and renders a fixed
template.

Examples:
- `청년농 대상이고 영농경력이 맞아요.`
- `경영체 유형과 재배 품목이 맞아요.`
- `신청 가능한 정책이고 농장 지역이 맞아요.`

## Region Matching

MVP matching uses existing farm addresses and source-derived tags.

Member side:
- parse `Farm.roadAddress` and `Farm.jibunAddress` for province and city/county
- if parsing fails, region contributes no positive score and no negative score

Policy side:
- use source region labels when available
- infer broad national policies as `전국`
- infer regional labels from contact agency names and source payload when no
  explicit region tag exists

No farm schema change is required in this MVP.

## API Contract

### Recommendation List

```http
GET /api/v1/policy-recommendations?cursor={cursor}&size=20
```

Behavior:
- authenticated member only
- generate recommendations if missing or stale
- return cursor-paged results
- cursor includes sorting keys and source sync job id
- if cursor is for an old sync job, return `INVALID_INPUT`

Response:

```json
{
  "items": [
    {
      "recommendationId": "uuid",
      "policyProgramId": "uuid",
      "programTitle": "친환경농업직불",
      "eligibilitySummary": "친환경 인증 농업인",
      "benefitSummary": "인증단계별 직불금",
      "applicationPeriodLabel": "2026.03.25~06.30",
      "agencyName": "농림축산식품부",
      "score": 82.0,
      "reason": "경영체 유형과 재배 품목이 맞아요."
    }
  ],
  "nextCursor": "opaque"
}
```

### Policy Detail

```http
GET /api/v1/policy-programs/{policyProgramId}
```

Response:

```json
{
  "id": "uuid",
  "programTitle": "친환경농업직불",
  "sourceYear": "2026",
  "agencyName": "농림축산식품부",
  "departmentName": "친환경농업과",
  "applicationPeriodLabel": "2026.03.25~06.30",
  "onlineApplyAvailable": true,
  "sourceUrl": "https://www.nongupez.go.kr/nsm/bizAply/wholeBiz/wholeBizDtls?afbzCd=AB000009&bizYr=2026",
  "applicationUrl": "https://www.nongupez.go.kr/nsm/dpsUntyAply/dpsUntyAplyMain",
  "purpose": "...",
  "summary": "...",
  "eligibility": "...",
  "benefit": "...",
  "applicationMethod": "...",
  "requiredDocuments": "...",
  "selectionCriteria": "...",
  "contacts": [
    {
      "agencyName": "농림축산식품부",
      "departmentName": "친환경농업과",
      "phoneNumber": "044-201-2434"
    }
  ],
  "attachments": [
    {
      "fileName": "’26년 친환경농업직접지불 시행지침 1부.pdf",
      "extension": "pdf",
      "sizeBytes": 1537272,
      "url": "https://www.nongupez.go.kr/nsm/..."
    }
  ]
}
```

### Admin Sync Job Creation

```http
POST /api/v1/admin/policy-sync-jobs
```

Response:

```json
{
  "jobId": "uuid",
  "status": "RUNNING",
  "targetYear": "2026"
}
```

### Admin Sync Job Detail

```http
GET /api/v1/admin/policy-sync-jobs/{jobId}
```

Response:

```json
{
  "jobId": "uuid",
  "source": "NONGUP_EZ",
  "targetYear": "2026",
  "triggerType": "ADMIN",
  "status": "SUCCEEDED",
  "totalCount": 372,
  "syncedCount": 372,
  "detailSuccessCount": 360,
  "detailFailureCount": 12,
  "errorMessage": null,
  "startedAt": "2026-07-07T10:00:00",
  "finishedAt": "2026-07-07T10:07:12"
}
```

## Error Handling

Recommendation list:
- no latest successful sync job: return empty items and log `policy sync not ready`
- insufficient member profile/farm/crop data: compute from available signals
- old or malformed cursor: existing `INVALID_INPUT`

Policy detail:
- missing policy id: policy-specific not found error or existing invalid input
- detail not synchronized: return not found in the MVP. List-level-only policies
  are retained for future sync recovery but are not exposed through member APIs.

Sync:
- whole source failure: job `FAILED`, old successful data remains usable
- partial detail failure: job `SUCCEEDED`, failed policies are not recommendable
- card summary extraction failure: use fallback summary text

Admin:
- unauthenticated: `UNAUTHORIZED`
- non-admin: `FORBIDDEN`
- missing job: policy sync job not found error or existing invalid input

## Testing

Application tests:
- latest year detection ignores `0000` and selects max numeric year
- policy list and detail upsert by `source + externalId + sourceYear`
- detail failure records counts and makes policy non-recommendable
- card summary fields are 19 characters or fewer
- closed policies are excluded
- non-detail-synced policies are excluded
- missing recommendations are generated on read
- stale recommendations are deleted and recreated for latest sync job
- recommendation ordering is `score desc, applyEndsOn asc, id asc`
- reason text is built from matched signals

API tests:
- recommendation list returns cursor page
- malformed/stale cursor returns invalid input
- policy detail maps sections, contacts, and attachments
- admin sync job creation requires admin role
- admin sync job detail returns counters and status

Adapter tests:
- parse condition fixture
- parse list fixture
- parse detail fixture
- tolerate null optional fields
- parse `yyyyMMdd`
- handle `접수기관문의` period labels

Network calls to `농업e지` must not run in unit tests. Use JSON fixtures for
repeatability. A separate manual or integration profile can verify live calls.

## Open Implementation Notes

- Use `OpaqueCursorCodec` if it fits the recommendation cursor shape.
- Avoid adding new dependencies for JSON tags unless existing Hibernate/Jackson
  support is sufficient.
- If admin role support is incomplete, add the smallest security change needed
  for `ROLE_ADMIN`.
- Because Flyway is not present, schema update mechanics must be addressed in
  the implementation plan before running against dev/prod databases.
