# Farming Record CRUD Design

Date: 2026-07-07
Status: Approved

## Purpose

Add full CRUD for `farming_record` (영농일지 기록): list, detail, create, update,
and delete. `FarmingRecordService.create()` and its type-specific detail
validation already exist at the application layer but are not exposed over
HTTP yet, and the result shape only carries `id` and `workType`. This design
adds the missing read/update/delete behavior, a weather field pair on the
record, media attachment, and the `api` layer surface.

Weather values (condition + temperature) are resolved by an external weather
API. Calling that external API is **out of scope** for this design (see
Non-Goals) — it will be designed separately. This spec only adds the storage
fields and accepts them as plain request input.

## Product Decisions

- A member can only list, view, update, and delete their own farming records.
- List filters: `cropId`, `workType`, and a `workedAt` date range
  (`startDate`..`endDate`). All filters are optional and combinable.
- List pagination is cursor-based infinite scroll, matching the
  `CommunityPost` list pattern (`(workedAt, id)` cursor via
  `OpaqueCursorCodec`).
- Weather is stored as `weatherCondition` (free-text string, e.g. `"맑음"`) and
  `weatherTemperature` (integer degrees). Both are required on every record.
  The backend does not validate `weatherCondition` against a fixed enum,
  since the value originates from an external provider whose categories are
  not finalized yet.
- Update re-submits the full record shape (same fields as create), including
  `workType`. Changing `workType` is allowed.
- On update, whichever type-specific detail row currently exists for the
  record is deleted (using the repository matching the record's *previous*
  `workType`, not all six repositories), then a new detail row is inserted
  for the new `workType` — mirroring the existing per-type `saveDetail`
  dispatch used by create.
- `FarmingRecordDetailValidator` currently validates
  `FarmingRecordCommand.Create` only. Extract a shared
  `FarmingRecordDetailPayload` interface (workType + the six optional detail
  blocks) implemented by both `Create` and `Update` commands, and change the
  validator to accept that interface. This avoids duplicating the per-type
  required-detail rules for update.
- Delete is a soft delete (`is_deleted`), not a hard delete. Records must
  survive even after the owning member's account is removed, and
  `community_post.farming_record_id` may reference a record — hard delete
  would either orphan that FK or require cascading into community data,
  which is out of scope here.
- Accessing a record that does not exist (or is soft-deleted) returns
  `FARMING_RECORD_NOT_FOUND` (404). Accessing a record that exists but is
  owned by a different member returns a new `FARMING_RECORD_FORBIDDEN` (403).
  This means detail/update/delete look up the record by `id` alone (filtered
  by `is_deleted = false`), then separately assert ownership — unlike the
  existing `farmRepository.findByIdAndOwner_Id` pattern, which collapses
  both cases into `FARM_NOT_FOUND`.
- Farming-record media reuses `UploadedMediaUsageType.FARMING_RECORD` (already
  defined) and the same validation rules as community post media: owned by
  the member, correct usage type, and attachable. Limited to 5 images per
  record, matching the community post image cap.
- `entryMode` stays fixed at `"MANUAL"` and is not part of the update payload
  — it is not part of this design's scope to change.

## Data Model

### farming_record

Add:

- `weather_condition varchar(64) not null`
- `weather_temperature int not null`
- `is_deleted boolean not null default false`

Existing `val` fields (`farm`, `crop`, `workType`, `workedAt`, `memo`) become
`var` so `FarmingRecord.update(...)` can mutate them in place under JPA dirty
checking, matching the `CommunityPost.update()` pattern. `id`, `member`, and
`entryMode` stay immutable. Add `softDelete()` setting `is_deleted = true`.

### FarmingRecordRepository

Add `findByIdAndIsDeletedFalse(id: UUID): FarmingRecord?` for detail/update/
delete lookups (ownership is checked separately, see Product Decisions).

### FarmingRecordQueryRepository (new, QueryDSL)

Mirrors `CommunityPostQueryRepository`/`Impl`. Dynamic `BooleanBuilder`
conditions for `memberId` (required), `cropId?`, `workType?`,
`workedAtFrom?`, `workedAtTo?`, plus a `(workedAt, id)` cursor and `size`.
Only `is_deleted = false` rows are returned.

