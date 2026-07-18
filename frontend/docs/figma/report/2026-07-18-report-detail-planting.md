#Report Detail — 심기 (Planting) Screen Capture

Captured 2026-07-18 via TalkToFigma. Figma node `1711:24746`, name `심기 리포트`,
type `FRAME`, size 390×1514. This is the first real Report Detail screen
capture — one screen shared across all `workType`s, with per-workType chart
sections varying (see
[2026-07-18-report-detail-chart-spec.md](2026-07-18-report-detail-chart-spec.md)
for the chart-only spec, already captured/checked separately).

Current implementation: `ReportDetailView.swift` +
`ReportMetricCard.swift` + `ReportChartCard.swift` (charts, already covered) +
`ReportCoachingSection.swift` (not yet compared here) +
`ReportRecordHistoryView.swift`.

## Structure (top to bottom) vs current code

1. **Top app bar** (`1711:24816`) — no title, leading back icon, trailing
   `more_vert`. Code:
   [ReportDetailView.swift:24-30](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift).
2. **Badges + period row** (`1711:24748`) — crop badge "인삼" + farm badge
   "1번밭" + right-aligned date range. Code: `reportHeader`'s `ViewThatFits`
   ([ReportDetailView.swift:96-109](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)).
3. **WorkType title** ("심기") — `1711:24758`.
4. **Metric cards** (2-up grid) — `1711:24759` `overview`. Code:
   `LazyVGrid` of `ReportMetricCard`
   ([ReportDetailView.swift:115-125](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)).
5. **"상세 정보" chart section** — already covered by the chart spec doc;
   this capture reconfirms the same stacked-bar + donut cards/colors 1:1.
6. **"참참참의 코칭" (AI coaching) section** — 4 cards in this mock (잘한 점/이전
   리포트과의 비교/개선 필요점/추천 행동), not compared in detail here (separate
   component `ReportCoachingSection.swift`, out of scope for this pass).
