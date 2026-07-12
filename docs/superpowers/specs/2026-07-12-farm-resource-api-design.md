# Farm Resource API Design

Date: 2026-07-12

Status: Pending user review

## Purpose

Separate farm management from member-profile editing so a member can add,
edit, list, and delete farms without sending unrelated profile fields. A farm
owns the member's current crop registrations and is referenced by farming
records; that relationship requires explicit resource-level mutation rules.

## Decisions

- Keep `POST /api/v1/auth/onboarding/complete` as a **single initial-farm**
  flow. It does not accept multiple farms.
- Make onboarding `nickname` optional. An omitted, `null`, or blank nickname
  is persisted as the supplied `name`.
- Remove `farms` from `PUT /api/v1/members/me/profile`. That endpoint updates
  only member profile fields and profile media.
- Add authenticated, member-owned farm resource APIs.
- Treat each farm request's `cropIds` as its complete current crop set.
  Create and update atomically synchronize `member_crop` links.
- Block farm deletion and crop-link removal when farming records reference the
  affected scope.
- Defer the equivalent farming-cycle-report guard until that module is merged
  into `dev` and exposes its repository contract.
- Preserve the existing profile read endpoint as a My Page projection. New
  farm-editing screens use the farm resource endpoints.

## Non-Goals

- Multi-farm onboarding.
- Soft deletion or audit history for farms.
- Editing historical farming records or reports as part of a farm change.
- Farming-cycle-report reference checks; that module is not yet available on
  the `dev` base for this feature.
- Changing the `PUT /api/v1/members/me/profile` nickname rule; only
  onboarding has nickname fallback behavior.

## Public API

All responses use the existing `ApiResponse` wrapper and all endpoints require
an authenticated member.

### Profile update

```http
PUT /api/v1/members/me/profile
```

The request keeps `name`, `phone`, `birthDate`, `nickname`,
`experienceLevel`, `managementType`, and `profileMediaId`. It no longer has a
`farms` field. This is an intentional breaking contract change while the
frontend integration is still in draft.

### Farm list

```http
GET /api/v1/farms
```

Returns every farm owned by the authenticated member. Each item carries the
full editable farm fields and its crop summaries, so the client can reload an
edit draft after navigation or app restart.

```json
{
  "farms": [
    {
      "farmId": "…",
      "name": "횡성 황기밭",
      "roadAddress": "강원특별자치도 횡성군 둔내면 샘물로 12",
      "jibunAddress": "강원특별자치도 횡성군 둔내면 현천리 101",
      "latitude": 37.0,
      "longitude": 127.0,
      "pnu": "…",
      "landCategory": "전",
      "areaSqm": 1234.5,
      "areaIsManualEntry": false,
      "boundaryCoordinates": [],
      "dataSource": {
        "address": "JUSO",
        "coordinate": "VWORLD",
        "parcel": "VWORLD",
        "landCharacteristic": "VWORLD"
      },
      "crops": [
        {
          "id": "…",
          "externalNo": 422,
          "name": "황기",
          "usePartCategory": "ROOT_BARK",
          "usePartCategoryLabel": "뿌리·껍질"
        }
      ]
    }
  ]
}
```

### Create and replace a farm

```http
POST /api/v1/farms
PUT /api/v1/farms/{farmId}
```

Both endpoints accept the same complete farm representation. `POST` returns
`201 Created`; `PUT` returns `200 OK`. Both return the saved farm detail shape
used by the list endpoint.

```json
{
  "name": "횡성 황기밭",
  "roadAddress": "강원특별자치도 횡성군 둔내면 샘물로 12",
  "jibunAddress": "강원특별자치도 횡성군 둔내면 현천리 101",
  "latitude": 37.0,
  "longitude": 127.0,
  "pnu": "…",
  "landCategory": "전",
  "areaSqm": 1234.5,
  "areaIsManualEntry": false,
  "boundaryCoordinates": [],
  "dataSource": {
    "address": "JUSO",
    "coordinate": "VWORLD",
    "parcel": "VWORLD",
    "landCharacteristic": "VWORLD"
  },
  "cropIds": ["…"]
}
```

