# Community Compose Figma Implementation Design

## Goal

Replace the current community-compose wireframe presentation with the captured
Figma design while preserving the existing community architecture, API-ready
view model behavior, and strict reuse of the code design system.

This design covers two presentation surfaces:

1. `CommunityComposeView`, including the five captured or user-confirmed input
   states.
2. The full-screen farming-record picker opened from the compose screen.

## Sources Of Truth

The 2026-07-12 recaptures are authoritative for this implementation pass:

- [Compose default](../../figma/community/2026-07-12-community-compose-default-recapture.md)
- [Required values complete](../../figma/community/2026-07-12-community-compose-required-complete-recapture.md)
- [All values complete](../../figma/community/2026-07-12-community-compose-all-complete-recapture.md)
- [Title over limit](../../figma/community/2026-07-12-community-compose-title-over-limit-recapture.md)
- [Body over limit user-confirmed delta](../../figma/community/2026-07-12-community-compose-body-over-limit-user-confirmed.md)
- [Farming-record picker selected](../../figma/community/2026-07-12-community-compose-record-picker-selected-recapture.md)

When Figma placeholder counters disagree with the rendered content, runtime
values win. The app derives body and image counters from actual state.

## Global Constraints

- Do not modify `AppTopAppBar`; the screen continues using its current API.
- Ignore the bottom `TabView`; it is intentionally outside this implementation.
- Reuse `Core/DesignSystem` components, typography, colors, and spacing before
  adding feature-local presentation.
- Do not add raw equivalents of existing `Color+App.swift`, `Font+App.swift`,
  or `Spacing.swift` tokens.
- Do not implement the Figma status-bar template.
- Do not invent a farming-record API or submit synthetic preview record IDs.
- Preserve iPhone SE usability with scrollable content and safe-area-aware
  bottom actions.

## Design-System Change

Only `AppCard` receives a design-system behavior change.

### Selected card state

Add an opt-in `isSelected` input with a default value of `false`. Existing
callers therefore preserve their current rendering without source changes.

The selected state is implemented only for the two captured compositions:

- `.xsmall`, used by the compose screen's horizontal attached-record list.
- `.small`, used by the full-screen record picker.

`.medium` and `.large` keep their current rendering because no selected state
for those sizes was captured. The component must not infer their appearance.

The selected `.xsmall` and `.small` card maps the captured values to existing
semantic tokens:

| Element | Default | Selected |
|---|---|---|
| Card fill | `Color.Object.default` | `Color.Object.primarySubtle` |
| Card border | `Color.Border.default` | `Color.Border.primary` |
| Title | `Color.Text.subtle` | `Color.Text.default` |
| Caption | `Color.Text.muted` | `Color.Text.subtle` |
| Small-card badge fill | `Color.Object.muted` | `Color.Object.default` |
| Small-card badge text | `Color.Text.subtle` | `Color.Text.primary` |
| Date | `Color.Text.muted` | unchanged |

The selected badge treatment belongs to the `AppCard` selected composition.
It does not add an unverified standalone `AppBadge` variant.

### Small-card caption

Change only the `.small` card caption from one line to two lines. Its title
remains one line, and the card stays `350 × 180` with existing typography,
padding, thumbnail geometry, and corner radius.

### Explicit non-changes

Do not change the APIs or implementation of `AppTopAppBar`, `AppSearchBar`,
`AppChip`, `AppBadge`, `AppImageUploadSlot`, `AppToggle`, `AppDivider`,
`AppButton`, or any foundation token file.

## Compose Screen Structure

`CommunityComposeView` remains a full-screen composition with the existing
`AppTopAppBar`, a vertically scrolling form body, and a safe-area-inset bottom
submit action.

The body renders these sections in Figma order:

1. Required crop-board label and horizontal chip selector.
2. Combined title/body text area.
3. `AppDivider(size: .small)`.
4. Farming-record attachment section with horizontal `.xsmall` `AppCard`s.
5. Image attachment section with `AppImageUploadSlot` instances.
6. `AppDivider(size: .small)`.
7. Question row with `AppToggle`.

The bottom action uses `AppButton` rather than a feature-local button. The
enabled compose action uses the existing primary green variant; disabled state
comes from `.disabled(!viewModel.canSubmit)`.

## Compose State And Validation

The existing `CommunityComposeViewModel` remains the owner of crop selection,
title, body, question state, attachments, upload state, submit state, and API
errors.

