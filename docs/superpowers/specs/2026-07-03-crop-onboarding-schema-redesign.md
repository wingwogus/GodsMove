# Crop And Onboarding Schema Redesign

Date: 2026-07-03

## Purpose

Simplify ChamChamCham's crop and onboarding model around the current product
need:

- Crops are selected from a fixed medicinal-plant catalogue.
- Crop category means medicinal use-part category, not crop family or broad
  agriculture type.
- Onboarding creates the member profile and the member's first farm together.
- Onboarding also receives the member's initial crop selections and creates
  `member_crop` links.
- Farm location is stored as one road-address string.
- The implementation will target `dev` first and then be reflected to `main`.

This design intentionally chooses the minimal model. It does not add
many-to-many crop categories, crop lifecycle, planting status, or farm address
parsing until a concrete feature needs them.

## Current Context

The backend currently has these relevant entities:

- `Crop`: `name`, `category`, `lifecycleType`, `defaultUnit`
- `MemberCrop`: `member`, `farm`, `crop`, `plantingYear`, `status`,
  `startedOn`
- `Member`: profile fields plus `region`, `experienceLevel: String?`, and
  `managementType` with `REGISTERED` / `UNREGISTERED`
- `Farm`: `name`, `region`, `city`, `street`

The current onboarding endpoint updates only `Member`; it does not create a
farm. There is no public crop catalogue API yet.

The generated API-derived data source is:

- `outputs/forest-mcllt-api/forest_mcllt_records.json`
- 534 records from `mclltInfoService/getMclltSearch`
- Relevant fields: `mclltNo`, `mclltSpecsNm`, `usMthodDscrt`

The generated Excel workbook is a convenience artifact for review, but the
seed should be generated from the normalized JSON or equivalent checked-in
seed resource, not from binary Excel at runtime.

## Product Decisions

- Use approach A: minimal domain redefinition.
- `crop.usePartCategory` is the crop category used by the service.
- `crop.externalNo` stores the Forest Service `mclltNo` and is unique.
- Keep one category per crop. Do not introduce a many-to-many table.
- Merge root-related categories:
  - previous `뿌리`
  - previous `뿌리/껍질`
  - previous `껍질`
  into `ROOT_BARK`.
- Keep `RHIZOME` separate from `ROOT_BARK`.
- Remove `lifecycleType` and `defaultUnit` from `Crop`.
- Remove `plantingYear`, `status`, and `startedOn` from `MemberCrop`.
- Remove `region` from `Member`.
- Change `experienceLevel` from `String?` to `Int?` with API validation
  range `0..100`.
- Replace `ManagementType` with three explicit values:
  - `AGRICULTURAL_INDIVIDUAL`
  - `AGRICULTURAL_CORPORATION`
  - `NON_REGISTERED_FARMER`
- Change `Farm` address modeling from `region/city/street` to one `address`
  string.
- Onboarding creates or updates the member profile and creates the first farm
  and initial member-crop links in one transaction.

## Domain Model

### Crop

```kotlin
class Crop(
    val id: UUID? = null,
    val externalNo: Int,
    val name: String,
    val usePartCategory: CropUsePartCategory,
)
```

Constraints:

- `external_no` is not null and unique.
- `name` is not null.
- `use_part_category` is stored as an enum string.

Enum:

```kotlin
enum class CropUsePartCategory {
    WHOLE_HERB,
    ROOT_BARK,
    RHIZOME,
    LEAF,
    FLOWER,
    FRUIT,
    SEED,
    STEM_BRANCH,
    UNKNOWN,
}
```

API display labels can be supplied by response mapping:

| Code | Label |
| --- | --- |
| `WHOLE_HERB` | 전초 |
| `ROOT_BARK` | 뿌리·껍질 |
| `RHIZOME` | 뿌리줄기 |
| `LEAF` | 잎 |
| `FLOWER` | 꽃 |
| `FRUIT` | 열매/과실 |
| `SEED` | 종자 |
| `STEM_BRANCH` | 줄기/가지 |
| `UNKNOWN` | 기타 |

### MemberCrop

```kotlin
class MemberCrop(
    val id: UUID? = null,
    val member: Member,
    val farm: Farm,
    val crop: Crop,
)
```

