# 댓글 입력창 하단 여백 — 조사 기록 (미해결)

- 작성일: 2026-07-19
- 관련 추적: [`../../RELEASE_HARDENING.md`](../../RELEASE_HARDENING.md) 축 B
  "커뮤니티 | 댓글 UI (하단 여백 제거)" (P1)
- 대상 파일: `Features/Community/Presentation/Views/CommunityDetailView.swift`
- 상태: **미해결.** 두 차례 수정 시도 모두 사용자가 실기기/시뮬레이터에서
  확인 후 "해결 안 됐다"고 반려함.

## 문제 정의 (사용자 지적, 정확히)

`CommunityDetailView`의 댓글 입력창(`AppCommentInput`, 화면 하단 고정) **아래쪽**에
불필요한 흰 여백이 남는다. 스크린샷 기준(2026-07-19 22:00 캡처): 입력창(`댓글을
입력해주세요.` 행)과 화면 맨 아래(홈 인디케이터 영역) 사이에 큰 빈 공간이 보임.

> 사용자 확인 코멘트: "댓글 입력 창 아래에 빈 여백을 줄이는거야."

주의: 이전에 잘못 짚었던 문제(원래 스크린샷 #1)는 **댓글 리스트와 입력창 사이**의
여백이었다. 사용자가 재확인한 스크린샷(#2, 10:00 캡처)은 **입력창 자체 아래**의
여백을 가리킨다 — 서로 다른 지점이다. 이후 시도는 모두 후자(입력창 아래 여백)를
겨냥했다.

## 시도 1 — `ScrollView` 콘텐츠 하단 padding 제거 (되돌림)

**가설**: `content`의 `ScrollView` VStack에 걸린 `.padding(.bottom, Spacing.xl)`
(32pt)가 원인이다.

**변경**: `CommunityDetailView.swift` 166번째 줄 부근,
`.padding(.bottom, Spacing.xl)` 제거.

**결과**: 사용자가 "그런 일차원적인 해결로는 당연히 안 된다"고 반려. 원인 분석
결과, 이 padding은 **스크롤 콘텐츠 자체의 하단 여백**(댓글 리스트 마지막 항목
아래)이었고, 실제 문제는 **입력창 아래**(safe area 처리 쪽)였다. 즉 애초에
잘못된 지점을 겨냥한 수정이었다. **코드와 `RELEASE_HARDENING.md` 상태 모두
원복 완료.**

## 시도 2 — `safeAreaInset` / `ignoresSafeArea` 이중 처리 제거 (현재 작업 트리에 적용된 상태, 미해결)

**재분석**: `commentComposer`가 `.safeAreaInset(edge: .bottom) { ... }`로 붙는데,
`commentComposer` 내부에 `.ignoresSafeArea(edges: .bottom)`가 걸려 있었다.
`safeAreaInset`은 이미 inset 뷰를 안전 영역까지 포함해 배치하는데, 그 안에서
다시 `ignoresSafeArea`를 걸면 SwiftUI가 컴포저 뷰를 실제 필요 높이보다 더
크게(안전 영역만큼 더 확장된 것으로) 측정 → 상위 스크롤 콘텐츠가 그만큼 더 많은
하단 inset을 확보 → `AppCommentInput`의 자체 하단 padding(비포커스 시 32pt,
`AppCommentInput.swift:108`, Figma 기준 이미 홈 인디케이터 클리어런스 포함 설계)
위에 안전 영역 높이(~34pt)가 한 번 더 겹쳐 잡힌다는 가설.

이 코드베이스의 다른 하단 고정 바들(`RecordComposeView`, `FarmListView`,
`ProfileBasicInfoView`, `BasicProfileView`)은 모두 `.safeAreaInset(edge: .bottom,
spacing: 0)`만 쓰고 `ignoresSafeArea`를 걸지 않는 패턴 — `CommunityDetailView`만
이 패턴에서 벗어나 있었다는 점도 근거로 삼음.

**변경** (현재 작업 트리에 그대로 남아 있음, 커밋 안 함):

```diff
- .safeAreaInset(edge: .bottom) { commentComposer }
+ .safeAreaInset(edge: .bottom, spacing: 0) { commentComposer }
```

```diff
         .background(Color.Background.default)
-        .ignoresSafeArea(edges: .bottom)
     }
```

**검증한 것**: `xcodebuild build` 성공만 확인. 실기기/시뮬레이터에서 로그인 →
게시글 상세까지 들어가 실제 렌더를 보지는 못함(로그인 필요, 자동화 불가 —
[[community-frontend-status]] 참조).

**결과**: 사용자가 시뮬레이터에서 직접 확인 후 "안 됐다"고 재반려. **아직
어느 부분이, 얼마나, 왜 안 됐는지는 사용자로부터 구체적 피드백을 받지 못한
상태** — 여백이 그대로인지, 줄었지만 여전히 남는지, 다른 문제(예: 레이아웃
깨짐)가 생겼는지 불명.

## 현재 코드 상태

시도 2의 diff가 작업 트리에 그대로 남아 있고(미커밋), `git diff`로 확인 가능.
사용자가 "문서화만 해두라"고 지시해 추가 코드 변경은 보류 중.

## 다음에 확인해야 할 것

1. 시도 2 적용 후에도 여백이 **줄지 않았는지 / 줄었지만 남았는지** 정확한
   재현 스크린샷 필요 — 지금은 "안 됐다"는 결과만 있고 정도를 모름.
2. `AppCommentInput.containerHeight`/패딩 계산이 애초에 Figma 스펙(`390 × 88`
   고정 높이, 비포커스 하단 32pt)과 실제 렌더 높이가 일치하는지 실측 필요 —
   `GeometryReader`로 실제 프레임 높이를 찍어보는 디버그 오버레이 등 고려.
3. `safeAreaInset` 자체가 진짜 원인이 맞는지 재검증 — 예를 들어 `MainTabView`나
   `AppNavBar` 쪽 주석(`safeAreaInset`은 greedy하게 값을 가져간다는 취지의 코멘트,
   `AppNavBar.swift:73`)이 이 케이스에도 적용되는 다른 메커니즘을 가리킬 수 있음.
4. 이 화면이 탭바 안에 있는지 push 형태인지(네비게이션 스택 최상단인지) 확인 —
   탭바 존재 여부에 따라 안전 영역 계산이 또 달라질 수 있음.
