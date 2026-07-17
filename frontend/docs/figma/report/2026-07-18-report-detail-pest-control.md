#Report Detail — 병해충 관리 (Pest Control) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:24974`, name
`병해충 관리 리포트`, type `FRAME`, size 390×1769. Fourth Report Detail
workType capture, following
[2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md)
(심기, findings 1–5 fixed in `3aa1457b`),
[2026-07-18-report-detail-watering.md](2026-07-18-report-detail-watering.md)
(물주기), and
[2026-07-18-report-detail-fertilizing.md](2026-07-18-report-detail-fertilizing.md)
(비료 주기).

Current implementation: shared chrome (already covered), charts via
`ReportChartCard.swift` (already covered by
[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md)),
workType wiring in
[ReportPresentationModels.swift:105-147](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
(`case .pestControl`), backed by
[`PestControlReportStatistics`](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift)
(`categoryDistribution`, `pesticideAmounts`, `categoryAmounts`,
`totalSprayAmountLiters`, `sprayAmountCoverage`, `targets`).

Also confirmed this pass: chart visual style (stacked bar vs semi-donut) is
purely data-count-driven —
[ReportChartModel.swift:75](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift):
`style = normalized.count <= 3 ? .stackedBar : .semiDonut`. This explains why
some `appendChart` calls render as a donut (fertilizing's 6-category
material breakdown) and others as a stacked bar (this screen's 3-item
"관리 대상" pest-target breakdown) — it's the same code path picking a
visual based on item count, not a per-call-site choice. Not a new finding,
just closing the loop on how chart style is chosen.

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:25063`) — `arrow_back_ios_new` / `more_vert`. Matches.
2. **Badges + period row** — same shape, `-` (hyphen) date separator again.
3. **WorkType title** ("병해충 관리", `1711:24986`) — SemiBold 28px `#1a1a1a`.
4. **Metric cards** (`1711:24987` `overview`, **3-up** this time, ~108.67pt
   each instead of 169pt 2-up):
   - card-1: "총 작업 횟수" / "15회" (shared base metric).
   - card-2: **"총 농약 사용량" / "500ml"**.
   - card-3: **"총 살포량" / "5000ml"**.
5. **"상세 정보" chart section** (`1711:24997`, 3 cards, in this order):
   - card `1711:24999`: title **"사용한 약제 종류"**, **donut** (6 slices,
     full `Color.Chart.palette`), center "A약제" / "12번" (count).
   - card `1711:25014`: title **"각 약제 사용량"**, donut (same palette),
     center "A약제" / "500ml" (amount).
   - card `1711:25029`: title **"대상 병해충"**, **stacked bar** — "A나방"
     8번 (`#38c284`), unlabeled (`#c8f468`), unlabeled (`#f7dc11`).
6. **"참참참의 코칭"** — same 4-card shape/copy as prior captures.
7. **Divider + "기록 내역 리스트"** — `arrow_forward_ios` + 3 inline preview
   rows, identical shape to prior captures.

## Confirmed matches

- **Findings 1–5 hold on this screen too** (icons, WorkType title font,
  metric label/value styling) — no regressions, 3rd reconfirmation.
- **Chart order matches code here** (unlike 물주기/비료 주기): code appends
  `관리 유형` (categoryDistribution) → `유형별 사용량 (unit)` →
  `관리 대상`, in that order
  ([ReportPresentationModels.swift:119-146](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)),
  and Figma shows the same 3-chart order (distribution-style chart first,
  target/count chart last). **So the chart-order issue found in 물주기/비료
  주기 is not universal — pestControl's code already has the right order.**
  Revise the "always wrong" framing from the fertilizing doc: it's
  workType-specific, not systemic to the append helpers themselves.
- **"총 살포량" metric title matches Figma exactly** (both say "총 살포량") —
  only its *value formatting* differs (see finding 3 below), not the title.
- Donut/stacked-bar colors match `Color.Chart` exactly, same as prior
  captures — no color bug.

## ⚠️ New findings (not fixed — documented only)

### 1. All 3 chart titles differ from Figma's copy (3rd workType in a row)
Code
([ReportPresentationModels.swift:119-146](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):
`"관리 유형"`, `"유형별 사용량 (\(unit))"`, `"관리 대상"`. Figma:
`"사용한 약제 종류"`, `"각 약제 사용량"`, `"대상 병해충"`. None match. This is
the same systemic chart-title drift already seen on 물주기 and 비료 주기 —
**3 for 3 workTypes captured so far have 100% chart-title mismatches**. This
looks less like a per-workType copy bug and more like the Figma-facing chart
titles were never synced with the implementation's placeholder strings at
all. Worth treating as one cross-cutting copy pass across all of
`ReportPresentationModels.swift`, not per-workType edits, once the full
capture pass is done.

### 2. Pesticide-amount metric title format differs from Figma
Code
([ReportPresentationModels.swift:107-112](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):

```swift
for amount in statistics.pesticideAmounts where amount.amount > 0 {
    metrics.append(ReportMetricPresentation(
        title: "농약 사용량 (\(amount.unit))",   // e.g. "농약 사용량 (mL)"
        value: ReportValueFormatter.value(amount.amount, unit: amount.unit)
    ))
}
```

Figma's card-2 (`1711:24992`) is titled **"총 농약 사용량"** — no unit
suffix in the title, and with a "총" (total) prefix the code doesn't add.
Same class of issue as the watering metric-title finding: a wording gap
between what the code emits per-record-unit and what Figma's simpler copy
shows.

### 3. "총 살포량" value unit mismatch: code hardcodes "L", Figma shows "ml"
Code
([ReportPresentationModels.swift:113-117](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):

```swift
if let total = statistics.totalSprayAmountLiters, total > 0 {
    metrics.append(ReportMetricPresentation(
        title: "총 살포량",
        value: ReportValueFormatter.value(total, unit: "L")   // always "L"
    ))
}
```

`ReportValueFormatter.value` just concatenates the number and unit string
directly ([ReportChartModel.swift:89-98](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift)) —
so for `total == 5` this renders `"5L"`. Figma's card-3 shows **"5000ml"**.
Either the field is genuinely liters and Figma's copy should say `"5L"`
(design used a mock value in the wrong unit), or `totalSprayAmountLiters`'s
actual backend semantics are already milliliters despite the field/property
name saying "Liters" (a naming/unit bug upstream, not just a display one).
Flagging as an open question rather than assuming which side is wrong —
needs a look at the DTO/backend contract, not a blind unit swap in
presentation code.

## Reconfirmed open items (not new)

- **Date-range separator** `-` vs `~` — 4th recurrence.
- **Inline record-row preview** — 4th recurrence, still a placeholder link.

## Summary of open findings (not yet fixed)

1. Chart titles ("관리 유형"/"유형별 사용량 (unit)"/"관리 대상" in code vs
   "사용한 약제 종류"/"각 약제 사용량"/"대상 병해충" in Figma) — 3rd
   consecutive workType with 100% chart-title mismatch; treat as a
   cross-cutting copy issue, not per-workType.
2. Pesticide-amount metric title format ("농약 사용량 (unit)" vs Figma's
   "총 농약 사용량") — same class as 물주기's metric-title finding.
3. "총 살포량" value unit: code always shows `L`, Figma shows `ml` for the
   same field — needs a DTO/backend check before touching, not a
   presentation-only fix.
4. (Reconfirmed, not new) Chart order for this workType is **correct** —
   contrast with 물주기/비료 주기 where it wasn't. Order issue is
   workType-specific, not universal.
5. (Reconfirmed, not new) Date-range separator `-` vs `~`.
6. (Reconfirmed, not new) Inline record-row preview scope gap.
