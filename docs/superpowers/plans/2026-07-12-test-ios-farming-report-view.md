# Test iOS Farming Report View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one internal iOS QA tab that reads the current, completed-list, and detail farming-cycle report APIs for a selected farm and crop.

**Architecture:** Typed public response contracts live in `APIModels.swift`; authenticated URLSession calls live in `BackendAPIClient.swift`; request lifecycle and decoded values live in `HarnessState.swift`. A small SwiftUI form reuses the current farm data and renders only report metadata and aggregate statistics.

**Tech Stack:** SwiftUI, Foundation URLSession, XCTest, existing URLProtocolStub test transport.

## Global Constraints

- This is a local, utilitarian QA harness; do not add production-client abstractions or dependencies.
- Use only `GET /api/v1/farming-reports/current`, `GET /api/v1/farming-reports`, and `GET /api/v1/farming-reports/{reportId}`.
- Do not display or persist bearer tokens.
- `test-ios/` is ignored; never force-add its sources. Commit only tracked documents.
- Do not modify or stage `backend/api/src/main/resources/application-local.yml`.

---

### Task 1: Lock the public report contracts in XCTest

**Files:**
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/APIModelTests.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/BackendAPIClientTests.swift`

**Interfaces:**
- Produces `FarmingCycleReportCurrentResponseDTO`, `FarmingCycleReportPageDTO`, and `FarmingCycleReportDetailResponseDTO` expectations.
- Produces `getCurrentFarmingReport`, `listCompletedFarmingReports`, and `getFarmingReportDetail` request expectations.

- [x] **Step 1: Write failing JSON decode tests**

```swift
func testFarmingCycleReportCurrentDecodesSnapshotStatistics() throws {
    let response = try JSONDecoder.godsMove.decode(
        FarmingCycleReportCurrentResponseDTO.self,
        from: Data("""{\"current\":{\"id\":\"00000000-0000-0000-0000-000000000701\",\"farmId\":\"00000000-0000-0000-0000-000000000401\",\"farmName\":\"참참농장\",\"cropId\":\"00000000-0000-0000-0000-000000000301\",\"cropName\":\"참당귀\",\"status\":\"ACTIVE\",\"startsAt\":\"2026-07-01T09:00:00\",\"endsAt\":null,\"startBasis\":\"FIRST_RECORD\",\"finalHarvestRecordId\":null,\"statisticsSchemaVersion\":1,\"sourceRevision\":3,\"statistics\":{\"planting\":{\"recordCount\":1},\"watering\":{\"recordCount\":2},\"fertilizing\":{\"recordCount\":0},\"pestControl\":{\"recordCount\":0},\"weeding\":{\"recordCount\":0},\"pruning\":{\"recordCount\":0},\"harvest\":{\"recordCount\":0,\"totalAmountKg\":null,\"amountCoverage\":{\"recordedCount\":0,\"targetCount\":0}},\"etc\":{\"recordCount\":0}}},\"previous\":null}""".utf8)
    )
    XCTAssertEqual(response.current?.statistics.watering.recordCount, 2)
    XCTAssertEqual(response.current?.statistics.harvest.amountCoverage.targetCount, 0)
}
```

- [x] **Step 2: Write a failing authenticated transport test**

```swift
let current = try await client.getCurrentFarmingReport(farmId: farmId, cropId: cropId, accessToken: "access-token")
let page = try await client.listCompletedFarmingReports(farmId: farmId, cropId: cropId, cursor: "cursor-1", size: 20, accessToken: "access-token")
let detail = try await client.getFarmingReportDetail(reportId: reportId, accessToken: "access-token")
XCTAssertEqual(current.value.current?.id, reportId)
XCTAssertEqual(page.value.nextCursor, nil)
XCTAssertEqual(detail.value.selected.id, reportId)
```

The URLProtocol stub asserts `GET`, `Authorization: Bearer access-token`, the three paths, and `farmId`, `cropId`, `cursor`, `size` query values.

- [x] **Step 3: Run focused tests and verify missing symbols fail**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived -only-testing:GodsMoveAuthHarnessTests/APIModelTests -only-testing:GodsMoveAuthHarnessTests/BackendAPIClientTests
```

Expected: compilation failure naming missing report DTOs and client methods.

### Task 2: Implement typed read-only report transport and state

**Files:**
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/APIModels.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/BackendAPIClient.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/HarnessState.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/LoginView.swift`

**Interfaces:**
- Consumes Task 1's contract tests.
- Produces `loadCurrentFarmingReport(farmId:cropId:)`, `loadCompletedFarmingReports(farmId:cropId:cursor:)`, and `loadFarmingReportDetail(reportId:)`.

- [x] **Step 1: Add minimal typed DTOs**

```swift
struct FarmingCycleReportCurrentResponseDTO: Decodable, Equatable {
    let current: FarmingCycleReportSnapshotDTO?
    let previous: FarmingCycleReportSnapshotDTO?
}