The table only represents that a member grows or manages a crop at a farm. It
does not model planting start date, year, or status.

Implementation must keep the domain/table name `member_crop`. Do not introduce
project-owned `user_crop` naming.

### Member

Remove `region`. Keep member identity and personal onboarding data:

```kotlin
var name: String?
var phone: String?
var birthDate: LocalDate?
var nickname: String?
var experienceLevel: Int?
var managementType: ManagementType?
```

`managementType` must be nullable and mutable because social-login member
creation happens before onboarding. Onboarding sets the value, and onboarding
status treats a missing management type as incomplete.

### Farm

```kotlin
class Farm(
    val id: UUID? = null,
    val owner: Member,
    val name: String,
    val address: String,
)
```

The backend stores the road address as entered. It does not parse region,
city, street, legal-dong, coordinates, or policy region codes in this change.

## Public API

### Crop List

```http
GET /api/v1/crops
```

Response data:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000000",
    "externalNo": 159,
    "name": "가락지나물",
    "usePartCategory": "WHOLE_HERB",
    "usePartCategoryLabel": "전초"
  }
]
```

Sorting:

- Primary: `name` ascending using DB/default collation.
- Secondary: `externalNo` ascending.

### Crop Categories

```http
GET /api/v1/crops/categories
```

Response data:

```json
[
  {
    "code": "WHOLE_HERB",
    "label": "전초"
  }
]
```

The category list is enum-backed. It should not query the crop table unless a
future feature needs category counts.

### Complete Onboarding

```http
POST /api/v1/auth/onboarding/complete
Authorization: Bearer <accessToken>
```

Request:

```json
{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "birthDate": "1990-01-01",
  "nickname": "길동",
  "experienceLevel": 3,
  "managementType": "AGRICULTURAL_INDIVIDUAL",
  "farmName": "하늘들 약초농장",
  "farmAddress": "강원특별자치도 평창군 진부면 하늘들길 12",
  "cropIds": [
    "00000000-0000-0000-0000-000000000001",
    "00000000-0000-0000-0000-000000000002"
  ]
}
```

Validation:

- `name`, `phone`, `nickname`, `farmName`, `farmAddress`,
  `managementType`: required.
- `birthDate`: required.
- `experienceLevel`: required, integer, `0..100`.
- `cropIds`: required, non-empty list of UUIDs.
- Duplicate crop IDs are accepted but de-duplicated before insert.
- If any requested crop ID does not exist, the whole onboarding request fails.

Response:

```json
{
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "member@example.com",
    "name": "홍길동",
    "phone": "010-1234-5678",
    "birthDate": "1990-01-01",
    "nickname": "길동",
    "experienceLevel": 3,
    "managementType": "AGRICULTURAL_INDIVIDUAL"
  },
  "farm": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "하늘들 약초농장",
    "address": "강원특별자치도 평창군 진부면 하늘들길 12"
  },
  "crops": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "externalNo": 159,
      "name": "가락지나물",
      "usePartCategory": "WHOLE_HERB",
      "usePartCategoryLabel": "전초"
    }
  ],
  "onboarding": {
    "status": "COMPLETE",
    "missingFields": []
  }
}
```

Existing login responses also remove `member.region`, return
`experienceLevel` as a number, and return `managementType` as `null` until
onboarding sets it.

## Application Flow

`OnboardingService.complete` becomes a transaction that:

1. Loads the authenticated member.
2. Updates member profile fields and management type.
3. Creates a `Farm` owned by that member using `farmName` and `farmAddress`.
4. Loads all unique requested crops.
5. Fails the request if any crop ID is missing.
6. Creates one `MemberCrop` per unique crop for the new farm.
7. Returns member profile, farm summary, selected crop summaries, and
   onboarding status.

Onboarding status is complete when these member fields exist:

- `name`
- `phone`
- `birthDate`
- `nickname`
- `experienceLevel`
- `managementType`

Farm existence is also required for onboarding completion because onboarding
now creates the first farm. At least one selected crop link is also required
because onboarding now creates initial `member_crop` rows. Keep
`OnboardingStatusResolver` focused on member fields. `OnboardingService.complete`
returns `COMPLETE` only after the member fields are valid, the farm insert
succeeds, and at least one `member_crop` row is inserted in the same
transaction.

## Seed Data

The crop seed source is the Forest Service API export. The implementation
should transform each record:

| Source field | Target |
| --- | --- |
| `mclltNo` | `crop.externalNo` |
| `mclltSpecsNm` | `crop.name` |
| inferred category from `usMthodDscrt` | `crop.usePartCategory` |

Category inference should use the same merge policy as the workbook:

- `전초` -> `WHOLE_HERB`
- `뿌리줄기`, `근경`, `덩이줄기`, `괴경`, `구경`, `인경`, `비늘줄기` -> `RHIZOME`
- `뿌리`, `근피`, `뿌리껍질`, `나무껍질`, `수피`, `껍질` -> `ROOT_BARK`
- `잎`, `엽` -> `LEAF`
- `꽃`, `화서`, `꽃봉오리`, `화뢰` -> `FLOWER`
- `열매`, `과실`, `핵과`, `장과` -> `FRUIT`
- `종자`, `씨앗`, `씨를` -> `SEED`
- `줄기`, `가지`, `목부`, `덩굴` -> `STEM_BRANCH`
- unmatched -> `UNKNOWN`

The implementation should generate a checked-in JSON seed resource under the
backend source tree from `outputs/forest-mcllt-api/forest_mcllt_records.json`.
The final application must not depend on the local `outputs/` directory.

Seed loading should be idempotent by `externalNo`:

- insert new crops
- update `name` and `usePartCategory` for existing `externalNo`

## Database Reset And Home Server Operations

The current repository deploys via Docker Compose over SSH, not the NuguSauce
OCI/Kubernetes path. The home server hints are:

- SSH host: `wingwogus@hyunserver.iptime.org`
- common app root: `/home/wingwogus/apps`
- current deploy workflow path: `/home/wingwogus/apps/chamchamcham`

These are hints only. Before any DB reset, verify live state with
`home-server-ops` read-only inventory and service-map style checks. Do not
print `.env` values or Docker environment arrays.

DB reset is a separate implementation/deployment step after code is tested and
merged. The reset procedure must:

1. Confirm the target branch and deployed image.
2. Inspect the running Compose project and PostgreSQL container names.
3. Back up or explicitly confirm no data must be preserved.
4. Stop only the ChamChamCham API service if needed.
5. Recreate the ChamChamCham PostgreSQL schema/database or volume by the
   narrowest agreed command.
6. Start the API with the new schema strategy.
7. Load crop seed data.
8. Verify crop count, onboarding smoke test, and API health.

No destructive DB command should run during design or planning.

## Branch Flow

Implementation flow:

1. Build and verify on the current feature branch from `dev`.
2. Merge or replay the verified changes onto `dev`.
3. Reflect the same approved change onto `main`.
4. Let the existing GitHub Actions Docker deploy run from `main`.
5. Perform the home-server DB reset only after the deployed application is
   verified to be compatible with the new schema.

## Testing

Backend tests should cover:

- `CropUsePartCategory` labels and category response mapping.
- Crop list API returns `externalNo`, `name`, and category only.
- Crop category API returns all enum values in stable order.
- Onboarding request validation rejects missing farm fields, invalid
  management type, empty `cropIds`, malformed crop IDs, and `experienceLevel`
  outside `0..100`.
- `OnboardingService` updates member data, creates a farm, validates all
  requested crops, and creates member-crop links in one transaction.
- `OnboardingService` de-duplicates repeated crop IDs before creating
  `MemberCrop` rows.
- `OnboardingService` fails atomically when any requested crop ID does not
  exist.
- `OnboardingStatusResolver` no longer expects member region.
- Coaching context uses farm address and crop use-part category, not removed
  fields.

Recommended verification:

```bash
cd backend
./gradlew test
```

## Out Of Scope

- Many-to-many crop categories.
- Crop lifecycle and default unit.
- Planting year/status/started date tracking.
- Farm address parsing, geocoding, latitude/longitude, and region codes.
- Preserving existing production data during the requested DB reset.
- OCI/Kubernetes deployment changes.
