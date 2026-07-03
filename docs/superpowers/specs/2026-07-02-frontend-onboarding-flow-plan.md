# Frontend Onboarding Flow Plan

Date: 2026-07-02

## Purpose

Define the onboarding flow's screens, client-side state, and the API contracts it needs, ahead of backend API finalization. This is a flow/behavior plan only — no visual design. Source material: `frontend/docs/와이어프레임 초안/onboarding_1.png`, `onboarding_2.png`, and `BR-USER-001`/`BR-USER-002` in `Business Rule.md`.

## Why Onboarding Is a Special Case

The rest of the app defaults to offline-first local storage (see [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)). Onboarding is the one exception: identity and account creation cannot happen offline, so this flow is online-only, with explicit error/retry states rather than local draft-and-sync.

## Screen / State Sequence

Driven by an `OnboardingViewModel` (`@Observable`) holding draft form state — not SwiftData, since this is a one-shot flow outside the sync pipeline. The flow is resumable across app kill and network loss: `OnboardingDraftStore` persists a `{ step, draft }` snapshot as JSON in `UserDefaults` (the profile photo itself is written to a file under Application Support, with only its filename in the snapshot, to keep the `UserDefaults` payload small). The snapshot is saved on every step transition and on the `scenePhase → .background` lifecycle event — the reliable hook for imminent process termination, the same reasoning used for the voice-session reconciliation sweep. On relaunch, `OnboardingViewModel` restores `currentStep`/`draft` from the snapshot if present, so the flow reopens exactly where it left off rather than restarting at Landing. The snapshot is cleared once the step 6→7 atomic submission succeeds (not yet implemented).

1. **Landing** — 소셜로그인 버튼 (카카오 / 네이버 / 애플). No custom UI beyond a loading state; provider SDKs own their own login UI.
2. **Social login exchange** — generate a nonce → provider SDK login requesting OIDC → send `idToken` + `nonce` to the backend → receive access/refresh tokens → store in Keychain. `LoginResponse` (confirmed, see below) already carries `onboarding: { status, missingFields }`, so the client can route straight to onboarding vs. Home off the login response — no separate status round trip needed.
3. **Basic info + Profile (merged into one screen, `BasicProfileView`)** — 이름\*, 닉네임, 연락처\*(phone), 생년월일\*, 영농 경력\*(numeric years input, "N년차" — maps to `experienceYears: Int?` on `OnboardingDraft`, not a categorical picker), 농업경영체 자격(managementType, 일반/법인/미가입), 사진(선택). Fields marked \* map 1:1 to `OnboardingCompleteRequest`'s required fields **except** `managementType` and profile photo, both of which stay in the UI as product decisions even though the current API contract doesn't cover them yet — see "Known Gaps" below.
4. **Crop selection** (`CropSelectionView`, built) and **Farm location** (screen not built yet) — both **in scope for onboarding**, per the resolved data-creation scope below. Farm location is scoped next now that the map/address-search SDK decision (below) is resolved.
5. **Complete** → navigate into `MainTabView` / Home.

Nothing is sent to the backend until submission — the real network call isn't wired up yet (`OnboardingRepository` is a protocol stub only). The atomic submission must end up creating the member profile, a farm, and `user_crops` together — see "Onboarding Data Creation Scope" below, which is broader than the currently-documented `POST /api/v1/auth/onboarding/complete` request shape.

## Onboarding Data Creation Scope (resolved 2026-07-03 — product decision)

**Decision: onboarding creates the member profile, a default farm, and `user_crops` together, in one flow.** `Business Rule.md` BR-USER-002 is authoritative; the currently-documented `온보딩 완료` endpoint contract is stale/incomplete on this point, not a competing product decision.

Three source documents previously disagreed on what onboarding creates:

