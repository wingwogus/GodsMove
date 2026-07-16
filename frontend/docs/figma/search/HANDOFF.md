# Search (검색) Figma Capture and Implementation Handoff

이 문서는 어떤 Claude 대화 세션이든 검색 화면 Figma 캡처 → 기록 → SwiftUI
구현까지 문제없이 이어받기 위한 인수인계 문서다. TalkToFigma 연결 절차는
[`record/HANDOFF.md`](../record/HANDOFF.md) Part 1과 완전히 동일하다 — 아래는
그 절차를 Search 폴더 기준으로 요약한 것이다.

## Project

- Repository: `/Users/user/Project/ChamChamCham/GodsMove/frontend`
- App: native iOS SwiftUI app, iOS 17+
- Docs folder: `docs/figma/search/`
- `Features/Search` 모듈은 아직 코드에 없음.
- 총 7개 상태 예정: 검색 진행 시 3개 + 검색 결과 화면 4개. 사용자가 Figma에서
  화면을 하나씩 순서대로 선택해 전달하는 방식으로 진행한다.
- 모든 상태 캡처가 끝나기 전까지 SwiftUI 구현을 시작하지 않는다.

---

## Part 1. TalkToFigma 연결 (요약 — 전체 절차는 record/HANDOFF.md 참고)

구성 요소 3개, 모두 갖춰져야 도구를 호출할 수 있다: socket relay(포트 3055),
이 세션에 등록된 TalkToFigma MCP 서버, Figma 플러그인의 채널 `chamchamcham`
connect.

```bash
curl -s http://localhost:3055/   # "WebSocket server running" 확인
claude mcp list                  # "TalkToFigma: ... - ✓ Connected" 확인
```

세션에서 도구는 deferred 상태로 온다:

```
ToolSearch: select:mcp__TalkToFigma__join_channel,
  mcp__TalkToFigma__get_selection,mcp__TalkToFigma__read_my_design,
  mcp__TalkToFigma__scan_text_nodes,mcp__TalkToFigma__export_node_as_image
mcp__TalkToFigma__join_channel(channel: "chamchamcham")
```

상세(등록 안 됐을 때 등록 방법, 대체 경로인 공식 Figma Dev Mode MCP)는
[record/HANDOFF.md](../record/HANDOFF.md) Part 1 참고.

---

## Part 2. 캡처 절차 (프레임 1개당)

사용자가 Figma에서 **정확히 한 개**의 프레임/상태를 선택한 뒤 진행한다.

1. `get_selection()` — 선택된 노드 id/이름/타입 확인
2. `read_my_design()` — 선택 노드의 전체 구조·치수·텍스트·색상·폰트 획득
3. `scan_text_nodes(nodeId)` — 텍스트 노드 증거 확보
4. `export_node_as_image(nodeId, format: "PNG", scale: 2)` — 렌더 육안 검증용

PNG는 인라인 이미지로만 반환되고 디스크 저장 불가(클라이언트 한계) — 구조·색상·
텍스트는 `read_my_design` 값으로 정밀 기록한다.

---

## Part 3. 기록 (캡처 문서 형식)

`docs/figma/search/YYYY-MM-DD-search-<state>.md`로 저장하고
[README.md](README.md)의 Captures 목록에 한 줄 추가한다. 포함 항목은
[record/HANDOFF.md](../record/HANDOFF.md) Part 3과 동일: node ID, frame
size/state, layout table, text styles, key colors, 기존 디자인 시스템 컴포넌트
후보, 제품/API 미결 질문.

---

## Part 4. 구현 (캡처 완료 후에만)

1. 7개 상태 모두 캡처 완료 확인 ([README.md](README.md) 체크리스트).
2. `Core/DesignSystem/`에서 `AppSearchBar`(이미 존재, 첫 캡처에서 완전 일치
   확인됨), `AppButton`, 텍스트 헤더 스타일 재사용 가능 여부 확인.
3. 검색 대상이 무엇인지(커뮤니티 게시글? 전역 검색?) 먼저 확정 — 최근 검색어
   저장 위치(SwiftData 로컬 vs 서버)와 검색 API 존재 여부에 따라 offline-first
   설계가 갈린다. 필요 시 `docs/swagger/` 확인
   (`python3 scripts/sync_swagger_spec.py --write`로 최신화).
4. `docs/Business Rule.md`에서 검색 관련 BR-* 규칙 확인.
5. 짧은 구현 계획 작성 후 사용자 수락 또는 명시적 요청 후에만 구현 시작.
6. 완료 주장 전 빌드 실행:

