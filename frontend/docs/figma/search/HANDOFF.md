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

### 캡처 완료 (3/7)

| 상태 | node | 문서 | 비고 |
|---|---|---|---|
| 검색 / default | `1547:23510` | [문서](2026-07-16-search-default.md) | 최근 검색어 0건, `AppSearchBar` 완전 일치 |
| 검색 / default - 최근 검색어 존재 시 | `1547:23476` | [문서](2026-07-16-search-history-exists.md) | 6개 chip wrap 그리드, `AppChip` border color 불일치(`#e0e0e0` vs `#f3f3f3`) |
| 검색 / 검색어 입력 중 | `1547:23494` | [문서](2026-07-16-search-typing.md) | 동일/연관 검색어 제안 리스트(텍스트+구분선, 신규 DS 컴포넌트 불필요). `AppDivider` 기본색도 동일 패턴 불일치 |

**검색 진행 시 3개 전부 완료.** 남은 캡처: 검색 결과 화면 4개 상태 전부.

---

## Part 6. 다른 세션에서 붙여넣을 Resume Prompt

```text
검색(Search) 화면 Figma 캡처와 SwiftUI 구현을 이어가자.
프로젝트: /Users/user/Project/ChamChamCham/GodsMove/frontend

먼저 docs/figma/search/HANDOFF.md 를 읽고 그 절차를 따라줘.
(이어서 README.md 와 이미 캡처된 상태 문서들도 읽어줘.)

TalkToFigma 사용:
1) socket relay가 3055에 떠 있는지 확인 (curl http://localhost:3055/).
2) claude mcp list 에 TalkToFigma가 있는지 확인. 없으면 record/HANDOFF.md
   Part 1-B 등록 후 claude 재시작 필요.
3) ToolSearch로 mcp__TalkToFigma__* 도구 로드 → join_channel("chamchamcham").
4) Figma에서 내가 프레임 하나를 선택하면, get_selection → read_my_design →
   scan_text_nodes → export_node_as_image(PNG, scale 2) 순으로 캡처.
   PNG 파일 저장은 클라이언트 한계로 불가하니 read_my_design 값으로 정밀 기록.

총 7개 상태(검색 진행 시 3개 + 검색 결과 화면 4개) 중 남은 것을 이어서
캡처한다. 모든 캡처가 끝나기 전엔 SwiftUI 구현을 시작하지 말고, 끝나면
Core/DesignSystem, 관련 Business Rule, Swagger를 대조해 구현 계획을 세운 뒤
진행해줘.
```
