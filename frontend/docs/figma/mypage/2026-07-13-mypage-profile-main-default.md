# Figma Capture: My Page / Profile Main / Default

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`,
  `read_my_design`, `scan_text_nodes`
- Figma node: `1247:17727`
- Frame name: `프로필 메인 / default`
- Frame size: `390 x 1321`
- Text evidence: `36` text nodes
- Export target: `assets/2026-07-13-mypage-profile-main-default.png`, 2x PNG
  (`780 x 2642`)
- Export status: MCP image generation succeeded once, but the binary was not
  persisted before the Figma plugin stopped responding. Re-export the same node
  after reconnecting the plugin.
- Capture state: 프로필 탭이 선택되고 `나의 게시물`과 기본 게시판 필터가 열린
  기본 프로필 화면

## State Matrix

| State | Capture node | Evidence | Status |
|---|---|---|---|
| 프로필 메인 / 나의 게시물 / 기본 필터 | `1247:17727` | 구조 + 36 text nodes | captured; PNG pending |
| 프로필 메인 / 좋아요 누른 글 | 미수집 | 별도 Figma 프레임 필요 | pending |
| 프로필 메인 / 게시판 필터 선택 | 미수집 | 별도 Figma 프레임 필요 | pending |
| 프로필 메인 / 목록 없음 | 미수집 | Figma 또는 제품 상태 정의 필요 | pending |
| 프로필 메인 / 로딩·오류·재시도 | 미수집 | 런타임 상태 정의 필요 | pending |

## Screen Structure

1. iOS status bar: `390 x 54`.
2. Top app bar: `390 x 60`, title `나의 프로필`, settings and notification
   actions.
3. Profile card: frame inset `20`, `350 x 271`, corner radius `20`, border
   `#e0e0e0`.
4. Profile image: `96 x 96`; edit affordance `36 x 36` over the lower-right
   corner with a `24 x 24` icon.
5. Profile identity: centered nickname, region and farming-years row, divider,
   three crop badges and `외 n종` overflow label.
6. Content tabs: `390 x 56`; `나의 게시물` selected and `좋아요 누른 글`
   unselected.
7. Board filter row: `390 x 60`, background `#fafafa`; pill filter at left.
8. Post list: begins after a `20pt` gap, with `390 x 184` rows separated by
   `20pt` vertical gaps. Each row has two badges, date, title/caption and a
   `120 x 120` image with radius `8`.
9. Bottom navigation: `390 x 72`, profile item selected.

The status bar and bottom navigation are app-shell/device-chrome references.
Do not duplicate them inside a feature-local profile screen.

## Profile Card Measurements

| Element | Value |
|---|---:|
| Card horizontal inset | `20` |
| Card size | `350 x 271` |
| Card corner radius | `20` |
| Card content horizontal inset | `20` |
| Avatar | `96 x 96` |
| Avatar/edit overlay | `36 x 36` |
| Identity block | `310 x 59` |
| Region/year separator | `20` high |
| Divider | `310 x 1` visual stroke |
| Crop badge | `48 x 32`, radius `8` |
| Crop badge gap | `6` |

## Captured Text Styles

Values below come from the selected Figma frame. Match an existing design-system
token only after verifying every font, weight, size, line height, tracking and
color value.

| Area | Text | Font | Line height | Tracking | Color |
|---|---|---:|---:|---:|---:|
| Top title | `나의 프로필` | Pretendard Bold 32 | 41.6 | -0.32 | `#242428` |
| Nickname | `닉네임` | Pretendard SemiBold 20 | 26 | -0.2 | `#1a1a1a` |
| Profile metadata | `사용자 설정 지역`, `귀농 연차` | Pretendard Medium 18 | 27 | -0.36 | `#4f4f4f` |
| Crop badge | `작물` | Pretendard Medium 15 | 19.5 | -0.3 | `#ffffff` |
| Crop overflow | `외 n종` | Pretendard Medium 15 | 19.5 | -0.3 | `#4f4f4f` |
| Selected content tab | `나의 게시물` | Pretendard SemiBold 20 | 26 | -0.2 | `#242428` |
| Unselected content tab | `좋아요 누른 글` | Pretendard Medium 20 | 26 | -0.2 | `#878787` |
| Filter label | `게시판 선택` | Pretendard Medium 15 | 19.5 | -0.3 | `#4f4f4f` |
| Post badge/date | `레이블`, `mm/dd` | Pretendard Medium 15 | 19.5 | -0.3 | token-dependent / `#878787` |
| Post title | `타이틀` | Pretendard SemiBold 24 | 31.2 | -0.24 | `#4f4f4f` |
| Post caption | `캡션` | Pretendard Medium 18 | 27 | -0.36 | `#878787` |
| Selected bottom tab | `프로필` | Pretendard SemiBold 15 | 19.5 | -0.3 | `#242428` |
| Unselected bottom tabs | `홈`, `영농 기록`, `정보 공유` | Pretendard Medium 15 | 19.5 | -0.3 | `#4f4f4f` |