```bash
cd /Users/user/Project/ChamChamCham/GodsMove/frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```

구현 규칙(Swift 6 strict concurrency, `@Observable`, SwiftData, offline-first,
`member` 용어 유지 등)과 디자인 시스템 규칙(재사용 우선순위, foundation 밖 raw
토큰 금지)은 [record/HANDOFF.md](../record/HANDOFF.md) Part 4와 동일하게 적용한다.

---

## Part 5. 진행 상태 (Status)

### 캡처 완료 (7/7) — 전체 완료

| 상태 | node | 문서 | 비고 |
|---|---|---|---|
| 검색 / default | `1547:23510` | [문서](2026-07-16-search-default.md) | 최근 검색어 0건, `AppSearchBar` 완전 일치 |
| 검색 / default - 최근 검색어 존재 시 | `1547:23476` | [문서](2026-07-16-search-history-exists.md) | 6개 chip wrap 그리드, `AppChip` border color 불일치(`#e0e0e0` vs `#f3f3f3`) |
| 검색 / 검색어 입력 중 | `1547:23494` | [문서](2026-07-16-search-typing.md) | 동일/연관 검색어 제안 리스트(텍스트+구분선, 신규 DS 컴포넌트 불필요). `AppDivider` 기본색도 동일 패턴 불일치 |
| 검색 결과 / default | `1547:23518` | [문서](2026-07-16-search-result-default.md) | 전체/나의 일지/정책 정보/게시글 4탭 + 카테고리별 3개 미리보기. `AppListItem`(.large/.xlarge/.medium)과 `AppTabBar`가 색상까지 정확히 일치 — 신규 컴포넌트 불필요. 검색 대상 3종(기록/정책/게시글) 확정 |
| 검색 결과 / 나의 일지 탭 | `1547:23557` | [문서](2026-07-16-search-result-record-tab.md) | `AppListItem.large` 재사용. 새 "총 N개" 헤더, 구분선 규칙 깨짐(placeholder 복제 실수로 판단) |
| 검색 결과 / 정책 정보 탭 | `1547:23573` | [문서](2026-07-16-search-result-policy-tab.md) | `AppListItem.xlarge` 재사용. 구분선 정상, 헤더 개수만 오류 |
| 검색 결과 / 게시글 탭 | `1547:23587`(레이어명 오기 "정책 정보 탭") | [문서](2026-07-16-search-result-post-tab.md) | `AppListItem.medium` 재사용. **탭바 선택 상태가 콘텐츠와 모순**(정책 정보로 표시되나 실제는 게시글) |

**7개 상태 전부 캡처 완료.** 다음은 Part 4(구현 계획)로 진행하되, 아래 "관통 이슈"의
placeholder 결함들은 구현 스펙으로 그대로 베끼지 말고 정정해서 반영할 것.

### 7개 캡처를 관통하는 공통 관찰 사항 (구현 계획 세울 때 재확인)

- **`#e0e0e0`(`Color.Border.default`) vs `#f3f3f3`(`Color.Border.subtle` /
  `Color.Object.muted`) 혼용이 검색 화면에서 반복 관찰됨** — 캡처 2(최근 검색어
  chip border)와 캡처 3(제안 리스트 구분선) 둘 다 기존 컴포넌트(`AppChip`,
  `AppDivider`)의 기본값(`#f3f3f3` 계열)보다 한 단계 진한 `#e0e0e0`을 씀. 이 화면만의
  의도적 변형인지, 스포이드 오차인지 사용자에게 확인 필요 — 결론 없이 구현 계획에서
  그대로 밀어붙이지 말 것.
- **검색바(`AppSearchBar`)는 캡처 3개 모두와 완전히 일치** — 신규 작업 없음, placeholder/
  입력됨/클리어 버튼 상태 전부 기존 구현이 이미 커버.
- **최근 검색어/제안 리스트 데이터 소스(로컬 vs 서버) 미확정** — 검색 대상이 무엇인지
  (커뮤니티 게시글? 전역?) 아직 모름. 검색 결과 화면 캡처에서 결과 아이템의 구조를 보면
  단서가 될 수 있음(예: `AppListItem`류 재사용이면 커뮤니티/기록 검색일 가능성).
- **제안 리스트 row(text+divider)와 최근 검색어 chip 둘 다 신규 디자인 시스템 컴포넌트
  승격 불필요** — 화면 내부 처리로 충분하다고 각 캡처 문서에 기록됨.
