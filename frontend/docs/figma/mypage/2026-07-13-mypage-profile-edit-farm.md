# Figma Capture: My Page / Profile Edit / 농업 정보 수정 (등록한 밭)

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`,
  `read_my_design`, `export_node_as_image`
- Figma node: `1247:17987`
- Frame name: `프로필 수정 / 농업 정보 수정`
- Frame size: `390 x 844`
- Capture state: 프로필 수정 화면의 `농업 정보` 탭. 등록한 밭(농장) 카드 목록
  + 밭 추가/삭제. 화면 배경 `#fafafa`(기본 정보 탭의 흰 배경과 다름).
- PNG export: `export_node_as_image(PNG, scale 2)` 성공, 렌더 육안 검증함.
  (Claude Code 파일 저장 불가 — HANDOFF Part 2 참고.)

## Screen Structure

앱 셸(status/top-app-bar/tab-bar)은 [기본 정보 탭](2026-07-13-mypage-profile-edit-basic.md)과
동일 컴포넌트. 이 탭에서는 tab-bar의 `농업 정보`가 선택 상태.

1. **top-app-bar**: 뒤로가기 + `프로필 수정`(SemiBold 28) + 빈 trailing. (기본 정보와 동일)
2. **tab-bar**: `기본 정보`(비선택) / `농업 정보`(선택, 하단 border `#38c284`).
3. **content** (`390 x 628`, 좌우 inset `20`):
   - **header** (`350 x 48`): 타이틀 `등록한 밭` + 개수 뱃지 + `삭제하기` 텍스트 버튼.
   - **setting-card** × 2 (밭 1개당 카드).
   - **추가하기** 버튼.

## Header

| Element | 값 |
|---|---|
| 타이틀 `등록한 밭` | Pretendard SemiBold `24` / lh `31.2` / `-0.24`, `#000000` |
| 개수 뱃지 (`badge-number`) | `28 x 28` pill, fill `#38c284`, 숫자 `2` `#ffffff` Medium 15 |
| `삭제하기` 버튼 | text-only(버튼 fill·stroke `visible:false`), 텍스트 `#4f4f4f` Medium 16 |

`삭제하기`는 밭 삭제 모드 진입으로 보임(고스트/텍스트 버튼).

## Farm Card (`setting-card`)

> 이 카드는 디자인 시스템 컴포넌트로 승격하기로 결정됨(2026-07-13).
> 컴포넌트 스펙과 variant는
> [setting-card 컴포넌트 문서](2026-07-13-mypage-setting-card-component.md) 참조.

- 카드: `350 x 232`, 배경 `#ffffff`, radius `20`, border `#e0e0e0`.
- 카드 내부 inset `20`, 행 폭 `310`, 행 간격 `12`.
- 각 행은 `#fafafa` fill 필드(radius `8`, height `56`)이며, **입력이 아니라
  표시/이동 행**이다(우측 아이콘으로 동작 구분).

| 행 | 텍스트 | 우측 아이콘 | 동작 |
|---|---|---|---|
| 농지명 | `농지명` SemiBold 18 `#1a1a1a` | `edit_line` | 인라인 편집 |
| 도로명 주소 | `도로명 주소` SemiBold 18 | `chevron_forward` | 주소 검색 화면 이동 |
| 작물 | 작물 뱃지들 + `외 n종` | `chevron_forward` | 작물 선택 화면 이동 |

작물 행(`products`, `310 x 56`, `#fafafa`): 작물 뱃지(`48 x 32`, fill `#e6f7bf`,
텍스트 `#27865c` Medium 15) 나열 + 초과 시 `외 n종`(`#4f4f4f`).

