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

## Design-System Source of Truth (Strict)

Before implementing or modifying UI, inspect all relevant files under
`Core/DesignSystem/`; never bypass that folder or recreate something it already
provides.

- Reuse `Components/` and `Modifiers/` before writing feature-local UI.
- Use `Foundation/Color+App.swift`, `Font+App.swift`, and `Spacing.swift` for
  color, text typography, and spacing. Do not introduce an equivalent raw value
  or duplicate token when the design system already defines it.
- Outside the foundation, do not add raw app colors, raw text-font styling, or
  component-specific overrides merely to chase a Figma value. Genuinely missing
  one-off layout measurements are the exception.
- Icon glyphs: check `Assets.xcassets/icon/` first and use the matching custom
  SVG (via `AppIconSource.asset("name")` / `AppIconView`) before reaching for an
  SF Symbol placeholder. Figma capture docs under `docs/figma/<feature>/` record
  the exact `icon/<name>` used per screen — follow that mapping instead of
  guessing a semantically-close glyph. Only fall back to an SF Symbol
  (`AppIconSource.system("name")` / a plain string literal) when no custom
  asset exists yet for that glyph, and note the gap so it can be added later.
- If Figma conflicts with the design system, keep the existing design-system
  value and report the mismatch. Do not modify foundations, component token
  mappings, or public component APIs without explicit user authorization.
- Before completion, inspect the Swift diff for duplicated components, raw
  foundation values, and unapproved design-system changes.

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

The backend is Spring Boot Kotlin with a `member`-centric domain (UUID ids). See [backend/AGENTS.md](../backend/AGENTS.md). The frontend API contract source is the deployed Swagger snapshot in `docs/swagger/`, generated from `https://chamchamcham.jaehyuns.com/v3/api-docs`; do not hand-edit it. Before work that touches API endpoints, DTOs, or request/response shapes, run `python3 scripts/sync_swagger_spec.py --write` from `frontend/`. `docs/_archive/API 명세서 (Notion, 사용금지)/` and `scripts/sync_api_spec.py` are archived Notion artifacts: **never use or regenerate them for frontend API decisions.** When Swagger and local backend source differ, treat Swagger as the deployed frontend contract and record the discrepancy with the backend commit before changing client behavior. Onboarding completion (`AuthController` → `CompleteOnboardingRequest`) creates a `member`, a `farm`, and `member_crop` rows together in one call (`Business Rule.md` BR-USER-002 is authoritative).

Production base URL is confirmed: `https://chamchamcham.jaehyuns.com` (see `Core/Networking/APIEnvironment.swift`).

For farm-location/map work (address search, 지적도 parcel lookup, coordinate resolution), the confirmed choice (2026-07-03) is JUSO (주소 검색) + V-World (연속지적도/좌표변환/토지특성정보) — not MapKit/Kakao Local. A throwaway spike at `/Users/user/Project/v-world-test/v-world-test` already validates this API chain end-to-end — reuse its request shapes as a reference rather than re-deriving them, but treat its structure only as a spike, not as a pattern to copy file-for-file into this codebase. The current `FarmCreateRequest`/`FarmResponse` DTOs don't yet have coordinate/PNU/지목/면적 fields — see the [Onboarding Flow Plan](../docs/superpowers/specs/2026-07-02-frontend-onboarding-flow-plan.md) for the backend-communication item this raises before farm-location submission can be wired up.

## Secrets / API Keys

Real API keys never get committed. The convention: a gitignored `Secrets.swift` next to a checked-in `Secrets.example.swift` template with placeholder values (see `Core/Config/`). To work with real keys locally, copy the example file to `Secrets.swift` in the same folder and fill in real values — `**/Secrets.swift` is covered by the root `.gitignore`.

## Development Loop

1. Plan non-trivial changes before writing code (architectural decisions, multi-file changes).
2. Implement, then build and run in the Simulator to verify the golden path.
3. Prefer sequential, plan-gated feature work over multi-agent parallel fan-out — this is a single app target with shared files under `Core/` and `App/` that parallel agents would collide on.
4. Add tests alongside behavior, prioritizing view-model tests and the voice-session state machine (BR-VOICE-*, BR-STATE-001) — these are pure and rule-dense.
