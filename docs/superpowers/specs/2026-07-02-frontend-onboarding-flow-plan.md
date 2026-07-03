# Frontend Onboarding Flow Plan

Date: 2026-07-02

## Purpose

Define the onboarding flow's screens, client-side state, and the API contracts it needs, ahead of backend API finalization. This is a flow/behavior plan only — no visual design. Source material: `frontend/docs/와이어프레임 초안/onboarding_1.png`, `onboarding_2.png`, and `BR-USER-001`/`BR-USER-002` in `Business Rule.md`.

## Why Onboarding Is a Special Case

The rest of the app defaults to offline-first local storage (see [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)). Onboarding is the one exception: identity and account creation cannot happen offline, so this flow is online-only, with explicit error/retry states rather than local draft-and-sync.

## Screen / State Sequence

Driven by an `OnboardingViewModel` (`@Observable`) holding draft form state — not SwiftData, since this is a one-shot flow outside the sync pipeline. The flow is resumable across app kill and network loss: `OnboardingDraftStore` persists a `{ step, draft }` snapshot as JSON in `UserDefaults` (the profile photo itself is written to a file under Application Support, with only its filename in the snapshot, to keep the `UserDefaults` payload small). The snapshot is saved on every step transition and on the `scenePhase → .background` lifecycle event — the reliable hook for imminent process termination, the same reasoning used for the voice-session reconciliation sweep. On relaunch, `OnboardingViewModel` restores `currentStep`/`draft` from the snapshot if present, so the flow reopens exactly where it left off rather than restarting at Landing. The snapshot is cleared once the step 6→7 atomic submission succeeds (not yet implemented).

1. **Landing** — 소셜로그인 버튼 (카카오 / 네이버 / 애플). No custom UI beyond a loading state; provider SDKs own their own login UI.
2. **Social login exchange** — generate a nonce → provider SDK login requesting OIDC → send `idToken` + `nonce` to the backend → receive access/refresh tokens → store in Keychain.
3. **Basic info** — 이름\*(필수), 닉네임, 연락처\*(필수), 생년월일.
4. **Profile** — 사진(선택), 자기소개(선택), 농업경영체 자격 (일반 / 법인 / 미가입) — required per BR-USER-002.
5. **Crop selection** — 1개 이상 필수, category-filterable list sourced from backend.
6. **Farm location** — 농지명 + 주소 검색 + map pin confirmation.
7. **Complete** → navigate into `MainTabView` / Home.

Steps 3–6 are pure local form state. Nothing is sent to the backend until the step 6→7 transition, where **one atomic submission** creates the member profile, farm, and first `user_crops` entry together — matching BR-USER-002 exactly: "온보딩 완료 시 다음 데이터를 생성한다: users, farms, user_crops... 기본 농장은 최초 1개 생성한다." Submitting each step separately would risk partial member state if the app is killed mid-flow, so the flow must not do that.

## Gating Behavior

Per BR-USER-001: "회원가입만 완료된 상태는 서비스를 사용할 수 없다. 온보딩이 완료되어야 정상 회원으로 간주한다. 온보딩 완료 전에는 Home, 영농기록, 커뮤니티 접근을 제한한다." `AppState.isOnboarded` must gate `RootView`'s switch between `AuthFlowView` and `MainTabView` — a member who completed login but not onboarding must always land back in the onboarding flow, never in Home.

## API Contracts Needed (Proposed — Pending Backend Confirmation)

These are proposals to hand off for backend discussion, not finalized contracts.

> **Update (2026-07-03):** A draft API spec now exists at `frontend/docs/API명세서(260702)/` (Notion export: an endpoint index CSV + a DTO name registry CSV). It resolves most of the gaps below at the endpoint-existence level. **Treat everything below sourced from it as a draft, not confirmed** — it is explicitly a work-in-progress export, the DTO CSV lists type names only (no field-level shape, since the Notion sub-pages with actual fields were not exported), and paths/shapes may still change. Do not scaffold networking code against exact field names from it yet.

### 1. Login response should carry onboarding status

`POST /api/v1/auth/kakao/login` should return an `isOnboarded` boolean alongside tokens, so the client can route directly to onboarding vs. Home without a second round trip. This matters more than usual here given the project's own offline/low-connectivity rationale — avoid an avoidable extra request on a slow network.

**Draft spec status:** unresolved. All login endpoints (`AUTH-001/003/004/005`) share a `LoginResponse` DTO, but its fields aren't in the exported CSV, so whether it carries `isOnboarded` is still unknown. A separate `OnboardingResponse` DTO name also appears in the DTO registry (distinct from `OnboardingCompleteResponse`) — possibly a status-check shape — but its shape and the endpoint that returns it are not in the index. Needs explicit confirmation once full DTO fields are available.

### 2. Naver and Apple login — now appear in the draft spec

Previously flagged as an open gap (only Kakao OIDC was backend-designed). The draft spec now lists:

- `AUTH-004` — `POST /api/v1/auth/apple/login` (`AppleLoginRequest` → `LoginResponse`)
- `AUTH-005` — `POST /api/v1/auth/naver/login` (`NaverLoginRequest` → `LoginResponse`)

This suggests **Option B** (equivalent endpoints for all three providers) is the current direction, superseding the earlier Kakao-only-MVP option. **Not confirmed** — this is a draft index entry with no field-level detail on `AppleLoginRequest`/`NaverLoginRequest` yet, and Apple/Naver OIDC verification isn't covered by the existing backend design doc (`2026-07-01-backend-kakao-oidc-member-design.md`, Kakao-only). Treat as directionally resolved, not implementation-ready.

### 3. Crop listing endpoint — now appears in the draft spec

Previously flagged as possibly not existing. The draft spec now lists `CROP-001` — `GET /api/v1/crops`, query params `keyword, category, lifecycleType, page, size`, returning `PageResponse<CropResponse>`. This matches what step 5 needs (category-filterable) and adds pagination + a `lifecycleType` filter not previously anticipated. **Not confirmed** — `CropResponse` field shape not in the exported CSV.

### 4. Onboarding-completion endpoint — draft spec uses a different shape than proposed here

This plan previously proposed `POST /api/v1/members/onboarding`. The draft spec instead lists:

- `AUTH-006` — `POST /api/v1/onboarding/complete`, request `OnboardingCompleteRequest`, response `OnboardingCompleteResponse`

**The path and DTO names differ from this plan's proposal and should be treated as the current direction, not this doc's proposed shape.** The actual field list for `OnboardingCompleteRequest`/`OnboardingCompleteResponse` is not available yet (Notion sub-pages weren't exported into the CSV) — so it's still unconfirmed whether it atomically creates `member` profile + `farm` + `member_crop` rows together in one call, which is the hard requirement from BR-USER-002. **This must be verified once the real request/response shape is available — do not assume atomicity from the endpoint name alone.**

### 5. Open decision: map / address-search SDK

Step 6 needs an interactive map plus Korean address search. Recommendation: Apple MapKit for the map view itself (native, no extra dependency), combined with the Kakao Local REST API for address search / reverse-geocoding (better accuracy for Korean addresses than `MKLocalSearch`, and a Kakao REST key is already required for login regardless). This is a recommendation, not a decision — confirm before building.

## Related Documents

- [Frontend Architecture Design](2026-07-02-frontend-architecture-design.md)
- [Business Rule.md](../../../frontend/docs/Business%20Rule.md)
- [ERD 초안.md](../../../frontend/docs/ERD%20초안.md)
- [API 명세서(260702) — draft, unconfirmed](../../../frontend/docs/API명세서(260702)/API%20명세서%20d5b9e2d944058337a774015bc187a6aa.md)
