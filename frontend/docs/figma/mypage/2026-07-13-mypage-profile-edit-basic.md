# Figma Capture: My Page / Profile Edit / 기본 정보 수정

- Captured at: `2026-07-13 KST`
- Source: TalkToFigma MCP `join_channel(chamchamcham)`, `get_selection`,
  `read_my_design`, `export_node_as_image`
- Figma node: `1247:18133`
- Frame name: `프로필 수정 / 기본 정보 수정`
- Frame size: `390 x 1062`
- Capture state: 프로필 수정 화면의 `기본 정보` 탭. 아바타 편집 + 폼 필드
  (이름/닉네임/연락처/생년월일/자격/귀농 연차) + 하단 `저장` 버튼.
- PNG export: `export_node_as_image(PNG, scale 2)` 성공, 렌더 육안 검증함.
  (Claude Code 파일 저장 불가 — HANDOFF Part 2 참고.)

## Screen Structure

위→아래, 앱 셸(status bar/top-app-bar/tab-bar) + 스크롤 폼 + 고정 하단 버튼.

1. **status bar** (`390 x 54`) — 디바이스 크롬.
2. **top-app-bar** (`390 x 60`): leading 뒤로가기(`arrow_back_ios_new` 32),
   가운데 타이틀 `프로필 수정`, trailing 아이콘 슬롯(빈 상태).
3. **tab-bar** (`390 x 56`): `기본 정보`(선택) / `농업 정보`(비선택). 각 탭 `195` 폭.
4. **avatar** (`96 x 96`): 편집 어포던스 `36 x 36`(fill `#343434`) + edit 아이콘.
5. **content** (`390 x 600`, 좌우 inset `20`, 필드 폭 `350`): 폼 필드들.
6. **bottom-button** (`390 x 100`, top border `#f3f3f3`): `저장` 버튼 `350 x 56`.

`농업 정보` 탭 내용은 별도 프레임(미수집).

## Top App Bar (수정 화면 변형)

프로필 메인 top-app-bar와 **다른 변형**이다.

| | 프로필 메인 (`1247:17756`) | 프로필 수정 (`1247:18160`) |
|---|---|---|
| Leading | 없음 | 뒤로가기 `arrow_back_ios_new` |
| 타이틀 | `나의 프로필` Bold 32 | `프로필 수정` SemiBold 28 |
| Trailing | 설정 + 알림 | 빈 슬롯 |

타이틀: Pretendard SemiBold `28` / lh `36.4` / tracking `-0.28`, `#242428`.

## Tab Bar

`기본 정보`(선택) / `농업 정보`(비선택). 프로필 메인 콘텐츠 탭과 동일 컴포넌트로 보임.

- 선택 탭: 하단 border `#38c284`, 텍스트 `#242428`, Pretendard SemiBold 20 / lh 26.
- 비선택 탭: 텍스트 `#878787`, Pretendard Medium 20 / lh 26.

## Form Field Component (`text-input`)

각 필드는 `text-input` 인스턴스: 헤딩(라벨 + 필수표시) + 입력 field + 헬퍼 메시지.

- 필드 블록 세로 리듬: 약 `104` 간격(heading 24 + gap 8 + field 56 + 헬퍼 영역).
- Heading 라벨: Pretendard Medium `16` / lh `24` / `-0.32`, `#1a1a1a`.
- 필수 표시 `*`: `#ef4444`.
- Field: `350 x 56`, radius `8`, border `#e0e0e0`, 좌 패딩 `16`, 우측 아이콘 24×24
  (우 패딩 16).
- Field 텍스트: Pretendard Medium `18` / lh `27` / `-0.36`.
- 헬퍼 메시지(`메시지를 전달합니다.` placeholder): Pretendard Medium `15` /
  lh `19.5` / `-0.3`, `#878787`. (검증/도움말 라인)

### Field States (캡처 근거)

| State | Field fill | Field 텍스트 색 | 예시 |
|---|---|---|---|
| Editable (기본) | `#ffffff` | `#1a1a1a` | 닉네임 `인삼왕`, 연락처, 귀농 연차 |
| Disabled/readonly | `#f3f3f3` | `#acacac` | 이름 `장윤서` (회색 처리, 수정 불가로 보임) |
| Placeholder(날짜) | `#ffffff` | `#1a1a1a`(포맷 힌트) | 생년월일 `yyyy.mm.dd` |

