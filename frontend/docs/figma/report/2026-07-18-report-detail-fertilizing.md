#Report Detail — 비료 주기 (Fertilizing) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:24886`, name
`비료주기 리포트`, type `FRAME`, size 390×1769. Third Report Detail workType
capture, following
[2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md)
(심기, findings 1–5 fixed in `3aa1457b`) and
[2026-07-18-report-detail-watering.md](2026-07-18-report-detail-watering.md)
(물주기).

Current implementation: shared chrome in `ReportDetailView.swift` +
`ReportMetricCard.swift` (already covered), charts via `ReportChartCard.swift`
(already covered by
[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md)),
workType wiring in
[ReportPresentationModels.swift:75-103](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
(`case .fertilizing`), backed by
[`FertilizingReportStatistics`](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift)
(`totalAmountKg`, `averageAmountKg`, `amountCoverage`, `materialCategories`,
`methodDistribution`, `categoryMethods`).

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:24972`) — leading `icon/arrow_back_ios_new`,
   trailing `icon/more_vert`. Matches.
2. **Badges + period row** — same crop/farm badges, date range with `-`
   (hyphen) separator — same as 심기/물주기.
3. **WorkType title** ("비료주기", `1711:24898`) — SemiBold 28px `#1a1a1a`.
4. **Metric cards** (`1711:24899` `overview`, 2-up grid):
   - card-1: "총 작업 횟수" / "15회" (shared base metric).
   - card-2: "총 비료 사용량" / "100kg" — **matches code exactly**
     ([ReportPresentationModels.swift:78-81](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift),
     title `"총 비료 사용량"`, value from `totalAmountKg`). No mismatch here,
     unlike the watering metric-title finding.
5. **"상세 정보" chart section** (`1711:24906`) — **3 cards this time**
   (fertilizing has 2 material-category donuts + 1 method stacked-bar, vs
   watering's 2 stacked bars):
   - card `1711:24908`: title **"진행한 비료주기 방식"**, stacked-bar graph
     (`1711:24912`) — "점적" 8번 (`#38c284`), unlabeled (`#c8f468`),
     unlabeled (`#f7dc11`). This is the `methodDistribution` chart.
   - card `1711:24918`: title **"각 비료 사용 횟수"**, **donut** graph
     (`1711:24922`, 6 slices: `#b1cbdf`, `#81dacb`, `#c8f468`, `#f7dc11`,
     `#a5e9b1`, `#38c284`), center label "A비료" / "12번". This is the
     `materialCategories` recordCount chart.
   - card `1711:24933`: title **"각 비료 사용량"**, donut graph (`1711:24937`,
     same 6-color palette), center label "A비료" / "500kg". This is the
     `materialCategories` amountKg chart.
6. **"참참참의 코칭"** (`1711:24948`) — same 4-card shape/copy as 심기/물주기
   (mock reuses identical placeholder text across workTypes), not compared
   in detail.
7. **Divider + "기록 내역 리스트"** (`1711:24964`) — `icon/arrow_forward_ios`
   + 3 inline preview rows, identical shape to prior captures.

## Confirmed matches

- **Findings 1–5 hold on this screen too** (icons, WorkType title font,
  metric label/value styling) — no regressions, same as the 물주기
  reconfirmation.
- **Base + fertilizing metric titles/values match code exactly** — no
  metric-title mismatch here (contrast with the 물주기 capture's finding 1).
- **Donut palette matches `Color.Chart.palette` exactly**: `#38c284`=primary,
  `#a5e9b1`=green300, `#f7dc11`=yellow, `#c8f468`=lime, `#81dacb`=turquoise
  (post-fix value), `#b1cbdf`=blue — all 6 palette colors appear in Figma's
  6-slice donut in the same
  [Color+App.swift:132-141](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift)
  order. No color bug.
- Donut center-label layout ("A비료" + count/amount) matches the existing
  `ReportChartCard` donut pattern already confirmed in the chart-spec doc —
  no new issue (the known "center label hidden on expand" bug from that doc
  still applies here, not re-flagged).

## Findings — ✅ fixed (Tier 1-A/1-B)

### 1. All 3 chart titles differed from Figma's copy
Code previously used `"비료 종류별 작업 횟수"` (count donut), `"비료 종류별
사용량"` (amount donut), `"비료 주는 방법"` (method bar). Figma's titles:
**"진행한 비료주기 방식"** (method bar), **"각 비료 사용 횟수"** (count
donut), **"각 비료 사용량"** (amount donut).

**Fixed (2026-07-18, report detail remediation plan Tier 1-A)**:
[ReportPresentationModels.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
now uses Figma's exact copy for all three chart titles.

### 2. Chart order: method chart is always first in Figma, but code put it in different positions per workType
Figma renders **method/style distribution first**, then the
material-specific charts (진행한 비료주기 방식 → 각 비료 사용 횟수 → 각 비료
사용량). Code previously appended the method chart **last**.

**Fixed (2026-07-18, Tier 1-B)**: method-distribution chart now appends first,
matching Figma order.

## Reconfirmed open items (not new)

- **Date-range separator** `-` vs `~` — same open question, recurs a third
  time.
- **Inline record-row preview** — same product-scope gap, recurs a third
  time; still just a placeholder link in code.

## Summary of findings

1. ✅ Fixed (2026-07-18, Tier 1-A): chart titles now "각 비료 사용 횟수"/
   "각 비료 사용량"/"진행한 비료주기 방식", matching Figma.
2. ✅ Fixed (2026-07-18, Tier 1-B): method-distribution chart now appends
   first, matching Figma order.
3. (Reconfirmed, not new, Tier 3) Date-range separator `-` vs `~`.
4. (Reconfirmed, not new, Tier 3) Inline record-row preview scope gap.