- **검색 결과 화면(캡처 4)에서는 앞선 3개 캡처의 색상 불일치 패턴이 재현되지 않음** —
  `#e0e0e0`은 `AppListItem`의 기본 구분선(`Color.Border.default`)과, `#f3f3f3`은 섹션
  구분선(`Color.Object.muted`)과 각각 정확히 일치. 오히려 `AppListItem`의 `.large`/
  `.xlarge`/`.medium` variant와 `AppTabBar`가 이 화면과 토큰 단위로 완전히 일치해
  **신규 컴포넌트 없이 기존 것만 조합**하면 되는 베스트 케이스 — 색상 재확인이 필요한
  화면은 검색 진행 시 캡처 2·3(chip/구분선)에 한정됨.
- **검색 대상 3종 확정**: 나의 일지(Record, 로컬), 정책 정보(이미 구현된 Home 기능 —
  `PolicyRepository`/`PolicyEndpoint`), 게시글(Community post). 통합 검색이 클라이언트
  병렬 호출인지 서버 통합 엔드포인트인지는 Swagger 확인 필요.
- **단일 카테고리 탭 3개(캡처 5~7) 상세 대조 결과 발견된 Figma 결함 — 구현 스펙으로
  그대로 인코딩 금지**:
  1. 세 프레임 모두 헤더 "총 12개" 텍스트가 동일(정책 정보=7, 게시글=5가 정답이므로
     명백한 placeholder 복사-붙여넣기 실수).
  2. 렌더링된 아이템 수가 실제 총 개수와 불일치(나의 일지: 6/12, 정책 정보: 4/7) —
     페이지네이션 여부 백엔드 확인 필요.
  3. 나의 일지 탭만 "마지막 아이템만 구분선 생략" 규칙이 깨져 4개 연속 구분선 없음 —
     다른 두 탭(정책 정보/게시글)은 규칙 정상 준수, 나의 일지 쪽이 예외(Figma 실수).
  4. 게시글 탭 프레임은 레이어 이름도 "정책 정보 탭"으로 오기돼 있고, 탭바 선택
     상태도 "정책 정보"인 채로 남아 콘텐츠(게시글)와 모순 — 정책 정보 프레임을
     복제해 콘텐츠만 바꾸고 탭바를 안 고친 것으로 추정.
  - 공통점: 새 색상·컴포넌트는 전혀 없음(전부 기존 `AppListItem` variant +
    `AppTabBar` 그대로) — 위 4가지는 순수 Figma 콘텐츠 작성 결함이며 디자인 시스템
    자체는 흔들림 없음.

---

## Part 6. 다른 세션에서 붙여넣을 Resume Prompt

```text
검색(Search) 화면 Figma 캡처와 SwiftUI 구현을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove/frontend

먼저 docs/figma/search/HANDOFF.md 를 읽고 그 절차를 따라줘. Part 5의 "공통 관찰
사항"을 꼭 확인해줘 — 색상 불일치(#e0e0e0 vs #f3f3f3) 패턴이 반복되고 있어서
구현 전에 사용자 확인이 필요하다.
(이어서 README.md 와 이미 캡처된 상태 문서 3개도 읽어줘.)

TalkToFigma 사용:
1) socket relay가 3055에 떠 있는지 확인 (curl http://localhost:3055/).
2) claude mcp list 에 TalkToFigma가 있는지 확인. 없으면 record/HANDOFF.md
   Part 1-B 등록 후 claude 재시작 필요.
3) ToolSearch로 mcp__TalkToFigma__* 도구 로드 → join_channel("chamchamcham").
4) Figma에서 내가 프레임 하나를 선택하면, get_selection → read_my_design →
   scan_text_nodes → export_node_as_image(PNG, scale 2) 순으로 캡처.
   PNG 파일 저장은 클라이언트 한계로 불가하니 read_my_design 값으로 정밀 기록.

현재 진행 상태: 7개 상태 전부 캡처 완료. 다음은 Part 4(구현)로 진행 — Core/DesignSystem,
관련 Business Rule, Swagger를 대조해 구현 계획을 세운 뒤 사용자 수락을 받고 진행해줘.
단, Part 5에 정리된 "Figma 결함"(총 12개 오표기, 아이템 수 불일치, 나의 일지 탭 구분선
붕괴, 게시글 탭 탭바 모순)은 그대로 스펙화하지 말고 정정해서 반영할 것 — 구현 계획에
이 4가지를 어떻게 정정했는지 명시해줘.
```
