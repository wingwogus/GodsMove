# Frontend Onboarding Flow Plan

Date: 2026-07-02

## Purpose

Define the onboarding flow's screens, client-side state, and the API contracts it needs, ahead of backend API finalization. This is a flow/behavior plan only — no visual design. Source material: `frontend/docs/와이어프레임 초안/onboarding_1.png`, `onboarding_2.png`, and `BR-USER-001`/`BR-USER-002` in `Business Rule.md`.

## Why Onboarding Is a Special Case

The rest of the app defaults to offline-first local storage (see [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)). Onboarding is the one exception: identity and account creation cannot happen offline, so this flow is online-only, with explicit error/retry states rather than local draft-and-sync.

## Screen / State Sequence

Driven by an `OnboardingViewModel` (`@Observable`) holding draft form state — not SwiftData, since this is a one-shot flow outside the sync pipeline. The flow is resumable across app kill and network loss: `OnboardingDraftStore` persists a `{ step, draft }` snapshot as JSON in `UserDefaults` (the profile photo itself is written to a file under Application Support, with only its filename in the snapshot, to keep the `UserDefaults` payload small). The snapshot is saved on every step transition and on the `scenePhase → .background` lifecycle event — the reliable hook for imminent process termination, the same reasoning used for the voice-session reconciliation sweep. On relaunch, `OnboardingViewModel` restores `currentStep`/`draft` from the snapshot if present, so the flow reopens exactly where it left off rather than restarting at Landing. The snapshot is cleared once the step 6→7 atomic submission succeeds (implemented 2026-07-03, see `OnboardingViewModel.submit`).

1. **Landing** — 소셜로그인 버튼 (카카오 / 네이버 / 애플). No custom UI beyond a loading state; provider SDKs own their own login UI.
2. **Social login exchange** — generate a nonce → provider SDK login requesting OIDC → send `idToken` + `nonce` to the backend → receive access/refresh tokens → store in Keychain. `LoginResponse` (confirmed, see below) already carries `onboarding: { status, missingFields }`, so the client can route straight to onboarding vs. Home off the login response — no separate status round trip needed.
3. **Basic info + Profile (merged into one screen, `BasicProfileView`)** — 이름\*, 닉네임, 연락처\*(phone), 생년월일\*, 영농 경력\*(numeric years input, "N년차" — maps to `experienceYears: Int?` on `OnboardingDraft`, not a categorical picker), 농업경영체 자격(managementType, 일반/법인/미가입), 사진(선택). Fields marked \* map 1:1 to `OnboardingCompleteRequest`'s required fields; `managementType` is also part of the confirmed request shape (see "Known Gaps" below) — only profile photo has no server-side field to map to.
4. **Crop selection** (`CropSelectionView`, built) and **Farm location** (`FarmLocationView`, built 2026-07-03) — both **in scope for onboarding**, per the resolved data-creation scope below. Farm location uses the JUSO + V-World chain from Section 5 below: address search sheet → geocode → tap-to-select cadastral parcel on a native SwiftUI `Map` → official area/지목 lookup, with a manual-area-entry fallback when no parcel is found at the tapped point. `OnboardingDraft`'s single `farmAddress` field was replaced with `farmRoadAddress`/`farmJibunAddress`/`farmPNU`/`farmLandCategory`/`farmAreaSqm`/`farmAreaIsManualEntry` to match the Section 6 schema draft; the full parcel polygon is intentionally not persisted in the draft (screen-local state only, resets on relaunch — only the resolved fields survive).
5. **Complete** → navigate into `MainTabView` / Home.

Submission is fully wired up (implemented 2026-07-03/confirmed 2026-07-06): `OnboardingViewModel.submit` calls the real `RemoteOnboardingRepository.completeOnboarding`, which sends `OnboardingCompleteRequestDTO` to `POST /api/v1/auth/onboarding/complete`. The atomic submission creates the member profile, a farm, and `member_crop` rows together — see "Onboarding Data Creation Scope" below.

## Onboarding Data Creation Scope (resolved 2026-07-03, confirmed against backend source 2026-07-06)

