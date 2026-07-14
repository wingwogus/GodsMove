# Figma Capture: Community Compose Record Attachment Picker States

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design` and `scan_text_nodes`
- Purpose: Record-attachment picker opened from `게시물 작성하기` by tapping
  `영농 기록 첨부하기` more/chevron.

## Selected Frames

The requested states were captured from these Figma frames:

- `631:9366` - `게시물 작성 / 영농 기록 첨부 더보기 탭 시 default`
- `631:9402` - `게시물 작성 / 영농 기록 첨부 더보기 탭 - 작물 카테고리 선택`
- `631:9384` - `게시물 작성 / 영농 기록 첨부 더보기 탭 - 검색 진행`
- `631:9420` - `게시물 작성 / 영농 기록 첨부 더보기 탭 - 영농 기록 선택`

All four frames are `390 x 844`.

## Shared Screen Structure

This is a full-screen picker, not a small modal.

Content order:

1. Status bar template: `54pt`.
2. Top app bar: `60pt`.
3. Search and chip filter area: `120pt`.
4. Record card list: starts below the filters and scrolls vertically.
5. Bottom fixed select area: `100pt`.

Implementation notes:

- Do not duplicate the status bar in SwiftUI.
- The card list must scroll behind or above a safe-area-aware bottom action.
- Keep a bottom inset so the final card is not hidden by the fixed select
  button.
- On iPhone SE, the title and search/filter area should remain visible without
  overlap; card list height should absorb the smaller viewport by scrolling.

## Top App Bar

- Bounds: `390 x 60`.
- Background: `#ffffff`.
- Title: `영농 기록 첨부하기`
  - Font: Pretendard SemiBold `28`.
  - Color: `#242428`.
  - Line height: `36.4`.
  - Center aligned.
- Leading icon:
  - `icon/arrow_back_ios_new`.
  - Icon visual bounds: `32 x 32`.
  - Slot: `48 x 48`.
- Trailing slot:
  - Empty `48 x 48` frame.

## Search And Filter Area

Container bounds: `390 x 120`.

Search bar:

- Bounds: `350 x 56`.
- X inset: `20`.
- Fill: `#fafafa`.
- Corner radius: `8`.
- Internal leading inset: `16`.
- Search icon: `icon/search`, `24 x 24`.
- Text starts after a `32pt` icon/text gap from the search-bar content frame.
- Text font: Pretendard Medium `18`.
- Line height: `27`.

Search text states:

- Empty/default placeholder: `어떤 기록을 올릴까요?`
  - Color: `#878787`.
- Search-in-progress text: `검색 키워드 입력 완료`
  - Color: `#1a1a1a`.

Chip list:

- Chip area bounds: `370 x 64`.
- Chip row height: `32`.
- First chip:
  - Label: `전체`.
  - Width: `50`.
- Crop chips:
  - Label: `작물 카테고리`.
  - Width: `104`.
- Chip gap: `8`.
- Selected chip:
  - Fill: `#343434`.
  - Label color: `#ffffff`.
- Unselected chip:
  - Fill: `#f3f3f3`.
  - Label color: `#4f4f4f`.
- Label font: Pretendard Medium `15`.
- Label line height: `19.5`.

## Record Card List

The record list contains repeated `350 x 180` cards with a `16pt` vertical gap.
The list frame starts below the filter area and is visually taller than the
available viewport, so implementation must scroll.

Default card:

- Bounds: `350 x 180`.
- X inset: `20`.
- Fill: `#ffffff`.
- Stroke: `#e0e0e0`.
- Corner radius: `16`.
- Internal padding: `20`.

Card header:

- Bounds: `310 x 32`.
- Left badge group width: `195`.
- Right date: `mm/dd`
  - Font: Pretendard Medium `15`.
  - Color: `#878787`.
  - Line height: `19.5`.
- Badge 1:
  - Label: `작물 카테고리`.
  - Size: `100 x 32`.
  - Fill: `#f3f3f3`.
  - Corner radius: `8`.
  - Label color: `#4f4f4f`.
