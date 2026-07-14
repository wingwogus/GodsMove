# Home (홈) Figma Capture and Implementation Handoff

이 문서는 어떤 Claude 대화 세션이든 홈 탭 Figma 캡처 → 기록 → SwiftUI 구현까지
문제없이 이어받기 위한 인수인계 문서다. TalkToFigma 연결 절차는
[`record/HANDOFF.md`](../record/HANDOFF.md) / [`mypage/HANDOFF.md`](../mypage/HANDOFF.md)에서
검증된 것과 동일하다 — 아래는 그 절차를 Home 폴더 기준으로 요약한 것이다.

## Project

- Repository: `/Users/user/Project/ChamChamCham/GodsMove/frontend`
- App: native iOS SwiftUI app, iOS 17+
- Docs folder: `docs/figma/home/`
- **구현 완료 (2026-07-14)**: `Features/Home/{Data,Domain,Presentation}` 전체 모듈 구현,
  빌드 성공 확인. 아래 Part 5 참고.

---

## Part 1. TalkToFigma 연결 (검증된 절차)

구성 요소 3개, 모두 갖춰져야 도구를 호출할 수 있다.

1. **socket relay** (bun) — MCP 서버 ↔ Figma 플러그인 중계, 포트 `3055`
2. **TalkToFigma MCP 서버** — 이 Claude 세션에 등록되어 있어야 함
3. **Figma 데스크톱 플러그인** — 채널 `chamchamcham`에 connect

### 1-A. socket relay 확인 (터미널)

```bash
curl -s http://localhost:3055/
```

`WebSocket server running`이 나오면 이미 떠 있는 것. 안 떠 있으면:

```bash
cd /Users/user/.codex/tools/cursor-talk-to-figma-mcp
PATH=/Users/user/.bun/bin:$PATH /Users/user/.bun/bin/bun socket
```

### 1-B. MCP 서버 등록 확인 (터미널)

```bash
claude mcp list
```

`TalkToFigma: ... - ✓ Connected`가 보이면 완료. 없으면:

```bash
claude mcp add TalkToFigma -s local -- \
  /Users/user/.bun/bin/bun run \
  /Users/user/.codex/tools/cursor-talk-to-figma-mcp/dist/server.js
```

등록 직후엔 진행 중이던 세션에 도구가 안 붙으므로 `claude` 재시작 필요.

### 1-C. Figma 플러그인 connect (Figma 앱)

Figma 데스크톱에서 파일을 열고 cursor-talk-to-figma 플러그인 실행 → 채널
`chamchamcham`에 connect. socket 로그에 `Client joined channel "chamchamcham"`.

**중요**: `join_channel`이 "Not connected to Figma" 에러를 내면 이 단계가 안
끝난 것 — MCP 서버/relay는 정상이어도 플러그인이 채널에 붙어 있지 않으면
실패한다.

### 1-D. 세션에서 도구 로드 후 채널 접속 (Claude)

```
ToolSearch: select:mcp__TalkToFigma__join_channel,
  mcp__TalkToFigma__get_selection,mcp__TalkToFigma__read_my_design,
  mcp__TalkToFigma__scan_text_nodes,mcp__TalkToFigma__export_node_as_image
```

```
mcp__TalkToFigma__join_channel(channel: "chamchamcham")
→ "Successfully joined channel: chamchamcham"
```

### 대체 경로: 공식 Figma Dev Mode MCP

TalkToFigma가 없거나 등록이 안 됐을 때만. 구조/치수/텍스트는
`get_metadata` / `get_design_context`, 색상/타이포 토큰은 `get_variable_defs`,
스크린샷은 `get_screenshot`. 읽은 결과는 아래와 동일한 문서 형식으로 저장한다.

---

## Part 2. 캡처 절차 (프레임 1개당)

사용자가 Figma에서 **정확히 한 개**의 프레임/상태를 선택한 뒤 진행한다.

1. `get_selection()` — 선택된 노드 id/이름/타입 확인
2. `read_my_design()` — 선택 노드의 전체 구조·치수·텍스트·색상·폰트 획득
3. `scan_text_nodes(nodeId)` — 텍스트 노드 증거를 남기고 싶을 때(선택)
4. `export_node_as_image(nodeId, format: "PNG", scale: 2)` — 렌더 육안 검증용

문서 전체 조회(`get_document_info` 남용 등)는 타임아웃 위험이 있으니 **선택
노드 범위로만** 호출한다.

### PNG 파일 저장 한계

`export_node_as_image`는 결과를 **인라인 이미지로만** 반환한다. 디스크에
저장 불가. 구조·치수·텍스트·색상은 `read_my_design` 값으로 정밀 기록한다.
PNG 파일이 꼭 필요하면 Figma에서 수동 Export(2x)하여 `assets/`에 저장한다.

---

## Part 3. 기록 (캡처 문서 형식)

`docs/figma/home/YYYY-MM-DD-home-<screen>-<state>.md`로 저장하고,
[README.md](README.md)의 Captures 목록에 한 줄 추가한다.