`PUT` is chosen instead of `PATCH` because the client submits a full edit
draft: every mutable farm field and the final crop set are present.

### Delete a farm

```http
DELETE /api/v1/farms/{farmId}
```

Returns `204 No Content` only when no farming record (including soft-deleted
records) references the farm. The service deletes its `member_crop` links
before physically deleting the farm.

## Validation and Error Contract

The same creation validation applies to the onboarding farm and to `POST
/api/v1/farms`:

- Farm `name`, `roadAddress`, `latitude`, and `longitude` are required.
- `cropIds` contains from 1 through 5 distinct IDs.
- Every crop ID must exist.
- `areaSqm`, if supplied, is positive.
- Each boundary coordinate has both latitude and longitude. Latitude and
  longitude must be within their geographic ranges.
- `jibunAddress`, `pnu`, `landCategory`, area, boundary coordinates, and
  individual data-source values are optional.

Expected failures:

| Condition | Error | HTTP status |
| --- | --- | --- |
| Missing/invalid request field, crop count, or duplicate crop ID | existing validation error | 400 |
| Unknown crop ID | `CROP_NOT_FOUND` | 404 |
| Missing or non-owned farm | `FARM_NOT_FOUND` | 404 |
| Farm deletion has farming-record references | new `FARM_IN_USE` | 409 |
| Removing a crop linked to an existing farming record | new `FARM_CROP_IN_USE` | 409 |

Returning `FARM_NOT_FOUND` for another member's farm matches the current
owner-scoped repository convention and does not reveal its existence.

## Application and Data Flow

Create a focused `application.farm.FarmService` with list, create, replace,
and delete use cases plus `FarmCommand` and `FarmResult` types. Add
`api.farm.FarmController`, request DTOs, and response DTOs. Controllers map
HTTP DTOs to application commands; the service owns all validation beyond bean
validation, ownership checks, crop synchronization, and transactions.

On `PUT`, the service loads the owned farm and its current member-crop links.
Before removing a link, it checks whether the member/farm/crop scope has a
farming record. If it does, the whole request fails with
`FARM_CROP_IN_USE`; otherwise obsolete links are removed and newly requested
links are inserted in the same transaction as the farm update.

On `DELETE`, the service checks for any farm-scoped record before removing
links and the farm. Soft-deleted records still count because their foreign key
continues to reference the farm. Add equivalent report-reference checks in a
separate follow-up after the report module is available in `dev`.

`MemberProfileService` loses its nested farm command and its
upsert/synchronization helpers, but retains the farm and crop repositories
needed by its unchanged profile read projections. `OnboardingService` keeps
creating exactly one farm and its links, but gains the shared creation
constraints and nickname fallback.

No new tables or database migrations are required. Repository additions are
read-only farming-record existence queries for farm and member/farm/crop
references.

## Test Strategy

- Create `FarmServiceTest` for create, full replacement, crop-link add/remove
  synchronization, ownership, crop lookup failures, and both farming-record
  409 guards.
- Create `FarmControllerTest` for the `GET`, `POST`, `PUT`, and `DELETE`
  contracts, authentication, status codes, and bean-validation failures.
- Update `OnboardingServiceTest` and auth controller tests for nickname
  fallback, crop range, duplicate rejection, and required farm location.
  The current duplicate-crop de-duplication test becomes a rejection test.
- Update profile service and controller tests so profile writes no longer
  accept or mutate farms.
- Run focused domain/application/API tests first, then `./gradlew test` from
  `backend` as the completion gate.

## Risks and Mitigations

- Removing `farms` from the profile update is breaking. The frontend has not
  yet integrated the final API, and the Farm endpoints replace that mutation
  path in the same delivery.
- Preventing removal of crops with record history can require users to retain
  a crop registration longer than expected. It protects existing record
  queries, which validate the member/farm/crop relationship.
- Physical farm deletion is deliberately narrow: farm boundary values and
  member-crop links are removable only when no historical farming-record
  foreign-key references remain. A follow-up must add the same restriction for
  cycle reports after their module reaches `dev`.
