# 게시물 작성 / 내용 글자수 초과

- Recorded at: `2026-07-12 KST`
- Verification source: user-confirmed delta from the captured title-over-limit
  state
- Separate Figma capture: intentionally skipped by user direction
- Reference state: [게시물 작성 / 제목 글자수 초과](2026-07-12-community-compose-title-over-limit-recapture.md)

## Confirmed Delta

The body-over-limit state uses the same validation presentation and layout as
the captured title-over-limit state. The changed validation message is:

> 내용은 최대 500자까지 입력 가능합니다.

This sentence is a user-confirmed product/design specification. It was not read
from a separately selected Figma node, so the document must not be cited as
independent Cursor MCP capture evidence.

## Implementation Rule

- Show `제목은 최대 30자까지 입력 가능합니다.` when the title exceeds its
  limit.
- Show `내용은 최대 500자까지 입력 가능합니다.` when the body exceeds its
  limit.
- Apply the same error-message typography, color, placement, and disabled-submit
  behavior confirmed in the title-over-limit capture.
- Derive validation and counters from the actual input lengths rather than the
  placeholder values visible in Figma.
