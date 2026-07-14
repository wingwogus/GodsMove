# 디자인 시스템 구현 핸드오프 (Figma → SwiftUI)

> 신한 해커톤 디자인 파일의 컴포넌트를 SwiftUI 디자인 시스템으로 옮기는 작업의 인수인계 문서.
> 다음 작업자(및 그 AI)가 **이 문서만 읽고 바로 이어서** 컴포넌트를 추가할 수 있도록 정리함.
> 작성 기준일: 2026-07-06.

---

## 0. 30초 요약

- **토대 완성**: 컬러 토큰 + 타입스케일이 Figma export와 1:1로 일치 (`Core/DesignSystem/Foundation/`).
- **컴포넌트 배치 완성**: 아래 [2. 완료 목록](#2-완료된-컴포넌트) 참고. 전부 `xcodebuild` 빌드 검증됨.
- **작업 방식**: Figma Dev Mode MCP로 노드별 스펙을 뽑아 → 기존 토큰에 매핑 → SwiftUI로 **수작업 변환**(React/Tailwind 코드는 참고용, 복붙 금지) → **모든 상태를 담은 `#Preview` 필수** → 빌드 검증.
- **남은 일**: 현재 문서 기준 남은 링크 없음. 링크는 프로젝트 소유자가 [5장](#5-남은-figma-mcp-링크-작업-대상)에 추가.

---

## 1. 환경 / 빌드

- **파일 키(fileKey)**: `44I8cu41vwv7JViVr8McUs`
- **스택**: SwiftUI, iOS 17+, Swift 6 strict concurrency, `@Observable`, SwiftData, URLSession. (자세히는 `frontend/AGENTS.md`)
- **컴포넌트 위치**: `ChamChamCham/ChamChamCham/Core/DesignSystem/`
  - `Foundation/` — `Color+App.swift`, `Font+App.swift`, `Spacing.swift` (토큰)
  - `Components/` — 재사용 UI 컴포넌트 (여기에 새 컴포넌트 추가)
- **빌드 명령** (`frontend/ChamChamCham`에서 실행):
  ```bash
  xcodebuild -project ChamChamCham.xcodeproj -scheme ChamChamCham \
    -destination 'platform=iOS Simulator,name=iPhone 17' build
  ```
  시뮬레이터 이름은 로컬에 따라 다름 (`xcrun simctl list devices available`로 확인 후 교체).
- **Xcode 16 파일시스템 동기화 그룹 사용** → `Components/`에 `.swift` 파일을 만들면 **타깃에 자동 포함**됨. `.xcodeproj` 수동 편집 불필요.

### ⚠️ SourceKit 가짜 오류 주의
새 파일 생성 직후 IDE/진단이 `Cannot find 'Spacing' in scope`, `Type 'Color' has no member 'Text'`, `no member 'appTypography'` 같은 오류를 쏟아낼 수 있음. **거의 대부분 인덱싱 지연에 의한 가짜 양성.** 판단 기준은 **오직 `xcodebuild` 결과**로 할 것. 빌드가 `** BUILD SUCCEEDED **`면 정상.

---

## 2. 완료된 컴포넌트

| Figma 노드 | 컴포넌트 | 파일 |
|---|---|---|
| 290-7018 | text-input | `Components/AppTextField.swift` |
| 290-7055 | date-input | `Components/AppDateField.swift` |
| 290-7096 | text-area | `Components/AppTextEditor.swift` |
| 290-7137 | search | `Components/AppSearchBar.swift` |
| 341-3999 | select / dropdown | `Components/AppDropdown.swift` |
| 341-1591 | button | `Components/AppButton.swift` |
| 290-7009 | toggle | `Components/AppToggle.swift` |
| 341-1558 | badge / label | `Components/AppBadge.swift` |
| 341-1590 | badge / 알림(숫자·dot) | `Components/AppNotificationBadge.swift` |
| 341-4716 | chip | `Components/AppChip.swift` |
| 290-7000 | tabs (밑줄 탭바) | `Components/AppTabBar.swift` |
| 368-5492 / 368-5502 | segmented-control | `Components/AppSegmentedControl.swift` |
| 290-6914 | top-app-bar | `Components/AppTopAppBar.swift` |
| 290-6974 | nav-bar (하단 탭) | `Components/AppNavBar.swift` |
| 341-1160 | image-upload / 이미지 슬롯 | `Components/AppImageUploadSlot.swift` |
| 341-1161 | avatar | `Components/AppAvatar.swift` |
| 341-1970 | toast / message | `Components/AppToast.swift` |
| 450-6528 | card (size xsmall/small/medium/large) | `Components/AppCard.swift` |
| 341-2440 | list (size xsmall~xlarge, thumbnail right-aligned) | `Components/AppListItem.swift` |
| 341-4561 / 341-4551 | comment row | `Components/AppCommentRow.swift` |
| 515-2653 | sort | `Components/AppSortButton.swift` |
| (공용) | 입력 필드 공통 chrome | `Components/AppFieldContainer.swift` |
| (공용) | 이미지 체크보드 placeholder | `Components/AppImagePlaceholder.swift` |

- `PrimaryButton.swift`는 `AppButton`의 얇은 래퍼로 재구현됨 (온보딩/랜딩 기존 호출부 7곳 호환 유지).
- **입력 계열(text-input / date-input / text-area / select)은 `AppFieldContainer`를 공유** — 라벨(`레이블 *`) + 테두리 박스 + 헬퍼/에러 라인을 한 곳에서 관리. 새 입력형 컴포넌트도 이걸 재사용할 것.

---

## 3. 토큰 매핑 레퍼런스 (Figma → 코드)

컬러는 이미 `Color+App.swift`에 Figma export(Primitives + Light 시맨틱)와 **1:1로 반영**돼 있음. 새 컴포넌트에서 색을 하드코딩하지 말고 아래 시맨틱 토큰을 쓸 것.

### 컬러 (Figma 시맨틱 → 코드)
| Figma | 코드 | 예시 값 |
|---|---|---|
| `object/default` | `Color.Object.default` | #FFFFFF |
| `object/subtle` | `Color.Object.subtle` | #FAFAFA |
| `object/muted` | `Color.Object.muted` | #F3F3F3 |
| `object/strong` | `Color.Object.strong` | #E0E0E0 |
| `object/bold` | `Color.Object.bold` | #343434 |
| `object/primary` | `Color.Object.primary` | #38C284 |
| `object/primary-subtle` | `Color.Object.primarySubtle` | #E4F8E3 |
| `object/secondary` | `Color.Object.secondary` | #E6F7BF |
| `object/secondary-subtle` | `Color.Object.secondarySubtle` | #F9FCF3 |
| `object/disabled` | `Color.Object.disabled` | #E0E0E0 |
| `object/disabled-subtle` | `Color.Object.disabledSubtle` | #F3F3F3 |
| `object/red` | `Color.Object.red` | #EF4444 |
| `object/red-subtle` | `Color.Object.redSubtle` | #FEE2E2 |
| `text/default` | `Color.Text.default` | #1A1A1A |
| `text/subtle` | `Color.Text.subtle` | #4F4F4F |
| `text/muted` | `Color.Text.muted` | #878787 |
| `text/disabled` | `Color.Text.disabled` | #ACACAC |
| `text/inverse` | `Color.Text.inverse` | #FFFFFF |
| `text/primary` | `Color.Text.primary` | #27865C |
| `text/secondary` | `Color.Text.secondary` | #699018 |
| `text/red` | `Color.Text.red` | #EF4444 |
| `icon/default` | `Color.Icon.default` | #343434 |
| `icon/subtle` | `Color.Icon.subtle` | #686868 |
| `icon/disabled` | `Color.Icon.disabled` | #ACACAC |
| `icon/inverse` | `Color.Icon.inverse` | #FFFFFF |
| `icon/primary` | `Color.Icon.primary` | #2DA972 |
| `icon/red` | `Color.Icon.red` | #EF4444 |
| `border/default` | `Color.Border.default` | #E0E0E0 |
| `border/subtle` | `Color.Border.subtle` | #F3F3F3 |
| `border/strong` | `Color.Border.strong` | #ACACAC |
| `border/primary` | `Color.Border.primary` | #38C284 |
| `border/disabled` | `Color.Border.disabled` | #CFCFCF |
| `border/error` | `Color.Border.error` | #EF4444 |
| `background/default` | `Color.Background.default` | #FFFFFF |
| `background/subtle` | `Color.Background.subtle` | #FAFAFA |

> 주의: Figma의 `error`/`required` 계열은 시맨틱 정리 후 **`red`로 통일**됨(값 #EF4444). `text/error`(#DC2626)·`text/onPrimary`·`icon/muted`는 export에 없어 제거됨.

### 타이포 (Figma px → 코드)
`.appTypography(_:)` 모디파이어로 폰트+자간+행간을 한 번에 적용.
| Figma 스타일 | 크기 | 코드 |
|---|---|---|
| headline/large | 32 Bold | `.headlineLarge` |
| headline/medium | 28 Bold | `.headlineMedium` |
| title/large | 24 | `.titleLarge` / `.titleLargeEmphasized` |
| title/medium | 20 | `.titleMedium` / `.titleMediumEmphasized` |
| body/large | 18 | `.bodyLarge` / `.bodyLargeEmphasized` |
| body/medium | 16 | `.bodyMedium` / `.bodyMediumEmphasized` |
| label/medium | 15 | `.labelMedium` / `.labelMediumEmphasized` |

### 간격 (`Spacing` enum)
`xs=4, sm=8, md=16, lg=24, xl=32`. 이 토큰에 없는 값(14, 12, 라운드 8/12/16 등)은 Figma 스펙 그대로 리터럴로 씀(주석으로 근거 표기).

---

## 4. 컴포넌트 구현 워크플로우 (처음부터 끝까지)

새 Figma 노드 링크 하나를 SwiftUI 컴포넌트로 만드는 표준 절차.

> 로컬 Talk-to-Figma MCP 연결 절차는 [`FIGMA_MCP_WORKFLOW.md`](FIGMA_MCP_WORKFLOW.md)를 먼저 참고.

### STEP 1 — 노드 ID 추출
링크 `...?node-id=341-1160` → nodeId = `341:1160` (또는 `341-1160` 그대로). fileKey는 `44I8cu41vwv7JViVr8McUs` 고정.

### STEP 2 — 스크린샷으로 정체 파악
Figma MCP `get_screenshot(fileKey, nodeId)` 호출 → 반환된 URL을 `curl`로 내려받아 **눈으로 확인**. 어떤 컴포넌트인지, **상태/variant가 몇 개인지**(default/focus/filled/error/disabled, size, selected 등) 파악.

- 개별 컴포넌트/섹션 노드는 잘 됨. **페이지 전체 노드는 너무 커서 `get_metadata`가 타임아웃**나므로 시도하지 말 것.
- `get_variable_defs`는 원격 서버에서 "nothing selected" 오류가 날 수 있음 → 대신 아래 STEP 3의 `get_design_context`가 토큰명+값을 함께 반환하므로 그걸로 충분.

### STEP 3 — 정확한 스펙 추출
`get_design_context(fileKey, nodeId, clientFrameworks:"swiftui", clientLanguages:"swift")` 호출. 대형 variant 세트는 `forceCode:true` 추가.
- 반환된 **React/Tailwind 코드는 "참고용"** — 색/간격/라운드/폰트/상태 분기 스펙을 읽는 용도. **절대 그대로 붙여넣지 말 것.**
- 특히 export 코드의 `.frame(width: 350)`, `Constants.objectDefault = .white` 같은 **고정 너비/하드코딩 색은 버리고**, 아래 규칙대로 변환.

### STEP 4 — SwiftUI로 변환 (핵심 규칙)
1. **상태는 SwiftUI 상태에서 파생.** Figma의 `variant` 중 런타임 상태(focus/filled/error/disabled)는 열거형으로 받지 말고 파생:
   - focus → `@FocusState` (또는 시트/피커 열림 플래그)
   - filled → 바인딩 값이 비어있는지
   - error → `errorMessage != nil`
   - disabled → `@Environment(\.isEnabled)` (호출부에서 `.disabled(true)`)
   - 진짜 디자인 변형(size, style, variant 색상)만 열거형 파라미터로.
2. **입력형이면 `AppFieldContainer` 재사용** (라벨+박스+헬퍼). 텍스트에어리어처럼 라운드가 다르면 `cornerRadius:` 파라미터 전달.
3. **반응형**: Figma 고정 너비(350/390) 쓰지 말 것. 가로는 `.frame(maxWidth: .infinity)` 또는 콘텐츠 hug. 고정 **높이**(48/56/64 등)와 라운드는 스펙대로 OK. → 모든 아이폰 폭에 자동 대응.
   - iPhone 13 기준 Figma라도 iPhone SE 2/3에서 제목·탭 라벨·주요 CTA가 잘리거나 겹치면 안 됨. 특히 xsmall/onboarding/list/card류는 `lineLimit`, `minimumScaleFactor`, 스크롤, safe-area-aware bottom action을 먼저 검토할 것.
4. **색/폰트/간격은 3장 토큰만 사용.** 하드코딩 hex 금지.
5. **아이콘은 SF Symbol로 받기** (`systemImage: String`). Figma 커스텀 아이콘 세트가 아직 코드에 없음 — 임포트되면 교체 예정([6. 캐비엇](#6-알려진-이슈--판단이-필요한-것) 참고).
6. **네이밍/위치**: `App<컴포넌트>` (예: `AppTooltip`), `Core/DesignSystem/Components/`에 새 파일.
7. **파일 헤더 주석 필수** (기존 파일과 동일 템플릿):
   ```swift
   //
   //  <FileName>.swift
   //  ChamChamCham
   //
   //  Created by iyungui on <M/d/yy>.
   //
   ```

### STEP 5 — ⭐️ Preview 꼼꼼히 작성 (필수, 생략 금지)
**모든 컴포넌트에 `#Preview`를 반드시 작성하고, 존재하는 상태/variant/size 조합을 빠짐없이 보여줄 것.** 이건 선택이 아니라 이 프로젝트의 필수 규칙임.
- 예: 입력 필드 → default / focus(값 있음) / error / disabled 4가지.
- 예: 버튼 → 각 variant(primary/secondary/tertiary/neutral) × 대표 size × enabled/disabled, icon-only 포함.
- 예: 토글/세그먼트/탭 → on/off·선택 인덱스 변화를 `@State`로 상호작용 가능하게 (`struct Demo: View { @State ... }` 패턴, 기존 파일들 참고).
- 바인딩이 필요한 컴포넌트는 프리뷰 안에 `struct Demo: View`를 만들어 `@State`로 구동 (기존 `AppTextField`, `AppToggle`, `AppDropdown` 프리뷰가 표준 예시).

### STEP 6 — 빌드 검증
STEP 1의 `xcodebuild` 명령 실행. `** BUILD SUCCEEDED **` 확인. SourceKit 진단 오류는 무시(위 [⚠️ 가짜 오류](#️-sourcekit-가짜-오류-주의)).

### STEP 7 — 커밋
논리 단위(컴포넌트 1개 또는 배치)로 커밋. 메시지에 관련 있으면 `BR-*` 규칙 ID 참조. 무관한 기존 변경 파일은 함께 커밋하지 말 것.

---

## 5. 남은 Figma MCP 링크 (작업 대상)

현재 문서에 남아 있던 노드들은 2026-07-06 `feat/design-system-handoff` 배치에서 구현 완료. **프로젝트 소유자가 필요 시 링크를 계속 추가**.

> 링크의 node-id는 `341-1160` = nodeId `341:1160` 식으로 변환해서 MCP 호출.

---

## 6. 알려진 이슈 / 판단이 필요한 것

1. **아이콘 세트 미임포트**: 현재 모든 컴포넌트가 SF Symbol을 씀. Figma 커스텀 아이콘(icon/home, icon/forum, icon/assignment, icon/calendar_month 등)을 **애셋(SVG/PDF)으로 임포트하는 별도 작업**이 필요하고, 그 후 각 컴포넌트의 SF Symbol을 실제 아이콘으로 교체하면 됨. (임팩트 큰 다음 작업 후보)
2. **타이포 토큰 불일치**: Figma의 `label/medium-emphasized`는 **Bold 15**인데, 코드 `AppTypography.labelMediumEmphasized`는 (과거 스펙 근거로) **Medium**으로 정의돼 있음. `AppNavBar` 선택 라벨만 임시로 `Pretendard-Bold`를 직접 지정해 Figma와 맞춰둠. → **`labelMediumEmphasized`를 Bold로 바꿀지 팀 결정 필요**. 바꾸면 nav-bar의 임시 코드도 정리 가능.
3. **`AppTextEditor` placeholder 위치**: SwiftUI `TextEditor`의 내부 inset 특성상 placeholder를 ±5pt 근사로 배치함. 미세 조정 여지 있음.
4. **`AppDropdown`**: Figma엔 없지만 `errorMessage`(빨강 상태)를 확장으로 추가. 포커스 시 테두리는 Figma대로 초록으로 안 변함(chevron만 flip).
5. **텍스트/날짜 선택 UX**: `AppDateField`·`AppDropdown`은 `.sheet` + graphical picker / List로 구현. 필요 시 팀 UX에 맞게 교체 가능.

---

## 7. 참고 — 좋은 예시 파일
새 컴포넌트 작성 시 아래를 템플릿으로 참고:
- 입력형(라벨+박스+헬퍼): `AppTextField.swift` + `AppFieldContainer.swift`
- 다차원 variant 세트: `AppButton.swift` (variant×size×type을 파생 파라미터로 압축)
- 선택형/토글: `AppToggle.swift`, `AppSegmentedControl.swift`, `AppChip.swift`
- 바인딩 프리뷰 패턴: 위 파일들의 `#Preview` 내부 `struct Demo: View { @State ... }`