7. **Divider**, then **"기록 내역 리스트"** section header + 3 preview rows with
   `mm/dd` + title + caption + 88×88 thumbnail, using a `list` instance
   component with a `#e0e0e0` bottom border (last row's border hidden).

## Confirmed matches

- **Badges**: crop badge (`인삼`, bg `#e6f7bf`/text `#27865c`) and farm badge
  (`1번밭`, bg `#f3f3f3`/text `#4f4f4f`) match
  `AppBadge(style: .solidPastel, variant: .primary)` /
  `AppBadge(style: .solidPastel, variant: .secondary)` exactly —
  [AppBadge.swift:51-66](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppBadge.swift)
  color mapping checked against the hex values above, no discrepancy.
- **Metric card layout**: 2-column grid, 12pt spacing, corner radius 12,
  background `#fafafa` (`Background.subtle`) — matches
  [ReportMetricCard.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportMetricCard.swift)
  exactly.
- **Section title style** ("상세 정보"/"참참참의 코칭"/"기록 내역 리스트"): Figma
  `24px SemiBold #4f4f4f` = `AppTypography.titleLargeEmphasized` (size 24,
  emphasized→semibold, default color `Text.subtle`) — matches
  `sectionTitle`/`historyLink` text usage exactly.
- **Trailing top-bar icon** `more_vert` matches code exactly.

## ⚠️ New findings (not fixed — documented per today's "capture first" workflow)

### 1. Back-icon asset mismatch
Figma names the leading icon `icon/arrow_back_ios_new`
([1711:24816;290:6952;290:8662](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)),
but
[ReportDetailView.swift:28](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)
uses `.asset("chevron_backward")`. Both exist as separate assets in
`Assets.xcassets/icon/` (`arrow_back_ios_new.imageset` vs
`chevron_backward.imageset`) — per `frontend/AGENTS.md`'s icon-mapping rule,
the exact Figma-named asset should be used, not a semantically-close
substitute.

### 2. "기록 내역 리스트" header icon mismatch
Same class of issue: Figma names it `icon/arrow_forward_ios`, code
([ReportDetailView.swift:147](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift))
uses `.asset("chevron_forward")`. Both `arrow_forward_ios.imageset` and
`chevron_forward.imageset` exist separately in the asset catalog.

### 3. WorkType title uses the wrong typography token
Figma "심기" title (`1711:24758`): `SemiBold 28px`, tracking -0.28, lineHeight
36.4, color `#1a1a1a` — this is exactly `AppTypography.headlineMedium`.
[ReportDetailView.swift:111-113](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)
instead applies `.titleLargeEmphasized` (24px, tracking -0.24, lineHeight
31.2) — one size step too small. `Font+App.swift` confirms
`titleLargeEmphasized.size == 24` vs `headlineMedium.size == 28`.

### 4. `ReportMetricCard` label weight + value color both wrong
[ReportMetricCard.swift:15-23](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportMetricCard.swift):

```swift
Text(metric.title)
    .appTypography(.labelMedium)       // Figma wants labelMediumEmphasized (SemiBold)
    .foregroundStyle(Color.Text.muted) // ✅ correct (#878787)

Text(metric.value)
    .appTypography(.titleMediumEmphasized) // ✅ correct (20px SemiBold)
    .foregroundStyle(Color.Text.default)   // Figma wants Text.subtle (#4f4f4f), not #1a1a1a
```

Figma: label "총 작업 횟수"/"총 심은 양" = `SemiBold 15px` `#878787` (label
weight should be emphasized, i.e. `labelMediumEmphasized`, not the regular
`labelMedium`). Value "15회"/"1080g, 100주" = `SemiBold 20px` `#4f4f4f`
(`Text.subtle`, not `Text.default`).

### 5. Date-range separator differs from the List screen
This Detail screen's period text (`1711:24753`) is three separate nodes:
"2026. 12. 12." + "-" + "2026. 12. 14." — a plain hyphen. Current code
([ReportDetailView.swift:221-225](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift))
builds `"\(start) ~ \(end)"` — a tilde, matching the **List** screen's card
separator (confirmed correct there in the 2026-07-18 List View pass) but not
this Detail screen's Figma capture. Flagging as an open question rather than
an assumed bug — worth confirming with the designer whether List (`~`) and
Detail (`-`) are intentionally different or one of the two Figma frames has
drifted.

### 6. "기록 내역 리스트" — Figma shows an inline record-row preview; app only has a link
Figma's `record-list` section (`1711:24808`) renders the section header PLUS
3 inline preview rows (`mm/dd` + title + caption + 88×88 thumbnail, `list`
component instances) directly on the Report Detail screen. Current
`historyLink`
([ReportDetailView.swift:140-158](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift))
is just a single tappable row ("기록 내역 리스트" + chevron) that pushes
`ReportRecordHistoryView`, which itself is a **placeholder** — its
presentation
([ReportPresentationModels.swift:352-355](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift))
just shows "이 리포트에 포함된 기록을 불러오는 기능을 준비하고 있어요."
("we're preparing the feature to load records included in this report"). This
isn't a small styling bug — it's a real scope gap: the Figma spec expects an
inline record-row preview embedded in the detail screen (with a real API to
back it), and the current app defers that entirely to an unbuilt follow-up
screen.

## Summary of open findings (not yet fixed)

1. Back icon: `chevron_backward` → should be `arrow_back_ios_new`.
2. History-row icon: `chevron_forward` → should be `arrow_forward_ios`.
3. WorkType title: `.titleLargeEmphasized` → should be `.headlineMedium`.
4. `ReportMetricCard` label: `.labelMedium` → should be `.labelMediumEmphasized`.
5. `ReportMetricCard` value color: `Color.Text.default` → should be `Color.Text.subtle`.
6. Detail-screen period separator "~" vs Figma's "-" — open question, not an
   assumed bug (may be an intentional List vs Detail difference).
7. Inline record-row preview in "기록 내역 리스트" — unimplemented product
   scope, not a style fix; `ReportRecordHistoryView` is currently a
   placeholder.
