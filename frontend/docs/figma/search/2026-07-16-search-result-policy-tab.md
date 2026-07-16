# 검색 결과 / 정책 정보 탭

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection`(3개 프레임 동시 선택) →
  `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__scan_text_nodes` →
  `mcp__TalkToFigma__export_node_as_image` (per-frame), 서브에이전트로 대용량 JSON
  슬라이스·대조 검증.
- Figma node ID: `1547:23573`
- Frame name: `검색 결과 / 정책 정보 탭` (레이어 이름 정확함)
- Frame size: 390 × 844 (device viewport). 내부 `results` 391×796
  (`number` 헤더 390×40 + `list` 390×736)
- State: 검색 결과 상단 탭에서 "정책 정보" 탭을 직접 선택했을 때의 단일 카테고리 뷰.

## 기본(default, 전체 탭) 캡처와의 공통점

- 검색바(뒤로가기 + `icon/search` + placeholder) 동일.
- `AppTabBar` 4탭 유지, "정책 정보" 탭 선택 상태(텍스트 `#1a1a1a` SemiBold, 밑줄
  `#38c284`) — 탭 선택 표시가 콘텐츠와 정확히 일치(아래 게시글 탭 캡처와 대조되는
  포인트).
- 리스트 아이템은 [검색 결과 default 캡처](2026-07-16-search-result-default.md)의
  policy 섹션과 **완전히 동일한 `AppListItem.xlarge` 패턴**(390×169, 썸네일 없음,
  제목 SemiBold24 `#1a1a1a`, 기관 Medium16 `#4f4f4f`, info 라벨 `#878787`/값
  `#1a1a1a`) — 신규 색상·컴포넌트 없음.
- 구분선 규칙도 기본 캡처와 **정확히 일치**: 1~3번 아이템에 `#e0e0e0` 구분선, 마지막
  4번째만 없음 (나의 일지 탭 캡처에서 관찰된 "구분선 규칙 붕괴"가 여기서는 재현되지
  않음 — 정상 케이스로 참고).

## 기본 캡처와 다른 점 (신규 관찰)

### 1. 섹션 헤더 — 나의 일지 탭과 동일한 새 패턴

전체 폭(390×40) `#fafafa` 배경 띠 + "총 12개" 텍스트(Medium16, `#878787`)만 존재,
타이틀/화살표 없음. [나의 일지 탭 캡처](2026-07-16-search-result-record-tab.md)와
동일한 패턴 — 단일 카테고리 탭 공통 헤더로 확정할 수 있음(제품 확인은 여전히 필요).

### 2. 헤더 개수 텍스트가 확실히 잘못됨

헤더는 "총 12개"라고 표시하지만, 기본(전체) 탭 캡처에서 이미 "정책 정보 **7**"로
확정된 카테고리다. 실제 렌더링된 아이템도 **4개뿐**(12도 7도 아님). 나의 일지 탭도
동일하게 "총 12개"를 표시했던 것과 합쳐 보면, **이 "총 12개" 텍스트는 세 탭 프레임
모두에 복사-붙여넣기된 placeholder 값**이 거의 확실함 — 실제 개수 바인딩 로직은
Figma에 반영되지 않은 상태.

## 콘텐츠 (placeholder 상태)

4개 아이템 전부 동일한 placeholder("타이틀"/"기관"/대상자·지원내용·접수기간 라벨 +
"캡션"×3) — 행 간 차별화된 실제 콘텐츠 없음(기본 캡처의 policy 섹션도 마찬가지로
전부 placeholder였음, 일관됨).

## 색상 요약

기본 캡처 대비 **신규 hex 없음**.

## 기존 디자인 시스템 컴포넌트 매핑

- 리스트 아이템: `AppListItem(size: .xlarge, showsDivider: ...)` 그대로 재사용.
- "총 N개" 헤더 밴드: [나의 일지 탭 캡처](2026-07-16-search-result-record-tab.md)와
  동일 컴포넌트 재사용(화면 내부 로컬 뷰, 공통화 가능).

## 진행 상태

검색 결과 화면 3/4(정책 정보 탭). 이슈 종합은
[HANDOFF.md](HANDOFF.md) Part 5 참고.
