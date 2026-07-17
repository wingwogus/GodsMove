#Report Detail — 물주기 (Watering) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:24818`, name `물주기 리포트`,
type `FRAME`, size 390×1444. Second Report Detail workType capture, following
[2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md)
(심기) which established the shared screen chrome and already got findings
1–5 fixed (commit `3aa1457b`).

Current implementation: `ReportDetailView.swift` + `ReportMetricCard.swift`
(shared chrome, already covered) + `ReportChartCard.swift` (charts, already
covered by
[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md))
+ workType-specific metric/chart wiring in
[ReportPresentationModels.swift:62-73](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
(`case .watering`), backed by
[`WateringReportStatistics`](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift)
(`amountDistribution` / `methodDistribution`, both `[ReportCountDistribution]`).

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:24884`) — no title, leading `icon/arrow_back_ios_new`,
   trailing `icon/more_vert`.
2. **Badges + period row** (`1711:24820`) — same crop/farm badges as 심기,
   date range `"2026. 12. 12." + "-" + "2026. 12. 14."` (hyphen separator).
3. **WorkType title** ("물주기", `1711:24830`) — SemiBold 28px `#1a1a1a`.
4. **Metric cards** (`1711:24831` `overview`, 2-up grid):
   - card-1: "총 작업 횟수" / "15회" (shared base metric).
   - card-2: **"평균 물 준 양" / "보통"**.
5. **"상세 정보" chart section** (`1711:24838`) — two stacked-bar cards:
   - card `1711:24840`: title **"진행한 물주기 방식"**, graph
     `1711:24844` with 3 segments — "점적" 8번 (`#38c284`, width 180),
     unlabeled (`#c8f468`, width 65), unlabeled (`#f7dc11`, width 65).
   - card `1711:24850`: title **"물 준 양"**, graph `1711:24854` with 3
     segments — "적음" 8번 (`#38c284`, width 180), unlabeled (`#c8f468`, width
     65), unlabeled (`#f7dc11`, width 65).
6. **"참참참의 코칭"** (`1711:24860`) — same 4-card shape as 심기 capture, not
   compared in detail (out of scope, same as before).
7. **Divider + "기록 내역 리스트"** (`1711:24876`) — header +
   `icon/arrow_forward_ios` + 3 inline preview rows (`mm/dd` + title + caption
   + 88×88 thumbnail), identical shape to the 심기 capture.

## Confirmed matches

- **Findings 1–5 from the 심기 capture hold here too, post-fix** — this frame
  independently re-validates all five as still correct after commit
  `3aa1457b`:
  - Top-bar leading/back icon is `arrow_back_ios_new` (code:
    [ReportDetailView.swift:28](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)) ✅
  - Record-list header icon is `arrow_forward_ios` (code:
    [ReportDetailView.swift:147](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)) ✅
  - WorkType title "물주기" is `SemiBold 28px` = `.headlineMedium` (code:
    [ReportDetailView.swift:112](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)) ✅
  - Metric label "총 작업 횟수"/"평균 물 준 양" is `SemiBold 15px #878787` =
    `.labelMediumEmphasized` (code:
    [ReportMetricCard.swift:16](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportMetricCard.swift)) ✅
  - Metric value "15회"/"보통" is `SemiBold 20px #4f4f4f` =
    `.titleMediumEmphasized` + `Color.Text.subtle` (code:
    [ReportMetricCard.swift:20-22](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportMetricCard.swift)) ✅
- **Stacked-bar segment colors** match `Color.Chart` exactly: `#38c284` =
  `Chart.primary`, `#c8f468` = `Chart.lime`, `#f7dc11` = `Chart.yellow`
  ([Color+App.swift:132-141](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift)) —
  no color bug here (unlike the turquoise bug found/fixed for a different
  chart earlier).
- **Badges, date-separator styling** (aside from the hyphen-vs-tilde
  question already tracked), record-list row shape — all match 심기 1:1, no
  new issues.

## ⚠️ New findings (not fixed — documented only)

### 1. Watering-specific metric title doesn't match Figma's wording
Code
([ReportPresentationModels.swift:66-69](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):

```swift
if let mostFrequent = statistics.amountDistribution.sorted(by: Self.distributionOrder).first,
   mostFrequent.count > 0 {
    metrics.append(ReportMetricPresentation(
        title: "가장 자주 준 물의 양",   // "the amount most frequently given"
        value: mostFrequent.label
    ))
}
```

Figma's card-2 metric (`1711:24836`) is titled **"평균 물 준 양"** ("average
amount of water given"), not "가장 자주 준 물의 양". This isn't just a copy
diff — the two titles imply different underlying statistics: the code
computes the **mode** (most-frequent bucket via `distributionOrder` sort),
while Figma's wording implies an **average**. The captured value ("보통")
happens to look plausible either way, so this needs a product/design
decision — either update the Figma-facing copy to match what's actually
computed (mode), or change the computation to a real average — not a
find-and-replace string fix.

### 2. Chart order and titles differ from Figma
Code
([ReportPresentationModels.swift:71-72](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):

```swift
Self.appendDistributionChart(title: "물의 양", values: statistics.amountDistribution, to: &charts)
Self.appendDistributionChart(title: "물 주는 방법", values: statistics.methodDistribution, to: &charts)
```

This renders **amount chart first** ("물의 양"), **method chart second**
("물 주는 방법") — `ReportDetailView.swift`'s `chartSection` renders `charts`
in array order, so this is also the on-screen order.

Figma shows the opposite: **method chart first** (`1711:24840`, titled
**"진행한 물주기 방식"**), **amount chart second** (`1711:24850`, titled
**"물 준 양"**). Both the order and the exact title strings differ from the
current code on both charts. Documenting only — swapping order + retitling
both is a small change but touches user-facing copy, worth confirming
intent before editing (e.g. whether "물의 양"→"물 준 양" and "물 주는
방법"→"진행한 물주기 방식" are the *only* difference, or whether other
workTypes' distribution-chart titles have the same drift and should be
checked together).

## Reconfirmed open items (not new, still open from the 심기 capture)

- **Date-range separator**: this screen's Figma capture also uses `-`
  (hyphen) between the two dates, matching the same open question already
  raised for 심기 — reconfirms it's a Detail-screen-wide pattern, not a
  one-off. Still needs a designer call on List (`~`) vs Detail (`-`).
- **Inline record-row preview**: same 3-row inline preview shape as 심기 —
  `ReportRecordHistoryView` is still a placeholder. Same product-scope gap,
  not new.

## Summary of open findings (not yet fixed)

1. Watering metric title/semantics mismatch: code's "가장 자주 준 물의 양"
   (mode) vs Figma's "평균 물 준 양" (average) — needs a product decision,
   not a blind copy fix.
2. Chart order + titles: code is [물의 양, 물 주는 방법] vs Figma's
   [진행한 물주기 방식, 물 준 양] (reversed order, different titles on both).
3. (Reconfirmed, not new) Date-range separator `-` vs `~` — open design
   question shared with the 심기 capture.
4. (Reconfirmed, not new) Inline record-row preview — product scope gap
   shared with the 심기 capture.
