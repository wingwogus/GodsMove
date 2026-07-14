# My Page Figma Capture and Implementation Handoff

이 문서는 어떤 Claude 대화 세션이든 마이페이지 Figma 캡처 → 기록 → SwiftUI
구현까지 문제없이 이어받기 위한 **검증된 인수인계 문서**다. 아래 TalkToFigma
플로우는 2026-07-13 Claude Code 세션에서 실제로 end-to-end 동작을 확인한 것이다.

## Project

- Repository: `/Users/user/Project/ChamChamCham/GodsMove/frontend`
- App: native iOS SwiftUI app, iOS 17+
- Docs folder: `docs/figma/mypage/`
- 필수 마이페이지 화면/런타임 상태가 캡처되거나 명시적으로 정의되기 전에는
  SwiftUI 구현을 시작하지 않는다.

---

## Part 1. TalkToFigma 연결 (검증된 절차)

TalkToFigma(cursor-talk-to-figma-mcp)를 쓰는 이유: **무료**. 공식 Figma Dev
Mode MCP도 대체 가능하지만(뒤 참고), 기본 경로는 TalkToFigma다.

구성 요소는 3개이며 모두 갖춰져야 도구를 호출할 수 있다.

1. **socket relay** (bun) — MCP 서버 ↔ Figma 플러그인 중계, 포트 `3055`
2. **TalkToFigma MCP 서버** — 이 Claude 세션에 등록되어 있어야 함
3. **Figma 데스크톱 플러그인** — 채널 `chamchamcham`에 connect

### 1-A. socket relay 실행 (터미널)

```bash
cd /Users/user/.codex/tools/cursor-talk-to-figma-mcp
PATH=/Users/user/.bun/bin:$PATH /Users/user/.bun/bin/bun socket
```

- 정상 로그: `WebSocket server running on port 3055`
- 이미 떠 있으면 재실행 시 포트 충돌(EADDRINUSE)만 난다 → 그대로 두면 됨.
- 헬스체크: `curl -s http://localhost:3055/` → `WebSocket server running`

### 1-B. MCP 서버 등록 (최초 1회, 터미널)

로컬 socket과 버전을 맞추기 위해 `bunx@latest` 대신 로컬 dist를 가리킨다.
커밋 파일을 건드리지 않도록 `-s local` 스코프로 등록한다.

```bash
claude mcp add TalkToFigma -s local -- \
  /Users/user/.bun/bin/bun run \
  /Users/user/.codex/tools/cursor-talk-to-figma-mcp/dist/server.js
```

- 확인: `claude mcp list` → `TalkToFigma: ... - ✓ Connected`
- **중요:** MCP 서버는 세션 시작 시점에만 로드된다. 등록 직후 진행 중이던
  세션에는 도구가 안 붙는다 → **`claude`를 완전히 종료 후 재시작**해야 한다.
  (한 번 등록해두면 이후 세션부터는 이 단계 생략)

### 1-C. Figma 플러그인 connect (Figma 앱)

- Figma 데스크톱에서 해당 파일을 열고 cursor-talk-to-figma 플러그인 실행
- 채널 `chamchamcham` 에 connect
- socket 로그에 `Client joined channel "chamchamcham"` 가 뜨면 성공

### 1-D. 세션에서 도구 로드 후 채널 접속 (Claude)

TalkToFigma 도구는 deferred 상태로 온다. 먼저 스키마를 로드해야 호출 가능.

```
ToolSearch: select:mcp__TalkToFigma__join_channel,
  mcp__TalkToFigma__get_selection,mcp__TalkToFigma__read_my_design,
  mcp__TalkToFigma__scan_text_nodes,mcp__TalkToFigma__export_node_as_image
```

그다음:

```
mcp__TalkToFigma__join_channel(channel: "chamchamcham")
→ "Successfully joined channel: chamchamcham"
```

### 대체 경로: 공식 Figma Dev Mode MCP

TalkToFigma가 없거나 등록이 안 됐을 때만 사용. 커넥터 인증(사용자가 직접
OAuth)이 선행되어야 한다. 대응 도구:

- 구조/치수/텍스트: `mcp__Figma__get_metadata`, `get_design_context`
- 색상/타이포 토큰: `get_variable_defs`
- 스크린샷: `get_screenshot`

읽은 결과를 아래와 동일한 문서 형식으로 저장한다. 기존 캡처 사실은 새 Figma
읽기로 틀렸음이 증명되지 않는 한 덮어쓰지 않는다.

---

## Part 2. 캡처 절차 (프레임 1개당)

사용자가 Figma에서 **정확히 한 개**의 프레임/상태를 선택한 뒤 진행한다.

1. `get_selection()` — 선택된 노드 id/이름/타입 확인
2. `read_my_design()` — 선택 노드의 전체 구조·치수·텍스트·색상·폰트 획득
   (이것만으로 문서화에 필요한 값 대부분 확보됨)
