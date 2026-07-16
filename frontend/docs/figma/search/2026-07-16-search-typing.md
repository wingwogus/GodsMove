# 검색 / 검색어 입력 중

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` →
  `mcp__TalkToFigma__scan_text_nodes` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1547:23494`
- Frame name: `검색 / 검색 중`
- Frame size: 390 × 844 (iPhone 13/14 프레임 + Dynamic Island status bar)
- State: 검색바에 텍스트 입력 중(자동완성/제안 목록 노출). 최근 검색어 섹션은 사라지고
  "동일 검색어" 1개 + "연관 검색어" 9개로 구성된 제안 리스트로 교체됨.

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환하는 클라이언트 한계 —
[record/HANDOFF.md](../record/HANDOFF.md) Part 1 참고). 인라인 캡처를 육안 검증했고,
구조·색상·타이포는 `read_my_design`(원본 JSON)으로 정밀 기록함.

## 레이아웃 구조 (top → bottom)

| 순서 | 노드 | 타입 | y | height | 설명 |
|---|---|---|---|---|---|
| 1 | `Status Bar` (`1547:23495`) | INSTANCE | -455 | 62 | 시스템 상태바, 9:41 |
| 2 | `search` (`1547:23507`) | FRAME | -393 | 56 | 뒤로가기 아이콘 + 검색창(입력값 있음 + clear 아이콘) |
| 3 | `Frame 1428752860` (`1547:23496`) | FRAME | -317 | 580 | 제안 리스트(`list` 인스턴스 10개) |

## `search` (`1547:23507`) — default/최근검색어 캡처와의 차이

- placeholder 프레임 텍스트가 "검색어를 입력해주세요."(미입력 회색)에서 실제 입력값
  "검색어 입력 중"으로 교체, **색상도 `#878787`(muted) → `#1a1a1a`(default)로 변경** —
  입력된 텍스트는 기본 텍스트 색으로 렌더링됨(placeholder 색과 다름, 당연한 동작이나
  Figma에서 실측 확인함)
- trailing에 `icon/cancel` 24×24 신규 노출 (X 클리어 버튼, 스크린샷상 원형 배경 진회색 +
  흰색 X) — `AppSearchBar`의 "trailing clear button that appears only while focused with
  text" 로직과 **정확히 일치**하는 상태(placeholder 문서에 이미 이 케이스로 기술돼 있음).
  단, 배경이 채워진 원형 아이콘인지 순수 `icon/cancel` 그대로인지는 이 read_my_design만으론
  ambiguous — asset 자체가 원형 배경 포함 SVG인지 확인 필요

## `Frame 1428752860` (`1547:23496`) — 제안 리스트, `list` 인스턴스 10개

각 `list` row: full-width(390) × height 58, border-bottom `#e0e0e0`
(`Color.Border.default`), 좌측 padding 20, 텍스트 세로 중앙 정렬(y 16, height 26).

| 순번 | node | 텍스트 | border-bottom |
|---|---|---|---|
| 1 | `1547:23497` | "동일 검색어" | 있음 |
| 2~9 | `1547:23498`~`1547:23505` | "연관 검색어" ×8 | 있음 |
| 10 | `1547:23506` | "연관 검색어" | **없음** (`strokes.visible: false` — 리스트 마지막 항목) |

텍스트 스타일 전체 공통: Pretendard SemiBold 20, letterSpacing -0.2, lineHeight 26,
color `#4f4f4f`(`Color.Text.subtle`) — "동일 검색어"와 "연관 검색어" 항목 간 폰트/색상
차이 없음(둘 다 placeholder 라벨로 보임, 실제로는 카테고리 라벨이 아니라 각 항목의
실제 검색어 텍스트가 들어갈 자리로 추정 — 아래 질문 참고).

리스트 아이템에 아이콘(검색 아이콘, 최근 검색 시계 아이콘 등)이나 우측 화살표는 없음 —
순수 텍스트 + 구분선 rows.

## 색상 요약

| 용도 | hex | 디자인 시스템 토큰 |
|---|---|---|
| 입력된 검색어 텍스트 | `#1a1a1a` | `Color.Text.default` |
| 제안 리스트 텍스트 | `#4f4f4f` | `Color.Text.subtle` |
| 리스트 구분선 | `#e0e0e0` | `Color.Border.default` |

## 기존 디자인 시스템 컴포넌트 매핑 후보

- **search-bar (입력됨 + clear 아이콘)** → `AppSearchBar` 그대로, 이미 이 상태를
  지원하도록 구현돼 있음(주석에 명시) — **재사용, 신규 작업 불필요**
- **제안 리스트 row** → 기존 컴포넌트 중 정확히 일치하는 것 없음. `AppListItem`은
  배지/썸네일 포함 대형 row라 과함, `AppSelectItem`은 chip형 박스라 불일치. 텍스트 1줄 +
  하단 구분선뿐인 단순 row라 **신규 디자인 시스템 컴포넌트 승격은 불필요**(Figma Screen
  Implementation Rule 우선순위 3: "일회성 레이아웃은 피처 화면 내부") — `Text` +
  `AppDivider` 조합으로 화면 내부 처리 권장. 단, `AppDivider`의 기본 색은
  `Color.Object.muted`(#f3f3f3)로 이 화면의 구분선 `#e0e0e0`(`Border.default`)과
  **불일치** — `AppDivider`에 색상 커스터마이즈 옵션이 없으므로 그대로 쓰면 살짝 연한
  구분선이 나감 (history-chip의 border 불일치와 같은 패턴, `#f3f3f3` vs `#e0e0e0` 혼용이
  이 검색 화면에서 반복 관찰됨)

## 제품/API 질문 (미결)

- "동일 검색어" vs "연관 검색어" 구분 기준 — 동일(exact match)은 입력값과 정확히 일치하는
  과거 검색어 1건, 연관(related)은 유사/추천 검색어로 추정되나 확정 필요
- 이 리스트가 로컬 최근 검색어 필터링 결과인지, 서버 자동완성 API 응답인지 — offline-first
  원칙상 로컬 우선이어야 하나 "연관 검색어"는 서버 추천일 가능성 있음
- 항목 탭 시 동작: 검색창에 텍스트 채우고 바로 검색 실행 vs 채우기만
- 최대 노출 개수(9개 연관 검색어가 상한인지, 스크롤로 더 있는지) — 프레임 높이 844 안에
  10개 row(580)가 다 들어가 스크롤 여부 불확실

## 진행 상태

**검색 진행 시 3개 캡처 완료** (default / 최근 검색어 존재 / 검색어 입력 중). 다음은
검색 결과 화면 4개.
