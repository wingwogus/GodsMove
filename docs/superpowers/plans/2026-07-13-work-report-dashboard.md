# Work Report Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing local report console into a task-specific farming statistics dashboard and let the test iOS report tab call any manually entered farm/crop UUID pair.

**Architecture:** `frontend/dev-rag-test.html` keeps its existing request client and raw inspector, while its report renderer becomes a selected-work dashboard with a compact cycle context, work tabs, summary cards, and distribution cards. `FarmingReportTestView` remains a utilitarian API harness: a small parse helper resolves direct UUID text, and pickers only fill the same fields for convenience.

**Tech Stack:** Vanilla HTML/CSS/JavaScript, SwiftUI, Foundation UUID, XCTest, existing Xcode project.

## Global Constraints

- Reuse tracked `frontend/dev-rag-test.html`; do not introduce a separate ignored dashboard.
- Keep raw responses, request logs, API errors, current/previous/selected snapshots, and completed list access available for QA.
- Only render values in the typed backend report response; do not invent recommendations, scores, or sample data as live results.
- A direct valid UUID pair must work without calling `Load Farms`; picker selection copies its IDs into the same text fields.
- `test-ios/` sources are ignored and must not be force-added; `frontend/dev-rag-test.html` and docs are tracked.
- Do not modify or stage user-owned `backend/api/src/main/resources/application-local.yml` or `.claude/`.

---

### Task 1: Lock manual test-iOS target parsing first

**Files:**
- Create: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarnessTests/FarmingReportTargetTests.swift`
- Modify: `test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness/FarmingReportTestView.swift`

**Interfaces:**
- Produces `FarmingReportTarget(farmIdText:cropIdText:) -> FarmingReportTarget?`.
- `FarmingReportTarget` exposes `farmId: UUID` and `cropId: UUID`; actions consume only this parsed value.

- [x] **Step 1: Write failing focused XCTest**

```swift
func testFarmingReportTargetAcceptsManuallyEnteredUUIDs() {
    let target = FarmingReportTarget(
        farmIdText: " 00000000-0000-0000-0000-000000000401 ",
        cropIdText: "00000000-0000-0000-0000-000000000301"
    )

    XCTAssertEqual(target?.farmId.uuidString, "00000000-0000-0000-0000-000000000401")
    XCTAssertEqual(target?.cropId.uuidString, "00000000-0000-0000-0000-000000000301")
}

func testFarmingReportTargetRejectsInvalidOrPartialUUIDs() {
    XCTAssertNil(FarmingReportTarget(farmIdText: "not-a-uuid", cropIdText: "00000000-0000-0000-0000-000000000301"))
    XCTAssertNil(FarmingReportTarget(farmIdText: "00000000-0000-0000-0000-000000000401", cropIdText: ""))
}
```

- [x] **Step 2: Run red test**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived -only-testing:GodsMoveAuthHarnessTests/FarmingReportTargetTests
```

Expected: compilation fails because `FarmingReportTarget` does not yet exist.

- [x] **Step 3: Add the minimal parser and bind report actions to it**

```swift
struct FarmingReportTarget: Equatable {
    let farmId: UUID
    let cropId: UUID

    init?(farmIdText: String, cropIdText: String) {
        guard let farmId = UUID(uuidString: farmIdText.trimmingCharacters(in: .whitespacesAndNewlines)),
              let cropId = UUID(uuidString: cropIdText.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return nil
        }
        self.farmId = farmId
        self.cropId = cropId
    }
}
```

Add `@State private var farmIdText = ""` and `cropIdText = ""`. Put `TextField("Farm ID", text: $farmIdText)` and `TextField("Crop ID", text: $cropIdText)` above the pickers. Selecting a farm/crop copies its UUID string into the corresponding field. `Load Current`, `Load Completed`, and `Load More` use `reportTarget`, not optional picker IDs.

- [x] **Step 4: Run the focused XCTest green**

Run the Step 2 command. Expected: two passing `FarmingReportTargetTests`.

### Task 2: Replace the web report panel with a selected-work dashboard

**Files:**
- Modify: `frontend/dev-rag-test.html`

**Interfaces:**
- Consumes existing `state.report`, `state.reportScope`, `state.workType`, `renderReport()`, and report API loaders.
- Produces `renderReportDashboard(snapshot)`, `renderWorkDashboard(stats, type)`, and `renderDistribution(items)` DOM rendering helpers.

- [x] **Step 1: Preserve a small QA control strip and add app-style dashboard containers**

