# Figma Capture: Community Compose Required Complete

- Captured at: 2026-07-08
- Source: TalkToFigma MCP `read_my_design`
- Figma node: `631:7819`
- Frame name: `게시물 작성 / 필수값 입력 완료`
- Frame size: `390 x 1530`
- Purpose: Community post compose screen after required fields are filled and
  the submit button is enabled.

## Screen Structure

This state uses the same compose screen structure as `게시물 작성 / default`, but
the content is taller because the body text field contains long text.

Content order:

1. Status bar template: `54pt` high.
2. Top app bar: `60pt` high.
3. Crop board section header: `24pt`.
4. Crop chip picker row: `48pt`.
5. Compose content area: `1180pt`.
6. Bottom fixed submit area: `100pt`.

Implementation notes:

- Do not duplicate the status bar.
- The main form content must scroll.
- The bottom submit button should be safe-area-aware and stay reachable when
  the keyboard is open.
- This state is much taller than an iPhone viewport, so fixed-height layouts
  will fail on iPhone 13 and SE alike.

## Top App Bar

- Bounds: `390 x 60`.
- Background: `#ffffff`.
- Title: `게시물 작성하기`
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

## Crop Board Section

Header:

- Bounds: `390 x 24`.
- Leading inset: `20`.
- Label: `작물 게시판`
  - Font: Pretendard Medium `16`.
  - Color: `#1a1a1a`.
  - Line height: `24`.
- Required marker:
  - Text: `*`.
  - Font: Pretendard Medium `16`.
  - Color: `#ef4444`.

Chip row:

- Bounds: `390 x 48`.
- Left fixed add area:
  - Area: `60 x 48`.
  - Button: `32 x 32`, white fill `#ffffff`, border `#e0e0e0`.
  - Icon: `icon/add`, `24 x 24`.
  - Divider line: `24pt` high, color `#e0e0e0`.
- First crop chip is selected:
  - Size shown: `104 x 32`.
  - Fill: `#343434`.
  - Text: `작물 카테고리`.
  - Text color: `#ffffff`.
  - Font: Pretendard Medium `15`.
  - Line height: `19.5`.
- Following crop chips are unselected:
  - Fill: `#f3f3f3`.
  - Text color: `#4f4f4f`.
  - Font: Pretendard Medium `15`.
  - Line height: `19.5`.

## Text Area

- Container:
  - Bounds: `350 x 692`.
  - X inset: `20`.
  - Fill: `#fafafa`.
  - Corner radius: `12`.
- Internal horizontal inset: `20`.
- Title field area:
  - Bounds: `310 x 38`.
  - Text: `제목 입력 완료`.
  - Font: Pretendard Medium `20`.
  - Color: `#1a1a1a`.
  - Line height: `26`.
  - Bottom divider line: `#e0e0e0`.
- Body field area:
  - Bounds: `310 x 598`.
  - Text: long multi-paragraph sample body.
  - Font: Pretendard Medium `18`.
  - Color: `#4f4f4f`.
  - Line height: `27`.
- Character counter:
  - Text in frame: `0/500`.
  - Aligned trailing.
  - Font: Pretendard Medium `15`.
  - Color: `#878787`.
  - Line height: `19.5`.

Implementation note: the Figma filled-state sample still shows the counter as
`0/500`. Product implementation should use the actual current body length while
preserving this visual style.

## Divider After Text Area

- Bounds: `390 x 2`.
- Fill: `#f3f3f3`.

## Farming Record Attachment

Section bounds: `390 x 204`.

Header:

- Horizontal inset: `20`.
- Label: `영농 기록 첨부하기`
  - Font: Pretendard Medium `16`.
  - Color: `#1a1a1a`.
  - Line height: `24`.
- Trailing icon:
  - `icon/chevron_forward`.
  - Size: `24 x 24`.

Horizontal card list:

- Card list bounds: `370 x 168`, starts at x inset `20`.
- Card:
  - Size: `168 x 168`.
  - Fill: `#ffffff`.
  - Border: `#e0e0e0`.
  - Corner radius: `16`.
  - Internal inset: `12`.
- Card image:
  - Size: `144 x 84`.
  - Corner radius: `12`.
  - Image fill.
  - Top gradient shadow with date label.
- Date in image:
  - Text: `mm/dd`.
  - Font: Pretendard Medium `15`.
  - Color: `#ffffff`.
- Card title:
  - Example: `영농 활동` / `타이틀`.
  - Font: Pretendard SemiBold `20`.
  - Color: `#4f4f4f`.
  - Line height: `26`.
- Card captions:
  - Example: `작물 이름 · 필수 데이터?`.
  - Font: Pretendard Medium `15`.
  - Color: `#878787`.
  - Line height: `19.5`.

## Image Attachment

Section bounds: `390 x 132`.

Header:

- Label: `사진 첨부하기`.
- Horizontal inset: `20`.
- Font: Pretendard Medium `16`.
- Color: `#1a1a1a`.
- Line height: `24`.

Image list:

- Bounds: `370 x 96`.
- First uploader slot:
  - Size: `96 x 96`.
  - Fill: `#f3f3f3`.
  - Corner radius: `8`.
  - Icon: `icon/photo_camera`, `32 x 32`.
  - Count text: `0/10`.
  - Count font: Pretendard Medium `16`.
  - Count color: `#4f4f4f`.
  - Count line height: `24`.

## Question Toggle

- Row bounds: `390 x 28`.
- Horizontal inset: `20`.
- Label: `질문으로 올리기`
  - Font: Pretendard Medium `16`.
  - Color: `#1a1a1a`.
  - Line height: `24`.
- Toggle:
  - Size: `48 x 28`.
  - Off fill: `#e0e0e0`.
  - Knob: `24 x 24`.
  - Knob fill: `#ffffff`.
  - Knob position: left/off.

## Bottom Submit Area

- Bounds: `390 x 100`.
- Background: `#ffffff`.
- Top border/stroke: `#f3f3f3`.
- Button:
  - Bounds: `350 x 56`.
  - X inset: `20`.
  - Fill: `#38c284`.
  - Corner radius: `12`.
  - Label: `완료`.
  - Label font: Pretendard Medium `18`.
  - Label color: `#ffffff`.
  - Label line height: `27`.
- State: enabled after required values are complete.

## Implementation Notes

- This state maps to a valid `CommunityComposeViewModel` form:
  - A crop board is selected.
  - Title is non-empty.
  - Body is non-empty.
  - Submit button is enabled.
- The question toggle remains off, so the post type remains general unless the
  user toggles it on.
- Text area height should expand or scroll with content without breaking the
  form. The Figma sample uses a much taller text-area in the filled state.
- On SE-sized devices:
  - Use scrollable form content.
  - Keep the bottom submit area reachable.
  - Ensure long body text and keyboard interaction do not hide the active field.
