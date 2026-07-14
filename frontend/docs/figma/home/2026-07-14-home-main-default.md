# 홈 / default

- Captured: 2026-07-14
- Source: `mcp__TalkToFigma__get_selection` → `read_my_design` → `export_node_as_image` (PNG, scale 2)
- Figma node: `379:676`, frame name `홈 / default`
- Frame size: 390 × 1413 (iPhone 13 width, full scroll content height)
- Background: `#fafafa`
- PNG: 인라인 이미지로만 확인, 디스크 미저장 (TalkToFigma 한계)

## 구조 개요

```
홈 / default (390×1413, #fafafa)
├─ Status Bar - iPhone (기기 프레임, 구현 대상 아님)
├─ top-app-bar (390×60)
│   ├─ 타이틀 "홈"
│   └─ icon-1(icon/search) + icon-2(icon/notifications)
└─ content (390×1163)
    ├─ info-card (350×167) — weather-card + tip-card 좌우 배치
    ├─ record (350×343) — "나의 최근 영농 기록"
    ├─ policy (350×150) — "오늘의 추천 정책"
    └─ community (350×407) — "나의 게시판 인기글"
└─ nav-bar (390×72, 하단 고정)
```

## Section 1 — top-app-bar

- 타이틀 "홈": Pretendard Bold 32 / line-height 41.6 / letter-spacing -0.32 / `#242428`
- `icon-1` = `icon/search` (32×32, 48×48 탭 영역), `icon-2` = `icon/notifications` (동일 크기)
- 알림 뱃지(안 읽음 표시) 없음 — 현재 캡처에는 unread indicator 미확인

## Section 2 — info-card (weather-card + tip-card, 각 167×167 반반)

### weather-card (좌측, 흰 배경 `#ffffff`, border `#f3f3f3`, radius 16)

- header: "날씨" (Pretendard Medium 16 / `#4f4f4f`) + `icon/arrow_forward` (24×24, 우측 정렬)
- content:
  - `icon/clear_day` (40×40) + "29°" (Pretendard SemiBold 32 / `#1a1a1a`)
  - info row: "최저 19°" / 세로 divider(`#acacac`) / "최고 31°" (둘 다 Pretendard Medium 16 / `#878787`)
- 날씨 아이콘은 `icon/clear_day` 확인 — 다른 날씨 상태(비/흐림 등) 아이콘 이름은 미확인, 구현 시 아이콘 세트 확인 필요

### tip-card (우측, 배경 `#e6f7bf`, radius 16)

- badge-label "tip": 배경 `#38c284`, 텍스트 흰색, radius 8 (pill)
- 본문: "최근 관수 간격이  평균 7일로 길어요.\n화창한 오늘 즐겁게\n작업하는건 어떨까요?" — Pretendard Medium 16 / `#27865c`, 3줄
- Record 탭 캡처에서 확인된 것과 유사한 톤(관수 간격 팁) — 홈에서도 동일 팁 로직 재사용 가능성

## Section 3 — record ("나의 최근 영농 기록", 350×343)

- title row: "나의 최근 영농 기록" (Pretendard SemiBold 24 / `#1a1a1a`) + `icon/arrow_forward_ios` (24×24)
- card-list: 가로 스크롤, 카드 3장 이상 확인(문서상 폭 806 > 프레임 350 → overflow, 가로 스크롤 컨테이너). 카드 스펙:
  - 258×232, 흰 배경, border `#e0e0e0`, radius 20
  - image 영역: 226×126, radius 12, `scaleMode: FILL` + 상단 그라디언트 shadow(`#343434a3` → 투명) 오버레이
    - shadow 위에 "mm.dd" (Pretendard Medium 15 / 흰색) — 날짜 placeholder
    - badge-1 (흰 배경, radius 8) "레이블" 텍스트 placeholder — 실제 값 미정(작업유형? 작물?)
  - content: "타이틀"(SemiBold 24 / `#4f4f4f`) + caption 2줄("캡션\n캡션", Medium 18 / `#878787`)
  - 텍스트가 전부 플레이스홀더("타이틀"/"캡션"/"레이블") — 실제 바인딩 필드는 Record 탭 API(`GET /farming-records`) 응답과 매핑 필요 추정
- 버튼 "새로 작성하기": 배경 `#343434`(다크), radius 12, height 48, `icon/add` leading + 라벨(Medium 16, 흰색), full-width

## Section 4 — policy ("오늘의 추천 정책", 350×150)

- title row: "오늘의 추천 정책" (SemiBold 24 / `#1a1a1a`) + `icon/arrow_forward_ios`
- card (350×103, 흰 배경, border `#f3f3f3`, radius 12):
  - badge-label "D-12": 배경 `#e6f7bf`, 텍스트 `#27865c`, radius 8
  - "정책 제목" (Medium 24 / `#4f4f4f`) — placeholder
  - 원형 버튼 48×48, 배경 `#38c284`, radius 1000(circle), `icon/arrow_forward` 내장 — 상세 이동용으로 추정

## Section 5 — community ("나의 게시판 인기글", 350×407)

- title row: "나의 게시판 인기글" (SemiBold 24 / `#1a1a1a`) + `icon/arrow_forward_ios`
- list 컨테이너 (350×360, 흰 배경, border `#f3f3f3`, radius 20)에 list row 3개(각 350×120):
  - row 1: badge-1 "Q&A"(배경 `#38c284`, 흰 텍스트) + badge-2 "작물 카테고리"(배경 `#e6f7bf`, 텍스트 `#27865c`) — 2개 뱃지
  - row 2: badge-2만 "작물 카테고리" 노출, 옆에 빈 뱃지(배경 `#f3f3f3`, 텍스트 없음) — 게시판 유형 badge가 없을 때의 빈 슬롯으로 추정
  - row 3: row 2와 동일 패턴이나 content 폭이 더 넓음(318 vs 214) — 썸네일 이미지 유무 차이로 추정(레이아웃 값이 완전히 일관되진 않음, 실제 구현 시 `AppListItem` 표준 스펙 따르기)
  - 각 row: "타이틀"(SemiBold 20 / `#4f4f4f`) + "캡션"(Medium 16 / `#878787`) + 88×88 이미지 썸네일(radius 8, `scaleMode: FILL`)
  - Community 탭 캡처의 `AppListItem` 계열과 동일 톤 — 재사용 가능성 높음