3. `scan_text_nodes(nodeId)` — 텍스트 노드 개수/목록을 증거로 남기고 싶을 때
   (선택. `read_my_design`에 이미 텍스트가 포함됨)
4. `export_node_as_image(nodeId, format: "PNG", scale: 2)` — 렌더 육안 검증용

대용량 파일에서 문서 전체 조회(`get_document_info` 남용 등)는 타임아웃 위험이
있으니 **선택 노드 범위로만** 호출한다.

### PNG 파일 저장 한계 (중요)

Claude Code에서 `export_node_as_image`(및 공식 MCP의 `get_screenshot`)는 결과를
**인라인 이미지로만** 반환한다. 파일 경로나 base64 텍스트를 주지 않으므로
Claude가 디스크(`assets/`)에 PNG를 저장할 수 없다. 무료/유료 문제가 아니라
클라이언트 구조 한계다.

- 대응: 구조·치수·텍스트·색상은 `read_my_design` 값으로 정밀 기록(충분함).
- PNG 파일이 꼭 필요하면 **Figma에서 수동 Export(2x)** 하여 `assets/`에 저장하고,
  그때 해당 캡처 문서에 dimensions·SHA-256·embed를 추가한다.
- 사용자가 이미지를 채팅에 붙여넣은 경우(예: 배치 규칙 스펙)도 파일 저장은
  불가하므로 **내용을 텍스트로 정밀 전사**해 문서에 넣는다.

---

## Part 3. 기록 (캡처 문서 형식)

`docs/figma/mypage/YYYY-MM-DD-mypage-<screen>-<state>.md` 로 저장하고,
[README.md](README.md)의 Captures 목록에 한 줄 추가한다.

각 문서에 포함할 항목:

- Captured date, source MCP calls
- Figma node ID, frame name, frame size
- State description
- (PNG 저장 시) path, dimensions, SHA-256, embed / 미저장 시 그 사유
- Text styles: font, weight, size, line height, tracking, color
- Key colors, layout measurements
- Existing design-system component candidates
- Product / API / design-system questions
- Figma ↔ design-system conflicts

Figma에 없는 런타임 상태(로딩/빈/오류/재시도 등)는 임의로 API·네트워크를
지어내지 말고, 기존 디자인 시스템 패턴을 확인해 구현 계획에서 정의한다.

---

## Part 4. 구현 (캡처 완료 후에만)

### 순서

1. 모든 필수 상태 캡처/정의 완료 확인.
2. 현재 Profile/MyPage 피처 파일, 앱 내비게이션, `Core/DesignSystem/` 조사.
3. member/profile/post 실제 API shape이 필요할 때만 Swagger 조사
   (`docs/swagger/`; 갱신은 `python3 scripts/sync_swagger_spec.py --write`).
4. 짧은 구현 계획 작성: 화면 구성 / 상태 모델 / offline-local 동작 / API 준비도
   / 디자인 시스템 매핑 / 미결 제품 결정 / SE 2·3 레이아웃 전략.
5. 계획 수락 또는 사용자 명시 요청 후에만 구현 시작.
6. 완료 주장 전 빌드 실행:

