# 검색 결과 / default

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` →
  `mcp__TalkToFigma__scan_text_nodes` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1547:23518`
- Frame name: `검색 결과 / default`
- Frame size: 390 × 2070 (iPhone 13/14 프레임, `results` 프레임이 세로 스크롤 콘텐츠)
- State: 검색어가 입력/제출된 뒤의 통합 검색 결과. 상단 탭 4개(전체/나의 일지/정책 정보/
  게시글)로 카테고리 필터링, "전체" 탭 선택 시 카테고리별로 최대 3개씩 미리보기 +
  섹션 헤더에 전체 개수 + "더보기" 화살표.

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환하는 클라이언트 한계 —
[record/HANDOFF.md](../record/HANDOFF.md) Part 1 참고). 인라인 캡처를 육안 검증했고,
구조·색상·타이포는 `read_my_design`(원본 JSON)으로 정밀 기록함.

## 레이아웃 구조 (top → bottom)

| 순서 | 노드 | 타입 | y | height | 설명 |
|---|---|---|---|---|---|
| 1 | `Status Bar` (`1547:23553`) | INSTANCE | -455 | 62 | 시스템 상태바, 9:41 |
| 2 | `search` (`1547:23554`) | FRAME | -393 | 56 | 뒤로가기 아이콘 + 검색바(제출된 검색어 노출) |
| 3 | `tab-bar` (`1547:23519`) | INSTANCE | -337 | 56 | 전체/나의 일지/정책 정보/게시글, 4등분 underline 탭 |
| 4 | `results` (`1547:23520`) | FRAME | -281 | 1896 | 스크롤 콘텐츠: record → divider → policy → divider → post |

## `search` (`1547:23554`)

- `search-bar` 배경 `#fafafa`(`Color.Background.subtle`), 텍스트 "검색어 입력 완료"
  색상 `#1a1a1a`(`Color.Text.default`) — 검색어 입력 중 캡처와 동일하게 제출된 검색어는
  기본 텍스트 색으로 렌더링.
- clear 아이콘 노출 여부는 이 프레임에서 확인 안 됨(placeholder 프레임만 캡처됨, X 버튼
  노드 없음) — 검색 결과 화면에서는 clear 버튼이 없거나 스크롤에 가려진 것으로 추정,
  확인 필요.

## `tab-bar` (`1547:23519`) — 4개 탭

| 탭 | 상태 | 텍스트 색 | 폰트 | 밑줄 |
|---|---|---|---|---|
| 전체 | 선택됨 | `#1a1a1a`(Text.default) | SemiBold 20 | 3pt `#38c284`(Border.primary / Green.600) |
| 나의 일지 | 미선택 | `#878787`(Text.muted) | Medium 20 | 없음 |
| 정책 정보 | 미선택 | `#878787`(Text.muted) | Medium 20 | 없음 |
| 게시글 | 미선택 | `#878787`(Text.muted) | Medium 20 | 없음 |

바 하단 보더 `#f3f3f3`(`Color.Object.muted`) 1px, 배경 `#ffffff`. 4개 탭이 390px를
정확히 4등분(97.5px씩) — **`AppTabBar`(비-scrollable 모드)와 색상·레이아웃 전부
일치**.

## `results` (`1547:23520`) — 3개 섹션 + 구분선 2개

공통 섹션 헤더 패턴: `타이틀 SemiBold24 #1a1a1a` + `개수 SemiBold24 #4f4f4f` (같은 줄,
바짝 붙어 렌더링됨) + 우측 `icon/arrow_forward_ios`(24×24, "더보기" 화살표). 각 섹션은
정확히 3개의 리스트 아이템만 노출(전체 개수는 12/7/5로 더 많음 — 미리보기 truncation).

### 1. `record` (`1547:23521`) — "나의 일지 12"

3개 `list` 인스턴스, 각 391×184, border-bottom `#e0e0e0`(`Color.Border.default`) —
**단, 3번째(마지막) 아이템만 stroke 없음**(미리보기 마지막 행 구분선 생략).

| 행 | badge-1(크롭, pastel-primary) | badge-2(pastel-secondary) | 날짜 | 썸네일 |
|---|---|---|---|---|
| 1 | "인삼" `#e6f7bf`/`#27865c` | "영농 활동별 데이터" `#f3f3f3`/`#4f4f4f` | mm/dd | 120×120 rounded 8 |
| 2 | "인삼" | "레이블"(placeholder) | mm/dd | 120×120 |
| 3 | "율무" | "레이블"(placeholder) | mm/dd | 120×120 |