- **`Business Rule.md` BR-USER-002`**: required fields include "주요 작물(1개 이상)" and 농업경영체 유형; completion creates `users` + `farms` + `user_crops` together, "기본 농장은 최초 1개 생성한다."
- **`FarmCreateRequest` DTO doc** (`docs/API 명세서/`): "온보딩 완료 시 기본 농장 1개가 반드시 생성된다."
- **`온보딩 완료` endpoint doc** (`docs/API 명세서/`, most detailed/recent): "MVP에서는 약관 동의, **기본 농장 생성**, 중간 임시저장을 처리하지 않는다." `OnboardingCompleteRequest` has no crop/farm/managementType fields at all — only `name, phone, birthDate, nickname, region, experienceLevel`. **This is now known to be a stale/incomplete API contract**, not the correct behavior.

`Business Rule.md` and the API spec are both Notion exports owned outside this codebase and get wholesale-replaced on re-export (as already happened once to the old `API명세서(260702)` folder) — editing them here would be lost on the next sync, so this doc remains the reconciled, current account.

**Action item — needs backend communication:** the endpoint contract must be updated to actually create the farm + `user_crops` rows described by BR-USER-002. Whether that becomes one call with an expanded `OnboardingCompleteRequest` payload, or a defined sequence of calls (e.g. `onboarding/complete` → `POST /api/v1/farms` → crop upsert) that still completes atomically from the user's perspective, is undecided and must be confirmed with backend — do not assume either shape client-side. Also confirm what happens on partial failure mid-sequence (e.g. member created but farm creation fails); the client's resumable-draft mechanism only covers client-side state, not partial-server-state recovery.

## Known Gaps in the Merged Basic Info + Profile Screen

- **`region`** — resolved (2026-07-03), stronger decision than before: `users.region` ("사용자 거주 또는 활동 지역") is being **removed from the ERD entirely**, not just excluded from the onboarding screen. The user's own residence isn't collected at all; what onboarding collects instead is **농경지 위치** (farm location) — road address, parcel/coordinate data — captured on the `farms` entity via the JUSO+V-World flow (see "Farm Location ERD Draft" below). `OnboardingCompleteRequest.region` in the current API spec still marks this required — since the field is going away, not just going unused client-side, this needs backend to actually drop `region` from `OnboardingCompleteRequest`/`missingFields`/`MemberProfileResponse`, not merely tolerate an empty value. Flagged as a backend-communication item.
- **`managementType`** (자격) — resolved (2026-07-03): stays in `BasicProfileView` (segmented picker + guide banner, already built). `OnboardingCompleteRequest` as currently documented doesn't include it, and `member.managementType` defaults to `UNREGISTERED`/is otherwise changed via `PATCH /api/v1/users/me`. Decision is to keep collecting it in the onboarding UI as-is; backend will update the onboarding-complete contract to accept it, rather than the client working around it with a follow-up call. Needs backend communication to confirm the field is added to `OnboardingCompleteRequest`.
- **Profile photo** — confirmed absent from the API surface. Searched the entire `docs/API 명세서/` export for photo/image/avatar fields; the only image-related endpoints are for 영농일지(farming record) media attachments, not user profile. `UserProfileResponse`, `UserProfileUpdateRequest`, and `MemberProfileResponse` have no photo field. The `PhotosPicker` UI in `BasicProfileView` currently has nothing to upload to server-side.
- **`experienceLevel`** shape mismatch — product decision (2026-07-03): this is a plain numeric years-of-experience field ("N년차"), not a categorical level. `OnboardingDraft.experienceYears: Int?` and the `BasicProfileView` field are a numeric-only text input, not a picker. This conflicts with the API spec's own `OnboardingCompleteRequest`/`LoginResponse` example (`"experienceLevel": "BEGINNER"`), which reads as a categorical enum, not a number. Not yet resolved with backend — flagged here rather than silently reconciled; real submission will need either the backend to accept a numeric value under this field, or a mapping step (e.g. years → bucketed level) once backend confirms the actual contract.

## Gating Behavior

Per BR-USER-001: "회원가입만 완료된 상태는 서비스를 사용할 수 없다. 온보딩이 완료되어야 정상 회원으로 간주한다. 온보딩 완료 전에는 Home, 영농기록, 커뮤니티 접근을 제한한다." `AppState.isOnboarded` must gate `RootView`'s switch between `AuthFlowView` and `MainTabView` — a member who completed login but not onboarding must always land back in the onboarding flow, never in Home.

## API Contracts (Confirmed 2026-07-03)

> **Update (2026-07-03):** The API spec was re-exported with full field-level DTO shapes at `frontend/docs/API 명세서/` (previously `API명세서(260702)/`, CSV-only, no field shapes — that folder is gone). The items below are now confirmed at the field level unless noted otherwise.

### 1. Login response carries onboarding status — confirmed

All three login endpoints (Kakao `AUTH-001`, Apple `AUTH-002`, Naver `AUTH-003`) return the same `LoginResponse`:

```json
{
  "accessToken": "...", "refreshToken": "...",
  "member": { "id", "email", "name", "phone", "birthDate", "nickname", "region", "experienceLevel", "managementType" },
  "onboarding": { "status": "REQUIRED" | "COMPLETE", "missingFields": [...] }
}
```

`missingFields` values: `NAME`, `PHONE`, `BIRTH_DATE`, `NICKNAME`, `REGION`, `EXPERIENCE_LEVEL`. Client can route to onboarding vs. Home directly off this, no second request needed.

### 2. Naver and Apple login — confirmed

- `KakaoLoginRequest { idToken, nonce, kakaoAccessToken? }`
- `AppleLoginRequest { identityToken, nonce, authorizationCode?, userIdentifier? }`
- `NaverLoginRequest { accessToken }`

All three create/find a member via `ExternalIdentity(provider, providerSubject)`; provider profile values only prefill empty member fields; existing identities don't re-require email.

### 3. Crop listing endpoint — confirmed shape

`CROP-001` — `GET /api/v1/crops`, query `keyword, category, lifecycleType, page, size` → `PageResponse<CropResponse>`. `CropResponse { id, name, category?, lifecycleType: ANNUAL|PERENNIAL, defaultUnit? }`. In scope for onboarding per "Onboarding Data Creation Scope" above; the crop-registration call sequence (upsert into `user_crops`) still needs to be confirmed with backend.

### 4. Onboarding-completion endpoint — confirmed shape, contract needs to expand

- `AUTH-004` — `POST /api/v1/auth/onboarding/complete` (not `/api/v1/onboarding/complete` or `/api/v1/members/onboarding` as earlier drafts proposed), Bearer auth, uses the token's member id.
- Request `OnboardingCompleteRequest { name, phone, birthDate, nickname, region, experienceLevel }` — all required, as currently documented.
- Response `OnboardingCompleteResponse { member: MemberProfileResponse, onboarding: OnboardingResponse }`; token is not reissued.
- **As currently documented, does NOT create a farm or `user_crops` row and doesn't accept `managementType`.** Per "Onboarding Data Creation Scope" above, this is now known to be an incomplete contract, not the confirmed final behavior — the endpoint (or an accompanying call sequence) needs to expand to cover farm + crop creation and `managementType`. This is an open item to raise with backend, not something to resolve unilaterally client-side.

### 5. Map / address-search SDK — resolved 2026-07-03

**Decision: JUSO (주소 검색) + V-World (연속지적도/좌표변환/토지특성정보).** The Apple MapKit + Kakao Local REST API candidate previously suggested in this doc is dropped. Reference implementation: the throwaway spike at `/Users/user/Project/v-world-test/v-world-test` validates the full JUSO + V-World call chain end-to-end — reuse its request/response shapes as a reference, but treat it only as a spike (its architecture, not its file layout, is what to reuse), per `frontend/AGENTS.md`.

Concretely, from the spike:
- `JusoAPIService` — 주소 검색 (JUSO API) → `JusoResult { roadAddrPart1, jibunAddr, bdNm, entX, entY }` (road address, jibun address, building name, and entrance coordinates as strings).
- `VWorldAPIService` — 연속지적도(GetFeature) lookup by coordinate → parcel polygon + `FarmlandParcel { pnu, jibunAddr, jimok, areaSqm (computed from polygon via Shoelace formula), coordinates: [CLLocationCoordinate2D] }`.
- `LandCharacterAPIService` — V-World-hosted 토지특성정보 (`api.vworld.kr/ned/data/getLandCharacteristics`) lookup by `pnu` → official `areaSqm`/지목, used to correct the polygon-derived estimate.
- Combined into `FarmlandRegistration { roadAddress, jibunAddress, coordinate: CLLocationCoordinate2D, parcel: FarmlandParcel? }` as the client-side model for the farm-location step.

**Action item — needs backend communication:** the currently-documented `FarmCreateRequest`/`FarmResponse`/`FarmUpdateRequest` DTOs only have `name, region, city, street?, farmType?` — **no coordinate, PNU, 지목, or 면적 fields at all.** See "Farm Location ERD Draft" below for the proposed schema to raise with backend.

### 6. Farm Location ERD Draft (proposed 2026-07-03 — needs backend/PM confirmation before porting into `ERD 초안.md`)

Following from the `region` decision above: `users.region` (사용자 거주지) is dropped, and `farms` gains real parcel/location data sourced from the JUSO + V-World flow, instead of the free-text `region/city/street` it has today. Proposed `farms` fields (additive/replacing, mapped from the spike's types):

| Proposed field | Type | Source (spike) | Notes |
|---|---|---|---|
| `name` | string | user input | unchanged |
| `road_address` | string | `JusoResult.roadAddrPart1` | 도로명주소, replaces free-text `street` |
| `jibun_address` | string | `JusoResult.jibunAddr` / `FarmlandParcel.jibunAddr` | 지번주소 |
| `latitude`, `longitude` | decimal | `JusoResult.entX/entY` (converted) or the resolved `CLLocationCoordinate2D` | entrance/representative point, not necessarily parcel centroid |
| `pnu` | string | `FarmlandParcel.pnu` | 필지 고유번호 — stable key for re-querying V-World later |
| `land_category` (지목) | string | `FarmlandParcel.jimok` / `LandCharacterInfo.jimokName` | prefer `LandCharacterInfo` (official) over the polygon-derived guess when both are available |
| `area_sqm` | decimal | `LandCharacterInfo.officialAreaSqm`, fallback `FarmlandParcel.areaSqm` (Shoelace-computed) | spike explicitly treats the official figure as authoritative and the polygon calc as a fallback when 토지특성정보 lookup fails |
| `parcel_polygon` | json (array of lat/lng points) | `FarmlandParcel.coordinates` | optional — only needed if the parcel shape must be redrawn later (e.g. My Page); pure client-side map display doesn't require persisting it |
| `farmType` | string | unchanged | untouched by this proposal |

**Open question — not yet decided, raise with backend/PM:** whether `region`/`city` stay on `farms` as coarse fields (useful for region-based filtering, e.g. `policy_programs.region` matching) alongside the new precise fields above, or whether they're fully superseded by `road_address`/`jibun_address` and should be dropped too. Leaning toward keeping a derived coarse `region`/`city` (parseable from the road address) for cheap filtering, but this hasn't been confirmed.

**Not yet written into `ERD 초안.md`** — that file (like `Business Rule.md`) reads as a Notion export owned outside this codebase (same folder, same image-asset-export pattern) that would be overwritten on next re-sync, so this table is the working draft to review before someone ports it into Notion by hand. Say if you'd rather I edit `ERD 초안.md` directly instead.

## Related Documents

- [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)
- [Business Rule.md](../../../frontend/docs/Business%20Rule.md) — now the authoritative source on onboarding data creation, see "Onboarding Data Creation Scope" above
- [ERD 초안.md](../../../frontend/docs/ERD%20초안.md)
- [API 명세서 — field-level, 2026-07-03](../../../frontend/docs/API%20명세서/API%20명세서/온보딩%20완료%203909e2d94405816ea1ecd78b53053b4d.md)
- V-World/JUSO spike (reference only, not a pattern to copy file-for-file): `/Users/user/Project/v-world-test/v-world-test`
