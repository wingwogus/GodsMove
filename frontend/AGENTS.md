# ChamChamCham Frontend Agent Guide

GodsMove frontend is a native iOS app (`ChamChamCham`) written in SwiftUI.

Follow YAGNI: do not add code, dependencies, or abstractions before a concrete need exists.

Common project rules still come from the root [AGENTS.md](../AGENTS.md).

## Stack

- SwiftUI, iOS 17+ minimum deployment target.
- Swift 6 language mode (strict concurrency). Data races are compile errors, not warnings — respect actor isolation instead of adding `@unchecked Sendable`/`nonisolated(unsafe)` escape hatches.
- Observation framework (`@Observable`) for view models — not Combine or `ObservableObject`.
- SwiftData for local persistence — not CoreData or Realm.
- Native `URLSession` + async/await for networking — no Alamofire or other third-party networking library.
- Swift Package Manager only. No local SPM packages at MVP stage.
- Swift Testing (`@Test`) for new unit tests.

## File Header Convention

Every new Swift file must start with the standard Xcode header comment, matching the existing template files:

```swift
//
//  <FileName>.swift
//  ChamChamCham
//
//  Created by iyungui on <M/d/yy>.
//

```

Do not omit this when creating files, including ones generated in an agentic session.

## Core Constraint: Offline-First

Most features must default to local-first storage with background sync, not a thin API client that assumes network availability — target users (farmers) often have poor connectivity. Every write goes to SwiftData first and is never blocked on network. See the full design rationale in
[Frontend Architecture Design](../docs/superpowers/specs/2026-07-02-frontend-architecture-design.md).

## Module Structure

Feature-first folders inside a single app target (`ChamChamCham/App`, `ChamChamCham/Core`, `ChamChamCham/Features/<Feature>/{Data,Domain,Presentation}`). Full layout and rationale in the architecture design doc linked above. Do not introduce local SPM packages without revisiting that decision first.

## Product Source of Truth

Do not duplicate product behavior into this file. Read from:

- [Business Rule.md](docs/Business%20Rule.md) — when/under-what-conditions behavior (`BR-*` rule IDs).
- [ERD 초안.md](docs/ERD%20초안.md) — data shape and requirements.
- [와이어프레임 초안](docs/와이어프레임%20초안/) — screen flow references.
- [docs/superpowers/specs/](../docs/superpowers/specs/) and [docs/superpowers/plans/](../docs/superpowers/plans/) — architecture and feature design docs, including the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md).

Reference `BR-*` rule IDs in commits/PRs when a change implements or affects a specific business rule, matching backend convention.

## Run and Build

Run from the `frontend/ChamChamCham` directory.

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' test
```

The exact simulator name depends on what Xcode has installed locally (`xcrun simctl list devices available`) and drifts across Xcode versions — swap it if the build fails with "Unable to find a destination".

Use the IDE run button / Simulator for interactive development.

## Backend Integration

The backend is Spring Boot Kotlin with a `member`-centric domain (UUID ids). See [backend/AGENTS.md](../backend/AGENTS.md). An API spec with field-level DTO shapes exists at `docs/API 명세서/` (endpoint index + per-DTO field docs, re-exported 2026-07-03; the older `docs/API명세서(260702)/` CSV-only export is gone) covering auth (Kakao/Naver/Apple), crops, farms, farming records, voice sessions, community, and onboarding completion. Auth/onboarding endpoints are now confirmed at the field level — see the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md) for the confirmed shapes. Onboarding is confirmed (2026-07-03) to create a member, a farm, and `user_crops` together (`Business Rule.md` BR-USER-002 is authoritative); the currently-documented `온보딩 완료` endpoint contract doesn't yet cover farm/crop/managementType creation and needs to be raised with backend — see "Onboarding Data Creation Scope" in that doc. Other domains (crops, farms, farming records, community) still need the same per-feature confirmation pass before scaffolding networking code against them.

Production base URL is confirmed: `https://chamchamcham.jaehyuns.com` (see `Core/Networking/APIEnvironment.swift`).

For farm-location/map work (address search, 지적도 parcel lookup, coordinate resolution), the confirmed choice (2026-07-03) is JUSO (주소 검색) + V-World (연속지적도/좌표변환/토지특성정보) — not MapKit/Kakao Local. A throwaway spike at `/Users/user/Project/v-world-test/v-world-test` already validates this API chain end-to-end — reuse its request shapes as a reference rather than re-deriving them, but treat its structure only as a spike, not as a pattern to copy file-for-file into this codebase. The current `FarmCreateRequest`/`FarmResponse` DTOs don't yet have coordinate/PNU/지목/면적 fields — see the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md) for the backend-communication item this raises before farm-location submission can be wired up.

## Secrets / API Keys

Real API keys never get committed. The convention: a gitignored `Secrets.swift` next to a checked-in `Secrets.example.swift` template with placeholder values (see `Core/Config/`). To work with real keys locally, copy the example file to `Secrets.swift` in the same folder and fill in real values — `**/Secrets.swift` is covered by the root `.gitignore`.

## Development Loop

1. Plan non-trivial changes before writing code (architectural decisions, multi-file changes).
2. Implement, then build and run in the Simulator to verify the golden path.
3. Prefer sequential, plan-gated feature work over multi-agent parallel fan-out — this is a single app target with shared files under `Core/` and `App/` that parallel agents would collide on.
4. Add tests alongside behavior, prioritizing view-model tests and the voice-session state machine (BR-VOICE-*, BR-STATE-001) — these are pure and rule-dense.