- Badge 2:
  - Label: `필수 데이터`.
  - Size: `87 x 32`.
  - Fill: `#f3f3f3`.
  - Corner radius: `8`.
  - Label color: `#4f4f4f`.

Card content:

- Content row bounds: `310 x 96`.
- Text column: `202 x 96`.
- Thumbnail:
  - Size: `96 x 96`.
  - Corner radius: `8`.
  - Image fill.
- Title:
  - Text: `영농 활동 카테고리`.
  - Font: Pretendard SemiBold `24`.
  - Color: `#4f4f4f`.
  - Line height: `31.2`.
- Caption:
  - Figma text: `작성 내용은 최대 2줄입니다.`
  - Font: Pretendard Medium `18`.
  - Color: `#878787`.
  - Line height: `27`.
  - The text node contains repeated sample text, but its height is `54`, so
    implementation should clamp to two lines.

Selected card:

- Fill: `#e4f8e3`.
- Stroke: `#38c284`.
- Title color: `#1a1a1a`.
- Caption color: `#4f4f4f`.
- Badges:
  - Fill: `#ffffff`.
  - Label color: `#27865c`.
- Date remains `#878787`.

## Bottom Select Area

- Bounds: `390 x 100`.
- Background: `#ffffff`.
- Top stroke: `#f3f3f3`.
- Button:
  - Bounds: `350 x 56`.
  - X inset: `20`.
  - Corner radius: `12`.
  - Label: `선택`.
  - Label font: Pretendard Medium `18`.
  - Label line height: `27`.

Button states:

- Disabled:
  - Fill: `#e0e0e0`.
  - Label color: `#878787`.
- Enabled:
  - Fill: `#343434`.
  - Label color: `#ffffff`.

## Variant: Default

Figma node: `631:9366`

- Search field shows placeholder `어떤 기록을 올릴까요?`.
- `전체` chip is selected.
- Crop category chips are unselected.
- All visible record cards are unselected.
- Bottom `선택` button is disabled.

## Variant: Crop Category Selected

Figma node: `631:9402`

- Search field still shows placeholder `어떤 기록을 올릴까요?`.
- `전체` chip is unselected.
- First `작물 카테고리` chip is selected.
- Record cards are shown in default/unselected style.
- Bottom `선택` button is disabled.

## Variant: Search In Progress

Figma node: `631:9384`

- Search field shows `검색 키워드 입력 완료`.
- Search text color changes from placeholder gray to `#1a1a1a`.
- `전체` chip remains selected.
- Crop category chips are unselected.
- Record cards are shown in default/unselected style.
- Bottom `선택` button is disabled.

## Variant: Record Selected

Figma node: `631:9420`

- Search field shows placeholder `어떤 기록을 올릴까요?`.
- `전체` chip is unselected.
- First `작물 카테고리` chip is selected.
- First visible record card is selected:
  - Card fill `#e4f8e3`.
  - Card stroke `#38c284`.
  - Badges become white with green labels `#27865c`.
  - Title becomes `#1a1a1a`.
  - Caption becomes `#4f4f4f`.
- Bottom `선택` button is enabled:
  - Fill `#343434`.
  - Label color `#ffffff`.

## Implementation Notes

- Suggested state model:
  - `searchText`
  - `selectedCropCategoryID`
  - `selectedRecordID`
  - `canConfirmRecordSelection`
- `canConfirmRecordSelection` should be true only when `selectedRecordID` is
  non-nil.
- Filtering by search and crop category should not reset `selectedRecordID`
  unless the selected record is no longer in the filtered result set.
- If the record list is empty after filtering/search, use a proper empty state;
  this specific empty state was not included in the captured Figma frames.
- The picker is part of compose presentation. Avoid inventing new networking
  here; use the existing farm/record data source or local placeholder flow
  until the relevant API/data layer is confirmed.
- On iPhone SE:
  - Keep chip row horizontally scrollable.
  - Keep the fixed bottom action safe-area-aware.
  - Add bottom padding to the scroll view so the last card remains reachable.
  - Use two-line title/caption limits rather than fixed card internals that can
    overlap.
