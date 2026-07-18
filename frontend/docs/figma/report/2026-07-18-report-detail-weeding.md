#Report Detail — 잡초 관리 (Weeding) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:25065`, name
`잡초 관리 리포트`, type `FRAME`, size 390×1341. Fifth Report Detail workType
capture, following 심기
([2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md),
findings 1–5 fixed in `3aa1457b`), 물주기
([2026-07-18-report-detail-watering.md](2026-07-18-report-detail-watering.md)),
비료 주기
([2026-07-18-report-detail-fertilizing.md](2026-07-18-report-detail-fertilizing.md)),
and 병해충 관리
([2026-07-18-report-detail-pest-control.md](2026-07-18-report-detail-pest-control.md)).

Current implementation: shared chrome (already covered), charts via
`ReportChartCard.swift` (already covered by
[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md)),
workType wiring in
[ReportPresentationModels.swift:149-152](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
(`case .weeding`) — the simplest workType case, backed by a single field:
[`WeedingReportStatistics.methodDistribution`](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift).

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:25121`) — `arrow_back_ios_new` / `more_vert`. Matches.
2. **Badges + period row** — same shape, `-` (hyphen) date separator again.
3. **WorkType title** ("잡초 관리", `1711:25077`) — SemiBold 28px `#1a1a1a`.
4. **Metric cards** (`1711:25078` `overview`) — **only 1 card, full width**:
   "총 작업 횟수" / "15회" (shared base metric only — matches code exactly,
   since `WeedingReportStatistics` has no extra metric field to append).
5. **"상세 정보" chart section** (`1711:25082`, **1 card only**):
   - card `1711:25084`: title **"진행한 잡초 관리 방식"**, **donut** (4
     slices this time: `#c8f468`, `#f7dc11`, `#a5e9b1`, `#38c284`), center
     label "손으로 뽑기" (hand-pulling) / "12번".
6. **"참참참의 코칭"** — same 4-card shape/copy as prior captures.
7. **Divider + "기록 내역 리스트"** — `arrow_forward_ios` + 3 inline preview
   rows, identical shape to prior captures.

## Confirmed matches

- **Findings 1–5 hold on this screen too** — 4th reconfirmation, no
  regressions.
- **Base metric matches exactly** — no metric-title mismatch here (this
  workType has no extra metric field in code, and Figma agrees: only 1
  card).
- **Donut-vs-bar style rule reconfirmed with a new case**: this is the first
  capture where a chart built via `appendDistributionChart` (normally
  rendered as a 3-segment stacked bar in prior captures) renders as a
  **4-slice donut** instead, because `methodDistribution` has 4 categories
  here (>3) — consistent with the count-driven rule confirmed in the
  pest-control capture
  (`style = normalized.count <= 3 ? .stackedBar : .semiDonut`,
  [ReportChartModel.swift:75](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift)).
  Not a bug — just the first time we've seen this branch trigger for a
  `appendDistributionChart` call specifically.
- Donut colors match `Color.Chart` — no color bug.

## Findings

### 1. Chart title mismatch (✅ fixed, Tier 1-A)
Code previously used
`Self.appendDistributionChart(title: "잡초 관리 방법", values: statistics.methodDistribution, to: &charts)`.
Figma's only detail card is titled **"진행한 잡초 관리 방식"**.

**Fixed (2026-07-18, report detail remediation plan Tier 1-A)**:
[ReportPresentationModels.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
now uses `"진행한 잡초 관리 방식"` verbatim.

### 2. Chart order: trivially "correct" (only one chart)
Not really a new finding — noting for completeness since chart order was an
open question for 물주기/비료 주기: weeding only produces one chart, so
there's no order to get wrong. Doesn't change the "order is workType-specific,
not universal" conclusion from the pest-control capture.

## Reconfirmed open items (not new)

- **Date-range separator** `-` vs `~` — 5th recurrence.
- **Inline record-row preview** — 5th recurrence, still a placeholder link.

## Summary of findings

1. ✅ Fixed (2026-07-18, Tier 1-A): chart title now "진행한 잡초 관리 방식",
   matching Figma.
2. (Reconfirmed, not new, Tier 3) Date-range separator `-` vs `~`.
3. (Reconfirmed, not new, Tier 3) Inline record-row preview scope gap.
