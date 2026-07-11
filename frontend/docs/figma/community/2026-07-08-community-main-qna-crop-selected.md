# Figma Capture: Community Main Q&A, Crop Board Selected

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design`
- Figma node: `631:7749`
- Frame name: `커뮤니티 메인 / Q&A 게시물 선택, 특정 작물 게시판 선택`
- Frame size: `390 x 844`
- Purpose: Community main feed state where the Q&A post tab and a specific crop
  board chip are selected together.

## Screen Structure

This frame shares the same layout as the Q&A selected state:

1. Status bar template: `54pt` high.
2. Top app bar: `60pt` high.
3. Post type tab bar: `56pt` high.
4. Crop chip row: `60pt` high.
5. Sort row: `48pt` high.
6. Post list rows: repeated `160pt` list cells.
7. Floating compose button: `72 x 72`.
8. Bottom nav-bar template: `72pt` high.

Implementation note: status bar and bottom nav-bar are app-shell references and
should not be duplicated inside `CommunityView`.

## Top App Bar

- Background: `#ffffff`.
- Title: `커뮤니티`
  - Font: Pretendard Bold `32`.
  - Color: `#242428`.
  - Line height: `41.6`.
- Trailing icons:
  - Search icon: `32 x 32` inside a `48 x 48` slot.
  - Notifications icon: `32 x 32` inside a `48 x 48` slot.

## Post Type Tabs

- Bounds: `390 x 56`.
- `일반 게시물` is unselected:
  - Font: Pretendard Medium `20`.
  - Color: `#878787`.
- `Q&A 게시물` is selected:
  - Font: Pretendard SemiBold `20`.
  - Color: `#242428`.
  - Active underline/stroke: `#38c284`.

## Crop Chip Row

- Bounds: `390 x 60`.
- Background: `#fafafa`.
- Left fixed add area:
  - Area: `60 x 60`.
  - Button: `32 x 32`, white fill `#ffffff`, border `#e0e0e0`.
  - Icon: `icon/add`, `24 x 24`.
  - Divider line: `24pt` high, color `#e0e0e0`.
- `전체` chip is unselected:
  - Width: `50`.
  - Fill: `#ffffff`.
  - Border: `#f3f3f3`.
  - Text color: `#4f4f4f`.
  - Font: Pretendard Medium `15`, line height `19.5`.
- First crop chip is selected:
  - Width: `104`.
  - Fill: `#343434`.
  - Text: `작물 카테고리`.
  - Text color: `#ffffff`.
  - Font: Pretendard Medium `15`, line height `19.5`.
- Following crop chips are unselected:
  - Width: `104`.
  - Fill: `#ffffff`.
  - Border: `#f3f3f3`.
  - Text color: `#4f4f4f`.

## Sort Row

- Bounds: `390 x 48`.
- Sort control aligned trailing.
- Label: `최신순`
  - Font: Pretendard Medium `15`.
  - Color: `#4f4f4f`.
- Icon: `icon/keyboard_arrow_down`, `24 x 24`.

## Feed Row

Repeated list item:

- Bounds: `390 x 160`.
- Row stroke: `#e0e0e0`.
- Horizontal content inset: `20`.
- Header:
  - Bounds: `350 x 32`.
  - Date `mm/dd`, Pretendard Medium `15`, color `#878787`.
  - Crop badge fill `#f3f3f3`, corner radius `8`, label color `#4f4f4f`.
- Main content:
  - Left text/reaction area: `242 x 96`.
  - Right image: `96 x 96`, corner radius `8`.
  - Title: Pretendard SemiBold `24`, color `#4f4f4f`, one line.
  - Caption: Pretendard Medium `18`, color `#878787`, one line.
  - Reaction icons: `24 x 24`.
  - Reaction counts: Pretendard Medium `16`, color `#878787`.

## Floating Compose Button

- Bounds: `72 x 72`.
- Fill: `#38c284`.
- Shape: circle/capsule.
- Icon: `icon/edit`, `40 x 40`.

## Implementation Notes

- This state maps to:
  - `CommunityFeedViewModel.postType == .question`
  - `CommunityFeedViewModel.selectedCropId != nil`
- It should reuse the same row, sort, top app bar, and compose button as the
  other community main states.
- The API query should combine both filters: `postType=QUESTION` and `cropId`.
- Keep the chip row horizontally scrollable on SE-sized devices.
