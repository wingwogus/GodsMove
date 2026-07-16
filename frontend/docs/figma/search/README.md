# 검색(Search) Figma Captures

이 폴더는 TalkToFigma MCP로 수집한 검색 화면의 Figma 캡처와 구현 준비 메모를
저장한다. 사용자가 화면을 하나씩 순서대로 전달하는 방식으로 진행 중이며,
총 7개 상태(검색 진행 시 3개 + 검색 결과 화면 4개)를 수집할 예정이다. 모두
수집한 뒤 기존 SwiftUI 화면, 디자인 시스템, 관련 Business Rule, 배포 Swagger를
함께 대조해 구현 계획을 확정한다.

## MCP Connection

- TalkToFigma channel: `chamchamcham`
- Figma에서 화면 또는 상태 프레임 하나만 선택한다.
- 캡처 순서: `get_selection` → `read_my_design` → `scan_text_nodes` →
  `export_node_as_image`
- 문서 전체 조회는 사용하지 않는다.
- PNG 참고: Claude Code에서 `export_node_as_image`는 결과를 인라인 이미지로만
  반환하고 디스크 파일로 저장하지 않는다. 구조·텍스트·색상은 `read_my_design`
  값으로 정밀 기록하고, PNG 파일이 필요하면 Figma에서 수동 Export(2x)한다.
- 연결 절차(socket relay, MCP 등록, 플러그인 connect) 전체는
  [record/HANDOFF.md](../record/HANDOFF.md) Part 1과 동일 — 이 폴더에서는 반복하지 않는다.

## Captures

- [검색 / default](2026-07-16-search-default.md) — `1547:23510`, 검색어 미입력 +
  최근 검색어 0건(헤더 + "전체 삭제" 버튼만 노출). `AppSearchBar`와 완전 일치.
- [검색 / default - 최근 검색어 존재 시](2026-07-16-search-history-exists.md) —
  `1547:23476`, 최근 검색어 6개(2행×3열 wrap chip). `AppChip`과 거의 일치하나
  border color 불일치(`#e0e0e0` vs `AppChip` 기본 `#f3f3f3`) 확인 필요.
- [검색 / 검색어 입력 중](2026-07-16-search-typing.md) — `1547:23494`, 입력값 있는
  검색바(clear 아이콘) + "동일 검색어" 1개 + "연관 검색어" 9개 제안 리스트. 신규 DS
  컴포넌트 불필요(Text+AppDivider 화면 내부 처리), `AppDivider` 기본색도 `#f3f3f3` vs
  `#e0e0e0` 불일치.

## Capture Checklist

### 검색 진행 시 (3개) — 완료

1. **default** — 완료 (최근 검색어 0건)
2. **최근 검색어 있음 상태** — 완료 (6개 chip, wrap 그리드)
3. **검색어 입력 중 상태** — 완료 (동일/연관 검색어 제안 리스트)

### 검색 결과 화면 (4개)

4. 검색 결과 — 기본/성공 상태
5. 검색 결과 — 빈 결과(0건) 상태
6~7. 나머지 2개 상태 (사용자가 전달하는 대로 채움 — 로딩/오류 등 예상)

Figma에 없는 런타임 상태는 기존 디자인 시스템 패턴을 사용하되 구현 계획에
명시한다.

Claude 또는 다른 AI 세션에서 캡처와 구현 작업을 이어갈 때는
[HANDOFF.md](HANDOFF.md)를 인수인계 문서로 사용한다.
