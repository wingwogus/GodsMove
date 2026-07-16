# 검색 / default

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` →
  `mcp__TalkToFigma__scan_text_nodes` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1547:23510`
- Frame name: `검색 / defalut`
- Frame size: 390 × 844 (iPhone 13/14 프레임 + Dynamic Island status bar)
- State: 검색 화면 진입 직후(default) 상태. 검색어 미입력, 최근 검색어 리스트는 빈 상태(항목 0개,
  섹션 헤더 + "전체 삭제" 버튼만 노출).

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환하는 클라이언트 한계 —
[record/HANDOFF.md](../record/HANDOFF.md) Part 1 참고, 검색 화면도 동일 절차). 인라인 캡처를
육안 검증했고, 구조·색상·타이포는 `read_my_design`(원본 JSON)으로 정밀 기록함.

## 레이아웃 구조 (top → bottom)

| 순서 | 노드 | 타입 | y | height | 설명 |
|---|---|---|---|---|---|
| 1 | `Status Bar` (`1547:23511`) | INSTANCE | -455 | 62 | 시스템 상태바, 9:41 |
| 2 | `search` (`1547:23515`) | FRAME | -393 | 56 | 뒤로가기 아이콘 + 검색창(`search-bar`) |
| 3 | `search-history` (`1547:23512`) | FRAME | -317 | 48 | "최근 검색어" 타이틀 + "전체 삭제" 버튼 |

3번 아래로 리스트 아이템 노드 없음 — 최근 검색어 0건 상태로 확인됨(빈 리스트 UI는 Figma에
별도 표현 없이 헤더만 남은 형태).

## `search` (`1547:23515`), height 56

- `icon` (`1547:23516`, 32×32) → `icon/arrow_back_ios_new` — 뒤로가기
- `search-bar` (`1547:23517`, INSTANCE, 314×56, fill `#fafafa`, cornerRadius 8)
  - `icon/search` 24×24
  - placeholder 텍스트 "검색어를 입력해주세요." — Pretendard Medium 18, letterSpacing -0.36,
    lineHeight 27, color `#878787`

`AppSearchBar`([Core/DesignSystem/Components/AppSearchBar.swift](../../../ChamChamCham/ChamChamCham/Core/DesignSystem/Components/AppSearchBar.swift))와
**완전 일치** — fill `Color.Object.subtle`(#fafafa), cornerRadius 8, leading `icon/search`,
placeholder 문구까지 기본값과 동일. 신규 컴포넌트 불필요, 그대로 재사용.

## `search-history` (`1547:23512`), height 48

- "최근 검색어" (`1547:23513`) — Inter Medium 24, letterSpacing -0.72, lineHeight 36,
  color `#1a1a1a`
- `button` (`1547:23514`, INSTANCE) — fill/stroke 모두 `visible: false`(투명 배경, 텍스트만
  보이는 버튼). 레이블 "전체 삭제" — Pretendard Medium 16, letterSpacing -0.32, lineHeight 24,
  color `#4f4f4f`

## 색상 요약

| 용도 | hex | 디자인 시스템 토큰 후보 |
|---|---|---|
| 배경(전체) | `#ffffff` | `Color.Object.default` |
| 검색바 배경 | `#fafafa` | `Color.Object.subtle` |
| 검색바 placeholder | `#878787` | `Color.Text.muted` |
| "최근 검색어" 타이틀 | `#1a1a1a` | `Color.Text.default` (record 캡처에서 봤던 `#242428` 불일치 없음 — 이 화면은 정상 토큰) |
| "전체 삭제" 텍스트 | `#4f4f4f` | `Color.Text.subtle` |

## 기존 디자인 시스템 컴포넌트 매핑 후보

- **search-bar** → `AppSearchBar` — placeholder까지 기본값 일치, **그대로 재사용**
- **뒤로가기 아이콘** → `icon/arrow_back_ios_new`, `Assets.xcassets/icon/`에 존재 여부 확인 필요
  (다른 화면에서 이미 쓰는지 우선 확인)
- **"전체 삭제" 버튼** → 텍스트 전용 버튼, `AppButton`에 텍스트-only/ghost variant가 있는지 확인
  필요 (없으면 `Button { Text(...) }` 로컬 처리로 충분한 단순 케이스)
- **"최근 검색어" 타이틀** → 섹션 헤더, 다른 화면에 동일 스타일(Inter Medium 24) 텍스트 헤더가
  있는지 확인 필요 (커뮤니티/기록의 top-app-bar 타이틀과는 폰트 패밀리가 다름 — Inter vs Pretendard)

## 제품/API 질문 (미결)

- 최근 검색어 데이터 소스: 로컬 저장(SwiftData, offline-first 원칙)인지 서버 동기화인지 미확정
  — 검색 기능 자체가 로컬 검색(예: 커뮤니티 게시글 제목 검색)인지 서버 API 검색인지에 따라 갈림
- 최근 검색어 항목이 실제 있을 때의 리스트 아이템 UI(칩/태그 형태 vs 리스트 row)는 이 상태(0건)엔
  없음 — 다음 캡처("검색 진행 시" 2/3, 3/3)에서 확인 예정
- "전체 삭제" 탭 시 동작(즉시 삭제 vs 확인 다이얼로그) 미확정

## 진행 상태

7개 캡처 예정 중 1/7 (검색 진행 시 3개 중 1번째). 나머지: 검색 진행 시 2개 + 검색 결과 화면 4개.
사용자가 화면을 하나씩 순서대로 전달하는 방식으로 진행 중 — 모든 캡처 완료 전 SwiftUI 구현
시작하지 않는다([record/HANDOFF.md](../record/HANDOFF.md) Part 2/4 규칙과 동일).
