# Frontend (iOS) Architecture Design

Date: 2026-07-02

## Purpose

Establish the SwiftUI application architecture for `frontend/ChamChamCham` before feature implementation begins. The project currently contains only the default Xcode template (`ChamChamChamApp.swift`, `ContentView.swift`).

## Confirmed Constraints

- iOS 17+ minimum deployment target.
- Swift 6 language mode (strict concurrency enforced at compile time). `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor` and `SWIFT_APPROACHABLE_CONCURRENCY = YES` are set project-wide, so types are implicitly `@MainActor`-isolated unless declared otherwise (e.g. `actor`) — this is why `APIClient`, `TokenRefreshCoordinator`, and `SyncEngine` below are explicitly `actor`s rather than classes.
- SwiftUI with the Observation framework (`@Observable`), not Combine/`ObservableObject`.
- SwiftData for local persistence, not CoreData or Realm.
- Native `URLSession` + async/await for networking, no Alamofire or other third-party networking library.
- Swift Package Manager only; no local SPM packages at MVP (see Module Structure).

## Product Constraint Driving This Design

`Business Rule.md`: "농업의 인터넷 환경을 고려하여, 클라이언트에서는 대부분에 오프라인 저장(임시저장)이 기본 구현." Target users (farmers) frequently have poor connectivity, so most client features must default to local-first storage with background sync, not a thin API client that assumes network availability.

## Module Structure

Feature-first folders inside a single app target. No local SPM packages at MVP: packaging forces premature public/internal API decisions before the domain has stabilized, which is expensive churn with no MVP benefit. Revisit only if build times become a problem or a second target (widget/watch) appears.

```
ChamChamCham/
  App/
    ChamChamChamApp.swift
    RootView.swift
    AppState.swift
    DIContainer.swift
  Core/
    Networking/
      APIClient.swift            (actor)
      Endpoint.swift
      AuthTokenStore.swift       (Keychain-backed)
      TokenRefreshCoordinator.swift
      APIError.swift
    Persistence/
      ModelContainer+App.swift
      SchemaV1.swift
    Sync/
      SyncEngine.swift
      Syncable.swift
      ReachabilityMonitor.swift
    DesignSystem/                (built out later, not now)
    Routing/
      Route.swift
      ScreenPathRouter.swift
  Features/
    Onboarding/{Data,Domain,Presentation}
    Home/{Data,Domain,Presentation}
    FarmingRecord/{Data,Domain,Presentation}
      VoiceSession/
    Community/{Data,Domain,Presentation}
    Search/{Data,Domain,Presentation}
    Policy/{Data,Domain,Presentation}
    MyPage/{Data,Domain,Presentation}
```

Each feature keeps three kinds of models distinct:

- SwiftData `@Model` types (persistence).
- Network `Codable` DTOs (wire format).
- Plain-struct domain models (what ViewModels actually consume).

This mirrors the layering discipline the backend already uses (`api` / `application` / `domain`, see `backend/AGENTS.md`), reapplied per-feature so it stays navigable across the nine feature areas (온보딩, 홈, 영농기록, 리포트, 커뮤니티, 마이페이지, 검색, 정책추천, 알림). Keeping the domain layer free of SwiftUI/SwiftData imports also means a future package extraction is a pure move, not a rewrite.

## Offline-First Data Flow

This is the load-bearing design decision for the whole app.

**Write path (e.g. a farming record):**

1. `RecordEditorViewModel` validates required fields client-side (`worked_at`, `farm`, `crop`, `work_type` — BR-RECORD-003) against a locally cached `WorkTypeSchema`. The schema must be cached because dynamic per-work-type fields (BR-RECORD-004) need to render offline.
2. Save persists a `FarmingRecordDraft` (`@Model`) immediately: `syncStatus: pendingCreate`, `localID: UUID`, `serverID: UUID?`. The UI never waits on network.
3. `SyncEngine` (an actor observing `NWPathMonitor` and app-foreground events) walks pending drafts oldest-first and calls the repository's remote create.
   - Success → `serverID` set, `syncStatus = synced`.
   - 4xx validation failure → `syncStatus = syncFailed` with a user-visible reason (BR-EXCEPTION-004/005).
   - Network failure → stays `pendingCreate`, retried on next connectivity/foreground event.
4. Photo attachments (`RecordMedia`) sync independently with their own `syncStatus`. A record's save is never blocked by photo upload (BR-EXCEPTION-003, BR-RECORD-009).
5. Lists always read from SwiftData (single source of truth); server pulls on launch/refresh upsert by `serverID`.
6. Conflict resolution: last-write-wins by `updatedAt` for MVP — one user, effectively one active device. This is an accepted simplification, not a gap, and should be documented as such if revisited later.

**AI structuring and offline (BR-RECORD-005, BR-AI-*, BR-EXCEPTION-001):**