The status-bar time uses SF Pro Semibold 17 and is not an app text style.

## Key Visual Values

- Screen and top-bar background: `#ffffff`.
- Selected accent and crop badge fill: `#38c284`.
- Selected crop badge text: `#ffffff`.
- Profile card and row divider: `#e0e0e0`.
- Filter-row background: `#fafafa`.
- Filter pill background: `#ffffff`; border `#f3f3f3`; radius `1000`.
- Profile avatar placeholder: fill `#f3f3f3`, border `#e0e0e0`.
- Avatar edit affordance: fill `#343434`.
- Post primary badge: fill `#e6f7bf`, text `#27865c`.
- Post secondary badge: fill `#f3f3f3`, text `#4f4f4f`.
- List thumbnail corner radius: `8`.

## Filter Chip States (게시판 선택)

프로필 메인 필터 행의 `게시판 선택` 칩은 두 상태가 있다. 바텀시트
([게시판 선택 시트](2026-07-13-mypage-board-select-bottom-sheet.md))에서 작물을
고르고 `완료`하면 이 칩이 **기본 → 활성**으로 바뀐다.

출처: 사용자 첨부 Figma inspector(2026-07-13). 활성 상태는 하드코딩 hex가 아닌
디자인 시스템 **시맨틱 변수 토큰**으로 지정돼 있다 — 구현 시 이 토큰명을 앱의
`Color+App` 매핑에 맞춰 사용한다.

| Property | 기본 (default) | 활성 (선택됨) |
|---|---|---|
| Fill | `#ffffff` | `object/default` (흰색) |
| Border(stroke) | `#f3f3f3` | `border/primary` (그린 계열), Inside, weight `1` |
| Label 텍스트 | `#4f4f4f` | `text/primary` (그린 계열) |
| 드롭다운 아이콘 | 회색 | `icon/primary` (그린 계열) |
| Radius | `1000` | `1000` |

활성 상태 셀렉션 컬러 토큰(inspector 표기): `text/primary`, `icon/primary`,
`border/primary`, `object/default`.

토큰 → hex 대응은 추정하지 않는다. `border/primary`는 앱 액센트 그린(`#38c284`)로
보이나, 각 토큰의 정확한 값은 구현 전 `get_variable_defs` 또는 `Core/DesignSystem/
Foundation/Color+App.swift`로 확정한다.

## Existing Component Candidates

Verify the actual component APIs and captured variants before implementation.

- `AppTopAppBar`
- `AppAvatar`
- `AppBadge`
- `AppTabBar`
- `AppChip`
- `AppListItem`
- `AppNavBar`

The profile card composition appears screen-specific unless another screen
reuses the same identity, metadata and crop-summary layout.

## Product and Implementation Questions

- The frame is `1321pt` high rather than a standard device viewport. Confirm
  that the profile header and tabs scroll with the list while the app-level
  bottom navigation remains fixed.
- Confirm whether the settings and notification icons open existing routes or
  require new screens.
- Map `사용자 설정 지역` and `귀농 연차` to deployed member-profile fields from
  Swagger rather than archived Notion API documents.
- Crop display rule for ≤3 / >3 / expanded is now defined by the designer spec —
  see [작물 키워드 배치 규칙](2026-07-13-mypage-profile-crops-expanded.md#crop-keyword-layout-rule-작물-키워드-배치).
  Remaining gap: the **zero-crop** state is still undefined (spec covers ≤3, >3,
  expanded only).
- Define long nickname, long region and large farming-year value behavior on
  iPhone SE 2/3.
- Confirm which board types appear in `게시판 선택` and whether that filter is
  shared across authored and liked posts.
- Capture or define loading, empty, error, retry and pagination states before
  implementation planning is finalized.

## Capture Follow-up

Reconnect the Figma plugin to channel `chamchamcham`, keep node `1247:17727`
selected, and export the 2x PNG to the target path above. After the file is
saved, add its SHA-256 and embed it in this document.
