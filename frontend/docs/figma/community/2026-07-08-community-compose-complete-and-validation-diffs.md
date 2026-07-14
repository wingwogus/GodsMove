# Figma Capture: Community Compose Complete And Validation Diffs

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design` and `scan_text_nodes`
- Related base captures:
  - [게시물 작성 / default](2026-07-08-community-compose-default.md)
  - [게시물 작성 / 필수값 입력 완료](2026-07-08-community-compose-required-complete.md)
- Purpose: Diff-only capture for compose states that share the same base layout:
  all values complete, title over-limit, and body over-limit.

## Selected Frames

The Figma selection contained three frames:

- `631:7861` - `게시물 작성 / 전체값 입력 완료`
- `631:9778` - `게시물 작성 / 필수값 입력 완료`
  - Interpreted as `필수값 입력완료(제목 글자수 초과 시)` from its internal
    title and validation message.
- `631:9944` - `게시물 작성 / 필수값 입력 완료`
  - Interpreted as `필수값 입력완료(내용 글자수 초과 시)` from its internal
    body validation message.

All three frames use the same `390 x 1530` long compose layout as
`게시물 작성 / 필수값 입력 완료`.

## Shared Base

Use `2026-07-08-community-compose-required-complete.md` as the visual base:

- Crop board chip is selected.
- Title and body are filled.
- Text area is `350 x 692`, with the title row and body/counter area inside.
- Main content scrolls.
- Bottom submit area remains fixed/safe-area-aware in implementation.

Only the state differences below should be applied on top of the base.

## Variant: All Values Complete

Figma node: `631:7861`

This state represents a fully filled compose form, including optional values.

Differences from `필수값 입력 완료`:

- Farming record attachment:
  - First record card is selected.
  - Selected card fill: `#e4f8e3`.
  - Selected card stroke: `#38c284`.
  - Selected card title color: `#1a1a1a`.
  - Selected card caption color: `#4f4f4f`.
  - Other cards remain unselected with white fill and `#e0e0e0` stroke.
- Image attachment:
  - Uploader slot remains first.
  - Uploader count text in Figma: `n/10`.
  - Three selected image thumbnails follow the uploader.
  - Each selected image thumbnail is `96 x 96`, image-filled, corner radius `8`.
  - Each thumbnail has a cancel icon `icon/cancel`, `24 x 24`, positioned near
    the top-right.
- Question toggle:
  - Toggle is on.
  - Track fill: `#38c284`.
  - Knob fill: `#ffffff`.
  - Knob is positioned on the right.
- Bottom button:
  - Enabled.
  - Fill: `#38c284`.
  - Label color: `#ffffff`.

Implementation notes:

- Treat Figma's image count `n/10` as a placeholder. Runtime should show the
  actual selected image count, for example `3/10`.
- This state maps to `postType == question` because the question toggle is on.
- Selected farming records and selected images are optional values; they should
  not be required for submit unless business rules change.

## Variant: Title Over Limit

Figma node: `631:9778`

This state is named `게시물 작성 / 필수값 입력 완료` in Figma, but represents
`필수값 입력완료(제목 글자수 초과 시)`.

Text area differences:

- Title text:
  - Text: `제목 30자 글자 초과 시`
  - Font: Pretendard Medium `20`.
  - Color: `#ef4444`.
- Body text:
  - Same long filled body as the required-complete state.
  - Color remains `#4f4f4f`.
- Description/counter row:
  - Left error message: `제목은 최대 30자까지 입력 가능합니다.`
  - Error font: Pretendard Medium `15`.
  - Error color: `#ef4444`.
  - Right body counter: `0/500`.
  - Counter color: `#878787`.

Other state differences:

- Farming record cards are unselected:
  - Card fill: `#ffffff`.
  - Card stroke: `#e0e0e0`.
  - Title color: `#4f4f4f`.
  - Caption color: `#878787`.
- Image uploader is empty:
  - Count text: `0/10`.
- Question toggle is off:
  - Track fill: `#e0e0e0`.
  - Knob fill: `#ffffff`.
- Bottom button is disabled:
  - Fill: `#e0e0e0`.
  - Label color: `#878787`.

Validation rule:

- Title maximum length is `30`.
- If the title exceeds `30`, submit must be disabled even when required fields
  are otherwise complete.

## Variant: Body Over Limit

Figma node: `631:9944`

This state is named `게시물 작성 / 필수값 입력 완료` in Figma, but represents
`필수값 입력완료(내용 글자수 초과 시)`.

Text area differences:

- Title text:
  - Text: `제목 입력 완료`.
  - Color: `#1a1a1a`.
- Body text:
  - Same long filled body as the required-complete state.
  - Color remains `#4f4f4f`.
- Description/counter row:
  - Left error message: `내용은 최대 500자까지 입력 가능합니다.`
  - Error font: Pretendard Medium `15`.
  - Error color: `#ef4444`.
  - Right counter in Figma: `500/500`.
  - Counter color: `#ef4444`.

Other state differences:

- Farming record cards are unselected.
- Image uploader is empty with count `0/10`.
- Question toggle is off.
- Bottom button is disabled:
  - Fill: `#e0e0e0`.
  - Label color: `#878787`.

Validation rule:

- Body maximum length is `500`.
- If the body exceeds `500`, submit must be disabled even when required fields
  are otherwise complete.
- Figma's over-limit sample shows `500/500`; runtime should derive the counter
  from actual input length and apply the red error style when the value is over
  the allowed limit.

## Implementation Notes

- Model validation as separate flags:
  - `isTitleOverLimit`
  - `isBodyOverLimit`
  - `canSubmit`
- `canSubmit` should require:
  - selected crop board
  - non-empty title
  - non-empty body
  - title length within `30`
  - body length within `500`
  - not currently submitting
- Inline validation lives in the bottom row of the text content area, replacing
  the normal counter-only row.
- On iPhone SE width, the Korean error text plus trailing counter may not fit
  in one horizontal row. Prefer wrapping or a two-line helper/counter layout
  over shrinking text until it becomes hard to read.
- Keep the bottom submit button reachable when the keyboard is open.
- Keep farming-record cards and image thumbnails horizontally scrollable.
