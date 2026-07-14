# Figma Capture: Community Main Q&A Selected

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design`
- Figma node: `631:7693`
- Frame name: `커뮤니티 메인 / Q&A 게시물 선택`
- Frame size: `390 x 844`
- Purpose: Community main feed state where the Q&A post tab is selected.

## Screen Structure

This frame shares the same overall community main feed structure as the default
state:

1. Status bar template: `54pt` high.
2. Top app bar: `60pt` high.
3. Post type tab bar: `56pt` high.
4. Crop chip row: `60pt` high.
5. Sort row: `48pt` high.
6. Post list rows: repeated `160pt` list cells.
7. Floating compose button: `72 x 72`.
8. Bottom nav-bar template: `72pt` high.

Implementation note: status bar and bottom nav-bar are Figma/device/app-shell
references. Do not duplicate them inside `CommunityView`.

## Top App Bar

- Background: `#ffffff`.
- Title: `커뮤니티`
  - Font: Pretendard Bold `32`.
  - Color: `#242428`.
  - Line height: `41.6`.
  - Leading inset: `20`.
- Trailing icons:
  - Search icon: `32 x 32` inside a `48 x 48` slot.
  - Notifications icon: `32 x 32` inside a `48 x 48` slot.

## Post Type Tabs

- Bounds: `390 x 56`.
- Two equal-width tabs: `195 x 56`.
- Background: `#ffffff`.
- Bottom stroke: `#f3f3f3`.
- Unselected tab: `일반 게시물`
  - Text: Pretendard Medium `20`.
  - Color: `#878787`.
- Selected tab: `Q&A 게시물`
  - Text: Pretendard SemiBold `20`.
  - Color: `#242428`.
  - Active underline/stroke: `#38c284`.

## Crop Chip Row

- Bounds: `390 x 60`.
- Background: `#fafafa`.
- Left fixed add area:
  - Area: `60 x 60`.
  - Button: `32 x 32`, white fill `#ffffff`, border `#e0e0e0`.
  - Icon: `icon/add`, `24 x 24`.
  - Right divider line: `24pt` high, color `#e0e0e0`.
- Default crop filtering state for this frame:
  - `전체` is selected.
  - Crop board chips are unselected.
- Selected `전체` chip:
  - Fill: `#343434`.
  - Text color: `#ffffff`.
  - Font: Pretendard Medium `15`, line height `19.5`.
- Unselected crop chips:
  - Fill: `#ffffff`.
  - Border: `#f3f3f3`.
  - Text color: `#4f4f4f`.
  - Font: Pretendard Medium `15`, line height `19.5`.

## Sort Row

- Bounds: `390 x 48`.
- Sort control aligned trailing.
- Label: `최신순`
  - Font: Pretendard Medium `15`.
  - Color: `#4f4f4f`.
  - Line height: `19.5`.
- Icon: `icon/keyboard_arrow_down`, `24 x 24`.

## Feed Row

Repeated list item:

- Bounds: `390 x 160`.
- Row stroke: `#e0e0e0`.
- Horizontal content inset: `20`.

Header:

- Bounds: `350 x 32`.
- Left badge group.
- Right date label.
- Date:
  - Text: `mm/dd`.
  - Font: Pretendard Medium `15`.
  - Color: `#878787`.
  - Line height: `19.5`.
- Crop badge:
  - Fill: `#f3f3f3`.
  - Corner radius: `8`.
  - Label: `작물 카테고리`.
  - Label color: `#4f4f4f`.
  - Font: Pretendard Medium `15`.
- Optional second badge slot exists in the list component instance.

Main content:

- Left text/reaction area: `242 x 96`.
- Right image: `96 x 96`, corner radius `8`, image fill.
- Title:
  - Text: `게시물 제목`.
  - Font: Pretendard SemiBold `24`.
  - Color: `#4f4f4f`.
  - Line height: `31.2`.
  - One line.
- Caption:
  - Text: `게시물 내용은 최대 1줄까지 제시합니다.`
  - Font: Pretendard Medium `18`.
  - Color: `#878787`.
  - Line height: `27`.
  - One line.
- Reaction row:
  - Height: `24`.
  - Like and comment icons: `24 x 24`.
  - Count font: Pretendard Medium `16`.
  - Count color: `#878787`.

## Floating Compose Button

- Bounds: `72 x 72`.
- Fill: `#38c284`.
- Shape: circle/capsule.
- Icon: `icon/edit`, `40 x 40`.

## Implementation Notes

- This state maps to `CommunityFeedViewModel.postType == .question`.
- This state should preserve `selectedCropId == nil` unless the user selected a
  crop board.
- It should reuse the same feed row component as the default and crop-selected
  states.
- The main implementation difference from default is the selected tab styling
  and the API query `postType=QUESTION`.
