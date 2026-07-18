#Report Detail — Chart Format & Usage Spec

Captured 2026-07-18 via TalkToFigma (`join_channel("chamchamcham")` →
`get_selection` → `read_my_design` → `export_node_as_image`).

- Figma node: `1711:25330`, name `그래프 양식 및 사용 경우` ("Chart format and usage
  cases"), type `SECTION`, size 406×978.
- This is a **design-system reference section**, not a live app screen — it
  documents the two chart styles and their color rules for use across Report
  Detail statistics cards (e.g. 심기 방법, 모종 번식법 breakdowns).
- PNG export: inline-only (TalkToFigma/official MCP limitation, no disk save
  possible per `docs/figma/record/HANDOFF.md` Part 2). Structure/colors/text
  below are transcribed from `read_my_design` verbatim.

## Chart type 1 — Stacked Bar Chart

Node `1711:25331` (`Group 42`) inside card `1711:25332`.

- **Usage rule**: "항목이 3개 이하일 때 사용합니다. 비율이 가장 높은 항목과 값을
  그래프 안에 표시합니다." (Use when there are ≤3 categories. Show the
  highest-ratio item's label + value inside the bar.)
- Card: white bg `#ffffff`, border `#f3f3f3`, corner radius 12, width 350.
- Title row: label `SemiBold 15px` color `#4f4f4f` (`Text.subtle`) +
  trailing `icon/keyboard_arrow_down` (24×24) — same affordance as a
  collapsible section header.
- Graph frame: 310×81, corner radius 8, background `#c8f468` (lime/palette
  index 3) — this is the "remaining"/default segment color for stacked bars
  with only 2 visible segments in the sample.
- Segment 1 (`bar-1`, primary/highlighted, widest): fill `#38c284`
  (`Color.Chart.primary`). Contains inline text:
  - Label "씨앗 심기": `SemiBold 16px`, tracking -0.32, lineHeight 24, color
    `#ffffff` (`Text.inverse`).
  - Value "12번": `SemiBold 24px`, tracking -0.24, lineHeight 31.2, color
    `#ffffff`.
- Segment 2 (`bar-2`, remaining/unlabeled): fill `#c8f468`, no inline text (only
  the top/first segment gets the in-bar label+value per the usage rule).

## Chart type 2 — Half Donut Chart

Node `1711:25346` (`Group 41`) inside card `1711:25347`.

- **Usage rule**: "항목이 4개 이상일 때 사용합니다. 비율이 가장 높은 항목과 값을
  도넛 안에 표시합니다." (Use when there are ≥4 categories. Show the
  highest-ratio item's label + value inside the donut.)
- Card: same white/`#f3f3f3` border/corner-12 pattern, width 350.
- Title row: identical pattern to stacked bar ("진행한 모종 번식법" + dropdown
  chevron).
- Graph area 310×155 (half-donut, so bounding box height = radius).
- 6 `ELLIPSE` slices (`slice-1`…`slice-6`, each a full 310×310 circle clipped by
  the frame to show only the top half), colors in order:
  1. `slice-1` `#38c284` (primary — this sample's largest segment)
  2. `slice-2` `#a5e9b1`
  3. `slice-3` `#f7dc11`
  4. `slice-4` `#c8f468`
  5. `slice-5` `#81dacb`
  6. `slice-6` `#b1cbdf`
- Center label (`info` frame): highlighted/primary entry only —
  - Label "꺾꽂이": `SemiBold 20px`, tracking -0.2, lineHeight 26, color
    `#4f4f4f` (`Text.subtle`), center-aligned.
  - Value "12번": `SemiBold 28px`, tracking -0.28, lineHeight 36.4, color
    `#1a1a1a` (`Text.default`).

## Color palette rule (applies to both chart types, up to 6 categories)

Figma explicit note: "현재 도넛차트의 경우, 최대 6개의 항목이 존재한다는 가정 하에
다음과 같이 hex 값 구성해두었습니다." + "(각 색상의 순서는 관계 없으나, 가장 큰
그래프 비율의 색상은 `#38C284`로 지정해주시면 감사하겠습니다.)" — order among the
other 5 is free, but the **largest-ratio segment must always be `#38C284`**.

Declared swatches (legend text nodes, exact hex):

| # | Hex | 누적막대(stacked bar) legend | 도넛차트(donut) legend |
|---|---|---|---|
| 1 | `#38C284` | ✓ (primary/largest) | ✓ (primary/largest) |
| 2 | `#C8F468` | ✓ (remaining/default) | ✓ |
| 3 | `#A5E9B1` | — | ✓ |
| 4 | `#F7DC11` | — | ✓ |
| 5 | `#81DACB` | — | ✓ |
| 6 | `#B1CBDF` | — | ✓ |

## Comparison against current implementation

Chart logic and color tokens are **already implemented** —
[ReportChartModel.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift)
and
[ReportChartCard.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportChartCard.swift) —
and match the spec closely:

- ✅ Style selection rule: `style = normalized.count <= 3 ? .stackedBar : .semiDonut`
  ([ReportChartModel.swift:75](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift)) —
  matches "≤3 → stacked bar, ≥4 → donut" exactly.
- ✅ Primary/highlighted entry (highest ratio) rendered inside the bar/donut
  with label+value, same as Figma (`highlightedEntry`/`primary` in
  `ReportChartModel`, rendered by `ReportStackedBar`/`ReportSemiDonut` in
  `ReportChartCard.swift`).
- ✅ Palette order and values mostly match
  [Color+App.swift](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift)
  `Color.Chart.palette = [primary, green300, yellow, lime, turquoise, blue]`
  against Figma's `#38C284, #A5E9B1, #F7DC11, #C8F468, #81DACB, #B1CBDF` — same
  6 colors, same relative order except lime/yellow swapped in the middle
  (harmless, since "order among the 5 non-primary colors is free" per the
  Figma note).
- ⚠️ **Color bug**: `Color.Chart.turquoise` is defined as `Color(hex: 0x81DAD8)`
  but the Figma spec's `slice-5` is `#81DACB` — last byte `D8` vs `CB` (216 vs
  203), a one-value transcription error. This is a Foundation
  (`Core/DesignSystem/Foundation/Color+App.swift`) change, which per
  `frontend/AGENTS.md` requires explicit user authorization before editing.
  Flagging here rather than changing it directly.
- ➖ Not yet compared: exact donut ring thickness/radius, gap-between-segments
  sizing (code uses `lineWidth: 56`, `gap: min(1.5°, sweep/4)` — no Figma
  numeric radius/gap value was legible from this section's ellipse geometry
  since slices are full overlapping circles, not stroked arcs). Real
  per-workType Report Detail screens (not yet captured) will show the donut in
  actual context and are a better source for pixel-checking ring thickness.

## Open questions

1. ~~Should the `#81DACB` fix land now~~ — **done** (2026-07-18): user authorized
   fixing `Color.Chart.turquoise` from `0x81DAD8` to `0x81DACB` in
   [Color+App.swift](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Foundation/Color+App.swift)
   directly (applied in this worktree).
2. The stacked-bar sample only labels the primary segment; unlabeled trailing
   segments (like `bar-2` here) have no legend/tooltip in the collapsed state —
   current code's collapsed `ReportStackedBar` matches this (only
   `highlightedEntry` renders text), confirmed correct.

