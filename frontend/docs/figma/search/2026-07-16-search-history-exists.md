# 검색 / default - 최근 검색어 존재 시

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` →
  `mcp__TalkToFigma__scan_text_nodes` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1547:23476`
- Frame name: `검색 / defalut -최근 검색어 존재 시`
- Frame size: 390 × 844 (iPhone 13/14 프레임 + Dynamic Island status bar)
- State: [검색 / default](2026-07-16-search-default.md)와 동일한 화면의 최근 검색어
  6건 상태. 검색어 미입력.

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환하는 클라이언트 한계 —
[record/HANDOFF.md](../record/HANDOFF.md) Part 1 참고). 인라인 캡처를 육안 검증했고,
구조·색상·타이포는 `read_my_design`(원본 JSON)으로 정밀 기록함.

## 레이아웃 구조 (top → bottom)

| 순서 | 노드 | 타입 | y | height | 설명 |
|---|---|---|---|---|---|
| 1 | `Status Bar` (`1547:23477`) | INSTANCE | -455 | 62 | 시스템 상태바, 9:41 |
| 2 | `search` (`1547:23491`) | FRAME | -393 | 56 | 뒤로가기 아이콘 + 검색창(`search-bar`) — [default](2026-07-16-search-default.md)와 동일 |
| 3 | `content` (`1547:23478`) | FRAME | -317 | 136 | `search-history` 헤더 + `history-chip` 그리드 |

default 캡처와 구조 차이는 `content` 프레임 안에 `history-chip`이 추가된 것뿐 —
`search`(검색바)와 `search-history` 헤더("최근 검색어" + "전체 삭제")는 완전히 동일.

## `history-chip` (`1547:23482`), height 72 — 2행 × 3열 wrap 그리드

- `row` 1 (`1547:23483`, y -253): chip 3개, x 시작 689 / 782 / 913 (다음 칩 시작 x =
  이전 칩 x + width + 8 간격)
- `row` 2 (`1547:23487`, y -213): chip 3개, 동일한 8 간격 규칙

각 chip 인스턴스 (예: `1547:23484`):
- fill `#ffffff`, stroke `#e0e0e0`, cornerRadius 1000(캡슐), height 32
- 레이블: Pretendard Medium 15, letterSpacing -0.3, lineHeight 19.5, color `#4f4f4f`,
  텍스트 길이에 따라 width 가변(85 vs 123 — "레이블"(3자) vs "레이블레이블"(6자))
- trailing `icon/close_small` 24×24 (검색어 삭제 버튼)

6개 chip 모두 placeholder 텍스트("레이블"/"레이블레이블")이며 실제 검색어로 교체될 자리.
Wrap 방향은 왼쪽 정렬, 한 행에 들어가는 개수는 텍스트 길이에 따라 유동적(1행: 3개 all fit,
2행: 3개 — 실제로는 콘텐츠에 따라 줄바꿈 개수가 달라질 것으로 추정, FlowLayout 필요).

## `AppChip`과의 비교 — 충돌 있음

`AppChip(trailingSystemImage: "icon/close_small", isSelected: false, style: .solidPastel)`로
근접 매핑되나, **border color가 다름**:

| | fill | border | 텍스트 색 |
|---|---|---|---|
| Figma history-chip | `#ffffff` | `#e0e0e0` (`Color.Border.default`) | `#4f4f4f` |
| `AppChip(.solidPastel, isSelected:false)` | `Color.Object.default`(#fff) 일치 | `Color.Border.subtle`(#f3f3f3) **불일치** | `Color.Text.subtle`(#4f4f4f) 일치 |

record 화면의 filter chip(`record/2026-07-13-record-main-default.md`)은 border
`#f3f3f3`로 `AppChip` 기본값과 정확히 일치했던 것과 달리, 이 최근 검색어 chip은
`#e0e0e0`(`Border.default`, 한 단계 진한 회색)를 쓴다 — 디자이너가 다른 톤을 의도한
것인지, 스포이드 오차인지 확인 필요. `AppChip`에 border variant 파라미터가 없으므로
그대로 재사용하면 살짝 연한 보더가 나간다.

## 아이콘 asset 확인

`icon/close_small`, `icon/arrow_back_ios_new` 모두 `Assets.xcassets/icon/`에 이미
존재 — 신규 asset 불필요.

## 기존 디자인 시스템 컴포넌트 매핑 후보

- **history-chip** → `AppChip(trailingSystemImage: .asset("close_small"), isSelected: false, style: .solidPastel)` —
  border color 불일치(위 충돌 항목) 제외하면 재사용 가능. Wrap 레이아웃은 SwiftUI
  `FlowLayout` 커스텀 필요(기존 디자인 시스템에 flow/wrap 컨테이너 있는지 먼저 확인)
- 나머지(`search`, `search-history` 헤더)는 [default 캡처](2026-07-16-search-default.md)와
  동일 — 재사용 컴포넌트 동일(`AppSearchBar`)

## 제품/API 질문 (미결)

- 최근 검색어 최대 개수 제한(6개가 예시인지 상한인지) — API/로컬 저장 스펙 확인 필요
- chip 탭 시 동작: 그 검색어로 재검색 실행 vs 검색창에 채우기만
- close 아이콘 탭 시: 개별 검색어 삭제, 이후 리스트 즉시 재정렬(wrap) 확인 필요
- 한 행에 들어가는 chip 개수가 유동적(FlowLayout)인지, 고정 3열 그리드인지 — 이 캡처만으로는
  텍스트 길이가 짧아 구분 어려움. 긴 검색어가 있는 상태 캡처가 없어 확정 불가

## 진행 상태

7개 캡처 예정 중 2/7 (검색 진행 시 3개 중 2번째). 나머지: 검색어 입력 중 1개 + 검색 결과
화면 4개.
