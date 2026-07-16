# Record / 음성으로 기록하기 (default)

- **Captured date**: 2026-07-16
- **Source MCP calls**: `get_selection`, `read_my_design` (TalkToFigma, channel `chamchamcham`)
- **Figma node ID**: `1257:25950`
- **Frame name**: `텍스트로 기록하기 / 음성 기록하기`
- **Frame size**: 390 × 844 (iPhone 13), fill `#ffffff` (`Background.default`)
- **State**: 음성 기록 진입 직후 default. 프롬프트 말풍선 1개 + 사용자(마이크 전사) 말풍선 1개가 이미 떠 있는 목업 상태. 마이크 버튼 idle, 완료 버튼 disabled.
- **PNG**: 미저장 (TalkToFigma export는 인라인 이미지만 반환 → 디스크 저장 불가; 구조/치수/텍스트/색상은 아래 `read_my_design` 값으로 정밀 기록).

## 레이아웃 (위→아래, 절대좌표 기준)

| 영역 | node | y (프레임 상단=2607 기준 offset) | 크기 | 비고 |
|---|---|---|---|---|
| Status Bar - iPhone | `1257:25977` | 0 | 390×54 | 시스템 상태바 (safe area) |
| top-app-bar | `1257:25976` | 54 | 390×60 | 타이틀 "기록하기" 중앙, leading `icon/arrow_back_ios_new`, trailing 비어있음 |
| chat (컨테이너) | `1257:26100` | 134 | 390×230 | 말풍선 세로 스택 (VStack) |
| ├ chat (프롬프트, 상대) | `1257:26101` | 134 | 390×118 | 왼쪽 정렬, bg `#f3f3f3` |
| └ chat (사용자, 내것) | `1257:26102` | 272 | 390×92 | 오른쪽 정렬, bg `#38c284` |
| button (mic FAB) | `1257:26040` | 616 | 96×96 | 원형(cornerRadius 1000), bg `#343434`, `icon/mic` 40×40 |
| bottom-button | `1257:25974` | 744 | 390×100 | 하단 고정 버튼 컨테이너, bg `#ffffff` |
| └ button "완료" | `1257:25975` | 756 | 350×56 | cornerRadius 12, disabled: bg `#e0e0e0`, 텍스트 `#878787` |

> mic 버튼 중심 x = 프레임 중앙(수평 센터). bottom-button 위에 겹쳐 떠 있는 형태(overlay), 컨테이너 위 여백에 위치.

## 텍스트 스타일

| 요소 | 폰트 | weight | size | lineHeight | tracking | color | 토큰 |
|---|---|---|---|---|---|---|---|
| top-app-bar 타이틀 "기록하기" | Pretendard | SemiBold 600 | 28 | 36.4 | -0.28 | `#1a1a1a` | `Text.default` |
| 프롬프트 말풍선 | Pretendard | Medium 500 | 20 | 26 | -0.2 | `#1a1a1a` | `Text.default` |
| 사용자 말풍선 | Pretendard | SemiBold 600 | 20 | 26 | -0.2 | `#ffffff` | `Text.inverse` |
| 완료 버튼 라벨 | Pretendard | Medium 500 | 18 | 27 | -0.36 | `#878787` | `Text.muted` (disabled) |

프롬프트 텍스트: `오늘 어떤 작업을 하셨나요?  마이크를 누르고 자유롭게  말씀해주세요!`
사용자 텍스트(예시): `오늘 사과밭 전체적으로 소독약 쳤어. 아침 8시부터 12시까지 했고.`

## 색상 → 디자인 시스템 매핑 (전부 일치, 신규 토큰 불필요)

| Figma | 토큰 | 용도 |
|---|---|---|
| `#38c284` (Green.c600) | `Color.Object.primary` | 사용자 말풍선 배경 |
| `#f3f3f3` (Gray.c100) | `Color.Object.muted` | 프롬프트 말풍선 배경 |
| `#1a1a1a` (Gray.c900) | `Color.Text.default` | 프롬프트·타이틀 텍스트 |
| `#ffffff` (Gray.c0) | `Color.Text.inverse` | 사용자 말풍선 텍스트 |
| `#343434` (Gray.c800) | `Color.Object.bold` | 마이크 버튼 배경 (idle) |
| `#e0e0e0` (Gray.c200) | `Color.Object.disabled` | 완료 버튼 배경 (disabled) |
| `#878787` (Gray.c500) | `Color.Text.muted` | 완료 버튼 라벨 (disabled) |

**메모 [[text-default-color-1a1a1a]] 확인**: 타이틀 텍스트가 이 프레임에선 `#1a1a1a`(Text.default)로 되어 있음. 텍스트 기록 화면 캡처에서 열려 있던 `#242428` 불일치는 이 프레임엔 없음.

## 디자인 시스템 컴포넌트 매핑

- **말풍선**: `AppChatBubble(message:isMine:)` — 이미 이 캡처 스펙대로 구현됨. `isMine=false`(프롬프트, muted/default), `isMine=true`(사용자, primary/inverse). padding 20, cornerRadius 20, 폰트/색 모두 일치. **재사용 그대로 OK.**
- **top-app-bar**: `AppTopAppBar` 재사용 (기록하기 타이틀 + 뒤로가기). 텍스트 기록 화면(`RecordComposeView`)과 동일한 바 사용.
- **완료 버튼**: `AppButton` (텍스트 기록 화면과 동일한 하단 고정 primary 버튼, disabled 상태).
- **마이크 버튼(96pt 원형 + `icon/mic`)**: 기존 DS 컴포넌트 **없음**. FAB 스피드다이얼(72pt)과 크기/역할 다름. → 이 화면 전용 one-off 레이아웃으로 시작하고, 녹음 상태(idle/recording/processing)에 따라 아이콘·배경이 바뀌면 그때 컴포넌트 승격 검토.
- **아이콘**: `icon/mic`, `icon/arrow_back_ios_new` 모두 `Assets.xcassets/icon/`에 존재 확인.

## 상태/제품 미결 질문 (계획 단계에서 정의)

이 프레임은 "말풍선 2개 + idle 마이크 + disabled 완료"의 정적 목업 한 장면만 보여줌. 실제 음성 기록 플로우의 나머지 상태(녹음 중, STT 처리 중, 전사 실패, 완료 활성화 조건, AI 구조화 연동)는 캡처에 없으므로 임의로 지어내지 않는다. 계획 단계에서:

- 마이크 버튼 상태 머신: idle → recording → stop/processing → 전사 결과 말풍선 append. (BR-VOICE-*, BR-STATE-001 대조 필요)
- 완료 버튼 활성화 조건 (전사 결과 1개 이상? AI 구조화 성공 후?).
- STT/음성 인식 수단: 온디바이스(`Speech` framework) vs 백엔드 업로드 — API 준비도 확인(Swagger).
- 텍스트 기록과의 관계: 음성 전사 결과를 텍스트 compose 폼으로 넘겨 AI 구조화하는지, 별도 저장 경로인지.
- 다중 말풍선 스크롤(대화가 길어질 때) 및 SE 2/3 레이아웃.

## Figma ↔ 디자인 시스템 충돌

없음. 모든 색/폰트/토큰이 기존 디자인 시스템과 1:1 일치. 신규 파운데이션/토큰 추가 불필요.
