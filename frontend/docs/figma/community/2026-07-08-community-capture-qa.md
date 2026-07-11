# Community Figma Capture QA

- Checked at: 2026-07-08
- Scope: saved captures in `docs/figma/community/`
- Purpose: implementation-readiness review before applying the community Figma
  screens to the SwiftUI presentation layer.

## Verdict

The captures are good enough to start implementation for:

- Community main feed filter states.
- Community compose default/filled/validation states.
- Compose record-attachment picker states.

They are not 100% complete for the full community product area because the
current capture set does not include post detail and edit/update screens.

Overall confidence:

- Community main feed: high for captured states, medium for missing default
  `일반 게시물 + 전체` state.
- Community compose: high for visual layout, medium for validation/runtime
  behavior.
- Record attachment picker: medium-high; the selected-record state was captured
  from MCP data, but the Figma plugin timed out when trying to restore the final
  multi-selection.
- Community detail/edit: low in this capture set because no current Figma
  capture document exists for those screens.

## Capture Coverage

| Area | Captured | Status |
| --- | --- | --- |
| Community main, general tab + crop selected | `2026-07-08-community-main-default-crop-selected.md` | Ready |
| Community main, Q&A tab + all crops | `2026-07-08-community-main-qna-selected.md` | Ready |
| Community main, Q&A tab + crop selected | `2026-07-08-community-main-qna-crop-selected.md` | Ready |
| Community main, general tab + all crops | Not found as a separate capture | Recapture recommended if this is a target state |
| Compose default | `2026-07-08-community-compose-default.md` | Ready |
| Compose required complete | `2026-07-08-community-compose-required-complete.md` | Ready with counter caveat |
| Compose all values complete | `2026-07-08-community-compose-complete-and-validation-diffs.md` | Ready with placeholder caveat |
| Compose title/body over-limit | `2026-07-08-community-compose-complete-and-validation-diffs.md` | Ready for validation styling |
| Compose record picker | `2026-07-08-community-compose-record-attachment-picker-states.md` | Ready with empty-state caveat |
| Community detail | No current capture doc | Recapture needed before pixel-target implementation |
| Community edit/update | No current capture doc | Recapture needed before pixel-target implementation |

## Evidence Limits

These are the places where the capture should not be treated as exact truth:

- Some MCP `read_my_design` outputs were long and partially truncated.
- The compose filled states show placeholder counters such as `0/500`,
  `500/500`, and `n/10`; runtime must compute actual counts.
- The record picker final selected state has enough MCP evidence for selected
  card and enabled button styling, but Figma selection restoration timed out
  after the read.
- Actual image assets were captured as image fills, not stable local assets.
- Auto Layout intent was inferred from bounds and repeated structures; it was
  not fully exported as design-system constraints.

## Missing Runtime States

The captured Figma set does not currently show:

- Community feed loading, empty, error, retry, pull-to-refresh, and pagination
  loading states.
- Search screen or notification screen from the main feed top app bar.
- Sort menu opened state.
- Crop picker/add-board flow opened from the main feed `+` chip.
- Compose keyboard-focused title/body states.
- Compose submitting state.
- Compose image upload failure state.
- Record picker no-result/empty state after filtering or search.
- Record picker search + crop filter combined state.
- Detail screen loading/error/comment-empty/comment-reply/comment-delete states.
- Edit screen default/validation/submitting states.

For MVP implementation, these can be handled with existing app patterns, but
they are not pixel-backed by the current Figma capture set.

## Existing Implementation Gaps

The current SwiftUI implementation already has community API/repository,
view models, feed, compose, and detail files, but several details diverge from
the captured Figma states:

- `CommunityComposeView` is currently a sheet with a top `등록` action. Figma
  shows a full-screen-style compose screen with a bottom fixed `완료` button.
- `CommunityComposeViewModel.titleLimit` is `50`, while Figma validation says
  title max is `30`.
- `CommunityComposeViewModel.maxImages` is `5`, while Figma image attachment
  count is `10`.
- Current title input truncates text at the limit. Figma shows over-limit text
  in red with an inline validation message and a disabled submit button.
- Current compose body does not appear to enforce/display the Figma `500`
  character limit as an over-limit validation state.
- Current farming record attach UI is a disabled placeholder because farm
  record data/API readiness is uncertain. Figma now includes a full picker and
  selected-record state.
- Current toggle label is `Q&A로 받기`; Figma label is `질문으로 올리기`.
- Detail presentation exists in code, but no matching current capture was saved
  in `docs/figma/community/`.

## Implementation Readiness

Ready to implement from captures:

- Main feed tab/chip/sort/list row/floating compose button visuals.
- Compose base form layout.
- Compose validation visuals for title/body limits.
- Compose optional selected-record/image/question states.
- Record picker base list/search/chip/selection states.

Use existing patterns or product judgment for:

- Loading/empty/error/retry states.
- Pull-to-refresh and pagination indicators.
- Record picker empty/no-result state.
- SE layout behavior.
- Actual image loading and placeholder assets.

Recapture before claiming full design parity:

- Community detail screen.
- Community edit/update screen.
- Main feed `일반 게시물 + 전체` if that state should be pixel-checked.
- Any Figma-opened menu/sheet states that designers expect to be exact, such as
  sort menu, search entry, crop picker, or image picker.

## SE And iOS 17+ QA Notes

Treat iPhone SE 2/3 as minimum usability QA, not pixel parity:

- Main feed chips must remain horizontally scrollable and keep the fixed `+`
  area tappable.
- Floating compose button must not cover the last visible row or bottom tab.
- Compose must be a scrollable form with safe-area-aware bottom actions.
- Compose title/body keyboard focus must not hide active fields or submit.
- Korean validation text plus counters may need a two-line layout on SE.
- Record picker needs bottom padding so the last card is not hidden behind the
  fixed `선택` button.

## Recommendation

Proceed with implementation only if the immediate scope is:

- Community main feed.
- Community compose.
- Record attachment picker shell/state.

Before implementing detail/edit parity, capture those Figma frames separately.
If time is tight, implement detail/edit using existing code patterns and mark
them as not yet Figma-final.
