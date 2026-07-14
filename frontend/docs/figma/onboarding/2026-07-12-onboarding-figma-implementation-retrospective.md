# Onboarding Figma Implementation Retrospective

- Date: 2026-07-12
- Scope: onboarding/auth flow Figma capture and SwiftUI implementation
- Source policy: use Figma MCP for UI details, Swagger/backend for API contracts, never Notion API specs

## What Went Wrong In This Session

### 1. Figma capture was treated too loosely at first

Some early implementation used approximate existing SwiftUI tokens/components without proving they matched the selected Figma frame.

The concrete failure mode:

- Figma had exact text styles such as Pretendard Medium/SemiBold, font size, line height, letter spacing, and colors.
- Code sometimes used nearby tokens such as `.font(...)` or a semantic color that looked close.
- This caused subtle but visible mismatches, especially in category tabs and selected chip text.

Correct rule going forward: do not infer typography or colors. Capture the Figma text node values first, then map to an existing design-system token only if it is verified.

### 2. Design-system components were not reused aggressively enough

The design-system folder already had components like `AppTabBar`, `AppSearchBar`, and `AppChip`.

The mistake:

- Reimplemented category tabs and chips directly inside `CropSelectionView`.
- That bypassed `.appTypography(...)`, so tracking and line-height from the design system were not applied.
- This made text weight/spacing subtly wrong even when font size looked close.

Correct rule going forward: check `Core/DesignSystem/Components` before custom UI. Use existing components first. Only write one-off layout code when the design-system component cannot express the state.

### 3. Visual screenshots were not compared state-by-state soon enough

The onboarding crop screen had multiple states:

- default / no selection
- selected rows
- selected bottom tray with chips
- sticky search + category bar after scroll
- loading/error/empty states

The initial pass over-focused on the default screen and under-checked the selected bottom tray. The mismatch became obvious only after capturing the selected-state Figma frame and reproducing the same state in Simulator.

Correct rule going forward: identify all visible states in Figma first, then verify each state against a matching Simulator screenshot.

### 4. Figma visual mistakes were not separated from implementation mistakes clearly enough

The Step 3 Figma progress bar visually showed 50%, but product flow says Step 3 should be 75%.

The mistake:

- The Figma value was initially followed too literally.
- The product decision was corrected later.

Correct rule going forward: record Figma/product conflicts immediately in markdown and make the chosen implementation source explicit.

Current decision:

- Step 3 progress implementation stays `0.75`.
- The 50% bar in that Figma frame is documented as a Figma visual mistake.

### 5. Temporary Simulator debug hooks had to be managed carefully

Temporary launch arguments were useful for reproducing exact states quickly, but they must never remain in production code.

Correct rule going forward:

- Temporary debug launch args are allowed only during verification.
- Remove them before final status.
- Run `rg "debug.*Selection|debug.*Onboarding"` or similar before finishing.

## Better Workflow For Future Figma → SwiftUI Screens

Use this exact sequence for future screens.

### Step 1. Capture the selected Figma frame precisely

Use Figma MCP on the selected frame:

1. `get_selection`
2. `read_my_design`
3. `scan_text_nodes`
4. `export_node_as_image`

Record:

- node id
- frame name
- frame size
- relevant state name
- screenshot/export evidence
- text styles
- colors
- dimensions and spacing
- ambiguous or conflicting details

### Step 2. Build a state matrix before coding

For each screen, list every state visible in Figma:

| State | Required? | Capture node | Runtime verification |
|---|---:|---|---|
| default | yes | Figma node id | Simulator screenshot |
| validation error | if shown | Figma node id | Simulator screenshot |
| selected/active | if shown | Figma node id | Simulator screenshot |
| disabled | if shown | Figma node id | Simulator screenshot |
| loading/error/empty | product-required | local implementation screenshot |
| sticky/scroll state | if scrollable | Figma node id or note | Simulator screenshot after scroll |

Do not call the screen done until every required state has either been implemented or explicitly deferred.

### Step 3. Map Figma values to design-system tokens, not guesses

For each text node:

| Figma value | Required code check |
|---|---|
| font family | confirm loaded font or fallback |
| font weight | match `AppTypography` weight |
| font size | match `AppTypography.size` |
| line height | use `.appTypography(...)`, not raw `.font(...)` |
| letter spacing | use `.appTypography(...)`, not raw `.font(...)` |
| color hex | match `Color+App.swift` token or record missing token |

If no existing token matches, do not silently approximate. Record one of:

- "existing token intentionally used despite Figma mismatch"
- "new token needed"
- "one-off style accepted for this screen"
- "unknown / needs designer confirmation"

### Step 4. Prefer existing components before one-off code

Check these first:

- `AppTopAppBar`
- `AppTabBar`
- `AppSearchBar`
- `AppButton` / `PrimaryButton` / `OnboardingCTAButton`
- `AppChip`
- `AppTextField`
- `AppDateField`
- `AppSegmentedControl`
- `AppImageUploadSlot`
- `AppListItem`

Only write custom UI when:

- the component cannot express the required state,
- the UI is clearly screen-specific,
- or the component would need a risky design-system-wide change.

### Step 5. Reproduce the same state in Simulator

For visual QA, use iPhone 17 Pro unless the user says otherwise.

Minimum screenshots:

- Figma export
- Simulator default state
- Simulator active/selected state
- Simulator scroll/sticky state when applicable

For temporary state reproduction:

- Use preview dependencies or temporary DEBUG launch args.
- Remove temporary hooks before final.
- Verify removal with `rg`.

### Step 6. Run verification before reporting completion

Required checks:

```bash
git diff --check
```

```bash
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Also verify:

- no temporary debug launch args remain,
- no Notion-derived API docs were referenced,
- no unrelated user changes were reverted,
- screenshots shown in the final answer are from the latest code state.

## Automation Candidates

### 1. Figma text-style audit markdown generator

Create a small script or MCP workflow that converts `scan_text_nodes` / `read_my_design` output into a markdown table:

| Node path | Text | Font | Weight | Size | Line height | Tracking | Color |
|---|---|---|---:|---:|---:|---:|---:|

This would make typography mismatches much easier to catch before coding.

### 2. Design-token comparison helper

Create a local script that compares captured Figma text/color values against:

- `Core/DesignSystem/Foundation/Font+App.swift`
- `Core/DesignSystem/Foundation/Color+App.swift`

Output should classify each value:

- exact token match
- close but not exact
- missing token
- unknown / cannot parse

Important: "close but not exact" should be treated as a review item, not an automatic match.

### 3. Reusable visual QA checklist template

Add a reusable markdown template for each Figma-backed screen:

```markdown
# Figma Screen Capture

## Nodes

## States

## Text Styles

## Color Tokens

## Component Mapping

## Product/Figma Conflicts

## Simulator Evidence

## Verification
```

This should be copied before implementing each new screen.

### 4. DEBUG-only screen state harness

Consider adding a reusable, explicitly DEBUG-only visual QA harness for onboarding screens.

Goal:

- quickly open a screen in a known state,
- inject preview data,
- avoid repeatedly patching temporary launch args,
- keep this out of release builds.

Guardrails:

- must be compiled only under `#if DEBUG`,
- must not alter production navigation,
- must have a test or grep check ensuring no ad-hoc debug flags are left behind.

This is useful, but should be added only if visual QA continues across many screens.

### 5. Snapshot metadata next to screenshots

When saving a screenshot path or image export, also record:

- exact Git branch / commit or dirty state,
- Figma node id,
- simulator device,
- launch state,
- date/time.

This avoids confusion when comparing old screenshots against newer code.

## Non-Negotiable Rules For The Next Session

1. Do not guess typography.
2. Do not guess colors.
3. Do not reimplement existing design-system components without checking them first.
4. Do not treat one Figma frame as the whole flow; capture states.
5. If Figma and product logic conflict, record the conflict in markdown immediately.
6. Use Swagger/backend for API contracts, not Notion.
7. Use iPhone 17 Pro for Simulator verification.
8. Show the final Simulator screenshot only after temporary debug code is removed or clearly state it was a temporary visual QA capture.
