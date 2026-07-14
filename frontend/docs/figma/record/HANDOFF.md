# Record (영농 기록) Figma Capture and Implementation Handoff

이 문서는 어떤 Claude 대화 세션이든 영농기록(Record) 탭 Figma 캡처 → 기록 →
SwiftUI 구현까지 문제없이 이어받기 위한 인수인계 문서다. TalkToFigma 연결
절차는 [`mypage/HANDOFF.md`](../mypage/HANDOFF.md)에서 2026-07-13 세션에
end-to-end 검증된 것과 동일하다 — 아래는 그 절차를 Record 폴더 기준으로
요약한 것이다.

## Project

- Repository: `/Users/user/Project/ChamChamCham/GodsMove/frontend`
- App: native iOS SwiftUI app, iOS 17+
- Docs folder: `docs/figma/record/`
- `Features/Record/{Data,Domain,Presentation}` 모듈 구현됨(2026-07-13):
  리스트·필터·FAB 스피드다이얼·텍스트 작성(compose, workType 8종)까지. `MainTabView`
  2번째 탭에 연결. 상세 상태는 아래 Part 5.
- 남은 캡처(음성/리포트/상세·수정·삭제)는 캡처/정의 전까지 SwiftUI 구현을
  시작하지 않는다.

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

`WebSocket server running`이 나오면 이미 떠 있는 것 — 그대로 두면 됨. 안 떠
있으면:

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

등록 직후엔 진행 중이던 세션에 도구가 안 붙으므로 `claude` 재시작 필요
(한 번 등록해두면 이후 세션부터 생략).

### 1-C. Figma 플러그인 connect (Figma 앱)

Figma 데스크톱에서 파일을 열고 cursor-talk-to-figma 플러그인 실행 → 채널
`chamchamcham`에 connect. socket 로그에 `Client joined channel "chamchamcham"`.

### 1-D. 세션에서 도구 로드 후 채널 접속 (Claude)

TalkToFigma 도구는 deferred 상태로 온다.

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
`mcp__Figma__get_metadata` / `get_design_context`, 색상/타이포 토큰은
`get_variable_defs`, 스크린샷은 `get_screenshot`. 읽은 결과는 아래와 동일한
문서 형식으로 저장한다. 기존 캡처 사실은 새 Figma 읽기로 틀렸음이 증명되지
않는 한 덮어쓰지 않는다.

---

## Part 2. 캡처 절차 (프레임 1개당)

사용자가 Figma에서 **정확히 한 개**의 프레임/상태를 선택한 뒤 진행한다.

1. `get_selection()` — 선택된 노드 id/이름/타입 확인
2. `read_my_design()` — 선택 노드의 전체 구조·치수·텍스트·색상·폰트 획득
3. `scan_text_nodes(nodeId)` — 텍스트 노드 증거를 남기고 싶을 때(선택)
4. `export_node_as_image(nodeId, format: "PNG", scale: 2)` — 렌더 육안 검증용

대용량 파일에서 문서 전체 조회(`get_document_info` 남용 등)는 타임아웃
위험이 있으니 **선택 노드 범위로만** 호출한다.

### PNG 파일 저장 한계 (중요)

`export_node_as_image`(및 공식 MCP의 `get_screenshot`)는 결과를 **인라인
이미지로만** 반환한다. 파일 경로나 base64 텍스트를 주지 않으므로 Claude가
디스크(`assets/`)에 PNG를 저장할 수 없다. 구조·치수·텍스트·색상은
`read_my_design` 값으로 정밀 기록(충분함). PNG 파일이 꼭 필요하면 Figma에서
수동 Export(2x)하여 `assets/`에 저장하고, 그때 해당 캡처 문서에
dimensions·SHA-256·embed를 추가한다.

---

## Part 3. 기록 (캡처 문서 형식)

`docs/figma/record/YYYY-MM-DD-record-<screen>-<state>.md`로 저장하고,
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

