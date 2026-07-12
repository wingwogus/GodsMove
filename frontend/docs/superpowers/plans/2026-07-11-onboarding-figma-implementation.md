# Onboarding Figma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the latest Figma onboarding flow to the SwiftUI frontend while preserving offline-first draft recovery and deferring server completion until the user taps `시작하기`.

**Architecture:** Keep the single app target and existing feature-first onboarding module. Move draft state from single farm fields to a `farms[]` model with an active farm index, keep `POST /auth/onboarding/complete` mapped to the first representative farm, and isolate extra-farm persistence behind a future standalone farm-add endpoint.

**Tech Stack:** SwiftUI, Swift 6 strict concurrency, Observation `@Observable`, Swift Testing, URLSession/async-await, SwiftData cache.

## Global Constraints

- iOS 17+ minimum deployment target.
- Swift 6 language mode; respect actor isolation.
- Observation framework for view models, not Combine.
- SwiftData for local persistence.
- Native URLSession + async/await networking.
- SPM only; no new dependencies.
- Offline-first: draft writes must be local-first and recoverable.
- Do not use Notion API specs.
- Product decisions: support multi-farm onboarding draft in frontend; call onboarding completion only when `시작하기` is tapped; backend will add a standalone farm-add endpoint instead of expanding onboarding complete to `farms[]`.

---

### Task 1: Multi-Farm Draft Model and DTO Compatibility

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Domain/OnboardingDraft.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/OnboardingCompleteRequestDTO.swift`
- Modify: `ChamChamCham/ChamChamChamTests/Support/OnboardingTestSupport.swift`
- Modify: `ChamChamCham/ChamChamChamTests/OnboardingCompleteRequestDTOTests.swift`

**Interfaces:**
- Produces: `OnboardingFarmDraft`, `OnboardingDraft.farms`, `OnboardingDraft.activeFarmIndex`, `OnboardingDraft.activeFarm`
- Produces: current backend-compatible DTO mapping using the first representative farm; additional farms wait for the standalone farm-add endpoint.

- [x] Write failing tests for `farms[]`, nickname blank rejection, max five crops per farm, and current first-farm onboarding-complete wire compatibility.
- [x] Run targeted tests and verify expected failures.
- [x] Implement `OnboardingFarmDraft` and compatibility accessors.
- [x] Update DTO validation and mapping.
- [x] Run targeted tests and verify pass.

### Task 2: ViewModel Routing and Completion Timing

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/ViewModels/OnboardingViewModel.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/AuthFlowView.swift`
- Modify: `ChamChamCham/ChamChamChamTests/OnboardingViewModelSubmitTests.swift`

**Interfaces:**
- Consumes: `OnboardingDraft.farms`
- Produces: `addFarmFromCompletion()`, explicit step order `basicProfile -> farmLocation -> cropSelection -> complete`

- [x] Write failing tests for step order, add-farm loop, and no auto-submit on completion view entry.
- [x] Run targeted tests and verify expected failures.
- [x] Implement explicit step routing and add-farm action.
- [x] Run targeted tests and verify pass.

### Task 3: Figma Completion Screen

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/OnboardingCompleteView.swift`

**Interfaces:**
- Consumes: `OnboardingViewModel.addFarmFromCompletion()`
- Consumes: `OnboardingViewModel.submit(appState:)`

- [x] Replace auto-submit progress screen with Figma completion UI.
- [x] Keep submitting/photo-upload/error retry states after `시작하기`.
- [x] Wire `재배지 추가하기` to Step 2 and `시작하기` to submit.

### Task 4: Step 2 and Step 3 Figma Behavior

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/FarmLocationView.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Presentation/Views/CropSelectionView.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Domain/Crop.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/DTOs/CategoryResponseDTO.swift`

**Interfaces:**
- Consumes: active farm draft fields and crop IDs
- Produces: Step 2 validation states, Step 3 sticky category/search UI, max five crop selection

- [x] Write failing tests for max five selection logic if extracted to view model/domain.
- [x] Implement active farm bindings in Step 2 and Step 3.
- [x] Implement Step 3 max five selection and bottom tray.
- [x] Preserve crop category code and label.

### Task 5: Session/Draft Safety

**Files:**
- Modify: `ChamChamCham/ChamChamCham/Core/Networking/TokenRefreshCoordinator.swift`
- Modify: `ChamChamCham/ChamChamCham/Features/Onboarding/Data/OnboardingDraftStore.swift`

**Interfaces:**
- Produces: network refresh failure does not clear local session; explicit unauthorized still does.
- Produces: draft persistence remains local and recoverable.

- [ ] Write failing test for refresh network failure not clearing token store if practical with injectable URLSession.
- [ ] Implement conservative token-clear behavior.
- [ ] Keep draft persisted on submission failure and cleared only after success.

### Task 6: Verification

**Files:**
- Test: `ChamChamCham/ChamChamChamTests/*`

- [x] Run targeted onboarding tests.
- [x] Run full app build.
- [x] Run `git diff --check`.
- [x] Report remaining backend-contract limitations clearly.