## nav-bar (하단, 390×72, 흰 배경, border `#f3f3f3`)

- 4개 탭: 홈(`icon/home`, 선택 상태: 텍스트 `#242428` SemiBold) / 영농 기록(`icon/assignment`) / 정보 공유(`icon/forum_line`) / 프로필(`icon/person_line`) — 비선택 텍스트 `#4f4f4f` Medium
- 최근 커밋(`f4c5ff3` 커스텀 nav bar 도입)의 `AppNavBar`와 아이콘 세트 일치 여부 코드 대조 필요

## 색상 팔레트 (이 화면 기준)

| 색상 | 용도 |
|---|---|
| `#fafafa` | 화면 배경 |
| `#f3f3f3` | 카드/구분선 보더 |
| `#242428` | 타이틀, nav 선택 텍스트 |
| `#1a1a1a` | 섹션 타이틀, 날씨 온도 |
| `#4f4f4f` | 보조 라벨, 카드 타이틀 |
| `#878787` | 캡션/최저·최고 온도 |
| `#e6f7bf` | 연두 배경(tip 카드, 뱃지 secondary) |
| `#27865c` | 초록 텍스트(tip 본문, 뱃지 secondary 텍스트) |
| `#38c284` | 초록 accent(뱃지 primary, tip 아이콘 배경, 원형 버튼) |
| `#343434` | 다크 버튼 배경 |
| `#e0e0e0` | 레코드 카드 보더 |

## 디자인 시스템 대조 후보 (코드 검증 필요)

- `AppTopAppBar` — 타이틀 + trailing 아이콘 2개(search, notifications) 패턴
- `AppBadge` — primary(초록 solid, 흰 텍스트) / secondary(연두 배경, 초록 텍스트) / empty(회색, 텍스트 없음) 3가지 변형 후보
- `AppButton` — 풀와이드 다크 버튼("새로 작성하기"), 원형 아이콘 버튼(정책 카드 화살표)
- `AppListItem` — 커뮤니티 인기글 row와 거의 동일 톤(뱃지+타이틀+캡션+썸네일), Record 탭에서 이미 `.large` variant 사용 확인됨
- `AppNavBar` — 최근 도입된 커스텀 nav bar와 아이콘 세트 대조 필요
- **레코드 카드(258×232, 세로형)는 기존 `AppListItem`과 다른 형태** — 가로 스크롤 카드 컴포넌트 신규 후보 (다른 화면에 동일 패턴 있는지 먼저 확인)

## 기존 HomeView.swift 와이어프레임과의 차이

- 기존 wireframe은 `List`형 단일 컬럼 구조였지만 실제 Figma는 weather+tip 2단 카드, record는 가로 스크롤 카드, policy는 단일 카드, community는 `AppListItem`형 리스트로 구체적 컴포넌트 스펙이 있음 — wireframe은 폐기하고 이 캡처 기준으로 재작성 필요
- 기존 wireframe엔 없던 요소: tip 카드의 badge-label "tip", weather의 최저/최고 온도, record 카드의 날짜(mm.dd)+뱃지 오버레이, policy의 원형 화살표 버튼
- 기존 wireframe에 있던 "검색창"(placeholder 텍스트형 검색 바)은 이 Figma엔 없음 — top-app-bar의 `icon/search` 아이콘 하나로 대체된 것으로 보임(검색은 아이콘 탭 → 별도 화면 이동 추정)

## 미해결 질문 / 백엔드 협의 필요

1. **레코드 카드 뱃지/날짜 실데이터**: "레이블"·"mm.dd"가 어떤 필드에 매핑되는지 — Record 탭 `GET /farming-records` 응답 필드(`workType`, `workedAt`, `thumbnailUrl`)와 대조 필요
2. **정책 카드 데이터 출처**: "오늘의 추천 정책" — 청년농 지원금 등 정책 추천 API가 백엔드에 존재하는지 확인 필요(Swagger에 없으면 API 미비)
3. **인기글 데이터 출처**: "나의 게시판 인기글" — Community 탭 API에 "인기글" 정렬/필터가 있는지, 아니면 별도 홈 전용 API인지 확인 필요
4. **날씨 데이터 출처**: Record 탭에서 이미 `GET /farms/{id}/weather` 사용 확인됨 — 홈에서도 동일 API 재사용 가능성 높음, 최저/최고 온도 필드 존재 여부 확인 필요
5. **팁 배너 로직**: "최근 관수 간격이 평균 N일" 같은 동적 문구 생성 로직(AI? 룰 기반?) 정의 필요
6. **empty/loading/error 상태**: 이번 캡처엔 없음 — 각 섹션(레코드 없음, 정책 없음, 인기글 없음, 날씨 조회 실패) 별도 상태 캡처 또는 정의 필요
7. **알림 아이콘 unread 뱃지**: 현재 캡처엔 없음 — 다른 화면(마이페이지 등)에 unread 패턴 있는지 확인 필요

## 다음 캡처 후보

- 로딩/빈/오류 상태 (레코드 없음, 정책 없음, 인기글 없음)
- 알림 아이콘 badge 상태
- SE 2/3 작은 화면 대응 여부
