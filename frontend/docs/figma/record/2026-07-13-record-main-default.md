# 기록 메인 / default

- Captured: 2026-07-13
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1247:23900`
- Frame name: `기록 메인 / default`
- Frame size: 390 × 844 (iPhone 13/14 프레임 + Dynamic Island status bar)
- State: 영농기록 탭의 기본(default) 목록 상태. 필터 미선택, 리스트 아이템 3개(플레이스홀더 텍스트: 타이틀/캡션/레이블), FAB 노출, 하단 nav-bar 선택 상태는 "영농 기록".

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환하는 클라이언트
한계 — [HANDOFF.md](HANDOFF.md) Part 1 참고). 아래는 인라인 캡처를 육안 검증한
내용이며, 구조·색상·타이포는 `read_my_design`(원본 JSON)으로 정밀 기록함.

## 레이아웃 구조 (top → bottom)

| 순서 | 노드 | 타입 | y | height | 설명 |
|---|---|---|---|---|---|
| 1 | `Status Bar - iPhone` (`1247:23914`) | INSTANCE | 1542 | 54 | 시스템 상태바, 9:41 |
| 2 | `top-app-bar` (`1247:23913`) | INSTANCE | 1596 | 60 | 타이틀 "영농 기록" + search/notifications 아이콘 |
| 3 | `tab-bar` (`1247:23912`) | INSTANCE | 1656 | 56 | "기록"(선택, 초록 언더라인) / "리포트" 2탭 |
| 4 | `filter-chip` (`1247:23901`) | FRAME | 1712 | 60 | 배경 `#fafafa`, chip 3개: 작물 / 영농 활동 / 기간 |
| 5 | `list` (`1247:23905`) | FRAME | 1792 | 796 (스크롤 영역) | list row 인스턴스 반복 |
| 6 | `button` (FAB, `1247:23910`) | INSTANCE | 2210 | 72×72 | list 위에 겹쳐 떠있는 원형 추가 버튼 (absolute overlay) |
| 7 | `nav-bar` (`1247:23911`) | INSTANCE | 2314 | 72 | 하단 탭 4개, "영농 기록" 선택 상태 |

프레임 전체 높이(844) 안에서 `list`(796)와 `nav-bar`(72)/`button`이 y좌표상 겹치므로,
list는 스크롤 콘텐츠이고 FAB/nav-bar는 화면에 고정(sticky) 오버레이로 보임 — 마이페이지
HANDOFF의 "헤더/탭이 리스트와 스크롤되고 하단은 고정?" 질문과 동일한 패턴.

## Filter chip 3개 (`1247:23901`)

각 chip: 배경 `#ffffff`, border `#f3f3f3`, cornerRadius 1000(캡슐), height 32, trailing에
`icon/keyboard_arrow_down` 24×24.

| chip | label | width |
|---|---|---|
| `1247:23902` | 작물 | 72 |
| `1247:23903` | 영농 활동 | 101 |
| `1247:23904` | 기간 | 72 |

label 텍스트 스타일: Pretendard Medium 15, letterSpacing -0.3, lineHeight 19.5, color `#4f4f4f`.

## List row (반복 인스턴스, 예: `1247:23906`)

전체 row: 390×184, border-bottom `#e0e0e0`.

### header (`860:12804`), height 32
- `mm/dd` 텍스트: Pretendard Medium 15, color `#878787`, 우측 정렬 위치
- `badge` 그룹 (2개, 좌측):
  - `badge-1`: fill `#e6f7bf`(연두), cornerRadius 8, label "레이블" Pretendard Medium 15 color `#27865c`(secondary 계열 초록)
  - `badge-2`: fill `#f3f3f3`(회색), cornerRadius 8, label "레이블" Pretendard Medium 15 color `#4f4f4f`

### content (`860:12809`), height 120
- 좌측 텍스트 블록 (width 218):
  - 타이틀: Pretendard SemiBold **24**, letterSpacing -0.24, lineHeight 31.2, color `#4f4f4f`
  - 캡션: Pretendard Medium **18**, letterSpacing -0.36, lineHeight 27, color `#878787`
- 우측 이미지: 120×120, cornerRadius 8, IMAGE fill(FILL scaleMode) — 실제 데이터에서는
  기록 첨부 사진, 플레이스홀더는 체커보드 표시(디자인 시스템 `AppImagePlaceholder`와 동일 패턴)

동일 구조가 `1247:23907`, `1247:23908`, `1247:23909`로 3회 더 반복(총 4개 row 확인,
스크린샷 상단엔 3개까지 노출, 4번째는 FAB에 가려 일부만 보임).

## Top app bar (`1247:23913`)
- 타이틀 "영농 기록": Pretendard **Bold 32**, letterSpacing -0.32, lineHeight 41.6, color `#242428`
- 우측 아이콘 2개(각 48×48 tap target, 아이콘 자체 32×32): `icon/search`, `icon/notifications`

## Tab bar (`1247:23912`)
- "기록" 탭: 선택 상태, 하단 보더 `#38c284`(Object.primary), 텍스트 Pretendard SemiBold 20 color `#242428`
- "리포트" 탭: 비선택, 텍스트 Pretendard Medium 20 color `#878787`
- 탭 각각 width 195 (390 / 2 균등 분할)

## FAB (`1247:23910`)
- 72×72 원형, fill `#38c284`(Object.primary)
- 내부 아이콘: `icon/add_2` 40×40 (다른 화면의 `icon/add`와 다른 variant — "add_2"로 확인)
- absoluteBoundingBox 기준 화면 우측 하단, nav-bar 위에 겹쳐 떠 있음(y 2210, nav-bar y 2314)