AI-assisted field extraction is a pure network call producing *candidate* values only — never persisted until the user confirms (BR-AI-005). If offline, the "AI 보조" action is disabled with an inline message; the user fills fields manually and the record still saves. AI failure or absence never blocks saving a record, matching BR-COACH-006 and BR-EXCEPTION-001 exactly.

**Voice session state machine (BR-STATE-001, BR-VOICE-008):**

`VoiceRecordSession` is modeled as a local `@Model` mirroring backend states: `CREATED → RECORDING → PROCESSING → WAITING_CONFIRMATION → COMPLETED`, with exceptions to `CANCELLED` (from `RECORDING`) or `FAILED` (from `PROCESSING`). `RECORDING` needs no network; `PROCESSING` onward does.

Two reconciliation mechanisms, since iOS gives no reliable app-termination hook:

- **Launch sweep** (`VoiceSessionRepository.reconcileOnLaunch()`): any session left in `RECORDING`/`PROCESSING`/`WAITING_CONFIRMATION` from a prior run (no terminal state reached) is force-transitioned to `FAILED` on next launch. This is the primary safety net, not `applicationWillTerminate`.
- **In-flight failure**: any `URLSession` failure/timeout during `PROCESSING` transitions the session to `FAILED` in the ViewModel's catch block and discards any not-yet-created draft record — per BR-VOICE-008, nothing partial is ever surfaced as a saved record.

## Networking Layer

Protocol-based endpoint modeling:

```swift
protocol Endpoint {
    var path: String { get }
    var method: HTTPMethod { get }
    var body: Encodable? { get }
    var requiresAuth: Bool { get }
}
```

`APIClient` is an actor exposing `func send<T: Decodable>(_ endpoint: Endpoint) async throws -> T`. It attaches the access token from a Keychain-backed `AuthTokenStore`. On `401`, it delegates to `TokenRefreshCoordinator` — an actor that single-flights concurrent refresh calls (so N simultaneous 401s don't fire N refresh calls) and retries the original request once after a successful refresh. Refresh failure broadcasts a session-expired event that `RootView` observes to force sign-out.

Errors map to a small `APIError` enum (`network`, `unauthorized`, `validation`, `server`, `decoding`), which repositories translate into feature-specific domain errors.

**ViewModels talk only to repositories** — never directly to `APIClient` or `ModelContext`:

```swift
protocol FarmingRecordRepository {
    func drafts() -> [FarmingRecord]
    func save(_ input: FarmingRecordInput) throws
    func syncPending() async
}
```

This is what keeps "local vs remote" invisible to presentation code.

## Navigation

`NavigationStack` + per-tab path routing, not a formal Coordinator hierarchy (a UIKit-era pattern `NavigationPath` already replaces). `RootView` swaps between `AuthFlowView` (pre-auth: login + onboarding, own `NavigationStack`) and `MainTabView` (Home / 영농기록 / 커뮤니티 / 마이페이지) based on `AppState.isAuthenticated && isOnboarded` (the BR-USER-001 gate). Each tab owns an `@Observable ScreenPathRouter { var path = NavigationPath() }` injected via `@Environment`. Multi-step linear flows (onboarding, voice recording) are driven by an `enum Step: CaseIterable` inside a dedicated `@Observable` flow ViewModel, presented via `.fullScreenCover` — no separate coordinator objects needed.

## Testing Strategy

Swift Testing (`import Testing`, `@Test`) as the default for new unit tests — the modern iOS 17/Xcode 16-native choice, coexisting with XCTest-based XCUITest for later UI automation.

Priority order:

1. ViewModel tests against mocked repository protocols.
2. Voice session state-machine tests — pure, rule-dense, cheapest high-value tests here.
3. Repository/sync tests using an in-memory `ModelContainer(configurations: .init(isStoredInMemoryOnly: true))` plus a stubbed `APIClientProtocol`.

UI/snapshot testing is out of scope for MVP.

## MVP Scope Guardrail

**Skip for now** (per BR-GENERAL-004: 기능단순성/데이터일관성/사용자입력우선/AI보조역할): local SPM packages, formal Coordinator classes, multi-device conflict resolution/CRDTs, background-mode audio recording, sophisticated backoff/priority sync queues (FIFO + fixed retry interval suffices), local full-text search (delegate real search to backend when online).

**Must build now** (expensive to retrofit later, especially offline-first): the SwiftData draft-first repository pattern for every syncable entity; the `syncStatus` enum shape on records and media from day one; the voice-session launch-reconciliation sweep; the repository-protocol abstraction ViewModels depend on; and `work_type_field`-driven dynamic form rendering with local schema caching.

## Related Documents

- [Frontend Onboarding Flow Plan](2026-07-02-frontend-onboarding-flow-plan.md)
- [Business Rule.md](../../../frontend/docs/Business%20Rule.md)
- [ERD 초안.md](../../../frontend/docs/ERD%20초안.md)
- [Backend Kakao OIDC Member Design](2026-07-01-backend-kakao-oidc-member-design.md)