**Decision: onboarding creates the member profile, a farm, and `member_crop` rows together, in one flow.** `Business Rule.md` BR-USER-002 is authoritative and now matches the actual backend implementation — confirmed directly against `AuthController`/`AuthRequests.kt`/`Member.kt`/`Farm.kt`/`MemberCrop.kt` in `backend/api` and `backend/domain`, not just the Notion API-spec export.

Three source documents previously disagreed on what onboarding creates; this is now resolved in code, not just by product decision:

- **`Business Rule.md` BR-USER-002`**: required fields include 이름/연락처/생년월일/닉네임/영농경력/자격/농장/주요 작물(1개 이상); completion creates `member` + `farms` + `member_crop` together, "기본 농장은 최초 1개 생성한다." (`Business Rule.md` updated 2026-07-06 to match.)
- **`온보딩 완료` endpoint doc** (`docs/API 명세서/`, updated 2026-07-06): previously said "MVP에서는 약관 동의, 기본 농장 생성, 중간 임시저장을 처리하지 않는다" — the "기본 농장 생성" clause was wrong and has been removed; the doc now states the endpoint creates member + farm + member_crop in one call.
- **Actual backend contract** (`AuthController.kt` → `AuthRequests.CompleteOnboardingRequest`): `{ name, phone, birthDate, nickname, experienceLevel: Int, managementType: ManagementType, farm: FarmRequest, cropIds: List<UUID> }`, with `@NotNull`/`@NotEmpty` validation on `farm` and `cropIds` — so farm + at least one crop are backend-enforced requirements, not just frontend UX gating. This exactly matches the frontend's `OnboardingCompleteRequestDTO`/`FarmRequestDTO` (`Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`).

`Business Rule.md` and the API spec are both Notion exports owned outside this codebase and get wholesale-replaced on re-export (as already happened once to the old `API명세서(260702)` folder) — edits made here (2026-07-06, during a pre-dev audit pass) may be lost on the next sync and should be re-verified against `backend/api`/`backend/domain` source at that point, which is the authoritative source of truth per `frontend/AGENTS.md`.

**Action item — resolved, no further backend communication needed.** The single-call shape is confirmed and implemented end-to-end (frontend DTO → backend request → member/farm/member_crop creation). Partial-failure-mid-sequence behavior (e.g. member row created but farm creation fails) is handled server-side within a single request/transaction, not a client-orchestrated multi-call sequence, so the client's resumable-draft mechanism covering client-side state only is sufficient — there is no partial-server-state case to recover from on the client.

## Known Gaps in the Merged Basic Info + Profile Screen

- **`region`** — resolved and confirmed (2026-07-06): `region` doesn't exist anywhere in the deployed backend — not on `Member`, not on `CompleteOnboardingRequest`, not in `OnboardingStatusResolver`'s `missingFields` tokens. It was fully removed, not just deprioritized. The user's own residence is never collected; what onboarding collects instead is **농경지 위치** (farm location) — road address, parcel/coordinate data — captured on the `Farm` entity via the JUSO+V-World flow (see "Farm Location ERD Draft" below, now ported into `ERD 초안.md`).
- **`managementType`** (자격) — resolved and confirmed (2026-07-06): collected in `BasicProfileView` (segmented picker + guide banner) and included in `CompleteOnboardingRequest.managementType: ManagementType?` with `@NotNull` validation server-side. `ManagementType` has exactly 3 values: `AGRICULTURAL_INDIVIDUAL`, `AGRICULTURAL_CORPORATION`, `NON_REGISTERED_FARMER` — matching `OnboardingDraft.ManagementType` client-side.
- **Profile photo** — still confirmed absent from the API surface (unchanged as of 2026-07-06). No photo/image/avatar field on `Member`, `MemberProfileResponseDTO`, or `CompleteOnboardingRequest`. The `PhotosPicker` UI in `BasicProfileView` currently has nothing to upload to server-side — this remains a real gap, not just a stale doc.
- **`experienceLevel`** shape — resolved and confirmed (2026-07-06): it's a plain `Int?` on the `Member` entity (`@Min(0) @Max(100)` server-side validation), not a categorical enum — matching `OnboardingDraft.experienceYears: Int?` and the numeric-only text field in `BasicProfileView`. The old API-spec example showing `"experienceLevel": "BEGINNER"` was simply wrong/stale; no mapping step is needed.

## Gating Behavior

Per BR-USER-001: "회원가입만 완료된 상태는 서비스를 사용할 수 없다. 온보딩이 완료되어야 정상 회원으로 간주한다. 온보딩 완료 전에는 Home, 영농기록, 커뮤니티 접근을 제한한다." `AppState.isOnboarded` must gate `RootView`'s switch between `AuthFlowView` and `MainTabView` — a member who completed login but not onboarding must always land back in the onboarding flow, never in Home.

## API Contracts (Confirmed 2026-07-03)

> **Update (2026-07-03):** The API spec was re-exported with full field-level DTO shapes at `frontend/docs/API 명세서/` (previously `API명세서(260702)/`, CSV-only, no field shapes — that folder is gone). The items below are now confirmed at the field level unless noted otherwise.

### 1. Login response carries onboarding status — confirmed against backend source 2026-07-06

All three login endpoints (Kakao `AUTH-001`, Apple `AUTH-002`, Naver `AUTH-003`) return the same `LoginResponse`:

```json
{
  "accessToken": "...", "refreshToken": "...",
  "member": { "id", "email", "name", "phone", "birthDate", "nickname", "experienceLevel", "managementType" },
  "onboarding": { "status": "REQUIRED" | "COMPLETE", "missingFields": [...] }
}
```

`missingFields` values (`OnboardingStatusResolver.OnboardingField` in `backend/application`): `NAME`, `PHONE`, `BIRTH_DATE`, `NICKNAME`, `EXPERIENCE_LEVEL`, `MANAGEMENT_TYPE`. No `REGION` token — `region` was removed, not just excluded from this list. Client can route to onboarding vs. Home directly off this, no second request needed.

### 2. Naver and Apple login — confirmed

- `KakaoLoginRequest { idToken, nonce, kakaoAccessToken? }`
- `AppleLoginRequest { identityToken, nonce, authorizationCode?, userIdentifier? }`
- `NaverLoginRequest { accessToken }`

All three create/find a member via `ExternalIdentity(provider, providerSubject)`; provider profile values only prefill empty member fields; existing identities don't re-require email.

### 3. Crop listing endpoint — confirmed against `CropController.kt` 2026-07-06 (real shape differs from the old API-spec draft)

`CropController` (`backend/api`) exposes three plain, unparameterized `GET` endpoints — no `keyword`/`lifecycleType`/`page`/`size` query params and no pagination envelope, contrary to the earlier API-spec draft:

- `GET /api/v1/crops` → `List<CropResponse>`, `CropResponse { id, externalNo, name, usePartCategory, usePartCategoryLabel }`
- `GET /api/v1/crops/categories` → `List<CategoryResponse>`, `CategoryResponse { code, label }`
- `GET /api/v1/crops/categories/{category}/crops` → `List<CropResponse>` filtered by `CropUsePartCategory` path variable

The client fetches the full list once and filters/searches client-side (`CropCatalogService.fetchCrops()`/`fetchCategoryLabels()`, `CropSelectionView`'s local `filteredCrops`) rather than sending `keyword` to the server. In scope for onboarding per "Onboarding Data Creation Scope" above; crop selection registers as `member_crop` rows (`member_id`/`farm_id`/`crop_id` only — no `planting_year`/`status`/`started_on` as an earlier ERD draft assumed) created atomically by the onboarding-completion call below, not a separate upsert call.

### 4. Onboarding-completion endpoint — confirmed final shape 2026-07-06

- `AUTH-004` — `POST /api/v1/auth/onboarding/complete`, Bearer auth, uses the token's member id.
- Request `CompleteOnboardingRequest { name, phone, birthDate, nickname, experienceLevel: Int, managementType: ManagementType, farm: FarmRequest, cropIds: List<UUID> }` — `farm` and `cropIds` are `@NotNull`/`@NotEmpty` server-side, i.e. backend-enforced, not just frontend UX gating. `FarmRequest { name, roadAddress, jibunAddress?, latitude, longitude, pnu?, landCategory?, areaSqm?, areaIsManualEntry, boundaryCoordinates? }` (no `region`/`city`/`street`/`farmType`).
- Response `OnboardingCompleteResponse { member: MemberProfileResponse, farm: FarmResponse, crops: [...], onboarding: OnboardingResponse }`; token is not reissued.
- **Confirmed: this single call creates the member profile, a farm, and `member_crop` rows together.** Matches the frontend's `OnboardingCompleteRequestDTO`/`FarmRequestDTO` exactly (`Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`). No accompanying call sequence is needed.

### 5. Map / address-search SDK — resolved 2026-07-03

**Decision: JUSO (주소 검색) + V-World (연속지적도/좌표변환/토지특성정보).** The Apple MapKit + Kakao Local REST API candidate previously suggested in this doc is dropped. Reference implementation: the throwaway spike at `/Users/user/Project/v-world-test/v-world-test` validates the full JUSO + V-World call chain end-to-end — reuse its request/response shapes as a reference, but treat it only as a spike (its architecture, not its file layout, is what to reuse), per `frontend/AGENTS.md`.

Concretely, from the spike:
- `JusoAPIService` — 주소 검색 (JUSO API) → `JusoResult { roadAddrPart1, jibunAddr, bdNm, entX, entY }` (road address, jibun address, building name, and entrance coordinates as strings).
- `VWorldAPIService` — 연속지적도(GetFeature) lookup by coordinate → parcel polygon + `FarmlandParcel { pnu, jibunAddr, jimok, areaSqm (computed from polygon via Shoelace formula), coordinates: [CLLocationCoordinate2D] }`.
- `LandCharacterAPIService` — V-World-hosted 토지특성정보 (`api.vworld.kr/ned/data/getLandCharacteristics`) lookup by `pnu` → official `areaSqm`/지목, used to correct the polygon-derived estimate.
- Combined into `FarmlandRegistration { roadAddress, jibunAddress, coordinate: CLLocationCoordinate2D, parcel: FarmlandParcel? }` as the client-side model for the farm-location step.

**Resolved 2026-07-06:** the onboarding-embedded `FarmRequest` (part of `CompleteOnboardingRequest`, see API Contracts §4 below) already carries `roadAddress`, `jibunAddress`, `latitude`, `longitude`, `pnu`, `landCategory`, `areaSqm`, `areaIsManualEntry` — the coordinate/PNU/지목/면적 fields this action item was raised about. What's still unconfirmed is a *standalone* Farm CRUD API (a `FarmController` for managing farms outside onboarding, e.g. adding a second farm from My Page) — no such controller exists in the backend yet, so the old `FarmCreateRequest`/`FarmResponse`/`FarmUpdateRequest` DTO pages in `docs/API 명세서/` describe a not-yet-built feature and can't be verified against real code. See "Farm Location ERD Draft" below for the confirmed onboarding-time schema.

**Implemented 2026-07-03** (`FarmLocationView`/`FarmLocationViewModel` in `Features/Onboarding/{Presentation,Data,Domain}`), ported from the spike with project-convention adjustments rather than a file-for-file copy:
- Renamed `JusoResult` → `JusoAddress` (avoids colliding with `Swift.Result`), dropped the unused `entX`/`entY` fields (geocoding happens via a separate V-World call, never via JUSO's own coordinates).
- `CLLocationCoordinate2D` isn't `Codable`/`Hashable`, so a `GeoPoint { latitude, longitude }` domain type carries coordinates through persistence/state/Shoelace math; conversion to `CLLocationCoordinate2D` happens only at the MapKit rendering boundary.
- Combined view model port to `@Observable @MainActor` (not `ObservableObject`/`@Published`), owned locally by `FarmLocationView` via `@State`, not injected into the shared `OnboardingViewModel`.
- Map rendering uses native SwiftUI `Map`/`MapReader`/`Marker`/`MapPolygon` (all iOS 17.0 APIs) instead of the spike's `UIViewRepresentable`-wrapped `MKMapView` — this part of the design was not spike-validated, only the network/API sequence was.
- **Resolved 2026-07-03**: the project originally had `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`, which made every new declaration implicitly `@MainActor` unless marked `nonisolated` — the first cut of `JusoAPIService`/`VWorldAPIService`/`LandCharacterAPIService` (and their protocols, `Decodable` response structs, and `Secrets`) needed 31 explicit `nonisolated` annotations to compile as a free-threaded networking layer, and every future third-party API integration would keep paying that tax. The module only actually has a handful of types that need `@MainActor` (the `@Observable` view models/state: `AppState`, `ScreenPathRouter`, `OnboardingViewModel`, `ReachabilityMonitor`, `FarmLocationViewModel`, plus `DIContainer`) versus a growing Data/Domain layer that needs `nonisolated`. So the build setting was flipped to `SWIFT_DEFAULT_ACTOR_ISOLATION = nonisolated` (Swift's own default) and those 6 types were annotated `@MainActor` explicitly instead — net fewer annotations today, and the ratio only gets better as more networking services are added. This also surfaced a real bug the old default was papering over: `AuthTokenStore` was a plain `final class` implicitly riding on the MainActor default, but it holds mutable token state shared across actor boundaries (`DIContainer` → `APIClient`) — it's now an `actor`, consistent with its siblings `TokenRefreshCoordinator`/`SyncEngine`. Convention going forward: new Data/Domain/networking types need no annotation (nonisolated is the default); new `@Observable` view models and app-wide state holders must be explicitly marked `@MainActor`. Separately, the project's Xcode 16 synchronized file group has no per-file target-membership exceptions, so `Secrets.swift` and `Secrets.example.swift` can't both declare `enum Secrets` without a collision — `Secrets.example.swift` now wraps its body in `#if false`/`#endif` (remove those lines when copying to a real `Secrets.swift`).

