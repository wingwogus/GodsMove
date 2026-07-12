# Figma Capture: Onboarding Step 1 — Social Prefill Default

- Captured at: 2026-07-11
- Source: TalkToFigma MCP `get_selection`, `read_my_design`, and provided flow notes
- Figma node: `631:10943`
- Frame name: `onboarding / step 1 default - 소셜 로그인 데이터 받아온 경우`
- Frame size: `390 x 1117`
- Purpose: social login response에서 받을 수 있는 회원 정보를 Step 1에 미리 채운 기본 상태. 자격은 개인 농업인 하나가 선택된 상태다.

## Flow Contract

### Entry condition

소셜 로그인 백엔드 교환이 성공했고, `LoginResponse.onboarding.status == REQUIRED`인
회원이 진입한다. access/refresh token 저장과 member cache 저장 뒤 이 화면으로 이동한다.

### Prefill rule

`LoginResponse.member`에서 값이 있는 항목은 **새 초안에만** 먼저 반영한다.

| LoginResponse member field | Step 1 field | Figma 상태 |
| --- | --- | --- |
| `name` | 이름 | 값이 있으면 prefill |
| `phone` | 연락처 | 값이 있으면 prefill |
| `birthDate` | 생년월일 | 값이 있으면 `yyyy.MM.dd` 표시 |
| `nickname` | 닉네임 | 값이 있으면 prefill, 없으면 필수 입력 |
| `profileImageUrl` | 프로필 사진 | 원격 이미지 표시 여부는 별도 상태 캡처 필요 |

이 규칙은 이전에 저장된 **동일 member의 초안**을 덮어쓰지 않는다. 다른 member의
초안을 불러오지 않는 정책은 전체 흐름 검토의 P0 항목과 함께 구현한다.

현재 Figma는 이름·연락처에 회색 배경을 사용한다. 이번 캡처에서는 읽기 전용인지
확정할 수 없으므로, 구현 계획의 기본안은 `prefill 후 수정 가능`이다. 최종 디자인에서
읽기 전용을 의도했다면 provider 값 오류·변경을 수정할 별도 경로가 필요하다.

### Continue condition

다음 필드가 모두 유효할 때만 `다음` 버튼을 활성화한다.

- 이름
- 닉네임
- 연락처
- 생년월일
- 영농 경력/귀농 년차의 확정된 의미에 맞는 정수 값
- 자격 1개

사진은 선택 사항이다. `다음`은 다음 Figma 온보딩 단계로 이동하고, 현재 초안을
저장한다. 최신 Figma는 대표 재배지 지도 화면을 Step 2로 표시하므로, 작물 선택의
최종 위치는 후속 Step 캡처까지 확인한 뒤 확정한다.

## Screen Structure

이 프레임은 iPhone 13 폭 `390pt` 기준이다. 상태바 템플릿과 하단 CTA를 제외한
폼은 iPhone SE 2/3에서도 스크롤 가능해야 한다.

1. Status bar template: `54pt` — 앱에서 별도 구현하지 않는다.
2. Top app bar: `390 x 60` — leading/trailing `48 x 48` 슬롯, 이번 상태에는 텍스트 타이틀 없음.
3. Progress area: `390 x 20`, 좌우 inset `20`.
4. Content: 헤더, 프로필 사진, 6개 입력/선택 영역.
5. Bottom CTA: `390 x 100`, 좌우 inset `20`의 `350 x 56` 버튼.

### Progress

- Track: `350 x 4`, `#f3f3f3`, capsule.
- Active fill: `88 x 4`, `#38c284`, capsule.
- 의미: 사용자 입력 4단계 중 Step 1의 25%.

랜딩 로그인과 제출 진행 화면은 progress 단계에 포함하지 않는다. 현재 SwiftUI의
5-step 진행 바는 이 캡처와 일치하지 않는다.

### Header and profile photo

- 헤더 inset: `20`.
- Title: `기본 정보 설정하기`, Pretendard SemiBold `28`, `#1a1a1a`, line height `36.4`.
- Subtitle: `프로필 정보를 입력해주세요.`, Pretendard Medium `18`, `#878787`, line height `27`.
- Profile image: 중앙 `96 x 96`, 원형, 기본 fill `#f3f3f3`, border `#e0e0e0`.
- Upload affordance: 사진 오른쪽 아래 `36 x 36` 원형, `#343434`, camera icon `24 x 24`.

프로필 사진은 선택 사항이다. 선택된 로컬 사진은 즉시 미리보기하고, 업로드는 최종
제출 시 실행한다.

### Input fields

모든 row는 `350 x 88`, label 높이 `24`, field `350 x 56`, radius `8`을 사용한다.
일반 label은 Pretendard Medium `16`, `#1a1a1a`; required marker는 `#ef4444`다.
field value/placeholder는 Pretendard Medium `18`, line height `27`이다.

