#Report Detail — 가지·순 정리 (Pruning) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:25194`, name
`가지·순 정리 리포트`, type `FRAME`, size 390×1027 — the shortest Report
Detail frame so far. Sixth Report Detail workType capture, following 심기
([2026-07-18-report-detail-planting.md](2026-07-18-report-detail-planting.md),
findings 1–5 fixed in `3aa1457b`), 물주기
([2026-07-18-report-detail-watering.md](2026-07-18-report-detail-watering.md)),
비료 주기
([2026-07-18-report-detail-fertilizing.md](2026-07-18-report-detail-fertilizing.md)),
병해충 관리
([2026-07-18-report-detail-pest-control.md](2026-07-18-report-detail-pest-control.md)),
and 잡초 관리
([2026-07-18-report-detail-weeding.md](2026-07-18-report-detail-weeding.md)).

Current implementation:
[ReportPresentationModels.swift:179-180](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift):

```swift
case .pruning, .etc:
    break
```

`.pruning` (and `.etc`) intentionally add **no extra metrics and no charts**
beyond the shared base "총 작업 횟수" metric — there's no
`PruningReportStatistics` type in
[FarmingWorkReportStatistics.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Domain/FarmingWorkReportStatistics.swift)
at all.

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:25235`) — `arrow_back_ios_new` / `more_vert`. Matches.
2. **Badges + period row** — same shape, `-` (hyphen) date separator again.
3. **WorkType title** ("가지·순 정리", `1711:25206`) — SemiBold 28px `#1a1a1a`,
   text matches `WorkType.pruning.label` exactly (middle-dot included).
4. **Metric cards** (`1711:25207` `overview`) — **only 1 card, full width**:
   "총 작업 횟수" / "15회" (shared base metric only).
5. **No "상세 정보" / chart section at all** — the Figma frame jumps straight
   from `title` (metrics) to `ai` (coaching). There is no `details` child
   frame in this capture, unlike every other workType so far.
6. **"참참참의 코칭"** — same 4-card shape/copy as prior captures.
7. **Divider + "기록 내역 리스트"** — `arrow_forward_ios` + 3 inline preview
   rows, identical shape to prior captures.

## Confirmed matches — this workType is a clean match, no new findings

- **Findings 1–5 hold** — 5th reconfirmation, no regressions.
- **No metrics/charts beyond the base metric — Figma and code agree exactly.**
  Code's `case .pruning, .etc: break` produces nothing extra, and Figma's
  capture likewise has no metric-2 card and no "상세 정보" section at all.
  This is the first workType where there's genuinely nothing to compare
  beyond the shared chrome — no chart-title question can even arise since
  no chart exists on either side.
- Badges, date-separator styling (aside from the hyphen question), coaching
  cards, record-list — all match prior captures 1:1.

## Reconfirmed open items (not new)

- **Date-range separator** `-` vs `~` — 6th recurrence.
- **Inline record-row preview** — 6th recurrence, still a placeholder link.

## Summary of open findings (not yet fixed)

No new findings for this workType — it's a structural match end-to-end.

1. (Reconfirmed, not new) Date-range separator `-` vs `~`.
2. (Reconfirmed, not new) Inline record-row preview scope gap.