```html
<div id="reportContext" class="report-context"></div>
<div id="reportWorkTabs" class="report-work-tabs" role="tablist"></div>
<div id="reportDashboard" class="report-dashboard"></div>
<details class="report-diagnostics">
  <summary>API 응답 · 완료 리포트 · 요청 로그</summary>
  <div id="reportList"></div>
</details>
```

Keep the three API buttons and snapshot tabs. Move the old report list and raw diagnostic-oriented content under the `details` element. Use existing green and gray tokens from the app’s `Color+App.swift` values: `#27865C`, `#38C284`, `#E4F8E3`, `#F2FAE1`, `#1A1A1A`, and `#686868`.

- [x] **Step 2: Render only the selected work’s supported statistics**

```javascript
function renderWorkDashboard(stats, type) {
  if (!stats || stats.recordCount === 0) {
    return emptyDashboard(`${workTypeLabels[type]} 기록이 없습니다.`);
  }

  return dashboard([
    hero(workTypeLabels[type], stats.recordCount, stats.lastWorkedOn, stats.averageIntervalDays),
    commonMetrics(stats),
    ...distributionSections(stats, type),
    type === "harvest" ? harvestSection(stats) : null
  ]);
}
```

Use actual API fields: `methodDistribution`, `amountDistribution`, `propagationMethods`, `materialCategories`, `categoryDistribution`, `targets`, `medicinalParts`, `totalAmountKg`, `averageAmountKg`, `amountCoverage`, and `growthPeriodRangeMonths`. Omit a card when the corresponding array/value is absent or empty.

- [x] **Step 3: Make work tabs count-aware and keep `전체` shallow**

```javascript
button.textContent = type === "all"
  ? "전체"
  : `${workTypeLabels[type]} ${statistics?.[type]?.recordCount || 0}`;
```

Insert `all` before the existing work types. The all tab shows only the eight-work record-count distribution; it does not combine incompatible work details. On initial load pick the first non-zero work type, otherwise `all`.

- [x] **Step 4: Syntax-check the HTML script**

```bash
node --check <(sed -n '/<script>/,/<\/script>/p' frontend/dev-rag-test.html | sed '1d;$d')
```

Expected: exit 0 with no output.

### Task 3: Full verification and recorded delivery

**Files:**
- Modify: `docs/superpowers/plans/2026-07-13-work-report-dashboard.md`

- [x] **Step 1: Run the full iOS harness suite**

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -quiet -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived
```

Expected: `** TEST SUCCEEDED **` and the manual target test passes.

- [x] **Step 2: Run static checks and inspect the intended tracked diff**

```bash
git diff --check
git status --short
git diff -- frontend/dev-rag-test.html docs/superpowers/plans/2026-07-13-work-report-dashboard.md
```

Expected: no whitespace errors; ignored `test-ios` source stays unstaged; user-owned local files remain untouched.

- [x] **Step 3: Mark actual evidence and commit tracked changes**

```bash
git add frontend/dev-rag-test.html docs/superpowers/plans/2026-07-13-work-report-dashboard.md
git commit -m "feat(report): 작업별 통계 대시보드를 제공한다"
```

Add Lore trailers with the rejected separate-dashboard alternative, test evidence, and the known absence of live report-data manual verification if applicable.

## Self-Review

- Spec coverage: Task 1 makes arbitrary test-iOS targets possible; Task 2 implements the approved selected-work dashboard using only real statistics; Task 3 verifies and commits tracked artifacts.
- Placeholder scan: each task fixes its interfaces, API fields, and commands; no unsupported report action is introduced.
- Type consistency: `FarmingReportTarget`, `reportTarget`, `renderReportDashboard`, and `renderWorkDashboard` name the same manual-target and selected-work concepts throughout.

## Execution Evidence

- RED: `FarmingReportTargetTests` initially failed because `FarmingReportTarget` did not exist.
- GREEN: the focused test target passed after adding UUID trimming/parsing and using that parsed pair for all report requests.
- Web syntax: `node --check <(sed -n '/<script>/,/<\/script>/p' frontend/dev-rag-test.html | sed '1d;$d')` exited successfully.
- Static serving: the local `frontend` server returned the modified `reportWorkTabs` and `reportDashboard` markup on `GET /dev-rag-test.html`.
- Full regression: `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild test -quiet -project test-ios/GodsMoveAuthHarness/GodsMoveAuthHarness.xcodeproj -scheme GodsMoveAuthHarness -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.3.1' -derivedDataPath /private/tmp/GodsMoveAuthHarness-derived` passed.
- Scope: test-iOS Swift sources remain ignored; only the existing tracked console and this plan are eligible for the implementation commit. The user-owned `application-local.yml` and `.claude/` remain untouched.