두 카드는 동일 컴포넌트다. 렌더상 카드1은 뱃지 3개만, 카드2는 뱃지 3개 + `외 2종`.
(`read_my_design` 데이터에는 두 카드 모두 `외 2종` 노드가 있으나, 카드1은 인스턴스
오버라이드로 숨김 처리된 것으로 보임 — 표시 규칙은
[작물 키워드 배치 규칙](2026-07-13-mypage-profile-crops-expanded.md#crop-keyword-layout-rule-작물-키워드-배치)과
동일하게 `>3`일 때 `외 n종`.)

## Add Button (`추가하기`)

- `350 x 56`, radius `12`, 배경 `#ffffff`, border `visible:false`(고스트).
- 라벨 `추가하기` `#4f4f4f` Medium 18. 밭(농장) 추가 진입.

## Implementation Mapping (사용자 지정, 2026-07-13)

밭 카드 행/추가 흐름은 온보딩의 기존 화면을 재사용한다.

| 대상 | 재사용 View | 위치 |
|---|---|---|
| 도로명 주소 행 | `FarmLocationView` | `Features/Onboarding/Presentation/Views/FarmLocationView.swift` |
| 작물 행 | `CropSelectionView` | `Features/Onboarding/Presentation/Views/CropSelectionView.swift` |
| 추가하기 버튼 | `FarmLocationView` → `CropSelectionView` 완료 후 밭 추가 | 위 두 파일 |

### 구현 결과 (2026-07-13)

구현 시 온보딩 View 셸의 강결합(아래)을 피하기 위해 **방식을 변경**했다:
온보딩 `FarmLocationView`/`CropSelectionView`를 직접 재사용하는 대신, 결합이 없는
**엔진 `FarmLocationViewModel` + `AddressSearchSheet` + 커뮤니티 `CropPickerSheet` +
`FarmRepository`를 재사용**해 `FarmAddView`/`FarmListView`를 새로 구성했다. 온보딩
화면은 건드리지 않았다(critical flow 회귀 방지). `DELETE /farms/{id}`는
`FarmRepository`/`FarmEndpoint`에 없어 신규 추가했다. 상세는
[구현 계획서](2026-07-13-mypage-implementation-plan.md) 7단계 참조.

### (참고) 온보딩 View 직접 재사용 시 결합(caveat)

두 View는 현재 **온보딩 전용으로 강결합**돼 있어 그대로 이식할 수 없다. 구현 계획에서
분리 방식을 먼저 정해야 한다.

- 둘 다 `@Environment(OnboardingViewModel.self)`에 의존하고 `viewModel.draft`
  (`farmName`, `cropIDs` 등), `availableCrops`, `cropCategories`를 직접 읽고 쓴다.
- `FarmLocationView`는 `OnboardingProgressBar`(온보딩 진행 바)와 온보딩 스텝
  네비게이션(`goBack()`, `currentStep`)을 포함한다 — 프로필 수정 맥락엔 부적합.
- 완료 시 온보딩 draft에 반영하는 구조라, "밭 1개 추가/편집 후 반환"하는
  독립 흐름용 입력/출력(초기값, 완료 콜백/바인딩)이 없다.

→ 재사용하려면 (a) 온보딩 의존성을 파라미터/콜백 또는 공유 뷰모델로 분리하거나,
(b) 프로필 수정용 얇은 래퍼 화면을 두고 내부 UI만 공유하는 방향을 구현 계획에서
결정한다. **캡처 단계에서는 코드를 수정하지 않는다.**

## Existing Component Candidates

실제 API는 `Core/DesignSystem/` 확인 후 확정.

- `setting-card` (흰 카드 radius 20) — 설정/그룹 카드로 재사용 가능성
- 표시/이동 행 (`#fafafa` 필드 + trailing 아이콘 edit/chevron) — 기본 정보 탭의
  editable text-input과 **다른 변형**(입력 아님). variant 구분 필요
- `badge-number` (개수 pill, `#38c284`)
- crop badge (`#e6f7bf`/`#27865c`, 게시물 primary 뱃지와 동일 계열)
- ghost/text 버튼 (`삭제하기`, `추가하기`) — dark primary(`저장`/`완료`)와 다른 변형
- top-app-bar, tab-bar (기본 정보 탭과 공유)

## Product / API Questions (중요)

- **밭 = farm(농장)**. `등록한 밭` 개수(2)는 member의 등록 농장 수.
  이 화면은 온보딩의 farm 생성/추가 농장 흐름과 이어진다(최근 커밋 `추가 농장`
  참조). Farm DTO(`FarmCreateRequest`/`FarmResponse`)와 매핑 필요.
- **도로명 주소 행**: AGENTS의 확정 사항대로 주소 검색은 JUSO + V-World
  (좌표/PNU/지목/면적) 체인을 사용. 단, 현재 Farm DTO에 좌표/PNU/지목/면적
  필드가 없다고 AGENTS가 명시 → 이 화면 구현 전 백엔드 협의 선행 필요.
- **작물 행**: 밭별 작물. 프로필 레벨 작물(프로필 메인 뱃지)과의 관계(밭 작물의
  합집합인지 별개인지) 정의 필요. `member_crop` / farm-crop 관계 확인.
- `농지명`은 인라인 편집(`edit_line`), `도로명 주소`→`FarmLocationView`,
  `작물`→`CropSelectionView` 이동으로 확정(위 Implementation Mapping 참조).
- `추가하기`는 `FarmLocationView` → `CropSelectionView` 완료 후 밭 추가로 확정
  (별도 Figma 캡처 불필요, 온보딩 화면 재사용). 단 온보딩 결합 분리 선행 필요.
- `삭제하기` 모드 UI(체크박스? 스와이프? 확인 다이얼로그?) 미수집 — 정의 필요.
- 저장 버튼이 이 탭에는 없음 — 각 행 편집/이동이 개별 저장인지, 화면 단위
  저장인지 흐름 정의 필요(기본 정보 탭은 하단 `저장` 존재).

## Runtime States To Define

- 등록한 밭 `0`개(빈 상태) — 카드 없이 `추가하기`만? 정의 필요.
- 밭 목록 로딩, 오류·재시도.
- 주소/작물 fetch 및 저장 시 offline-first 처리(AGENTS 규칙).
- iPhone SE 2/3: 카드 목록 스크롤, 추가/삭제 버튼 접근성.