```bash
cd /Users/user/Project/ChamChamCham/GodsMove/frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

시뮬레이터 이름이 없으면 `xcrun simctl list devices available`로 확인해 대체.

### 구현 규칙 (repo AGENTS.md 준수)

- SwiftUI, iOS 17+, Swift 6 strict concurrency (`@unchecked Sendable` 등 회피).
- 뷰모델은 Observation `@Observable` (Combine/`ObservableObject` 금지).
- 로컬 저장은 SwiftData. 네트워킹은 native `URLSession` + async/await.
- Offline-first: 쓰기는 로컬 우선, 네트워크에 블로킹되지 않게.
- 도메인 용어 `member` 유지. `userId`/`users` 재도입 금지.
- 프론트 API 결정에 아카이브된 Notion 문서 사용 금지. 배포 Swagger 기준.
- 새 파일은 프로젝트 표준 Xcode 헤더 주석으로 시작.

### 디자인 시스템 규칙 (Strict)

UI 작성 전 `ChamChamCham/Core/DesignSystem/` 관련 파일을 먼저 확인한다.

우선순위: (1) 기존 컴포넌트 재사용 → (2) 반복되는 상태/변형만 기존 컴포넌트
확장 → (3) 일회성 레이아웃은 피처 화면 내부 → (4) 안정적으로 반복되는 UI만
새 디자인 시스템 컴포넌트로 승격.

Foundation(`Color+App`, `Font+App`, `Spacing`) 밖에서 raw 색상/폰트/간격 토큰을
중복 추가하지 않는다. Figma가 디자인 시스템과 충돌하면 디자인 시스템 값을
유지하고 불일치를 문서화한다. Foundation·공개 컴포넌트 API는 사용자 승인 없이
변경하지 않는다.

컴포넌트 후보(첫 캡처 기준, 실제 API는 코드에서 검증 필요):
`AppTopAppBar`, `AppAvatar`, `AppBadge`, `AppTabBar`, `AppChip`, `AppListItem`,
`AppNavBar`.

---

## 구현 계획

통합 구현 계획서: [2026-07-13-mypage-implementation-plan.md](2026-07-13-mypage-implementation-plan.md).
화면 구성·API 매핑·DS 매핑·구현 순서·미결정 결정을 담고 있다. 구현은 이 문서를
기준으로 진행한다.

## Part 5. 진행 상태 (Status)

### 캡처 완료

| 상태 | node | 문서 | 비고 |
|---|---|---|---|
| 프로필 메인 / default (작물 요약) | `1247:17727` | [문서](2026-07-13-mypage-profile-main-default.md) | 작물 3개 + `외 n종`, 카드 271 |
| 프로필 메인 / 작물 뱃지 전체 공개 | `1247:17757` | [문서](2026-07-13-mypage-profile-crops-expanded.md) | 작물 8개 2줄, 카드 311 |
| 바텀시트 / 게시판 선택 시 | `1247:18048` | [문서](2026-07-13-mypage-board-select-bottom-sheet.md) | 작물 선택 칩(진행중/기타) + 완료 버튼 |
| 프로필 수정 / 기본 정보 | `1247:18133` | [문서](2026-07-13-mypage-profile-edit-basic.md) | 폼(이름/닉네임/연락처/생년월일/자격/귀농연차) + 저장 |
| 프로필 수정 / 농업 정보 | `1247:17987` | [문서](2026-07-13-mypage-profile-edit-farm.md) | 등록한 밭 카드 목록 + 추가/삭제 (farm/JUSO·V-World 연관) |
| DS 컴포넌트 / setting-card (밭 카드) | `1088:16697` | [문서](2026-07-13-mypage-setting-card-component.md) | COMPONENT_SET, `selected` variant, **DS 승격 결정** |

작물 키워드 배치 규칙(≤3 전부 / >3 → 3개+`외 n종` / `외 n종` 탭 시 전체 공개)은
[crops-expanded 문서](2026-07-13-mypage-profile-crops-expanded.md#crop-keyword-layout-rule-작물-키워드-배치)에
확정 기록됨.

### 남은 캡처 체크리스트

- 프로필 메인 / 좋아요 누른 글 탭
- 밭(농장) 삭제하기 모드 UI
- 콘텐츠 로딩 / 빈 목록 / 오류·재시도 / 추가 로딩

밭 추가하기는 온보딩 `FarmLocationView` → `CropSelectionView` 재사용으로 확정
(별도 Figma 캡처 불필요). 단 두 View의 온보딩 결합 분리가 구현 선행 과제 —
[농업 정보 문서](2026-07-13-mypage-profile-edit-farm.md#implementation-mapping-사용자-지정-2026-07-13) 참조.
- 프로필 기본 이미지 vs 사용자 이미지
- 작물 **0개** 상태 (스펙 미정)
- 긴 닉네임 / 긴 지역명 / 큰 귀농 연차 값 (SE 2·3)
- 설정 아이콘 진입 대상, 알림 아이콘 진입 대상
- iPhone SE 2·3에서 카드/탭/필터/목록/하단 내비 안전성

### 열린 질문

- `1321pt` 프레임: 프로필 헤더·탭이 리스트와 함께 스크롤되고 하단 내비는 고정?
- 설정/알림 아이콘이 기존 라우트로 가는지 신규 화면인지.
- `사용자 설정 지역`, `귀농 연차`를 배포 Swagger의 member 필드로 매핑.
- 작물 되접기(collapse) 인터랙션 존재 여부.
- 게시판 필터 옵션과, 작성글/좋아요글 간 필터 공유 여부.

---

## Part 6. 다른 세션에서 붙여넣을 Resume Prompt

```text
마이페이지 Figma 캡처와 SwiftUI 구현을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove/frontend

먼저 docs/figma/mypage/HANDOFF.md 를 읽고 그 절차를 따라줘.
(이어서 README.md 와 이미 캡처된 상태 문서들도 읽어줘.)

TalkToFigma 사용:
1) socket relay가 3055에 떠 있는지 확인 (curl http://localhost:3055/).
2) claude mcp list 에 TalkToFigma가 있는지 확인. 없으면 HANDOFF Part 1-B 등록
   후 claude 재시작 필요.
3) ToolSearch로 mcp__TalkToFigma__* 도구 로드 →
   join_channel("chamchamcham").
4) Figma에서 내가 프레임 하나를 선택하면, get_selection → read_my_design →
   (필요시 scan_text_nodes) → export_node_as_image(PNG, scale 2) 순으로 캡처.
   PNG 파일 저장은 클라이언트 한계로 불가하니 read_my_design 값으로 정밀 기록.

모든 필수 상태 캡처가 끝나기 전엔 SwiftUI 구현을 시작하지 말고, 끝나면
Core/DesignSystem, 현재 Profile/MyPage 코드, Swagger를 대조해 구현 계획을
세운 뒤 진행해줘.
```
