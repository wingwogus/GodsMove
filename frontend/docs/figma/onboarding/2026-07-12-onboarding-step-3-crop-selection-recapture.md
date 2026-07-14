# Figma Recapture: Onboarding Step 3 — Crop Selection

- Captured at: 2026-07-12
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`, `read_my_design`, `scan_text_nodes`
- Figma node: `631:12568` default state, `631:13342` selected state
- Frame name: `onboarding / step 3 재배작물 선택`
- Frame size: `390 x 844`

## Captured Screen Structure

1. Status bar template: `390 x 54` — app does not implement this.
2. Top app bar: `390 x 60`, leading back slot `48 x 48`.
3. Progress area: `390 x 20`.
   - Track: `350 x 4`, x inset `20`, color `#f3f3f3`.
   - Active fill: `176 x 4`, color `#38c284`.
4. Header: x inset `20`, width `350`, height `94`.
5. Search area: `390 x 88`.
   - Search bar: `370 x 56`, x inset `10`, y inset `16`.
   - Fill `#fafafa`, radius `8`.
   - Placeholder: `작물명을 입력해주세요.`
6. Category tab bar: `390 x 56`, horizontally scrollable.
   - Each tab item: `104 x 56`.
   - Selected tab: `전초`, text SemiBold `20`, active stroke `#38c284`.
   - Other captured labels: `뿌리·껍질`, `뿌리줄기`, `잎`, `꽃`, `열매·과실`, `종자`, `줄기·가지`, `기타`.
7. Crop list rows: `390 x 58`.
   - Row text: Pretendard SemiBold `20`, `#4f4f4f`.
   - Divider: `#e0e0e0`; final row divider hidden.
8. Bottom CTA: `390 x 100`.
   - Button: `350 x 56`, x inset `20`, top inset `12`.
   - Label: `완료`.
   - Default disabled fill `#e0e0e0`, label `#878787`.

## Selected Crop Bottom Tray

Selected-state node: `631:13342`.

- Bottom tray: `390 x 192`, y starts at screen y `652`.
- Tray top corners are rounded; tray overlays the crop list.
- Horizontal inset: `20`.
- Top padding to chip group: `16`.
- Chip group: `350 x 72`.
  - Row height: `32`.
  - Row gap: `8`.
  - First row has 3 chips; second row has 2 chips for the max-5 selection state.
  - Chip example size: `85 x 32`.
  - Chip fill: `#e4f8e3`, stroke `#38c284`, text `#27865c`.
  - Chip label: Pretendard Medium `15`, line height `19.5`.
- Chip group to CTA gap: `16`.
- CTA button: `350 x 56`, radius `12`, fill `#343434`, label `완료`.
- Bottom padding after CTA: `32`.

Implementation note: avoid adaptive grid spacing for selected chips; it spreads
the three chips too far apart compared with the captured fixed 8pt chip gap.
The bottom tray should cover the device bottom safe-area as a white surface so
the crop list never shows underneath the CTA.

## Header Text

- Title: `재배 중인 작물 설정하기`
- Subtitle:

```text
대표 재배지의 작물을 입력해주세요.
작물은 최대 5개까지 선택 가능합니다.
```

## Captured Text Styles

Read from Figma node `631:12568`; do not infer these values.

| Area | Text | Figma style | Figma color | Implementation note |
|---|---|---:|---:|---|
| Header title | `재배 중인 작물 설정하기` | Pretendard SemiBold 28, line-height 36.4, letter-spacing -0.28 | `#1a1a1a` | `AppTypography.headlineMediumEmphasized`, `Color.Text.default` |
| Header subtitle | `대표 재배지...` | Pretendard Medium 18, line-height 27, letter-spacing -0.36 | `#878787` | `AppTypography.bodyLarge`, `Color.Text.muted` |
| Search placeholder | `작물명을 입력해주세요.` | Pretendard Medium 18, line-height 27, letter-spacing -0.36 | `#878787` | `AppSearchBar` |
| Category selected | `전초` | Pretendard SemiBold 20, line-height 26, letter-spacing -0.2 | `#242428` | `AppTabBar`; repo color token for `#242428` was not found |
| Category unselected | `뿌리·껍질` etc. | Pretendard Medium 20, line-height 26, letter-spacing -0.2 | `#878787` | `AppTabBar`, `Color.Text.muted` |
| Crop row | `타이틀` | Pretendard SemiBold 20, line-height 26, letter-spacing -0.2 | `#4f4f4f` | `AppTypography.titleMediumEmphasized`, `Color.Text.subtle` |
| Bottom CTA | `완료` | Pretendard Medium 18, line-height 27, letter-spacing -0.36 | disabled `#878787`, enabled `#ffffff` | `OnboardingCTAButton` |

## Sticky Behavior

The captured hierarchy separates the search area and category tab bar directly above
the list. Runtime keeps both inside the pinned section header so they stay fixed
while the crop list scrolls.

## Open / Watch Item

The selected frame is named Step 3, but its progress active fill is `176 / 350`,
which equals `50%`, not `75%`.

Decision: this is a Figma visual mistake for Step 3. The implementation keeps
Step 3 progress at `0.75` based on the onboarding flow.
