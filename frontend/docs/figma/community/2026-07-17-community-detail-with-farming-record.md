# Figma Capture: 커뮤니티 상세 / 게시물 내 영농일지 포함 시

- Captured at: `2026-07-17 KST`
- Cursor MCP channel: `chamchamcham`
- Source: TalkToFigma MCP `get_selection`, `read_my_design`, `export_node_as_image`
- Figma node: `1827:24924`
- Frame name: `커뮤니티 / 게시물 내 영농일지 포함 시`
- Frame size: `390 × 844`
- PNG: 디스크 저장 불가(Claude Code inline 반환). 필요 시 Figma에서 수동
  Export → `assets/2026-07-17-community-detail-with-farming-record.png`.

이 프레임은 커뮤니티 상세(post detail)의 최신 레이아웃이면서, 동시에
`2026-07-11-community-remaining-work.md`에서 P0로 남아있던 "커뮤니티 상세
Figma 캡처"를 겸한다. 즉 영농일지 카드뿐 아니라 상세 화면 전체 배치가 현재
코드와 다르다.

## Confirmed Screen Geometry

좌표는 프레임 상단(0) 기준.

| Area | Relative bounds | Notes |
|---|---:|---|
| Status bar template | `0…54` | device chrome; do not implement |
| `top-app-bar` | `54…114`, `390 × 60` | leading back(48 슬롯, 32 아이콘), **title 비어있음**, trailing 48 빈 슬롯 |
| Badge row | `130…162`, `350 × 32` | x inset 20, **본문 위쪽(최상단)** |
| Author(profile) | `178…226`, `350 × 48` | badge와 gap 16 |
| Title | `242…273`, height 31 | author와 gap 16 |
| Body | `281…335`, height 54(2줄) | title과 gap 8 |
| 영농일지 card | `351…519`, `350 × 168` | body와 gap 16 |
| Reaction row | `535…583`, `350 × 48` | card와 gap 16 |
| Divider | `599…601`, `390 × 2` | full-width `#f3f3f3` |
| (댓글 리스트 영역) | `601…756` | 이 프레임은 댓글 empty 상태 |
| Comment input | `756…844`, `390 × 88` | 하단 고정 입력 바 |

## Badge Row (본문 최상단)

- x inset 20, 배지 간 gap 8, 높이 32, corner radius 8.
- Q&A 배지: `50 × 32`, fill `#38c284`(**solid green**), 텍스트 `#ffffff`.
- 작물 배지(`인삼`): `48 × 32`, fill `#e6f7bf`(pastel), 텍스트 `#27865c`.
- 텍스트: Pretendard Medium 15, line height 19.5.
- **현재 코드는 두 배지 모두 `.solidPastel .secondary`로 렌더 → Q&A가
  solid green이어야 하는 스펙과 불일치.** 그리고 위치도 본문 아래가 아니라
  본문 위여야 한다.

## Author (profile)

- avatar `48 × 48`(원형), 이후 gap 8.
- info 컬럼(세로 스택):
  - 닉네임: `#1a1a1a`, Pretendard SemiBold 16, line height 24 (위).
  - `mm.dd`: `#878787`, Pretendard Medium 16, line height 24 (아래).
- **현재 코드는 `닉네임 · mm.dd`를 한 줄 inline으로 배치 → 스펙은 닉네임
  위 / 날짜 아래 2줄 세로 스택.**

## Title / Body

- Title: `#1a1a1a`, Pretendard Medium 24, line height 31.2.
  (현재 코드 `.titleLarge` 사용 — 값 확인 필요)
- Body: `#4f4f4f`, Pretendard Medium 18, line height 27. 예시는 2줄이지만
  본문은 길이에 관계없이 전체를 한 번에 표시(잘림 없음).

## 영농일지 Card (핵심)

- `350 × 168`, fill `#ffffff`, stroke `#e0e0e0`, corner radius 16, padding 20.
- **썸네일 이미지 없음.** picker의 `AppCard(.small)`(썸네일 96×96 + 배지 2개)
  와 다른 구성이다.
- 내부 구조: `VStack(spacing: 12)` — header(32) + content(84).
  - header(`310 × 32`):
    - 배지 `작물 카테고리`: `100 × 32`, fill `#f3f3f3`, 텍스트 `#4f4f4f`,
      Pretendard Medium 15, radius 8. (배지 1개만)
    - 우측 `mm.dd`: `#878787`, Medium 15, line height 19.5.
  - content(`310 × 84`, spacing 4):
    - 타이틀 `타이틀`: `#4f4f4f`, Pretendard SemiBold 20, line height 26.
    - 캡션: `#878787`, Pretendard Medium 18, line height 27, 최대 2줄(54pt).
- 이 카드는 unselected 스타일(흰 배경/회색 배지)만 존재. detail에서는 선택
  개념이 없으므로 자연스럽다.

### 컴포넌트 갭

`AppCard`의 모든 size는 `media`(썸네일 or placeholder)를 항상 렌더한다.
이 detail 영농일지 카드는 썸네일 없는 구성이라 현행 `AppCard` API에 바로
맞지 않는다. 구현 시:

- picker 카드(`.small` + 썸네일)와 detail 카드(썸네일 없음)를 동시에 만족하는
  `AppCard` 확장(예: 썸네일 optional)을 먼저 검토하거나,
- detail 전용 한 번 쓰는 카드로 feature-local 구현하되 색/폰트/spacing은
  design system 토큰만 사용.

## Reaction Row

- like: `icon/favorite_line` 24 + `24`(`#4f4f4f`, Medium 16). frame 48×48.
- comment: `icon/chat_bubble_line` 24 + `16`. like와 gap 12.
- 좋아요/댓글 수만 표시(공유 등 없음). 현재 코드와 대체로 일치.

## Comment Input (하단 고정)

- `390 × 88`, fill `#ffffff`, top stroke `#e0e0e0`.
- input-area `378 × 48`: `icon/photo_line`(48 슬롯) + placeholder
  `댓글을 입력해주세요.`(`#878787`, Medium 18) + 전송 버튼(48 원형,
  비활성 `#e0e0e0`, `icon/arrow_forward`).
- image-area `378 × 64`: 첨부 미리보기 슬롯(이 프레임은 비어있음).
- 현재 `AppCommentInput`으로 매핑되는 영역 — 별도 변경 필요 없음(확인만).

## Existing Design-System Mapping

| Figma element | Existing code mapping |
|---|---|
| `top-app-bar` | 현재 detail은 custom `header`(back + more_vert). Figma는 title 없는 back-only. **정렬 검토 필요** |
| Q&A 배지 | `AppBadge(style: .solid, variant: ?)` — solid green white text |
| 작물 배지(본문) | `AppBadge(style: .solidPastel, variant: .secondary)` |
| 영농일지 카드 | `AppCard` (썸네일 없는 변형 필요) 또는 feature-local |
| 카드 내 배지 | `AppBadge` 회색(`#f3f3f3`/`#4f4f4f`) |
| 댓글 입력 | `AppCommentInput` |
| colors/fonts/spacing | `Color+App.swift`, `Font+App.swift`, `Spacing.swift` |

## Implementation Guardrails

- 배지 행을 본문 최상단으로 이동, Q&A는 solid green으로.
- author를 닉네임/날짜 2줄 세로 스택으로.
- 영농일지 카드를 body와 reaction 사이에 삽입(`farmingRecordId != nil`일 때).
- 영농일지 카드 탭 시 동작(해당 기록 상세로 이동 등)은 이 프레임에 없음 →
  별도 확인 필요.
- status bar 미구현. Dynamic Type/ SE 사용성 유지.
