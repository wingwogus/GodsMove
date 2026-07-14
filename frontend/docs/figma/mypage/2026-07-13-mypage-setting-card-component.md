# Figma Capture: Design-System Component / setting-card (밭 카드)

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `get_selection`, `read_my_design`,
  `export_node_as_image`
- Figma node: `1088:16697` (`COMPONENT_SET`)
- Component name: `setting-card`
- Variants: `selected=false`(`1172:14766`), `selected=true`(`1172:14765`)
- Variant frame size: `350 x 232` (각 variant)
- 결정(사용자, 2026-07-13): 이 밭 카드는 **디자인 시스템 컴포넌트로 별도 추가**한다.
  ([농업 정보 화면](2026-07-13-mypage-profile-edit-farm.md)에서 인스턴스로 사용됨.)
- PNG export: `export_node_as_image(PNG, scale 2)` 성공, 두 variant 육안 검증함.
  (Claude Code 파일 저장 불가 — HANDOFF Part 2 참고.)

## Variants

`selected` 불리언 하나. 카드/행 배경색만 반전된다.

| | selected=false | selected=true |
|---|---|---|
| Card fill | `#ffffff` | `#e4f8e3` |
| Card border | `#e0e0e0` | `#38c284` |
| 내부 행(field) fill | `#fafafa` | `#ffffff` |
| Card radius | `20` | `20` |

`selected=true`의 쓰임(선택 강조)은 확정 필요 — **`삭제하기` 선택 모드에서 삭제
대상 카드 강조** 용도로 추정. (농업 정보 화면 기본 목록에서는 selected=false.)

## Structure (variant 내부)

카드 `350 x 232`, radius `20`. 내부 inset `20`, 행 폭 `310`, 행 높이 `56`, 행 간격 `12`.
3개 행으로 구성.

| 행 | 컴포넌트 | 텍스트(기본값) | 우측 아이콘 | 동작 |
|---|---|---|---|---|
| 1 | `text-input` | `농지명` | `icon/edit_line` | 인라인 편집 |
| 2 | `text-input` | `재배지 도로명 주소` | `icon/chevron_forward` | 주소 검색 이동 |
| 3 | `products` | 작물 뱃지 + `외 n종` | `icon/chevron_forward` | 작물 선택 이동 |

- 행 텍스트: Pretendard SemiBold `18` / lh `27` / `-0.36`, `#1a1a1a`.
- 행 field: radius `8`, 좌 패딩 `16`, 우측 아이콘 24×24 (우 패딩 16).
- 작물 뱃지: `48 x 32`, radius `8`, fill `#e6f7bf`, 텍스트 `#27865c`
  (Pretendard Medium 15 / lh 19.5 / -0.3).
- `외 n종`: `#4f4f4f`, Medium 15. (표시 규칙은
  [작물 키워드 배치 규칙](2026-07-13-mypage-profile-crops-expanded.md#crop-keyword-layout-rule-작물-키워드-배치)과 동일)

주: 실제 사용 화면(농업 정보)에서는 2행 텍스트가 `도로명 주소`로 오버라이드돼 있었다.
컴포넌트 기본값은 `재배지 도로명 주소`.

## 구현 상태 (2026-07-13)

**구현 완료** — `Core/DesignSystem/Components/FarmCard.swift`.
`DesignSystemGallery`에 `#Preview("FarmCard")` 등록. 빌드 통과
(iPhone 17 시뮬레이터).

- API: `farmName`, `roadAddress`, `crops: [String]`, `isSelected`,
  `onEditName`/`onTapAddress`/`onTapCrops` 콜백.
- 토큰 매핑: 카드 `Object.primarySubtle`/`Border.primary`(선택) ↔
  `Object.default`/`Border.default`(기본), 행 `Object.subtle`↔`Object.default`,
  작물 뱃지 `AppBadge(.solidPastel,.primary)`, 라벨 `.bodyLargeEmphasized`.
- 아이콘: SF Symbols(`pencil`/`chevron.right`) — DS 관례(Figma 아이콘셋 도입 전).
- 작물 표시: `>3`이면 3개 + `외 n종`, 작물 0개면 `작물` 플레이스홀더.
- 뱃지 간격은 DS 관례(`Spacing.sm=8`) 사용 — Figma의 6은 토큰이 아니라 미채택.

## Design-System Integration

`Core/DesignSystem/Components/`에 새 컴포넌트로 추가한다. 기존 토큰/컴포넌트를
재사용하고 raw 값 중복을 피한다(AGENTS 디자인 시스템 규칙).

기존 재사용 후보(실제 API는 구현 시 확인):

- 컨테이너: `AppCard` 또는 `CardView` — radius 20 카드
- 행: `AppListItem` 또는 `AppFieldContainer` — 라벨 + trailing 아이콘 행
- 작물 뱃지: `AppBadge`
- 색상/타이포/간격: `Foundation/Color+App`, `Font+App`, `Spacing`

### 네이밍 주의

Figma 컴포넌트명은 `setting-card`지만 내용은 **밭(farm) 전용**
(농지명/도로명주소/작물). DS 컴포넌트명은 범용 `SettingCard`가 아니라
`FarmCard`처럼 도메인에 맞추는 것을 검토(추상화 과잉 방지 — AGENTS YAGNI).
`selected` variant는 `isSelected` 파라미터로.

### API 제안(초안, 구현 시 확정)

- `farmName`, `roadAddress`(옵션), `crops`(뱃지 데이터), `isSelected`
- 행별 액션 콜백: `onEditName`, `onTapAddress`, `onTapCrops`
- 프로필 수정 재사용 시 온보딩 결합 분리 이슈와 연결
  ([농업 정보 문서](2026-07-13-mypage-profile-edit-farm.md#implementation-mapping-사용자-지정-2026-07-13)).

## Open Questions

- `selected=true` 트리거 확정(삭제 선택 모드? 다른 용도?).
- 행 field의 상태(값 있음/플레이스홀더/오류) 표현이 이 컴포넌트 범위인지,
  내부 `text-input` 컴포넌트 책임인지.
- `농지명` 인라인 편집 UX(같은 자리 편집 vs 별도 입력).
- 헬퍼 메시지(`메시지를 전달합니다.`) 노출 조건(검증 실패 시?).