1. 모든 필수 상태 캡처/정의 완료 확인 ([README.md](README.md) 체크리스트).
2. 현재 코드에 `Features/Record`가 없으므로 `Features/Community` 구조를
   참고해 Data/Domain/Presentation 스캐폴드 방향을 잡는다. `Core/DesignSystem/`
   컴포넌트 재사용 가능 여부를 먼저 확인한다.
3. member/영농일지 실제 API shape이 필요할 때만 Swagger 조사
   (`docs/swagger/`; 갱신은 `python3 scripts/sync_swagger_spec.py --write`).
4. `docs/Business Rule.md`의 BR-RECORD-001~010, BR-VOICE-*, BR-STATE-001을
   먼저 읽는다 — 영농일지 생성/자동입력/저장조건/AI 구조화/수정/삭제/다년생
   작물/사진/AI 실패 규칙과 음성 입력 상태 머신이 이 화면의 동작을 규정한다.
5. 짧은 구현 계획 작성: 화면 구성 / 상태 모델 / offline-local 동작(SwiftData
   우선 저장) / API 준비도 / 디자인 시스템 매핑 / 미결 제품 결정 / SE 2·3
   레이아웃 전략.
6. 계획 수락 또는 사용자 명시 요청 후에만 구현 시작.
7. 완료 주장 전 빌드 실행:

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
`AppTopAppBar`, `AppTabBar`, `AppChip`, `AppListItem(size: .large)`,
`AppBadge`, `AppNavBar`. FAB(원형 72pt + `icon/add_2`)는 기존 컴포넌트에 없어
신규 후보 — 다른 화면에 동일 FAB가 있는지 먼저 확인.

---

## Part 5. 진행 상태 (Status)

### 캡처 완료

| 상태 | node | 문서 | 비고 |
|---|---|---|---|
| 기록 메인 / default | `1247:23900` | [문서](2026-07-13-record-main-default.md) | 필터 칩 3개 + list row(대형, `AppListItem(size: .large)` 거의 일치) + FAB + nav-bar |
| 기록 (필터 사용, 바텀시트) | `1247:23970` / `1247:23587` / `1247:23616` | [문서](2026-07-13-record-filter-bottom-sheets.md) | 작물/영농 활동(9종)/작성 기간 바텀시트, 복수 선택, 딤드 `#1a1a1a`@64% 공통 규칙 |
| 기록 메인 - 기록 버튼 탭 시 | `1247:23930` | [문서](2026-07-13-record-main-record-button-tapped.md) | FAB 탭 → 딤 + 음성/텍스트/닫기 스피드다이얼(BR-VOICE). 활성 필터 칩(초록 아웃라인+카운트) 확인 |
| 텍스트로 기록하기 / default | `1247:23161` | [문서](2026-07-13-record-text-compose-default.md) | 영농일지 텍스트 작성 폼(날짜/날씨 자동/농지·작물/작업유형/메모0-500/사진0-5/완료). POST `SaveRecordRequest` |
| 텍스트 / 심기 - 1(씨앗 심기) | `1247:23381` | [문서](2026-07-13-record-text-compose-planting-1-seed.md) | 작업="심기" 선택 시 동적 상세(심은 방법 씨앗/모종 2택 + 심은 씨앗량 g). `PlantingDetailRequest` |
| 텍스트 / 심기 - 2(모종 심기) | `1247:23417` | [문서](2026-07-13-record-text-compose-planting-2-seedling.md) | 모종 심기 → 심은 갯수(주) + 모종 번식법 드롭다운. 재배법-번식법 2단 구조 스펙 전사 |
| 텍스트 / 물주기 | `1247:23454` | [문서](2026-07-13-record-text-compose-watering.md) | 물주기 → 진행 방식 + 물의 양 드롭다운(`WateringDetailRequest`). 상세 필수 여부 API와 불일치 |
| 텍스트 / 비료 주기 | `1247:23481` | [문서](2026-07-13-record-text-compose-fertilizing.md) | 비료 주기 → 사용 비료 + 사용량/단위 + 진행 방식(`FertilizingDetailRequest`). 충돌 C-8~C-10 |
| 텍스트 / 병해충 관리 | `1247:23511` | [문서](2026-07-13-record-text-compose-pest-control.md) | 사용 농약 + 농약 사용량/단위(g·ml, API 일치) + 총 살포량(ml↔L) + 대상 병해충(`PestControlDetailRequest`). 충돌 C-11~C-12 |
| 텍스트 / 잡초 관리 | `1247:23542` | [문서](2026-07-13-record-text-compose-weeding.md) | 진행 방식 1개(손으로 뽑기/예초기/멀칭/제초제, `WeedingDetailRequest` enum 1:1). 충돌 C-13(필수 여부) |
| 텍스트 / 가지·순 정리 | `1247:23186` | [문서](2026-07-13-record-text-compose-pruning.md) | **상세 필드 없음**(default와 동일). API에 `pruning` 상세 객체 없음 — 충돌 없음 |
| 텍스트 / 수확 | `1247:23264` | [문서](2026-07-13-record-text-compose-harvest.md) | 재배 기간 + 수확량(+잘 모르겠음 체크) + 수확 부위 + 최종 수확 완료 토글(`HarvestDetailRequest`). 충돌 C-14~C-16 |
| 텍스트 / 기타 | (캡처 없음) | [문서](2026-07-13-record-text-compose-etc.md) | 상세 필드 없음(가지·순 정리와 동일). 구두 확정. **workType 8종 분기 완료** |
| 텍스트 / 입력 검증(에러) 케이스 | (참고 이미지) | [문서](2026-07-13-record-text-compose-error-cases.md) | 2필드 1영역 조합 에러 문구 + red border 변형, 500자 초과. 확정 워딩 목록 |