제목(`타이틀`) SemiBold 24 `#4f4f4f`(Text.subtle), 캡션(`캡션`) Medium 18 `#878787`
(Text.muted).

### 2. `policy` (`1547:23532`) — "정책 정보 7"

3개 `list` 인스턴스, 각 391×169, border-bottom `#e0e0e0` (3번째 아이템만 없음).
썸네일 없음(텍스트 전용 2단 레이아웃).

- 제목(`타이틀`) SemiBold 24 `#1a1a1a`(Text.default), 기관(`기관`) Medium 16
  `#4f4f4f`(Text.subtle)
- info 2열: 라벨(대상자/지원내용/접수기간) Medium 16 `#878787`(Text.muted) ×
  값(`캡션`×3) Medium 16 `#1a1a1a`(Text.default)

### 3. `post` (`1547:23543`) — "게시글 5"

3개 `list` 인스턴스, 각 390×160(2번째는 391), border `#e0e0e0` (3번째 아이템만 없음).

- badge-1 "작물 카테고리" `#e6f7bf`/`#27865c` (pastel-primary) — **badge-2는 배경
  `#f3f3f3`만 있고 텍스트 노드 자체가 없음**(레이블 없는 빈 pill, record/policy와 달리
  실제로 사용 안 하는 슬롯으로 보임 — 아래 질문 참고)
- 제목 "게시물 제목" SemiBold 20 `#4f4f4f`(Text.subtle), 캡션 "게시물 내용은 최대
  1줄까지 제시합니다." Medium 18 `#878787`(Text.muted), 1줄 truncate
- reaction row: `icon/favorite_line` + "nn", `icon/chat_bubble_line` + "nn", 둘 다
  `#878787`(Text.muted)
- 썸네일 96×96 rounded 8 (record보다 작음)

### 섹션 구분선

`record`↔`policy`, `policy`↔`post` 사이에 `divider` 인스턴스(12pt 높이, 배경
`#f3f3f3` = `Color.Object.muted`) — 리스트 아이템 내부 divider(`#e0e0e0`)보다 연하고
두꺼운 섹션 간격용 구분선.

## 색상 요약

| 용도 | hex | 디자인 시스템 토큰 |
|---|---|---|
| 섹션/제목 강조 텍스트 | `#1a1a1a` | `Color.Text.default` |
| 서브 텍스트(제목-record/post, 기관) | `#4f4f4f` | `Color.Text.subtle` |
| 캡션/라벨/reaction 텍스트 | `#878787` | `Color.Text.muted` |
| 리스트 아이템 구분선 | `#e0e0e0` | `Color.Border.default` |
| 섹션 간 구분선 / badge-2 배경 | `#f3f3f3` | `Color.Object.muted` |
| badge-1(크롭/카테고리) 배경 | `#e6f7bf` | `Color.Object.secondary` (Lime.200) |
| badge-1 텍스트 | `#27865c` | `Color.Text.primary` (Green.800) |
| 선택된 탭 밑줄 | `#38c284` | `Color.Border.primary` (Green.600) |
| 검색바 배경 | `#fafafa` | `Color.Background.subtle` |

**이 캡처에서는 이전 3개 캡처(default/history/typing)에서 반복되던 `#e0e0e0` vs
`#f3f3f3` 불일치 패턴이 재현되지 않음** — 오히려 두 값 모두 각자의 디자인 시스템
토큰(`Border.default`/`Object.muted`)과 정확히 일치하는 곳에 정확히 쓰였다. 컴포넌트
매핑도 그대로 뒷받침됨(아래 참고).

## 기존 디자인 시스템 컴포넌트 매핑 후보 — 전부 재사용 가능, 신규 컴포넌트 불필요

`Core/DesignSystem/Components/AppListItem.swift`의 기존 `Size` variant가 3개 섹션
전부와 **색상·치수·타이포까지 토큰 단위로 정확히 일치**한다:

