# Test iOS Record Feedback Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the obsolete Record RAG raw-fixture screen with an internal iOS harness that exercises the actual farming-record and record-feedback API flow.

**Architecture:** Keep the harness deliberately small and typed. `BackendAPIClient` owns the five HTTP contracts, `HarnessState` owns authenticated request lifecycle and latest record/feedback state, and `TodayRecordFeedbackTestView` renders the operator controls. A fixed WATERING request avoids inventing a generic record editor while selected farm/crop IDs make the request valid against real data.

**Tech Stack:** SwiftUI, Foundation URLSession, XCTest, existing URLProtocolStub test transport.

## Global Constraints

- Keep this an internal QA harness; do not add production-client concerns or dependencies.
- Use the backend's public `/api/v1/farming-records` and nested `coaching-feedback` endpoints only.
- Do not log or display raw access tokens.
- The repository ignores `test-ios/`; source edits are intentionally local and are not force-added to the backend commit.
- Keep test UI utilitarian and use bounded polling rather than a background worker abstraction.

---

### Task 1: Lock the current API contract in XCTest

**Files:**
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/APIModelTests.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`

**Interfaces:**
- Produces `FarmingRecordSaveRequestDTO`, `FarmingRecordIdResponseDTO`, and `RecordFeedbackStatusResponseDTO` expectations for later tasks.

- [x] **Step 1: Write failing JSON decode and request-shape tests**

```swift
func testRecordFeedbackStatusDecodesReadyContent() throws {
    let result = try JSONDecoder.godsMove.decode(
        RecordFeedbackStatusResponseDTO.self,
        from: Data(#"{\"feedbackId\":\"00000000-0000-0000-0000-000000000201\",\"recordId\":\"00000000-0000-0000-0000-000000000101\",\"status\":\"READY\",\"sourceRevision\":1,\"inputPrepared\":true,\"failureCode\":null,\"feedback\":{\"goodPoint\":{\"text\":\"관수 시점을 잘 판단했습니다.\"},\"nextActions\":[{\"text\":\"토양 수분을 확인하세요.\",\"due\":\"TODAY\",\"category\":\"OBSERVE\"}]},\"createdAt\":\"2026-07-12T10:00:00\",\"updatedAt\":\"2026-07-12T10:00:01\"}"#.utf8)
    )

    XCTAssertEqual(result.status, .ready)
    XCTAssertEqual(result.feedback?.nextActions.first?.due, .today)
}
```

- [x] **Step 2: Run focused tests and confirm they fail because the typed contracts do not exist**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -only-testing:GodsMoveAuthHarnessTests/APIModelTests -only-testing:GodsMoveAuthHarnessTests/BackendAPIClientTests
```

Expected: compilation failure naming the missing record-feedback DTOs and client methods.

### Task 2: Implement typed transport and state operations

**Files:**
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/LoginView.swift`

**Interfaces:**
- Consumes typed DTO assertions from Task 1.
- Produces `createRecordFeedbackFixture(farmId:cropId:)`, `pollRecordFeedback()`, `replaceRecordFeedbackFixture()`, `regenerateRecordFeedback()`, and `deleteRecordFeedbackFixture()` for the view.

- [x] **Step 1: Add only the real API DTOs and endpoint paths**

```swift
enum RecordFeedbackStatusDTO: String, Codable { case pending = "PENDING", ready = "READY", failed = "FAILED", stale = "STALE" }

struct FarmingRecordSaveRequestDTO: Encodable {
    let farmId: UUID
    let cropId: UUID
    let workType: String
    let workedAt: String
    let weatherCondition: String
    let weatherTemperature: Int
    let memo: String
    let watering: WateringRecordDetailDTO
    let mediaIds: [UUID]
}
```

Add client operations for create, replace, delete, get feedback, and regenerate; each delegates to existing `send`/`decodeRaw` helpers and attaches the state-supplied bearer token.

- [x] **Step 2: Add state transitions with one fixed WATERING fixture**

```swift
func createRecordFeedbackFixture(farmId: UUID, cropId: UUID) async {
    // Begin request, require session token, create record, store returned ID,
    // then load the nested feedback status.
}
```

Use a 30+ character Korean memo and ISO-8601 local date-time strings. Expose latest record ID, response, and latest feedback response as read-only published state. Poll at most ten times, sleeping two seconds between PENDING responses; stop immediately for READY, FAILED, or STALE.

- [x] **Step 3: Run focused XCTest again and confirm it passes**

Run the Task 1 command. Expected: zero test failures.

### Task 3: Replace the obsolete screen and remove raw-fixture plumbing

**Files:**
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/TodayRecordFeedbackTestView.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/AuthHarnessView.swift`
- Delete: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/TodayRecordFeedbackFixture.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/LoginView.swift`

**Interfaces:**
- Consumes state operations from Task 2.
- Produces a single `Record Feedback` tab that executes the actual end-to-end QA path.

- [x] **Step 1: Render farm/crop selectors and actual workflow actions**

```swift
Button("Create WATERING Record") {
    Task { await state.createRecordFeedbackFixture(farmId: selectedFarmId, cropId: selectedCropId) }
}
Button("Poll Feedback") { Task { await state.pollRecordFeedback() } }
Button("Update Record") { Task { await state.replaceRecordFeedbackFixture() } }
Button("Regenerate Failed Feedback") { Task { await state.regenerateRecordFeedback() } }
Button("Delete Record", role: .destructive) { Task { await state.deleteRecordFeedbackFixture() } }
```

Disable controls when prerequisites are absent or a request is in progress. Show record ID, feedback ID, revision, status, good point, actions, failure code, and existing request/error/raw-response sections. Rename the tab and navigation title to `Record Feedback`.

- [x] **Step 2: Delete `TodayRecordFeedbackFixture` and its raw-post phase/state plumbing**

Keep the generic RAG raw JSON tool intact; it is separate functionality. Remove only `postingTodayRecordFeedbackFixture` and its `LoginView` display case.

- [x] **Step 3: Run full harness tests and compile-check the app**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1'
```

Expected: `** TEST SUCCEEDED **`.

### Task 4: Record the implementation scope without force-adding ignored harness code

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-test-ios-record-feedback-flow.md`

- [x] **Step 1: Mark completed steps and add actual verification evidence**

Include exact command results and note if simulator/device availability blocks XCTest.

- [x] **Step 2: Inspect repository state and commit tracked documentation only**

```bash
git status --short
git diff --check
```

Commit only the plan document with project Lore trailers. Do not force-add ignored `test-ios/` source or unrelated `.claude/` files.

## Self-Review

- Spec coverage: Tasks 1-3 cover real record creation, feedback retrieval/polling, update, failure regeneration, and deletion. Task 4 preserves the ignored-harness constraint.
- Placeholder scan: no deferred implementation behavior remains; operational limits and request types are fixed above.
- Type consistency: the DTO/client/state/view naming is `RecordFeedback` throughout, while generic RAG retains its existing name and implementation.

## Execution Evidence

- Red: the focused XCTest command initially failed as intended because `RecordFeedbackStatusResponseDTO` did not exist.
- Green: the same focused run passed after adding the DTO and client contract tests, including `testFarmingRecordAndFeedbackEndpointsUseAuthenticatedContract`.
- Full regression: `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -quiet -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived` completed with all listed XCTest cases passing.
- Scope check: no Swift source still references `TodayRecordFeedbackFixture`, `postingTodayRecordFeedbackFixture`, `/api/v1/dev/coaching/record-feedback`, or `Record RAG`.
- Repository check: `git diff --check` passed. Harness sources remain intentionally ignored by root `.gitignore`; only this tracked plan document is eligible for the commit.