## Fields (기본 정보 탭)

| 필드 | 필수(data) | 입력 타입 | 값/힌트 | 우측 아이콘 |
|---|---|---|---|---|
| 이름 | `*` | text (disabled) | `장윤서` (회색) | 있음 |
| 닉네임 | `*`(주의) | text | `인삼왕` | 있음 |
| 연락처 | `*` | text | `000-0000-0000` | 있음 |
| 생년월일 | `*` | date | `yyyy.mm.dd` | `calendar_month` |
| 자격 | `*` | 세그먼트 선택 | 개인 농업인 / 농업경영 법인 / 비경영체 | header에 `info` 아이콘 |
| 귀농 연차 | `*` | text | `2년차` | 있음 |

주의: `read_my_design` 데이터상 **닉네임에도 required(`*`) 노드**가 있으나 렌더에서
별표가 뚜렷하지 않다. 닉네임 필수 여부는 BR/Swagger로 확정 필요.

## Qualification Selector (자격 · 세그먼트)

`select-item` 3개 가로 배열. 각 `≈111.33 x 48`, radius `8`, 아이템 간격 `8`.

| State | Fill | Border | 텍스트 |
|---|---|---|---|
| 선택됨 (개인 농업인) | `#e4f8e3` | `#38c284` | `#27865c` |
| 비선택 (농업경영 법인, 비경영체) | `#fafafa` | `#f3f3f3` | `#878787` |

라벨: Pretendard Medium `16` / lh `24` / `-0.32`. 헤더에 `info` 아이콘(설명 툴팁?).

## Bottom Button

- 컨테이너 `390 x 100`, top border `#f3f3f3`, 배경 `#ffffff`.
- 버튼 `350 x 56`, radius `12`, fill `#343434`, 라벨 `저장` `#ffffff`
  (Pretendard Medium 18 / lh 27 / -0.36).
- 바텀시트 `완료` 버튼과 동일 버튼 컴포넌트로 보임(라벨만 다름).

## Existing Component Candidates

실제 API는 `Core/DesignSystem/` 확인 후 확정.

- top-app-bar (back + title + trailing) 변형
- tab-bar (프로필 메인과 공유 가능성)
- `text-input` 폼 필드 (label/required/field/helper, states: editable/disabled/
  error) — 온보딩 등 다른 폼과 공유 가능성 높음, 우선 재사용 검토
- `date-input` (text-input + calendar 아이콘 변형)
- `select-item` 세그먼트 선택 (selected/unselected)
- primary button (저장/완료 공용)
- avatar + edit 어포던스

## Product / API Questions

- 이 폼은 member 프로필 수정. 필드 매핑을 배포 Swagger member update DTO와 대조:
  이름(name), 닉네임(nickname), 연락처(phone/contact), 생년월일(birthdate),
  자격(개인 농업인 / 농업경영 법인 / 비경영체 → enum), 귀농 연차(farming years).
- 이름이 disabled인 이유(본인인증 값? 수정 불가 정책?) 확인 필요.
- `농업 정보` 탭 필드 구성 미수집 — 별도 캡처 필요.
- 자격 `info` 아이콘 동작(툴팁/바텀시트?) 정의 필요.
- 저장 버튼 활성 조건(필수값 검증), 검증 실패 시 헬퍼 메시지/색상 상태 정의 필요.
- offline-first: 수정 저장은 로컬 우선 반영 후 동기화(AGENTS 규칙) — 구현 계획에서
  네트워크 실패/재시도 처리 정의.

## Runtime States To Define

- 로딩(기존 값 프리필), 저장 중(버튼 로딩/비활성), 저장 성공/실패.
- 필드별 검증 오류 상태(헬퍼 메시지 색상 — `#ef4444` 계열?), 미확인.
- iPhone SE 2/3: 폼 스크롤 + 키보드가 활성 입력/저장 버튼 가리지 않게(AGENTS 규칙).
