# Figma Capture: Community Main Default, Crop Board Selected

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design`
- Figma node: `631:7721`
- Frame name: `커뮤니티 메인 / default, 특정 작물 게시판 선택`
- Frame size: `390 x 844`
- Purpose: Community main feed state where a specific crop board chip is selected.

## Screen Structure

This frame is an iPhone 13-sized community feed screen. It includes the iOS
status-bar template, app content, floating compose button, and Figma nav-bar
template. In SwiftUI implementation, the status bar and bottom app navigation
should be provided by the system/app shell rather than duplicated inside this
feature screen.

Content order:

1. Status bar template: `54pt` high.
2. Top app bar: `60pt` high, white background.
3. Post type tab bar: `56pt` high.
4. Crop chip row: `60pt` high, subtle background `#fafafa`.
5. Sort row: `48pt` high.
6. Post list rows: repeated `160pt` list cells.
7. Floating compose button: `72 x 72`, green fill.
8. Bottom nav-bar template: `72pt` high.

## Top App Bar

- Bounds: `390 x 60`, y after status bar.
- Background: `#ffffff`.
- Title: `커뮤니티`
  - Font: Pretendard Bold `32`
  - Color: `#242428`
  - Line height: `41.6`
  - Leading inset: `20`
- Trailing icons:
  - Search icon: `32 x 32` inside a `48 x 48` slot.
  - Notifications icon: `32 x 32` inside a `48 x 48` slot.
  - Trailing icon group width: `96`.

## Post Type Tabs

- Bounds: `390 x 56`.
- Background: `#ffffff`.
- Bottom stroke: `#f3f3f3`.
- Two equal-width tabs: `195 x 56`.
- Selected tab: `일반 게시물`
  - Text: Pretendard SemiBold `20`
  - Color: `#242428`
  - Active underline: `#38c284`, bottom stroke on selected item.
- Unselected tab: `Q&A 게시물`
  - Text: Pretendard Medium `20`
  - Color: `#878787`.

## Crop Chip Row

- Bounds: `390 x 60`.
- Background: `#fafafa`.
- Left fixed add area:
  - Area: `60 x 60`.
  - Button: `32 x 32`, white fill `#ffffff`, border `#e0e0e0`, full capsule/circle.
  - Icon: `icon/add`, `24 x 24`.
  - Right divider line: `24pt` high, color `#e0e0e0`.
- Scrollable chips start after the fixed add area.
- Chip height: `32`.
- Chip gap: `8`.
- `전체` chip is unselected in this state:
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
- Control size: `63 x 24`.
- Label: `최신순`
  - Font: Pretendard Medium `15`
  - Color: `#4f4f4f`
  - Line height: `19.5`
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
- Optional second badge exists in component instance, also `#f3f3f3`.

Main content:

- Bounds: `350 x 96`.
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
  - Like group: `44 x 24`, icon `24 x 24`, count text `nn`.
  - Comment group: `44 x 24`, icon `24 x 24`, count text `nn`.
  - Count font: Pretendard Medium `16`.
  - Count color: `#878787`.

## Floating Compose Button

- Bounds: `72 x 72`.
- Position: trailing, above nav template.
- Fill: `#38c284`.
- Shape: circle/capsule.
- Icon: `icon/edit`, `40 x 40`, centered with `16pt` inset.

## Bottom Nav Template

Figma includes a `390 x 72` nav-bar template:

- Items: `홈`, `영농 기록`, `정보 공유`, `프로필`.
- Selected item: `정보 공유`.
- Selected label color: `#242428`, Pretendard SemiBold `15`.
- Unselected label color: `#4f4f4f`, Pretendard Medium `15`.

Implementation note: keep using the app-level tab navigation rather than
rendering this nav bar inside `CommunityView`. Use this only for spacing and
selected-state reference.

## Implementation Notes

- This state differs from the default community main state only in the crop chip
  selection: `전체` is unselected and a crop chip is selected.
- Existing `CommunityFeedViewModel.selectedCropId` maps directly to this state.
- Use the same feed row component as the default state.
- Make sure SE-sized devices keep chip scrolling horizontal and do not shrink
  the fixed `+` area below a tappable size.
