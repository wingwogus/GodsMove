# 디자인 시스템 / chat (bubble)

- Captured: 2026-07-16
- Source MCP calls: `mcp__TalkToFigma__get_selection` → `mcp__TalkToFigma__read_my_design` → `mcp__TalkToFigma__get_nodes_info` → `mcp__TalkToFigma__export_node_as_image`
- Figma node ID: `1257:26071` (COMPONENT_SET `chat`)
- Variant property: `my-chat` (boolean) — `false`(상대/시스템 발화) / `true`(내 발화)
- 용도: 음성으로 영농일지를 기록하는 대화형 플로우에서 시스템 프롬프트와 마이크로
  받아적힌 사용자 발화를 채팅 버블로 표시.

## PNG

파일로 저장하지 않음 (`export_node_as_image`는 인라인 이미지만 반환 — 육안 검증만
아래에 기록, 구조/색상/타이포는 `read_my_design`/`get_nodes_info` JSON으로 정밀 기록).

## Variant: `my-chat=false` (`1257:26070`)

- Bubble frame (`1257:26064`): 배경 `#f3f3f3`, 패딩 20(상하좌우 동일), 좌측 정렬
  (component 컨테이너 안에서 왼쪽에 붙고 우측에 여백)
- 텍스트 (`1257:26065`): "오늘 어떤 작업을 하셨나요?  마이크를 누르고 자유롭게  말씀해주세요!"
  Pretendard **Medium 20**, letterSpacing -0.2, lineHeight 26, color `#1a1a1a`, LEFT 정렬

## Variant: `my-chat=true` (`1257:26069`)

- Bubble frame (`1257:26067`): 배경 `#38c284`, 패딩 20(상하좌우 동일), 우측 정렬
  (component 컨테이너 안에서 오른쪽에 붙고 좌측에 더 넓은 여백)
- 텍스트 (`1257:26068`): "오늘 사과밭 전체적으로 소독약 쳤어. 아침 8시부터 12시까지 했고."
  Pretendard **SemiBold 20**, letterSpacing -0.2, lineHeight 26, color `#ffffff`, LEFT 정렬
  (버블 자체는 우측 정렬이지만 내부 멀티라인 텍스트는 좌측 정렬)

## Corner radius

`get_node_info`/`read_my_design` 응답에 cornerRadius 필드가 없었으나(0으로 보고됨
직전 스크린샷 상 뚜렷하게 라운딩된 형태를 확인 — `export_node_as_image` 결과 확인 후
20으로 추정(기존 `FarmCard.swift`의 20 라운딩과 동일 시각적 정도). Figma 플러그인
쪽 corner-radius 정밀값이 별도로 필요하면 재확인.

## 색상/타이포 토큰 매핑

| 용도 | hex | 디자인 시스템 토큰 |
|---|---|---|
| false 배경 | `#f3f3f3` | `Color.Object.muted` |
| false 텍스트 | `#1a1a1a` | `Color.Text.default` |
| true 배경 | `#38c284` | `Color.Object.primary` |
| true 텍스트 | `#ffffff` | `Color.Text.inverse` |
| false 타이포 (Medium 20) | — | `AppTypography.titleMedium` |
| true 타이포 (SemiBold 20) | — | `AppTypography.titleMediumEmphasized` |

패딩 20은 `Spacing`(4/8/16/24/32) 토큰에 없는 값 — genuinely missing one-off로 처리해
raw `20`을 그대로 사용(AGENTS.md 예외 조항).

## 구현

`Core/DesignSystem/Components/AppChatBubble.swift` 신규 컴포넌트로 구현, `isMine: Bool`
파라미터로 두 variant를 표현(기존 `AppComment.isMyComment` 네이밍 관례와 동일).
`DesignSystemGallery.swift`에 "Chat" 섹션으로 프리뷰 추가.

## 제품/API 질문 (미결)

- 실제 음성 인식 결과가 스트리밍/증분으로 들어올 때 버블이 실시간으로 텍스트를
  업데이트하는지, 완료 후 한 번에 렌더링되는지 — 음성 플로우 상태 머신(BR-VOICE-*)
  확정 필요
- 대화 히스토리 전체를 스크롤 리스트로 유지하는지, 마지막 프롬프트/발화 1쌍만
  보여주는지 — 화면 레이아웃(상위 프레임) 캡처 시 확인 필요