Confirmed limits:

- Title: 30 characters.
- Body: 500 characters.
- Images: 5 attachments.

Over-limit input remains visible so the user can edit it. Input is not silently
truncated. Submission is disabled while either field is over its limit.

Validation copy:

- Title: `제목은 최대 30자까지 입력 가능합니다.`
- Body: `내용은 최대 500자까지 입력 가능합니다.`

The title text and helper message use `Color.Text.red` for a title error, while
the title divider remains `Color.Border.default`. The helper occupies the
bottom-left description slot, and the live body counter stays bottom-right.

Required values are crop board, non-whitespace title, and non-whitespace body.
Farming record, images, and the question toggle remain optional.

## Compose Design-System Replacements

Remove these feature-local reimplementations from `CommunityComposeView.swift`:

- `FarmingRecordCompactCard` in favor of `AppCard(size: .xsmall)`.
- `AppSwitch` and `AppSwitchToggleStyle` in favor of `AppToggle`.
- Raw section rectangles in favor of `AppDivider`.
- Raw submit button chrome in favor of `AppButton`.

Keep the combined title/body container feature-local because it is a one-off
screen composition and does not match the existing standalone `AppTextEditor`
contract. Its colors, typography, radii, and spacing still come exclusively
from the design-system foundations.

## Farming-Record Picker

The picker remains a `fullScreenCover`, not a sheet. It uses:

- The existing `AppTopAppBar` unchanged.
- `AppSearchBar` with placeholder `어떤 기록을 올릴까요?`.
- Horizontally scrolling `AppChip(style: .solid)` filters.
- Vertically scrolling `AppCard(size: .small)` rows.
- `AppButton(variant: .secondary, size: .medium, fullWidth: true)` for `선택`.

Selection is single-choice. The button is enabled only when a visible record
is selected. If search or crop filtering removes the selected record from the
result set, selection is cleared and the button becomes disabled.

The list scrolls behind a fixed, safe-area-aware 100pt bottom action region and
receives sufficient bottom content inset for its final card to move fully above
the button.

The current preview records remain presentation-only until a real farming-
record data source is confirmed. Selecting a preview updates the compose UI but
does not send its synthetic UUID as `farmingRecordId`.

## Image Attachment Behavior

Set `CommunityComposeViewModel.maxImages` to 5. The photo picker limit, upload
slot visibility, and live `count/5` label all derive from this constant.

Existing upload behavior remains unchanged:

- A local preview appears while upload is in progress.
- Submit is disabled during upload.
- Successful uploads submit server `mediaId` values.
- Failed uploads are removed and expose the existing retry-oriented error copy.

## Error And Empty States

- Repository submission errors continue to render below the scroll content via
  the existing `errorMessage` state.
- An empty record-filter result continues to use `EmptyStateView`.
- Board loading failure retains the current non-blocking empty-board behavior;
  the user can still open the crop picker.
- No new networking, loading protocol, or persistence abstraction is added.

## Accessibility And Small Devices

- Preserve horizontal scrolling for crop chips, compact record cards, and image
  attachments.
- Keep title/body and picker card lists vertically scrollable.
- Use safe-area insets for both bottom actions.
- Provide bottom scroll padding so the keyboard or action bar does not hide
  editable content or the final record card.
- Preserve two-line picker captions without fixed offsets or absolute screen
  positioning.

## Testing Strategy

Use Swift Testing and follow red-green-refactor.

1. Update `CommunityComposeViewModelValidationTests` first to require an image
   limit of 5 and verify the 30/500 validation boundaries and submit gating.
2. Extend `DesignSystemCaptureStyleTests` with selected/unselected `AppCard`
   semantic token assertions and the `.small` two-line caption rule.
3. Build the app after the component and screen changes.
4. Run the focused test suites, then the full test target when feasible.
5. Launch on an available iPhone 13-size simulator and an iPhone SE-size
   simulator, capturing visual evidence for default, completed, validation,
   and selected-record states.

## Delivery Sequence

1. Add and test the captured `AppCard` selected state.
2. Rebuild the compose screen using the existing design system.
3. Rebuild the farming-record picker using the existing design system.
4. Verify validation, attachments, selection, scrolling, and small-device
   behavior in tests and Simulator.

Each sequence item should remain reviewable on its own. Production code changes
must not include unrelated design-system or community refactoring.
