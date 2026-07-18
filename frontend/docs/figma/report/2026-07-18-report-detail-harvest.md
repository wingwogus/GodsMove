#Report Detail — 수확 (Harvest) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:25123`, name
`수확 리포트`, type `FRAME`, size 390×1518. Seventh (last regular) Report
Detail workType capture, following 심기
([2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md),
findings 1–5 fixed in `3aa1457b`), 물주기, 비료 주기, 병해충 관리, 잡초 관리,
가지·순 정리 (see
[HANDOFF.md](HANDOFF.md)
for the full list). `.etc` (기타) still not captured — shares `.pruning`'s
empty `break` code path, so likely not needed (confirm with user).

Current implementation:
[ReportPresentationModels.swift:154-177](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
(`case .harvest`), backed by
[`HarvestReportStatistics`](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift)
(`totalAmountKg`, `averageAmountKg`, `amountCoverage`, `firstHarvestedOn`,
`lastHarvestedOn`, `medicinalParts: [ReportHarvestPartStatistics]`,
`finalGrowthPeriodMonths`, `growthPeriodRangeMonths: ReportGrowthPeriodRange?`
— note `ReportGrowthPeriodRange` is just `{minMonths, maxMonths}`, not a
per-bucket distribution).

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:25192`) — `arrow_back_ios_new` / `more_vert`. Matches.
2. **Badges + period row** — same shape, `-` (hyphen) date separator again.
3. **WorkType title** ("수확", `1711:25135`) — SemiBold 28px `#1a1a1a`.
4. **Metric cards** (`1711:25136` `overview`, 2-up):
   - card-1: "총 작업 횟수" / "15회" (shared base metric).
   - card-2: **"총 수확량" / "500kg"** — matches code exactly
     ([ReportPresentationModels.swift:156-160](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift),
     title `"총 수확량"`, value from `totalAmountKg` with `kg` unit).
5. **"상세 정보" chart section** (`1711:25143`, **2 cards**):
   - card `1711:25145`: title **"수확 부위 종류"**, **donut** (4 slices:
     `#c8f468`, `#f7dc11`, `#a5e9b1`, `#38c284`), center "뿌리 줄기" (root/
     stem) / "12번".
   - card `1711:25158`: title **"재배 개월에 따른 수확량"** (harvest count
     by growth-month bucket), **stacked bar** — "24개월 때 수확" 2번
     (`#38c284`, width 180), unlabeled (`#c8f468`), unlabeled (`#f7dc11`).
6. **"참참참의 코칭"** — same 4-card shape/copy as prior captures.
7. **Divider + "기록 내역 리스트"** — `arrow_forward_ios` + 3 inline preview
   rows, identical shape to prior captures.

## Confirmed matches

- **Findings 1–5 hold** — 6th reconfirmation, no regressions.
- **Base + harvest-amount metric titles/values match code exactly** — "총
  수확량" is the one metric title that matches Figma verbatim across all
  workTypes captured so far (same class of clean match as fertilizing's
  "총 비료 사용량").
- **Donut colors match `Color.Chart`** exactly — no color bug.
- **First chart ("수확 부위 종류") maps cleanly to code's `medicinalParts`
  data** (recordCount-based donut) — just the title differs (see finding 1).

## Findings

### 1. Chart title mismatch (✅ fixed, Tier 1-A)
Code previously used
`Self.appendChart(title: "수확 부위", data: statistics.medicinalParts.map {...}, to: &charts)`.
Figma titles the same chart **"수확 부위 종류"**.

**Fixed (2026-07-18, report detail remediation plan Tier 1-A)**:
[ReportPresentationModels.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)
now uses `"수확 부위 종류"` verbatim.

### 2. Second Figma chart ("재배 개월에 따른 수확량") has no code/data-model equivalent at all (Tier 2 — documented only, not fixed)
This is a bigger gap than a title mismatch — there is **no chart-building
call for this in code, and no field in `HarvestReportStatistics` that could
back it**. The chart groups harvest records by growth-period bucket (e.g.
"24개월 때 수확" → count), but:

- `finalGrowthPeriodMonths: Int?` is a single scalar (the most recent/final
  growth period), not a per-bucket distribution.
- `growthPeriodRangeMonths: ReportGrowthPeriodRange?` is just
  `{minMonths, maxMonths}` — a range, not counts per bucket.

Neither can produce the 3-segment stacked-bar breakdown Figma shows. This
looks like a genuine **missing feature**, not a copy/title bug — building it
would need a new statistics field (something like
`growthPeriodDistribution: [ReportCountDistribution]`, mirroring
`WateringReportStatistics.amountDistribution`'s shape) added to the backend
response and DTO, not just a presentation-layer fix. Flagging for the
eventual remediation plan as a scope item, not a quick patch.

### 3. "재배 기간" metric not shown in this capture — inconclusive, not asserting a bug
Code has an optional 3rd metric
([ReportPresentationModels.swift:162-164](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)):
`if let months = statistics.finalGrowthPeriodMonths, months > 0 { ... title: "재배 기간" ... }`.
Figma's capture only shows 2 metric cards (총 작업 횟수, 총 수확량), no
"재배 기간" card. Could simply mean this mock's `finalGrowthPeriodMonths` is
nil/0 in the example data — not enough evidence to call this a mismatch.
Noting for awareness, not as a confirmed finding.

## Reconfirmed open items (not new)

- **Date-range separator** `-` vs `~` — 7th recurrence.
- **Inline record-row preview** — 7th recurrence, still a placeholder link.

## Summary of findings

1. ✅ Fixed (2026-07-18, Tier 1-A): chart title now "수확 부위 종류",
   matching Figma.
2. (Tier 2 — open, not fixed) **Missing chart/data**: "재배 개월에 따른
   수확량" has no code or domain model support at all — a real scope gap
   requiring a new statistics field (`growthPeriodDistribution:
   [ReportCountDistribution]`), not a presentation fix. Needs backend work.
3. (Inconclusive, not a confirmed finding) "재배 기간" metric didn't appear
   in this mock — may just be missing mock data, not a code bug.
4. (Reconfirmed, not new, Tier 3) Date-range separator `-` vs `~`.
5. (Reconfirmed, not new, Tier 3) Inline record-row preview scope gap.