### 6. Farm Location ERD Draft — ported into `ERD 초안.md` 2026-07-06, confirmed against `Farm.kt`

This proposal has been superseded by the real `Farm` entity (`backend/domain/.../farm/Farm.kt`), which was already built along these lines. `ERD 초안.md`'s `farms` table was rewritten 2026-07-06 to match it exactly:

| Field | Type | Backend column | Notes |
|---|---|---|---|
| `name` | string | `name` | unchanged |
| `road_address` | string | `road_address` | 도로명주소, replaces the old free-text `street` |
| `jibun_address` | string | `jibun_address` | 지번주소, optional |
| `latitude`, `longitude` | double | `latitude`, `longitude` | optional |
| `pnu` | string | `pnu` | 필지 고유번호, optional |
| `land_category` (지목) | string | `land_category` | optional |
| `area_sqm` | decimal | `area_sqm` | optional |
| `area_is_manual_entry` | boolean | `area_is_manual_entry` | non-null, tracks the manual-area-entry fallback |
| `boundary_coordinates` | json (list) | child table `farm_boundary_coordinate` | renamed from the proposal's `parcel_polygon`; same purpose |

**Open question resolved:** `region`/`city` do **not** exist on `Farm` at all — confirmed absent from the entity, not merely superseded. The "keep a derived coarse region/city" idea was not adopted. Likewise, the proposal's `farmType` row turned out not to exist either — there is no `farmType` field anywhere on the real `Farm` entity.

`ERD 초안.md`, like `Business Rule.md`, is still nominally a Notion export owned outside this codebase and could be overwritten on a future re-sync — but per explicit instruction during the 2026-07-06 pre-dev audit pass, it was edited directly rather than left as a pending proposal here. Re-verify against `backend/domain/.../farm/Farm.kt` if the Notion doc is ever re-exported.

## Related Documents

- [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)
- [Business Rule.md](../../../frontend/docs/Business%20Rule.md) — now the authoritative source on onboarding data creation, see "Onboarding Data Creation Scope" above
- [ERD 초안.md](../../../frontend/docs/ERD%20초안.md)
- [API 명세서 — field-level, 2026-07-03](../../../frontend/docs/API%20명세서/API%20명세서/온보딩%20완료%203909e2d94405816ea1ecd78b53053b4d.md)
- V-World/JUSO spike (reference only, not a pattern to copy file-for-file): `/Users/user/Project/v-world-test/v-world-test`