### 종합 구현 계획

수집 종료(2026-07-13) 후 종합 계획서: [구현 계획서](2026-07-13-record-implementation-plan.md).
FAB 스피드다이얼 + 텍스트 기록 작성(생성) 플로우를 다루며, 백엔드 충돌로 일부 workType 쓰기가
차단됨(권장 순서 포함).

### 남은 캡처 체크리스트

[README.md](README.md)의 Capture Checklist 참고 — 로딩/빈/오류/추가로딩, 사진
없음, 다년생 작물 배지, 리포트 탭, 기록 작성/수정/삭제 플로우(FAB 진입),
SE 2·3 대응. (필터 드롭다운/바텀시트는 캡처 완료.)

### 구현 상태 (2026-07-13)

기록 메인 리스트 + 필터 바텀시트 3종 + **FAB 스피드다이얼 + 텍스트 작성(compose,
workType 8종)**까지 구현 완료 (빌드 성공).
- 신규 모듈 `Features/Record/{Data,Domain,Presentation}`, Community 패턴(네트워크
  커서 페이지네이션) 재사용. `MainTabView` 2번째 탭에 연결.
- API: `GET /farming-records`(리스트+필터), `GET /members/me/farm-crops`(작물 필터),
  `GET /farms/{id}/weather`(날씨 자동조회), `POST /farming-records`(작성).
- DS 재사용만 사용(신규 DS 컴포넌트 0): `AppTopAppBar`/`AppTabBar`/`AppChip`/
  `AppListItem(.large)`/`AppDateField`/`AppButton`.
- ⚠️ **작성(쓰기)은 미배포 `origin/dev` 계약 기준 선구현** — dev 배포 + Swagger
  재동기화 전엔 실제 제출 실패 가능 (`backend-conflicts.md` C-2/C-9/C-14 등).
- 음성은 스피드다이얼에 렌더하되 비활성.
- **상세 읽기(RecordDetailView) 구현 완료(2026-07-14)** — 리스트 row 탭 → push. `GET /farming-records/{id}`,
  workType 8종 작업정보 관용 매핑, AI 코칭 placeholder(C-18), ⋮ inert. 계획:
  [상세 구현 계획](2026-07-14-record-detail-implementation-plan.md), 캡처: [상세](2026-07-14-record-detail-planting-seed.md).
