# 검색 결과 / 나의 일지 탭

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection`(3개 프레임 동시 선택) →
  `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__scan_text_nodes` →
  `mcp__TalkToFigma__export_node_as_image` (per-frame). 결과 JSON이 커서(77,948자)
  서브에이전트로 슬라이스·대조 검증함 — 원본 저장 파일은 세션 로컬 tool-results에만
  있고 리포에는 없음.
- Figma node ID: `1547:23557`
- Frame name: `검색 결과 / 나의 일지 탭` (레이어 이름 정확함)
- Frame size: 390 × 844 (device viewport). 내부 `results` 391×1264
  (`number` 헤더 390×40 + `list` 391×1204)
- State: 검색 결과 상단 탭에서 "나의 일지" 탭을 직접 선택했을 때의 단일 카테고리 뷰.

## 기본(default, 전체 탭) 캡처와의 공통점

- 검색바(뒤로가기 + `icon/search` + placeholder "검색어 입력 완료") 동일하게 상단에 존재.
- `AppTabBar` 4탭 동일 구조로 유지, 이번엔 "나의 일지" 탭이 선택 상태(텍스트
  `#1a1a1a` SemiBold, 밑줄 `#38c284`).
- 리스트 아이템은 [검색 결과 default 캡처](2026-07-16-search-result-default.md)의
  record 섹션과 **완전히 동일한 `AppListItem.large` 패턴**(391×184, 썸네일 120×120,
  badge-1 pastel-primary `#e6f7bf`/`#27865c`, badge-2 `#f3f3f3`, 제목 SemiBold24
  `#4f4f4f`, 캡션 Medium18 `#878787`) — 신규 색상·컴포넌트 없음.

## 기본 캡처와 다른 점 (신규 관찰)

### 1. 섹션 헤더가 완전히 다른 패턴으로 교체됨

기본 캡처의 "타이틀 SemiBold24 + 개수 SemiBold24 + 더보기 화살표" 헤더 대신, 여기서는
전체 폭(390×40) `#fafafa`(`Color.Background.subtle`) 배경 띠 안에 **"총 12개" 텍스트
하나만**(Medium 16, `#878787`/Text.muted, 좌측 정렬) 존재. 타이틀도 없고 화살표 아이콘도
없음.

→ **제품 질문**: 단일 카테고리 탭에 진입하면 "더보기" 자체가 필요 없어서 사라진
의도적 설계인지, 아니면 미완성 placeholder 헤더인지 확인 필요.

### 2. 헤더 개수 텍스트가 실제 아이템 수와 불일치

헤더는 "총 12개"라고 표시하지만 **실제로 리스트에는 6개 아이템만 렌더링**돼 있음(전체
12개 중 6개만). 페이지네이션/무한 스크롤 어포던스는 이 프레임에 없음.

→ **제품 질문**: 실제 화면은 전체 12개를 페이지네이션/무한스크롤로 불러오는지,
6개가 한 페이지 크기인지 백엔드팀 확인 필요.

### 3. 구분선 규칙이 기본 캡처와 다르게(깨진 형태로) 나타남

기본 캡처에서는 "미리보기 마지막 아이템만 구분선 없음" 규칙이었지만, 이 프레임에서는
**아이템 1~2만 `#e0e0e0` 구분선이 있고, 3~6번 아이템은 전부 구분선 없음**(마지막
하나만이 아니라 4개 연속으로 없음). 3~6번 아이템은 서로 동일한 placeholder 콘텐츠라서
Figma 상에서 복제 후 border를 정리하지 않은 것으로 추정됨.

→ **이 프레임의 구분선 상태를 그대로 스펙으로 인코딩하지 말 것** — 기본 캡처에서
확인된 "마지막 아이템만 구분선 생략" 규칙을 유지하고, 이 프레임의 결측은 Figma
작성 실수로 간주 권장.

## 콘텐츠 (placeholder 상태)

| 행 | badge-1 | badge-2 | 비고 |
|---|---|---|---|
| 1 | 인삼 | 영농 활동별 데이터 | 실제 콘텐츠(기본 캡처와 동일) |
| 2 | 인삼 | 레이블 | placeholder |
| 3~6 | 율무 | 레이블 | 4개 모두 동일한 placeholder 복제본 |

## 색상 요약

기본 캡처의 색상 테이블에서 **신규 hex 없음**. `#fafafa` 헤더 배경은 이미 토큰화된
`Color.Background.subtle`의 새로운 사용처일 뿐(검색바 배경과 동일 토큰), 새 원시값
아님.

## 기존 디자인 시스템 컴포넌트 매핑

- 리스트 아이템: `AppListItem(size: .large, showsDivider: ...)` 그대로 재사용.
- 신규 "총 N개" 헤더 밴드: 승격된 컴포넌트 없음. `Text` + `Color.Background.subtle`
  배경으로 화면 내부 구현 권장(Figma Screen Implementation Rule 우선순위 3). Home의
  기존 리스트 헤더 패턴과 유사한 것이 있는지 대조 필요.

## 진행 상태

검색 결과 화면 2/4(나의 일지 탭). 관련 이슈는
[HANDOFF.md](HANDOFF.md) Part 5 "공통 관찰 사항"에 종합 정리.