## Capture 2 — expand/collapse interaction, stacked bar

Node `1711:25383`, `그래프 자세히보기 과정 - 누적막대` ("Chart detail-view process —
stacked bar"), 406×449. Two `card` states stacked with a red arrow annotation
(collapsed → expanded), same "진행한 심기 방법" example as Capture 1.

- **Collapsed** (`1711:25402`, chevron `keyboard_arrow_down`): bar height 81,
  primary segment (`#38c284`) shows inline "씨앗 심기" / "12번" — identical to
  Capture 1.
- **Expanded** (`1711:25384`, chevron `keyboard_arrow_up`): bar height 56 (no
  inline text on either segment), followed by a `content` legend block —
  one row per entry, each row = 12×12 `color-swatch` (corner radius 2) +
  label + value:
  - Label ("씨앗 심기" / "모종 심기"): `SemiBold 16px`, tracking -0.32, lineHeight 24,
    color `#1a1a1a` (`Text.default`).
  - Value ("12번"): `Medium 16px`, tracking -0.32, lineHeight 24, color
    `#4f4f4f` (`Text.subtle`), right-aligned (`plot-number` frame is at the far
    right of the card).
  - Rows ordered by descending fraction (primary entry first), matching
    `ReportChartModel`'s `sorted` order.

### ✅ Fixed — legend label/value style swapped

[ReportChartCard.swift:87-95](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportChartCard.swift)
does the opposite of the spec above:

```swift
Text(entry.label)
    .appTypography(.bodyMedium)          // should be emphasized
    .foregroundStyle(Color.Text.subtle)  // should be Text.default
Text(entry.formattedValue)
    .appTypography(.bodyMediumEmphasized) // should be regular
    .foregroundStyle(Color.Text.default)  // should be Text.subtle
```

Figma: label = emphasized + dark (`Text.default`), value = regular + subtle
(`Text.subtle`). Code previously rendered label = regular + subtle, value =
emphasized + dark — inverted. **Fixed (2026-07-18, Tier 1-C of the report
detail remediation plan)**: swapped in
[ReportChartCard.swift:87-95](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportChartCard.swift)
to label = `.bodyMediumEmphasized` + `Color.Text.default`, value =
`.bodyMedium` + `Color.Text.subtle`.

## Capture 3 — expand/collapse interaction, half donut chart

Node `1711:25504`, `그래프 자세히보기 과정 - 도넛 차트`, 406×735. Same two-card
pattern, "진행한 모종 번식법" example, now with a realistic 6-category donut
(꺾꽂이/접붙이기/휘묻이/포기나누기/조직 배양/시판 구매 — matches
`WorkType.propagationMethod`-style categories, 12번 each in this mock).

- **Collapsed** (`1711:25547`, chevron down): center shows highlighted entry
  "꺾꽂이" / "12번" only, no legend. Same layout as Capture 1's donut sample.
- **Expanded** (`1711:25505`, chevron up): center label **"꺾꽂이" / "12번" is
  still shown** (unlike the stacked bar, which hides its inline text on
  expand) — the ring itself is unchanged, and a `description`/`legend` block
  is appended below with all 6 entries (swatch + `SemiBold 16px` `#1a1a1a`
  label + `Medium 16px` `#4f4f4f` value, right-aligned), same text-style rule
  as Capture 2's legend (and the same label/value swap bug applies here too,
  since both chart types share `ReportChartCard`'s `legend` view).
- Slice colors confirmed again, same 6-color order as Capture 1 (indices
  1→6 = `#38c284, #a5e9b1, #f7dc11, #c8f468, #81dacb, #b1cbdf`).

### ✅ Fixed — donut center label incorrectly hidden when expanded

[ReportChartCard.swift:57-71](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportChartCard.swift)
uses the same `model.highlightedEntry(isExpanded: isExpanded)` call for both
`.stackedBar` and `.semiDonut`, and `ReportChartModel.highlightedEntry`
([ReportChartModel.swift:40-42](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportChartModel.swift))
unconditionally returns `nil` when `isExpanded`. That's correct for the
stacked bar (inline text should disappear on expand, confirmed Capture 2) but
**wrong for the donut** — Figma Capture 3 shows the center label must persist
regardless of expand state. **Fixed (2026-07-18, Tier 1-D)**:
`ReportChartModel.highlightedEntry(isExpanded:)` now branches on `style` —
`.semiDonut` always returns `primary`, `.stackedBar` keeps the
`isExpanded ? nil : primary` behavior.

## Where this is used today

Both chart styles are **already wired into the live app**, not just a
component preview — `ReportChartCard(model: chart.model)` is rendered inside
the real `ReportDetailView.chartSection`
([ReportDetailView.swift:129-138](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Views/ReportDetailView.swift)),
driven by `ReportDetailPresentation.charts` computed from real
`FarmingWorkReportDetail` API statistics
([ReportPresentationModels.swift](../../../ChamChamCham/ChamChamCham/Features/Report/Presentation/Models/ReportPresentationModels.swift)).
`ReportChartCard.swift` itself has **no standalone `#Preview`** block (unlike
`AppCard.swift`/`AppChip.swift`), so the only way to see it today is to run
the app in the Simulator and open a Report Detail screen for a report whose
`workType` produces chart data (e.g. planting → 심기 방법/propagation charts).

## Summary of findings

1. ✅ Fixed (2026-07-18, Tier 1-C): `ReportChartCard` legend label/value
   typography+color swapped (Capture 2 & 3).
2. ✅ Fixed (2026-07-18, Tier 1-D): `ReportChartCard`/`ReportChartModel` donut
   center label now stays visible when expanded; only the stacked bar hides
   its inline text (Capture 3).