### Type-specific detail repositories

Add `deleteByRecord(record: FarmingRecord)` to each of the six existing
repositories: `PlantingRecordRepository`, `WateringRecordRepository`,
`FertilizingRecordRepository`, `PestControlRecordRepository`,
`WeedingRecordRepository`, `HarvestRecordRepository`. Update calls exactly
one of these (the one matching the record's prior `workType`), not all six.

## Public API

All responses use the existing `ApiResponse` wrapper. All endpoints require
an authenticated member (`@AuthenticationPrincipal`, existing
`parseMemberId()` helper pattern).

### Create Record

```http
POST /api/v1/farming-records
```

Request:

```json
{
  "farmId": "00000000-0000-0000-0000-000000000101",
  "cropId": "00000000-0000-0000-0000-000000000201",
  "workType": "HARVEST",
  "workedAt": "2026-05-30T09:00:00",
  "weatherCondition": "맑음",
  "weatherTemperature": 28,
  "memo": "수확 완료",
  "harvest": {
    "harvestAmount": 12.5,
    "harvestAmountUnit": "KG",
    "harvestSource": "CULTIVATED",
    "growthPeriod": 2,
    "growthPeriodUnit": "YEAR"
  },
  "mediaIds": ["00000000-0000-0000-0000-000000000301"]
}
```

Response:

```json
{
  "id": "00000000-0000-0000-0000-000000000401",
  "workType": "HARVEST"
}
```

Validation:

- `farmId`, `cropId`, `workType`, `workedAt`, `weatherCondition`,
  `weatherTemperature` are required.
- `farmId` must belong to the member.
- Exactly one of the type-specific detail blocks matches `workType`; required
  per the existing `FarmingRecordDetailValidator` rules.
- `mediaIds` length must be 0 to 5. Media must be owned by the member, have
  `usage_type = FARMING_RECORD`, and be attachable.

### List Records

```http
GET /api/v1/farming-records
```

Query parameters:

- `cropId` (optional)
- `workType` (optional)
- `startDate`, `endDate` (optional, applied to `workedAt`)
- `cursor` (optional)
- `size` (default 20)

Behavior:

- Returns only the authenticated member's records, latest `workedAt` first.
- Only `is_deleted = false` records are returned.

Response:

```json
{
  "items": [
    {
      "id": "00000000-0000-0000-0000-000000000401",
      "cropName": "마늘",
      "workType": "HARVEST",
      "workedAt": "2026-05-30T09:00:00",
      "weatherCondition": "맑음",
      "weatherTemperature": 28,
      "memoPreview": "수확 완료",
      "thumbnailUrl": "https://res.cloudinary.com/example/image/upload/..."
    }
  ],
  "nextCursor": "opaque-cursor-string"
}
```

### Get Record Detail

```http
GET /api/v1/farming-records/{recordId}
```

Response includes farm id/name, crop id/name, `workType`, `workedAt`,
weather (`weatherCondition`, `weatherTemperature`), `memo`, the matching
type-specific detail block, all attached image URLs, `createdAt`, and
`updatedAt`.

Not found or soft-deleted -> `FARMING_RECORD_NOT_FOUND`. Owned by another
member -> `FARMING_RECORD_FORBIDDEN`.

### Update Record

```http
PATCH /api/v1/farming-records/{recordId}
```

Same request shape as create. `workType` may change. The existing detail row
is replaced and media associations are replaced (existing
`farming_record_media` rows for this record are deleted, then the new
`mediaIds` are attached), matching the `CommunityPostService.update()`
pattern.

Same validation as create, plus ownership (`FARMING_RECORD_FORBIDDEN` if the
record belongs to another member).

Response: same as create (`id`, `workType`).

### Delete Record

```http
DELETE /api/v1/farming-records/{recordId}
```

Soft-deletes the record (`is_deleted = true`). Ownership rules are the same
as update.

## Application Structure

```text
api.farming
- FarmingRecordController
- FarmingRecordRequests (SaveRecordRequest + per-type detail DTOs)
- FarmingRecordResponses (RecordIdResponse, RecordSummaryResponse,
  RecordDetailResponse, RecordPageResponse)

application.farming
- FarmingRecordService (create/search/getDetail/update/delete)
- FarmingRecordCommand (Create, Update, Delete)
- FarmingRecordDetailPayload (new shared interface for validator reuse)
- FarmingRecordSearchCondition (new)
- FarmingRecordResult (RecordId, Detail, Summary, Page — Detail is
  redefined to carry the full response shape; the current minimal
  id+workType shape moves to a new RecordId type)
- FarmingRecordDetailValidator (validates FarmingRecordDetailPayload)

domain.farming
- FarmingRecord (add weather fields, is_deleted, var fields, update()/softDelete())
- FarmingRecordRepository (add findByIdAndIsDeletedFalse)
- FarmingRecordQueryRepository / Impl (new, QueryDSL)
- FarmingRecordMediaRepository (add `deleteByRecord(record: FarmingRecord)`,
  matching `CommunityPostMediaRepository.deleteByPost(post)`)
- Planting/Watering/Fertilizing/PestControl/Weeding/HarvestRecordRepository
  (add `deleteByRecord(record: FarmingRecord)`, same style)
```

## Search And Pagination

Use `FarmingRecordSearchCondition` as the application-level query input:

```kotlin
data class FarmingRecordSearchCondition(
    val memberId: UUID,
    val cropId: UUID?,
    val workType: WorkType?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val cursor: String?,
    val size: Int
)
```

The domain query boundary is `FarmingRecordQueryRepository.search(condition)`,
implemented with QueryDSL, matching `CommunityPostQueryRepository`.

## Error Handling

Expected failures use `BusinessException(ErrorCode.X)`.

New error codes:

- `FARMING_RECORD_FORBIDDEN` (403) — record exists but is not owned by the
  requesting member.
- `FARMING_RECORD_TOO_MANY_IMAGES` (400) — more than 5 `mediaIds`.

Reused error codes: `FARMING_RECORD_NOT_FOUND`, `FARMING_RECORD_DETAIL_REQUIRED`,
`FARMING_RECORD_INVALID_DETAIL`, `FARM_NOT_FOUND`, `CROP_NOT_FOUND`,
`MEMBER_NOT_FOUND`, `MEDIA_NOT_FOUND`, `MEDIA_NOT_OWNED`,
`MEDIA_USAGE_MISMATCH`, `MEDIA_NOT_ATTACHABLE`.

## Testing

Application tests (`FarmingRecordServiceTest`):

- create persists weather fields and attaches up to 5 media
- create rejects more than 5 `mediaIds`
- update replaces the detail row when `workType` changes
- update replaces the detail row when `workType` is unchanged but detail
  values change
- update replaces media associations
- update/delete/getDetail throw `FARMING_RECORD_NOT_FOUND` for a missing or
  soft-deleted record
- update/delete/getDetail throw `FARMING_RECORD_FORBIDDEN` for a record owned
  by another member
- delete sets `is_deleted = true` and does not remove the row
- soft-deleted records are excluded from `getDetail` and `search`

`FarmingRecordDetailValidatorTest`:

- extend existing per-type cases to run against both `Create` and `Update`
  payloads via the shared `FarmingRecordDetailPayload` interface

`FarmingRecordQueryRepositoryTest` (new, integration test against a real
query, matching `CommunityPostQueryRepositoryTest`):

- filter combinations (`cropId`, `workType`, date range) return the expected
  rows
- cursor pagination does not skip or duplicate rows across pages
- another member's records are never returned

API tests (`FarmingRecordControllerTest`, new):

- request validation for create/update (`weatherCondition`,
  `weatherTemperature`, `mediaIds` size)
- authentication required on all endpoints
- list query parameter binding and cursor-page response shape

## Non-Goals

- Calling an external weather API from the backend. This spec only stores
  `weatherCondition`/`weatherTemperature` values supplied in the request.
- Hard delete or permanent purge of farming records.
- Changing `entryMode` via the update API.
- Any change to `community_post`'s existing `farmingRecordId` validation
  (crop-match, ownership) — that logic already exists and is unaffected.
- Farm transfer validation beyond "the target `farmId` must belong to the
  requesting member," which already exists via `findByIdAndOwner_Id`.