각 문서에 포함할 항목:

- Captured date, source MCP calls
- Figma node ID, frame name, frame size
- State description
- Text styles: font, weight, size, line height, tracking, color
- Key colors, layout measurements
- Existing design-system component candidates
- Product / API / design-system questions
- Figma ↔ design-system conflicts, Figma ↔ 기존 HomeView 와이어프레임 불일치

Figma에 없는 런타임 상태(로딩/빈/오류 등)는 임의로 API·네트워크를 지어내지
말고, 기존 디자인 시스템 패턴을 확인해 구현 계획에서 정의한다.

---

## Part 4. 구현 (캡처 완료 후에만)

### 순서

1. 모든 필수 상태 캡처/정의 완료 확인 ([README.md](README.md) 체크리스트).
2. `Core/DesignSystem/` 컴포넌트 재사용 가능 여부 먼저 확인 — `AppTopAppBar`,
   `AppChip`, `AppListItem`, `AppBadge`, `AppButton` 등 다른 피처에서 이미
   쓰인 컴포넌트 우선.
3. 홈에 노출되는 데이터(최근 영농기록/추천 정책/인기글/날씨)의 실제 API
   shape이 필요할 때만 Swagger 조사 (`docs/swagger/`; 갱신은
   `python3 scripts/sync_swagger_spec.py --write`).
4. `docs/Business Rule.md`에서 홈 화면 관련 BR(날씨 자동조회, 정책 추천 로직,
   인기글 기준 등)을 먼저 확인한다.
5. 짧은 구현 계획 작성: 화면 구성 / 상태 모델 / offline-local 동작(SwiftData
   캐시 여부) / API 준비도 / 디자인 시스템 매핑 / 기존 와이어프레임에서
   재사용할 부분·버릴 부분 / 미결 제품 결정 / SE 2·3 레이아웃 전략.
6. 계획 수락 또는 사용자 명시 요청 후에만 구현 시작.
7. 완료 주장 전 빌드 실행:

