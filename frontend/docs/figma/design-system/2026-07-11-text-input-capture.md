# Figma Capture: Design System Text Input

- Source: TalkToFigma MCP `read_my_design`
- Figma node: `290:7018`
- Node name: `text-input`
- Node type: `COMPONENT_SET`
- Captured at: 2026-07-11

## Variant Matrix

| Figma component | Node | Filled |
|---|---|---|
| `variant=default, filled=false` | `290:7019` | false |
| `variant=focus, filled=true` | `290:7040` | true |
| `variant=filled, filled=true` | `290:7048` | true |
| `variant=error, filled=false` | `290:7026` | false |
| `variant=error, filled=true` | `1012:13959` | true |
| `variant=disabeld, filled=false` | `290:7033` | false |

Figma now separates the `filled=true/false` property from the visual state variant.

## Shared Specs

| Part | Spec |
|---|---|
| Component width | 350 |
| Field height | 56 |
| Field corner radius | 8 |
| Field horizontal padding | 16 |
| Label typography | Pretendard Medium 16, line height 24 |
| Input typography | Pretendard Medium 18, line height 27 |
| Helper typography | Pretendard Medium 15, line height 19.5 |
| Default field | background `#FFFFFF`, border `#E0E0E0` |
| Focus field | border `#38C284`, clear icon shown when filled |
| Error field | border `#EF4444`, helper `#EF4444` |
| Disabled field | background `#F3F3F3`, border `#E0E0E0`, text `#ACACAC` |
| Empty input text | `#878787` |
| Filled input text | `#1A1A1A` |

## Code Comparison

Current code:

- `AppTextField` derives filled from `text.isEmpty`.
- `AppFieldContainer` derives focus/error/disabled chrome from `isFocused`, `isError`, and `isEnabled`.
- Error + filled is supported by combining `errorMessage != nil` with non-empty `text`.
- Focus + filled clear button is supported by `isFocused && !text.isEmpty && isEnabled`.

Observed difference:

- The code does not expose an explicit public `filled` property because runtime filled state is derived from the bound text.
- This is visually equivalent for `text-input`; no immediate implementation change is required unless the design system API itself should mirror Figma's separated `filled` property for previews or non-editable examples.