| 순서 | 라벨 | 기본 상태 | 비고 |
| --- | --- | --- | --- |
| 1 | 이름 `*` | 소셜 데이터가 있으면 `#f3f3f3` field에 prefill | 현재 캡처 텍스트: `소셜 로그인 데이터` |
| 2 | 닉네임 `*` | 빈 흰 field, placeholder `닉네임을 입력해주세요.` | backend와 Figma 모두 필수 |
| 3 | 연락처 `*` | 소셜 데이터가 있으면 `#f3f3f3` field에 prefill | 현재 캡처 예시: `000-0000-0000` |
| 4 | 생년월일 `*` | 날짜 field, calendar icon `24 x 24`, placeholder `yyyy.mm.dd` | 소셜 값이 있으면 같은 형식으로 prefill |
| 5 | 귀농 년차 `*` | 빈 흰 field, placeholder `귀농 년차를 입력해주세요.` | 현재 product/DTO의 `영농 경력(년차)`와 의미 확인 필요 |
| 6 | 자격 `*` | 세 개 중 개인 농업인 선택 | 아래 상세 참조 |

각 row에 보이는 `메시지를 전달합니다.`는 Figma 입력 컴포넌트의 도움말 자리다.
실제 구현에서는 기본 상태에서 숨기고 validation 오류일 때 구체적 메시지를 표시한다.

## Qualification Selection

### Default

- Header: `자격 *`, 우측 info icon `24 x 24`.
- Selection row: `350 x 48`, 세 단일 선택 영역으로 구성.
- Selected: `개인 농업인`, `111.33 x 48`, fill `#e4f8e3`, stroke `#38c284`, text `#27865c`.
- Unselected: `농업경영체 법인`, `비경영체`, fill `#fafafa`, stroke `#f3f3f3`, text `#878787`.

| 표시 라벨 | API 값 | 툴팁 제목 |
| --- | --- | --- |
| 개인 농업인 | `AGRICULTURAL_INDIVIDUAL` | 농업경영체(농업인) |
| 농업경영체 법인 | `AGRICULTURAL_CORPORATION` | 농업경영체(법인) |
| 비경영체 | `NON_REGISTERED_FARMER` | 농업인(비경영체) |

### Info tooltip proposal — approved for capture planning

Figma에 열린 tooltip 상태는 아직 없으므로, info icon tap 시 **draggable bottom
sheet**를 연다. 세그먼트 선택 상태는 바꾸지 않으며, sheet 밖 탭·drag down·닫기
버튼으로 닫힌다. iPhone SE에서도 본문을 스크롤할 수 있게 한다.

- Sheet title: `농업경영체 자격 안내`
- 개인 농업인: `일반 — 개인 자격으로 농업경영체에 등록된 농업인`
- 농업경영체 법인: `법인 — 영농조합법인·농업회사법인 등`
- 비경영체: `미가입 — 농업경영체에 등록되지 않은 농업인`
- 하단 action: `확인` — sheet만 닫고 선택 값은 변경하지 않는다.

이 내용은 설명 전용이다. 자격 선택은 항상 세그먼트 중 하나를 직접 탭해서만
변경한다.

## Bottom CTA

- Area: `390 x 100`, white background, top stroke `#f3f3f3`.
- Button: `350 x 56`, radius `12`.
- Default disabled: fill `#e0e0e0`, label `다음`, Pretendard Medium `18`, `#878787`.
- 활성 상태의 색상은 별도 Figma 캡처 또는 기존 `PrimaryButton` token을 기준으로 확정한다.

버튼은 safe-area-aware로 고정하되, form의 마지막 자격 선택 영역이 버튼에 가려지지
않도록 scroll bottom inset을 둔다. 키보드가 표시될 때 활성 입력과 버튼 모두 접근
가능해야 한다.

## Captured Design vs Current Implementation

| 항목 | Figma/flow 기준 | 현재 SwiftUI | 계획 반영 |
| --- | --- | --- | --- |
| 소셜 prefill | name, phone, birthDate 등 받을 수 있는 값 채움 | login response를 draft에 옮기지 않음 | 필요 |
| 닉네임 | 필수 | CTA·DTO 방어 검증 모두 누락 | P0 |
| 자격 | 구체적 3개 라벨, info icon | 일반/법인/미가입 segmented picker, inline guide | Figma 맞춤 변경 |
| 진행률 | 1/4 | basicProfile에서 2/5 | 단계 정의 변경 |
| 경력 라벨 | `귀농 년차` | `영농 경력(년차)` | 제품 의미 확정 필요 |
| 레이아웃 | scroll + fixed CTA가 필요한 1117pt form | non-scroll `VStack` | P1 |

## Runtime States Still Needed

- social provider가 이름/연락처/생년월일 중 일부 또는 전부를 주지 않은 상태
- prefill된 연락처·생년월일의 format validation
- 닉네임 중복 또는 서버 validation 오류가 존재한다면 해당 오류 상태
- 사진 선택 취소, 로컬 파일 저장 실패, 선택된 사진 preview
- date picker가 열린 상태와 키보드가 열린 text-input 상태
- 자격 info bottom sheet 열린 상태
- 모든 필수값 유효한 `다음` 활성 상태
