# Home (홈) Figma Captures

이 폴더는 TalkToFigma MCP로 수집한 홈 탭 화면의 Figma 캡처와 구현 준비
메모를 저장한다. 화면을 모두 수집한 뒤 기존 `HomeView.swift` 와이어프레임,
디자인 시스템, `docs/Business Rule.md`, 배포 Swagger를 함께 대조해 구현
계획을 확정한다.

**구현 완료 (2026-07-14, 빌드 성공)** — `Features/Home/{Data,Domain,Presentation}` 전체 구현.
상세는 [HANDOFF.md](HANDOFF.md) Part 5, [구현 계획서](2026-07-14-home-implementation-plan.md),
[백엔드 충돌 트래킹](2026-07-14-home-backend-conflicts.md) 참고.

## MCP Connection

- TalkToFigma channel: `chamchamcham`
- Figma에서 화면 또는 상태 프레임 하나만 선택한다.
- 캡처 순서: `get_selection` → `read_my_design` → `scan_text_nodes` →
  `export_node_as_image`
- 문서 전체 조회는 사용하지 않는다.
- PNG 참고: Claude Code에서 `export_node_as_image`는 결과를 인라인 이미지로만
  반환하고 디스크 파일로 저장하지 않는다.

## Captures

- [홈 / default](2026-07-14-home-main-default.md) — `379:676`, top-app-bar(검색/알림)
  + weather/tip 2단 카드 + 나의 최근 영농 기록(가로 스크롤 카드) + 오늘의 추천 정책
  + 나의 게시판 인기글(`AppListItem`류) + nav-bar. 기존 `HomeView.swift` wireframe과
  구조 차이 큼(검색창 없음, tip/weather 2단 카드 등) — 상세는 문서 참고
- [홈 -> 날씨 상세](2026-07-14-home-weather-detail.md) — `1247:25659`, back 버튼 top-app-bar
  + 오늘 날씨(체감/최저·최고/주소) + 관수 팁 배너 + 2×2 상세 그리드(자외선/강수확률/습도/풍속)
  + 5일 주간 예보. weekly-forecast의 "오늘" 아이콘이 상단 today 아이콘과 불일치(샘플
  데이터 이슈로 판단)
- [홈 -> 정책 리스트](2026-07-14-home-policy-list.md) — `1172:18031`, back top-app-bar
  + 카테고리 칩 11개(전체+지원금/융자·금융/시설·장비/교육/복지/인증/판로/창업/환경·인프라/기타,
  사용자 확인 목록과 일치) + 정렬 드롭다운(추천순) + list row(타이틀/기관/대상자·지원내용·접수기간).
  **정책 상세는 아직 미캡처**(선택된 프레임이 리스트였음)

## Capture Flow

1. Figma에서 홈 프레임 또는 상태를 하나 선택한다.
2. `캡쳐: 홈 / <화면명> / <상태>` 형식으로 전달한다.
3. 노드 ID, 화면 크기, 상태, 구조, 텍스트 스타일, 색상, 주요 치수를 이 폴더에
   기록한다.
4. Figma와 제품 규칙 또는 디자인 시스템이 충돌하면 구현 전에 문서에 분리해
   기록한다.
5. 모든 화면 수집이 끝나면 `홈 디자인 수집 끝`이라고 알린다.
6. 전체 상태 행렬과 현재 코드(`HomeView.swift` 와이어프레임)/API 준비 상태를
   기준으로 구현 계획을 작성한다.

## Capture Checklist

기존 와이어프레임(`Features/Home/Presentation/Views/HomeView.swift`) 기준
추정 섹션 — Figma 실제 구성으로 검증 필요:

- 헤더 (타이틀 + 알림 아이콘)
- 검색창 (작물 질문 검색)
- 팁 배너
- 날씨 예보 카드 (일자별 아이콘/기온 + 방제 추천 문구)
- 나의 최근 영농 기록 (가로 스크롤 카드 + 새로 작성하기 버튼) — 빈 상태(기록
  0건) 포함
- 오늘의 추천 정책 (D-day + 정책명)
- 게시판 인기글 (태그 + 미리보기, 다건)
- 로딩 / 오류·재시도 상태 (섹션별로 존재하는지 확인)
- 작은 기기: iPhone SE 2/3에서 가로 스크롤 카드, 날씨 카드 겹침 여부

Figma에 없는 런타임 상태는 기존 디자인 시스템 패턴을 사용하되 구현 계획에
명시한다.

Claude 또는 다른 AI 세션에서 캡처와 구현 작업을 이어갈 때는
[HANDOFF.md](HANDOFF.md)를 인수인계 문서로 사용한다.