## Nav bar (`1247:23911`), height 72
4개 nav-item, 각 width 75.5:
| 아이템 | 아이콘 | 라벨 | 상태 |
|---|---|---|---|
| nav-item-1 | `icon/home_line` | 홈 | 비선택 (color `#4f4f4f`) |
| nav-item-2 | `icon/assignment` | 영농 기록 | **선택** (라벨 SemiBold 15, color `#242428`) |
| nav-item-3 | `icon/forum_line` | 정보 공유 | 비선택 |
| nav-item-4 | `icon/person_line` | 프로필 | 비선택 |

## 색상 요약

| 용도 | hex | 디자인 시스템 토큰 후보 |
|---|---|---|
| 배경(필터 영역) | `#fafafa` | `Color.Background.subtle` / `Object.subtle` |
| chip 배경 | `#ffffff` | `Color.Object.default` |
| chip 보더 | `#f3f3f3` | `Color.Border.subtle` |
| chip 텍스트 | `#4f4f4f` | `Color.Text.subtle` |
| badge-1 배경(연두) | `#e6f7bf` | `Color.Object.secondarySubtle`(가장 근접, 정확 매핑 재확인 필요 — 기존 시맨틱은 `#f9fcf3`) |
| badge-1 텍스트 | `#27865c` | `Color.Text.primary` |
| badge-2 배경 | `#f3f3f3` | `Color.Object.muted` |
| badge-2 텍스트 | `#4f4f4f` | `Color.Text.subtle` |
| row 타이틀 | `#4f4f4f` | `Color.Text.subtle` |
| row 캡션 / mm/dd | `#878787` | `Color.Text.muted` |
| row 보더 | `#e0e0e0` | `Color.Border.default` |
| top-app-bar 타이틀 / 선택 nav 라벨 | `#242428` | 기존 `Color.Text.default`(#1a1a1a)와 **불일치** — 아래 충돌 항목 참고 |
| FAB / 선택 탭 언더라인 | `#38c284` | `Color.Object.primary` |

## 기존 디자인 시스템 컴포넌트 매핑 후보

- **top-app-bar** → `AppTopAppBar` (타이틀 + trailing 아이콘 2개) — 재사용 가능해 보임, 아이콘 2개 슬롯 API 확인 필요
- **tab-bar** (기록/리포트) → `AppTabBar` — 밑줄 탭바, 2탭 균등분할
- **filter chip 3개** → `AppChip(trailingSystemImage: "chevron.down", style: .solidPastel, isSelected: false)`에 근접. 단, 현재 `AppChip` fill 로직상 `isSelected: false` + `.solidPastel`은 배경 `Color.Object.default`(#fff) + 보더 `Color.Border.subtle`(#f3f3f3)로 **정확히 일치**. 드롭다운 자체 동작(바텀시트/피커 오픈)은 신규 로직 필요
- **list row** → `AppListItem(size: .large, ...)`와 **거의 완전 일치**: canvasSize 390×184, thumbnailSide 120, `mediaBody`가 이미 상단 badge 2개 + 우측 정렬 dateText 구조를 갖고 있음. `reactions: false`도 large variant 기본값과 일치. **바로 재사용 가능**, 커스텀 컴포넌트 신규 제작 불필요.
- **FAB (원형 + icon/add_2)** → 기존 `AppButton`에 원형 72pt 아이콘 전용 variant 없음. 신규 컴포넌트 후보 (다른 화면에 동일 FAB 있는지 먼저 확인 필요)
- **badge 2개(레이블)** → `AppBadge(style: .solidPastel, variant: .secondary)`로 이미 `AppListItem.badgeRow`가 사용 중 — 그대로 재사용됨
- **nav-bar** → `AppNavBar` (4 아이템, 선택 상태 파생 방식 기존과 동일)

## Figma ↔ 디자인 시스템 충돌 / 확인 필요

1. **텍스트 색상 `#242428`**: top-app-bar 타이틀, 선택된 nav 라벨, 선택된 탭 라벨에서
   반복 사용되나 `Color+App.swift`의 `text/default`(#1a1a1a)와 다른 값. 신규 시맨틱인지,
   기존 `Color.Text.default`를 그대로 쓰되 디자이너가 근사치를 스포이드한 것인지 확인 필요.
2. **badge-1 배경 `#e6f7bf`**: `DESIGN_SYSTEM_HANDOFF.md`의 `object/secondary`(#E6F7BF)와
   정확히 일치. 그러나 badge 텍스트 컬러 `#27865c`는 `text/primary`(#27865C)와 매칭되어
   variant 조합이 `Object.secondary` 배경 + `Text.primary` 텍스트로 **교차 매핑**됨 —
   `AppBadge(variant: .secondary)`가 현재 이 조합을 내는지 코드 확인 필요.
3. **`icon/add_2`**: 기존 asset에 `add`와 `add_2` 두 SVG가 모두 존재
   (`Assets.xcassets/icon/add.imageset`, `add_2.imageset` — git status 상 이미 추가돼 있음).
   FAB에는 `add_2` 사용 확정.

## 제품/API 질문 (미결)

- 필터 3종(작물/영농 활동/기간) 드롭다운 옵션 소스 — 작물은 member_crop, 영농 활동은
  BR-RECORD 상 활동 카테고리 enum 추정, 기간은 날짜 range picker로 추정 (확정 필요)
- list row의 badge 2개가 각각 무엇을 나타내는지(작물명 vs 활동유형 vs 다년생 여부 등)
  — BR-RECORD-008(다년생 작물) 관련 가능성
- 이미지 없는 기록(사진 미첨부, BR-RECORD-009 "사진은 보조 정보")일 때 우측 120×120
  슬롯 처리 — placeholder 유지 vs 레이아웃 축소
- FAB 탭 시 진입 지점 — 음성/텍스트 입력 모드 선택 시트(BR-VOICE-*) 여부