| 섹션 | `AppListItem.Size` | 근거 |
|---|---|---|
| record | `.large` (390×184, thumbnail 120) | `mediaBody` 제목 `Text.subtle`, 캡션 `Text.muted`, `reactions: false` — 정확히 일치 |
| policy | `.xlarge` (390×169, 썸네일 없음) | `xlargeBody` 제목 `Text.default` + 기관 `Text.subtle`, infoRows 라벨 `Text.muted`/값 `Text.default` — 정확히 일치. `AppListItem` 자체 주석에 "policy information layout"이라 명시돼 있음 |
| post | `.medium` (390×160, thumbnail 96, reactions) | `mediaBody(reactions: true)` 제목 `Text.subtle`, 캡션 `Text.muted`, `favorite_line`/`chat_bubble_line` 아이콘 — 정확히 일치 |

- 탭바(전체/나의 일지/정책 정보/게시글) → `Core/DesignSystem/Components/AppTabBar.swift`
  (non-scrollable, 4등분) 그대로 사용 가능 — underline 색(`Border.primary`), 하단
  구분선(`Object.muted`) 전부 일치.
- badge-1/badge-2 → `AppBadge(style: .solidPastel, variant: .primary/.secondary)` +
  `AppListItem`의 `badges: [AppListItemBadge]` 파라미터로 그대로 조합 가능.
- 섹션 간 12pt 구분선 → `AppDivider` (기본색이 이미 `Object.muted` = `#f3f3f3`라 이
  화면에서는 색 불일치 없음).
- **섹션 헤더(타이틀 + 개수 + "더보기" 화살표)** → 승격된 디자인 시스템 컴포넌트 없음.
  `icon/arrow_forward_ios`는 Home 화면(`HomeView.swift:353`)에서 이미 동일 용도로
  쓰이고 있어 새 아이콘 에셋은 불필요하나, "제목+개수+화살표" 조합 자체는 화면 내부
  로컬 뷰로 구현 권장(Figma Screen Implementation Rule 우선순위 3) — 반복되면(Home도
  유사 패턴 쓴다면) 추후 승격 후보.
- **미리보기 마지막 아이템만 구분선 없음** — `AppListItem`은 `showsDivider: Bool`
  파라미터를 이미 지원하므로 `ForEach`에서 `showsDivider: index != items.count - 1`로
  그대로 표현 가능.

## 제품/API 질문 (미결)

- **검색 대상 3종 확정**: 이번 캡처로 "나의 일지"(Record, 로컬 SwiftData 영농일지),
  "정책 정보"(이미 구현된 Home 기능 — `Features/Home/Data/PolicyRepository.swift`,
  `PolicyEndpoint.swift`), "게시글"(Community post)이 검색 대상임이 확정됨. Part 5의
  "데이터 소스 미확정" 질문 중 절반은 해소 — 남은 질문은 통합 검색이 클라이언트에서
  3개 API를 병렬 호출해 합치는 방식인지, 서버에 통합 검색 엔드포인트가 별도로 있는지
  (`docs/swagger/` 확인 필요, `python3 scripts/sync_swagger_spec.py --write`로 최신화).
- post의 badge-2가 배경만 있고 텍스트가 없는 이유 — 실수로 남은 빈 인스턴스인지, 아니면
  게시글 카드는 원래 배지 1개만 쓰는 디자인 의도인지 확인 필요(`CommunityView.swift`의
  기존 게시글 리스트가 배지를 몇 개 쓰는지 대조하면 단서가 될 수 있음).
  - 참고: [CommunityView.swift](../../../ChamChamCham/ChamChamCham/Features/Community/Presentation/Views/CommunityView.swift)
- 검색창의 clear(X) 버튼이 이 화면에서는 보이지 않음 — 검색 결과 화면에서는 clear 버튼을
  숨기는 의도적 상태 차이인지, 이 프레임에서 노드가 빠진 것뿐인지 확인 필요.
- 섹션 헤더 "더보기" 탭 시 동작 — 해당 카테고리 전용 탭으로 전환되는지(예: "나의 일지 12 >"
  탭 시 "나의 일지" 탭으로 스위칭), 별도 상세 화면으로 이동하는지 확인 필요.
- 각 섹션이 정확히 3개로 고정 truncate되는지(전체 개수가 3 미만이면 그만큼만 노출되는지)
  — 빈 결과(0건) 캡처에서 개별 섹션이 아예 숨겨지는지 함께 확인 예정(다음 캡처: 검색
  결과 빈 상태).

## 진행 상태

**검색 결과 화면 1/4 완료** (default/성공 상태). 검색 진행 시 3개 + 이번 1개로
누적 4/7. 다음 캡처: 검색 결과 빈 결과(0건) 상태 등 나머지 3개.
