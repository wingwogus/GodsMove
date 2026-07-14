# Figma Capture: My Page / Bottom Sheet / 게시판 선택 시

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`,
  `read_my_design`, `export_node_as_image`
- Figma node: `1247:18048`
- Frame name: `bottom-sheet / 게시판 선택 시`
- Frame size: `390 x 448`
- Capture state: 프로필 메인 필터 행의 `게시판 선택` 칩을 탭하면 올라오는 바텀시트.
  작물을 `진행중인 작물` / `기타 작물` 두 그룹의 선택 칩으로 고르고 `완료`로 확정.
- PNG export: `export_node_as_image(PNG, scale 2)` 호출 성공, 렌더 육안 검증함.
  (Claude Code에서 파일 저장은 불가 — HANDOFF Part 2 참고.)

## Screen Structure

바텀시트 컨테이너(`390 x 448`, 배경 `#ffffff`). 위→아래 3블록.

1. **grabber** (`390 x 20`): 핸들 바 `48 x 4`, radius `1000`, fill `#e0e0e0`,
   가로 중앙.
2. **filter** (`390 x 328`, 좌우 inset `20`):
   - 타이틀 `게시판 선택`
   - **진행중인 작물** 섹션 (`selected-chip`, `350 x 107`)
     - 섹션 라벨 + 칩 2줄 (1줄 5개 + 2줄 2개 = 7칩)
   - divider (`Line 6`, `350` wide, stroke `#e0e0e0`, 상하 패딩 `16`)
   - **기타 작물** 섹션 (`selected-chip`, `350 x 107`)
     - 섹션 라벨 + 칩 2줄 (1줄 5개 + 2줄 2개 = 7칩)
3. **bottom-button** (`390 x 100`, 배경 `#ffffff`): `완료` 버튼 `350 x 56`.

참고: 노드 데이터에 최상위 프레임 corner radius가 없다. 바텀시트 상단 라운딩은
디자인 시스템의 시트 컴포넌트 규칙을 따른다(임의 값 도입 금지).

## Measurements

| Element | Value |
|---|---:|
| Sheet | `390 x 448` |
| Content horizontal inset | `20` |
| Grabber block | `390 x 20` |
| Grabber handle | `48 x 4`, radius `1000` |
| Title → first section gap | title y=42 within filter, section top follows |
| Section block | `350 x 107` (label 27 + 8 + row 32 + 8 + row 32) |
| Section label → chips gap | `8` |
| Chip | `63 x 32`, radius `1000` (pill) |
| Chip horizontal gap | `8` |
| Chip row gap | `8` |
| Divider | `350 x 1` stroke, 상하 패딩 `16` |
| Bottom button block | `390 x 100` |
| Button | `350 x 56`, radius `12` |

## Chip States

| State | Fill | Stroke | Text color |
|---|---|---|---|
| Unselected | `#f3f3f3` | 없음 | `#4f4f4f` |
| Selected | `#e4f8e3` | `#38c284` | `#27865c` |

칩 라벨: Pretendard Medium `15` / lh `19.5` / tracking `-0.3`.

이 칩은 프로필 메인 필터 행의 `게시판 선택` 칩(`#ffffff` + `#f3f3f3` border +
드롭다운 아이콘)과 **다른 변형**이다. 여기 칩은 토글형 선택 칩이다.

## Captured Text Styles

| Area | Text | Font | Line height | Tracking | Color |
|---|---|---:|---:|---:|---:|
| Sheet title | `게시판 선택` | Pretendard SemiBold 20 | 26 | -0.2 | `#1a1a1a` |
| Section label | `진행중인 작물`, `기타 작물` | Pretendard Medium 18 | 27 | -0.36 | `#4f4f4f` |
| Chip label | `레이블` | Pretendard Medium 15 | 19.5 | -0.3 | 상태별 (표 참고) |
| Button label | `완료` | Pretendard Medium 18 | 27 | -0.36 | `#ffffff` |

## Key Visual Values

- Sheet 배경: `#ffffff`.
- Grabber / divider: `#e0e0e0`.
- Chip 선택 강조: fill `#e4f8e3`, border `#38c284`, text `#27865c`.
- Chip 비선택: fill `#f3f3f3`, text `#4f4f4f`.
- Primary button: fill `#343434`, radius `12`, text `#ffffff`.

## Existing Component Candidates

실제 API는 `Core/DesignSystem/` 확인 후 확정.

- 바텀시트 컨테이너 (grabber 포함) — 시트 프레젠테이션 컴포넌트/모디파이어
- 선택 칩 (selected/unselected) — `AppChip` 후보이나 프로필 필터 칩과 변형이
  다르므로 variant 확인 필요
- Primary 버튼 (`완료`, dark) — 앱 공통 버튼 컴포넌트 후보

## Product / API Questions

- 진입점: 프로필 메인 필터 행 `게시판 선택` 칩 탭 → 이 시트.
- 용어: 칩 라벨이 전부 `레이블`(플레이스홀더)이고 섹션은 작물 기준이다.
  즉 "게시판 선택"은 **작물별 게시판** 선택으로 보인다. 실제 라벨 소스 확인 필요.
- 그룹 구분: `진행중인 작물` = member의 현재 재배 작물, `기타 작물` = 그 외.
  데이터 소스를 Swagger member/crop 필드와 대조 필요.
- 선택 방식: 캡처상 그룹마다 1개씩(총 2개) 선택 표시 → **다중 선택** 가능성.
  단일/다중, 필수 최소 1개 여부, `완료`의 확정 규칙은 제품 규칙으로 정의 필요.
- 이 필터가 `나의 게시물` / `좋아요 누른 글` 두 탭에 공유되는지(default 문서의
  열린 질문과 연결).
- 칩이 많을 때 시트 높이(고정 `448`)와 스크롤 정책 정의 필요.

## 구현 상태 (2026-07-13)

**구현 완료** — `Features/MyPage/Presentation/Views/BoardSelectSheet.swift`, 빌드 통과.
프로필 메인 필터 칩 → 이 시트 → 단일 선택 → `완료` → `applyBoardFilter(cropId:)`.

기록해야 할 두 가지:

1. **진행중/기타 구분 (가정)**: boards API(`GET /community/boards`)는 `cropId`/`cropName`만
   주고 active/other 플래그가 없다. 그래서 `진행중인 작물 = profile.crops와 매칭되는
   boards`, `기타 작물 = 그 외`로 client-side 파생했다. 의도된 그룹핑인지 백엔드/디자이너
   확인 필요.
2. **디자인 시스템 불일치 (미선택 칩)**: Figma 미선택 칩은 `#f3f3f3` 배경 + `#4f4f4f`
   텍스트(테두리 없음)이나, DS `AppChip(.solidPastel)` 미선택은 흰 배경 + subtle 테두리 +
   그린 텍스트다. AGENTS 규칙(Figma가 DS와 충돌 시 DS 유지 + 보고, 컴포넌트 API 무단
   변경 금지)에 따라 **DS 값을 유지**했다. 픽셀 일치가 필요하면 `AppChip`에 새 variant를
   추가하는 승인이 필요하다. (선택 칩은 DS와 Figma가 정확히 일치)

## Runtime States To Define

- 로딩(작물 목록 fetch), 빈 목록(작물 없음), 오류·재시도.
- 아무것도 선택 안 된 초기 상태에서 `완료` 활성/비활성 여부.