- 미구현(캡처 대기): 리포트 탭(placeholder), 검색/알림 아이콘(inert),
  상세의 수정/삭제(⋮ 이후 디자인 미캡처), AI 코칭 실데이터, 음성 기록.

**API 대조로 확정된 배포 계약 이슈:**
1. **필터 단일 선택**: 리스트 API의 `cropId`/`workType`은 각각 **단일 값**만 받음.
   Figma/스펙의 "복수 선택"은 배포 API 미지원 → 단일 선택으로 구현. **복수 선택은
   백엔드 협의 필요** (list 쿼리 파라미터 배열화 or 클라 다중요청 머지).
2. **영농 활동 8종 (확정)**: API `workType` enum = PLANTING/WATERING/FERTILIZING/
   PEST_CONTROL/WEEDING/PRUNING/HARVEST/ETC ↔ 심기/물주기/비료 주기/병해충 관리/
   잡초 관리/가지·순 정리/수확/기타. 확정 워딩으로 정리 완료(`가공` 미채택) →
   [작업 유형 확정 워딩](2026-07-13-record-work-type-labels.md).
3. **바텀시트 딤드 `#1a1a1a`@64%**: 현재 시스템 `.sheet` 기본 dim 사용. 정확한 커스텀
   딤값 적용은 공통 바텀시트 인프라 도입 시 후속.
4. **리스트 행 필드 매핑(가정, 확인 필요)**: badges=[작물,활동유형], title=메모
   미리보기, caption=날씨(`weatherCondition`+온도), date=`workedAt`(MM/dd),
   thumbnail=`thumbnailUrl`. `weatherCondition`은 raw string(enum 여부 미확인).

### 앱 공통 규칙 (이 캡처에서 확정)

- **바텀시트 딤드(scrim)**: `#1a1a1a` @ opacity 64%, 모든 바텀시트 공통.
- **칩 필터**: 복수 선택 가능(작물/영농 활동). 기간은 시작~종료 range.
- **영농 활동 8종 (확정 워딩, 2026-07-13)**: 심기 / 물주기 / 비료 주기 /
  병해충 관리 / 잡초 관리 / 가지·순 정리 / 수확 / 기타. API enum과 1:1,
  `가공` 미채택. 단일 출처 = `WorkType.label`. 상세:
  [작업 유형 확정 워딩](2026-07-13-record-work-type-labels.md).

### 백엔드 협의 항목 (별도 트래킹)

Figma/스펙 ↔ 배포 Swagger 불일치는 **[백엔드 충돌 트래킹 문서](2026-07-13-record-backend-conflicts.md)**
(C-1~C-6)에 모아 관리한다. 새 충돌 발견 시 그 문서에 추가.

### 열린 질문

- 텍스트 색상 `#242428`(top-app-bar 타이틀, 선택 상태 라벨)과 기존
  `Color.Text.default`(#1a1a1a) 불일치 — 신규 시맨틱인지 확인 필요.
- badge-1(연두 `#e6f7bf` 배경 + `#27865c` 텍스트)이 `AppBadge(variant: .secondary)`
  조합과 실제로 일치하는지 코드 대조 필요.
- 필터 3종(작물/영농 활동/기간)의 옵션 소스와 드롭다운 UX(바텀시트 vs 피커).
- list row의 badge 2개가 각각 무엇을 나타내는지(작물/활동유형/다년생 여부).
- FAB 진입 시 Voice/Text 입력 모드 선택 여부(BR-VOICE-*).

---

## Part 6. 다른 세션에서 붙여넣을 Resume Prompt

```text
영농기록(Record) 탭 Figma 캡처와 SwiftUI 구현을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove/frontend

먼저 docs/figma/record/HANDOFF.md 를 읽고 그 절차를 따라줘.
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
Core/DesignSystem, docs/Business Rule.md의 BR-RECORD-*/BR-VOICE-*, Swagger를
대조해 구현 계획을 세운 뒤 진행해줘.
```