```bash
cd /Users/user/Project/ChamChamCham/GodsMove/frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

### 구현 규칙 (repo AGENTS.md 준수)

- SwiftUI, iOS 17+, Swift 6 strict concurrency.
- 뷰모델은 Observation `@Observable`.
- 로컬 저장은 SwiftData. 네트워킹은 native `URLSession` + async/await.
- Offline-first: 쓰기는 로컬 우선, 네트워크에 블로킹되지 않게.
- 도메인 용어 `member` 유지.
- 프론트 API 결정에 아카이브된 Notion 문서 사용 금지. 배포 Swagger 기준.
- 새 파일은 프로젝트 표준 Xcode 헤더 주석으로 시작.

### 디자인 시스템 규칙 (Strict)

UI 작성 전 `ChamChamCham/Core/DesignSystem/` 관련 파일을 먼저 확인한다.
우선순위: (1) 기존 컴포넌트 재사용 → (2) 반복되는 상태/변형만 기존 컴포넌트
확장 → (3) 일회성 레이아웃은 피처 화면 내부 → (4) 안정적으로 반복되는 UI만
새 디자인 시스템 컴포넌트로 승격. Figma가 디자인 시스템과 충돌하면 디자인
시스템 값을 유지하고 불일치를 문서화한다.

---

## Part 5. 진행 상태 (Status)

### 캡처 완료

| 상태 | node | 문서 | 비고 |
|---|---|---|---|
| 홈 / default | `379:676` | [문서](2026-07-14-home-main-default.md) | top-app-bar + weather/tip 카드 + 최근 영농 기록(가로 스크롤) + 추천 정책 + 인기글 + nav-bar |
| 홈 -> 날씨 상세 | `1247:25659` | [문서](2026-07-14-home-weather-detail.md) | back top-app-bar + 오늘 날씨/주소 + 관수 팁 + 2×2 상세 그리드 + 5일 주간 예보 |
| 홈 -> 정책 리스트 | `1172:18031` | [문서](2026-07-14-home-policy-list.md) | 카테고리 칩 11개 + 정렬 드롭다운 + list row. **정책 상세 미캡처** |

### 종합 구현 계획

[구현 계획서](2026-07-14-home-implementation-plan.md) 참고. 결정 사항(2026-07-14 확정):
날씨 상세는 더미 데이터 포함 전체 구현, 정책 D-day 배지 제거하고 "기간" 텍스트로 대체,
정책 카테고리 10종은 백엔드 검증 없이 우선 진행, 정책 리스트 row 탭은 시스템 브라우저로
외부 이동.

### 구현 상태 (2026-07-14, 빌드 성공)

`Features/Home/{Data,Domain,Presentation}` 신규 모듈 구현 완료.

- **Domain**: `HomeModels.swift`(`PolicyCategory`, `PolicyRecommendation`,
  `PolicyRecommendationPage`, `WeatherDetail`, `WeatherForecastDay`), `WeatherIconMapping.swift`.
  날씨/영농기록/커뮤니티 도메인 타입은 **Record/Community 기존 타입 재사용**
  (`CurrentWeather`, `FarmingRecordSummary`, `CommunityPostSummary` 등 — 중복 정의 없음).
- **Data**: 정책 추천만 신규(`PolicyEndpoint`, `PolicyRepository`, `PolicyDTOs`) —
  `GET /policies/recommendations`, `GET /policies/{id}`(외부 링크 조회 전용). 날씨/최근기록은
  `RecordRepository.fetchWeather`/`fetchRecords` 재사용, 인기글은 `CommunityRepository.fetchPosts`
  재사용(`sort: .popular`). `DIContainer.makePolicyRepository()` 추가.
- **Presentation**: `HomeViewModel`(섹션별 독립 loading/loaded/failed 상태),
  `PolicyListViewModel`(카테고리 필터 + 커서 페이지네이션), `HomeView`/`WeatherDetailView`/
  `PolicyListView`. `MainTabView` 0번 탭을 `HomeView(container:)`로 연결.
- **디자인 시스템**: 신규 컴포넌트 0개. `AppTopAppBar`(standard/detail 둘 다), `AppCard(.medium)`,
  `AppButton`, `AppListItem`(`.small`/`.xlarge`), `AppChip(.solid)`, `AppSortButton` 전부 재사용.
  weather/tip 카드, 정책 카드는 feature-local 컴포저블(기존 atom 조합).
- **외부 링크 이동**: 정책 리스트 row 탭 → `PolicyRepository.fetchExternalLink` →
  `@Environment(\.openURL)`(시스템 브라우저). 레포 최초 사례.
- 검색/알림 아이콘, 레코드/인기글 섹션 chevron은 inert(검색 화면·unread API·크로스탭 네비게이션
  미구현).

### 열린 질문 / 후속 작업

- **C-1** 날씨 상세 대부분 필드(체감/최저최고/자외선/강수확률/습도/풍속/5일예보/주소)가 더미 —
  백엔드 필드 추가 시 `WeatherDetail.dummy` 교체 필요.
- **C-2** weatherCondition → 아이콘 매핑이 키워드 추측(`WeatherIconMapping`) — 백엔드 enum 확인 필요.
- **C-3** 정책 D-day 배지 없음, `applicationPeriodLabel` 텍스트만 표시.
- **C-4** 정책 카테고리 10종 백엔드 미검증 — 결과 0건이어도 정상 빈 상태로 처리 중.
- **C-5** 알림 unread API 없음 — 알림 아이콘 inert.
- **C-6** 최근 영농 기록 정렬 파라미터 미문서화 — `size=3`만 지정, 서버 기본 정렬 신뢰.
- **C-7** 관수 팁 배너 정적 placeholder 문구 — 동적 로직 미정의.
- 인기글/레코드 섹션 chevron 탭 시 해당 탭으로 전환하는 크로스탭 네비게이션 미구현(현재 inert).
- 정책 정렬 드롭다운은 "추천순" 고정 표시, 옵션 메뉴 없음(다른 정렬 옵션 미확인).
- SE 2/3 실기기/시뮬레이터 육안 확인 아직 안 함(빌드만 확인).
- 정책 상세는 네이티브 UI 없음(외부 웹 링크 이동)으로 확정, 캡처 대상 아님.

---

## Part 6. 다른 세션에서 붙여넣을 Resume Prompt

```text
홈 탭 Figma 캡처와 SwiftUI 구현을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove/frontend

먼저 docs/figma/home/HANDOFF.md 를 읽고 그 절차를 따라줘.
(이어서 README.md 와 이미 캡처된 상태 문서들도 읽어줘.)

TalkToFigma 사용:
1) socket relay가 3055에 떠 있는지 확인 (curl http://localhost:3055/).
2) claude mcp list 에 TalkToFigma가 있는지 확인. 없으면 HANDOFF Part 1-B 등록
   후 claude 재시작 필요.
3) ToolSearch로 mcp__TalkToFigma__* 도구 로드 →
   join_channel("chamchamcham"). "Not connected to Figma" 에러가 나면 Figma
   플러그인이 채널에 아직 connect 안 된 것이므로 사용자에게 확인 요청.
4) Figma에서 내가 프레임 하나를 선택하면, get_selection → read_my_design →
   (필요시 scan_text_nodes) → export_node_as_image(PNG, scale 2) 순으로 캡처.

기존 코드에 Features/Home/Presentation/Views/HomeView.swift가 레이아웃 전용
와이어프레임(샘플 데이터)으로 이미 있으니, Figma 캡처와 대조해 재사용/교체
범위를 판단한다.

모든 필수 상태 캡처가 끝나기 전엔 SwiftUI 구현을 시작하지 말고, 끝나면
Core/DesignSystem, docs/Business Rule.md, Swagger를 대조해 구현 계획을 세운
뒤 진행해줘.
```
