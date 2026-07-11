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

## Small Device Layout Rule

Figma screens are designed around iPhone 13 size. Implementation must still
preserve basic usability on iPhone SE 2/3 size for iOS 17+.

SE support does not require pixel-perfect parity with the Figma frame, but must
prevent:

- clipped primary text, tab labels, or critical values
- overlapping or incoherently stacked content
- hidden primary actions
- the keyboard covering the active input or submit button
- fixed-height layouts that cannot scroll when content is taller than the screen

Use scrolling, safe-area-aware bottom actions, shorter labels, adaptive spacing,
and text fallbacks such as wrapping, `lineLimit`, or `minimumScaleFactor` where
needed. Treat iPhone SE 2/3 as the minimum layout QA size, especially for bottom
navigation, onboarding forms, compose screens, and comment input flows.

## Figma Screen Implementation Rule

When implementing a screen from Figma Dev Mode, treat the Figma frame as the
visual target while keeping implementation decisions aligned with API readiness,
offline-first behavior, and the existing SwiftUI architecture.

Apply screen designs in this order:

1. Reuse existing design-system components where possible.
2. Extend an existing component only when the new state or variant repeats.
3. Keep one-off layout details inside the feature screen.
4. Create a new design-system component only for repeated UI with stable
   behavior.

Screen implementation should follow API readiness:

- API ready: connect the screen to its real view model and repository flow.
- API partially ready: implement the skeleton with loading, empty, error, and
  disabled states.
- API unavailable: render a clear placeholder or disabled affordance without
  inventing networking code.

Every Figma-backed screen should include the relevant runtime states, not only
the happy-path frame: loading, empty, error, disabled, submitting, and retry
where applicable.

## Product Source of Truth

Do not duplicate product behavior into this file. Read from:

- [Business Rule.md](docs/Business%20Rule.md) — when/under-what-conditions behavior (`BR-*` rule IDs).
- [ERD 초안.md](docs/ERD%20초안.md) — data shape and requirements.
- [와이어프레임](docs/와이어프레임/) — screen flow references.
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

The backend is Spring Boot Kotlin with a `member`-centric domain (UUID ids). See [backend/AGENTS.md](../backend/AGENTS.md). Only three controllers are implemented so far: `AuthController` (Kakao/Naver/Apple login + onboarding completion), `CropController`, and `TestController` — Farm, Community, Voice, Report, and Policy domains have no backend endpoints yet, so don't scaffold networking code against them. An API spec with field-level DTO shapes lives at `docs/API 명세서/` (endpoint index in `README.md` + one file per endpoint and per DTO). It is **auto-generated** from the team's Notion API 명세서 by `scripts/sync_api_spec.py` — regenerate with `python3 scripts/sync_api_spec.py --out "docs/API 명세서"` (see `scripts/README.md`); don't hand-edit the generated files. Treat the actual `*Controller`/`*Request`/`*Response` Kotlin source under `backend/api` and `backend/domain` as authoritative when it disagrees with the doc — the doc lags behind confirmed backend changes (e.g. its 온보딩 완료 page still says onboarding doesn't create a farm; it does). Onboarding completion (`AuthController` → `CompleteOnboardingRequest`) creates a `member`, a `farm`, and `member_crop` rows together in one call (`Business Rule.md` BR-USER-002 is authoritative) — see the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md) for the confirmed field shapes.

Before starting work that touches API endpoints, DTOs, or request/response shapes, re-run `python3 scripts/sync_api_spec.py --out "docs/API 명세서"` first to pull the latest Notion spec — it's a manual pull, not a live sync, so stale local docs are the default unless you regenerate.

Production base URL is confirmed: `https://chamchamcham.jaehyuns.com` (see `Core/Networking/APIEnvironment.swift`).

For farm-location/map work (address search, 지적도 parcel lookup, coordinate resolution), the confirmed choice (2026-07-03) is JUSO (주소 검색) + V-World (연속지적도/좌표변환/토지특성정보) — not MapKit/Kakao Local. A throwaway spike at `/Users/user/Project/v-world-test/v-world-test` already validates this API chain end-to-end — reuse its request shapes as a reference rather than re-deriving them, but treat its structure only as a spike, not as a pattern to copy file-for-file into this codebase. The current `FarmCreateRequest`/`FarmResponse` DTOs don't yet have coordinate/PNU/지목/면적 fields — see the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md) for the backend-communication item this raises before farm-location submission can be wired up.

## Secrets / API Keys

Real API keys never get committed. The convention: a gitignored `Secrets.swift` next to a checked-in `Secrets.example.swift` template with placeholder values (see `Core/Config/`). To work with real keys locally, copy the example file to `Secrets.swift` in the same folder and fill in real values — `**/Secrets.swift` is covered by the root `.gitignore`.

## Development Loop

1. Plan non-trivial changes before writing code (architectural decisions, multi-file changes).
2. Implement, then build and run in the Simulator to verify the golden path.
3. Prefer sequential, plan-gated feature work over multi-agent parallel fan-out — this is a single app target with shared files under `Core/` and `App/` that parallel agents would collide on.
4. Add tests alongside behavior, prioritizing view-model tests and the voice-session state machine (BR-VOICE-*, BR-STATE-001) — these are pure and rule-dense.