struct FarmingCycleReportStatisticsDTO: Decodable, Equatable {
    let planting: FarmingCycleReportWorkStatisticsDTO
    let watering: FarmingCycleReportWorkStatisticsDTO
    let fertilizing: FarmingCycleReportWorkStatisticsDTO
    let pestControl: FarmingCycleReportWorkStatisticsDTO
    let weeding: FarmingCycleReportWorkStatisticsDTO
    let pruning: FarmingCycleReportWorkStatisticsDTO
    let harvest: FarmingCycleReportHarvestStatisticsDTO
    let etc: FarmingCycleReportWorkStatisticsDTO
}
```

`FarmingCycleReportWorkStatisticsDTO` decodes `recordCount`; harvest also decodes optional `totalAmountKg` and `amountCoverage(recordedCount,targetCount)`. Use `decodeIfPresent` defaults so defaulted backend statistics decode in tests and production.

- [x] **Step 2: Add canonical paths and three transport methods**

```swift
static let farmingReports = "/api/v1/farming-reports"
static func currentFarmingReport(farmId: UUID, cropId: UUID) -> String
static func farmingReportList(farmId: UUID, cropId: UUID, cursor: String?, size: Int) -> String
static func farmingReport(_ reportId: UUID) -> String
```

Each method delegates to the existing `send(path:method:accessToken:)`. The list request omits blank cursors.

- [x] **Step 3: Add state fields and transitions**

```swift
@Published private(set) var currentFarmingReport: FarmingCycleReportCurrentResponseDTO?
@Published private(set) var completedFarmingReports: [FarmingCycleReportMetadataDTO] = []
@Published private(set) var completedFarmingReportsNextCursor: String?
@Published private(set) var selectedFarmingReport: FarmingCycleReportDetailResponseDTO?
```

Use `beginRequest`, `withAccessToken`, `applyResponse`, and `fail` as existing farm/policy methods do. A nil cursor replaces items; a later cursor appends. Clear all values in `resetSessionState()` and add matching phase text cases.

- [x] **Step 4: Run Task 1 focused XCTest and verify green**

Run the Task 1 command. Expected: zero test failures.

### Task 3: Add the report QA tab

**Files:**
- Create: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/FarmingReportTestView.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/AuthHarnessView.swift`

**Interfaces:**
- Consumes Task 2's state APIs and values.
- Produces the `Reports` tab.

- [x] **Step 1: Render farm/crop selection and read buttons**

```swift
Button("Load Farms") { Task { await state.loadFarms() } }
Button("Load Current") { Task { await state.loadCurrentFarmingReport(farmId: farmId, cropId: cropId) } }
Button("Load Completed") { Task { await state.loadCompletedFarmingReports(farmId: farmId, cropId: cropId, cursor: nil) } }
```

Reuse the picker behavior from `TodayRecordFeedbackTestView`; disable report actions until both IDs exist or while busy.

- [x] **Step 2: Render facts instead of a generic report engine**

```swift
LabeledContent("Status", value: snapshot.status.rawValue)
LabeledContent("Revision", value: String(snapshot.sourceRevision))
LabeledContent("Watering Records", value: String(snapshot.statistics.watering.recordCount))
LabeledContent("Harvest Amount", value: snapshot.statistics.harvest.totalAmountKg ?? "-")
```

Show current and previous snapshots separately. Selecting a completed metadata row invokes `loadFarmingReportDetail(reportId:)`; show selected and previous detail snapshots. Retain raw response, latest path, and error sections.

- [x] **Step 3: Register the tab**

```swift
FarmingReportTestView()
    .tag(AuthHarnessTab.reports)
    .tabItem { Label("Reports", systemImage: "chart.bar.doc.horizontal") }
```

Add only `case reports` to `AuthHarnessTab`.

### Task 4: Verify and record the local QA change

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-test-ios-farming-report-view.md`

- [x] **Step 1: List the Xcode project and run the full harness suite**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -list -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -quiet -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived
```

Expected: project/scheme listed and `** TEST SUCCEEDED **`.

- [x] **Step 2: Record actual evidence and commit tracked documents only**

```bash
git diff --check
git add docs/superpowers/specs/2026-07-12-test-ios-farming-report-view-design.md docs/superpowers/plans/2026-07-12-test-ios-farming-report-view.md
git commit -m "docs(test-ios): 리포트 조회 하네스 계획을 기록한다"
```

Use Lore trailers. Never stage ignored Swift sources, `.claude/`, or `application-local.yml`.

## Self-Review

- Spec coverage: Tasks 1-3 cover all three read APIs, selected farm/crop input, concise summaries, completed pagination, detail selection, and request/error inspection.
- Placeholder scan: no mutating report action or artificial coaching response is included.
- Type consistency: `FarmingCycleReport*DTO`, `load*FarmingReport`, and `farmingReport*` distinguish this feature from record feedback.

## Execution Evidence

- RED: focused XCTest first failed as intended because `FarmingCycleReportCurrentResponseDTO` did not exist.
- GREEN: after implementing the typed models and client paths, focused `APIModelTests` and `BackendAPIClientTests` passed.
- Regression: `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -list -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj` listed the `GodsMoveAuthHarness` scheme.
- Regression: `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -quiet -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived` passed the full suite, including `testFarmingReportEndpointsUseAuthenticatedContract` and `testFarmingCycleReportCurrentDecodesSnapshotStatistics`.
- Scope: no ignored Swift source will be force-added; the unchanged user-owned `application-local.yml` remains outside the commit.
