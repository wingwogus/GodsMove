# 홈(Home) 구현 계획

작성일: 2026-07-14. 캡처 완료(default, 날씨 상세, 정책 리스트) 기준 종합 계획.
정책 상세는 네이티브 UI 없음(외부 웹 링크 이동)으로 확정, 캡처 대상에서 제외.

## 1. 모듈 구조

`Features/Record`, `Features/Community` 패턴 그대로 재사용.

```
Features/Home/
├─ Data/
│  ├─ DTOs/HomeResponseDTOs.swift      (WeatherSummary, RecentRecord, PolicyRecommendation, PopularPost 응답 DTO)
│  ├─ HomeEndpoint.swift               (Endpoint enum: weather / recent records / policy recommendation / popular posts)
│  └─ HomeRepository.swift             (protocol + impl, APIClient.send 호출 후 Domain 매핑)
├─ Domain/
│  └─ HomeModels.swift                 (WeatherSummary, RecentRecordPreview, PolicyRecommendation, PopularPostPreview)
└─ Presentation/
   ├─ ViewModels/HomeViewModel.swift   (@MainActor @Observable, 섹션별 개별 load 함수 + errorMessage)
   └─ Views/
      ├─ HomeView.swift               (기존 wireframe 교체)
      ├─ WeatherCard.swift / PolicyCard.swift / RecentRecordCard.swift 등 섹션별 서브뷰
      └─ PolicyListView.swift         (정책 리스트, 별도 화면)
```

- `MainTabView.swift:80`의 `case 0: HomeView()`를 `HomeView(container: container)`로 변경(Record/Community와 동일 방식).
- Record/Community 전례상 **GET 응답 로컬 캐시 없음** — Home도 네트워크 전용, `posts.isEmpty` 가드로 탭 재진입 시 중복 조회만 방지.

## 2. API 매핑 및 현재 배포 계약 상태

| 섹션 | 엔드포인트 | 사용 필드 | 상태 |
|---|---|---|---|
| 날씨 카드(홈) | `GET /farms/{farmId}/weather` | `observedAt`, `temperature`, `weatherCondition` | ⚠️ **최저/최고 기온 필드 없음** — Figma엔 있으나 API 미제공 |
| 날씨 상세 | (동일 엔드포인트, 필드 부족) | - | 🚫 **체감온도/최저·최고/자외선/강수확률/습도/풍속/5일예보/주소 전부 응답에 없음** |
| 최근 영농 기록 | `GET /farming-records?size=3` | `cropName`, `workType`, `workedAt`, `memoPreview`, `thumbnailUrl`, `weatherCondition`, `weatherTemperature` | ✅ 충분(정렬 파라미터 명시 안 됨 — `workedAt desc` 가정, 확인 필요) |
| 추천 정책(홈 단일 카드) | `GET /policies/recommendations?size=1&sort=RECOMMENDED` | `programTitle`, `agencyName`, `applicationPeriodLabel`, `reason`, `score` | ⚠️ **구조화된 마감일 없음**(문자열 라벨만) — D-day 배지 계산 불가 |
| 정책 리스트 | `GET /policies/recommendations?benefitCategory=&sort=` | 동일 + `benefitCategory` | ⚠️ `benefitCategory`가 스키마상 자유 문자열, 10종 enum 백엔드 미검증 |
| 정책 상세 | `GET /policies/{policyProgramId}` | `applicationUrl` / `sourceUrl` | ✅ 외부 링크 이동만 필요하므로 충분 |
| 인기글 | `GET /community/posts?sort=POPULAR&size=3` | `title`, `bodyPreview`, `thumbnailUrl`, `postType`, `cropName`, `likeCount`, `commentCount` | ✅ 충분 |
| 알림 unread 배지 | 없음 | - | 🚫 엔드포인트 자체 없음 |

전체 충돌 목록은 [백엔드 협의 트래킹 문서](2026-07-14-home-backend-conflicts.md)에 C-1~C-6으로 정리.

## 3. 디자인 시스템 매핑 (요약)

| Figma 조각 | 처리 방침 |
|---|---|
| top-app-bar (홈, 타이틀+아이콘2개) | `AppTopAppBar(title:, trailing:)` 그대로 재사용 |
| top-app-bar (뒤로가기+중앙타이틀, 날씨상세/정책리스트) | `AppTopAppBar(title:, isDetail: true, leading:, trailing:)` 그대로 재사용 |
| weather-card / tip-card | 신규 feature-local 컴포저블(디자인 시스템에 없음, `AppBadge`+텍스트로 내부 구성) |
| 최근 영농 기록 카드(258×232) | `AppCard(.medium)` 재사용 (기존 258×261과 거의 일치, 높이만 스펙 대비 확인) |
| "새로 작성하기" 버튼 | `AppButton(title:, systemImage: "plus", variant: .secondary, size: .small, fullWidth: true)` |
| 추천 정책 카드(D-day+제목+원형버튼) | `AppBadge(.solidPastel/.secondary)` + `AppButton(systemImage:, variant: .primary, size: .small)` 조합, 카드 래퍼는 feature-local |
| 인기글 리스트 row | `AppListItem` 재사용 — 배지 2개(하나는 solid, 하나는 pastel) 지원을 위해 `AppListItem` 뱃지 스타일 파라미터화 소폭 확장 필요, 또는 Community 자체 row 구성 방식 재사용 검토 |
| nav-bar | `AppNavBar` 이미 `MainTabView`에 연결됨, 변경 없음 |
| 카테고리 칩 11개(정책 리스트) | `AppChip(style: .solid)` 재사용. 단, Figma엔 미선택 칩에 보더가 있는데 현재 `.solid` variant는 보더 없음 — 시각 확인 후 필요시 소폭 확장 |
| 정렬 드롭다운 | `AppSortButton` 그대로 재사용 |
| 날씨 상세 2×2 detail-card | 신규 컴포넌트 필요하지만 **API 자체가 없어 이번 라운드에서 보류** |
| 주간 예보 카드 | 동일하게 API 부재로 보류 |
| 정책 리스트 row(제목+기관+3행 라벨/값) | `AppListItem(size: .xlarge, infoRows: [...])`가 거의 정확히 일치 — 그대로 재사용 |

## 4. 섹션별 구현 범위 (제안)

### 4-A. top-app-bar
- 타이틀 "홈" + 검색 아이콘(inert, 검색 화면 미설계) + 알림 아이콘(inert, unread 배지 API 없음).

### 4-B. 날씨 카드 — **확정: 더미 데이터 포함 전체 구현** (2026-07-14 사용자 확정)
- 홈 카드 + 날씨 상세 화면 **UI는 Figma 그대로 전부 구현**한다.
- 실데이터: `temperature`, `weatherCondition`(→아이콘 매핑) — `GET /farms/{farmId}/weather` 사용.
- **더미 데이터(하드코딩, 명확히 주석 처리)**: 체감온도, 최저/최고 기온, 자외선 지수, 강수확률,
  습도, 풍속, 5일 주간 예보, 주소. 값은 Figma 캡처에서 본 예시값(체감 30°, 최저19·최고31,
  자외선 높음, 강수확률 0%, 습도45%, 풍속2.1m/s 등)을 그대로 사용.
  **백엔드가 필드 제공하면 더미를 실데이터로 교체**하는 것이 후속 작업(C-1 참고).
- weatherCondition은 raw string, 아이콘 매핑 테이블 필요(Record 탭에서도 동일하게 미확정 — `docs/figma/record/README.md` 참고). 매핑 실패 시 `clear_day` 기본값 fallback.
- 날씨 상세 진입 화살표는 **활성화**(더미 포함 전체 화면 이동).
- tip 배너: 문구 생성 로직 미정의 → 우선 정적 placeholder 문구 사용(C-7), 동적 로직은 후속.

### 4-C. 나의 최근 영농 기록
- `GET /farming-records?size=3`, 정렬은 서버 기본값 신뢰(확인 필요, C-6).
- 필드 매핑(Record 자체 리스트 관례 재사용): 뱃지=`cropName`, 날짜=`workedAt`(mm.dd), 타이틀=`memoPreview`(축약), 캡션 2줄=`workType` 라벨 + `weatherCondition`/`weatherTemperature`.
- "새로 작성하기" → Record 탭의 기존 `RecordComposeView` 재사용(텍스트 작성 플로우로 바로 진입).
- 빈 상태(기록 0건): "아직 작성한 기록이 없어요" + 작성 CTA.
- 섹션 화살표 → Record 탭으로 전환(탭 인덱스 전환) 또는 Record 전체 리스트 화면 이동.

### 4-D. 오늘의 추천 정책
- `GET /policies/recommendations?size=1&sort=RECOMMENDED`의 첫 항목만 카드로 표시.
- **D-day 배지 → 확정: 제거하고 "기간" 텍스트로 대체** (2026-07-14 사용자 확정). 카드 배지 자리
  대신 정책 제목 아래에 "기간: {applicationPeriodLabel}"을 텍스트로 표시. 날짜 파싱 없음.
- 추천 결과 0건일 때: 카드 대신 빈 상태 문구.
- 원형 버튼/카드 탭 → 정책 리스트 화면으로 이동.

### 4-E. 정책 리스트
- 카테고리 칩 11개(전체+10종) — Swift enum으로 정의, `benefitCategory` 쿼리 파라미터로 그대로
  전송 (**확정: 백엔드 검증 없이 우선 진행**, 2026-07-14). 필터 결과가 조용히 비어도 에러가 아닌
  정상 빈 상태로 처리(C-4).
- 정렬 드롭다운 "추천순"(다른 옵션 미확인 — 우선 정렬 파라미터 없이 고정 또는 `RECOMMENDED` 단일 옵션으로 시작).
- row 탭 → **네이티브 상세 없음**, `GET /policies/{id}`로 `applicationUrl`/`sourceUrl` 조회 후
  **확정: `@Environment(\.openURL)`로 시스템 브라우저(Safari 앱) 이동** (2026-07-14 사용자 확정,
  인앱 브라우저 아님). 레포에 기존 패턴 없음 — 이번이 첫 사례.
- 빈 목록/로딩/오류: Record/Community 관례(ProgressView/emptyState/errorMessage) 그대로.

### 4-F. 나의 게시판 인기글
- `GET /community/posts?sort=POPULAR&size=3`.
- row 탭 → 기존 `CommunityDetailView` 재사용.
- 섹션 화살표 → Community 탭 전환(정렬=POPULAR로 진입 옵션 고려, 확인 필요).
- 빈 상태: "아직 인기글이 없어요" 문구.

### 4-G. nav-bar
- 변경 없음, 기존 `AppNavBar` 그대로.

## 5. SE 2/3 대응

- 최근 영농 기록 가로 스크롤 카드: 프레임 폭에 관계없이 `ScrollView(.horizontal)`로 자연 대응.
- weather+tip 2단 카드(각 167×167 고정폭 절반): SE 폭(320)에서 비율 유지, `GeometryReader` 없이 `HStack` 균등 분배로 충분할 것으로 예상.
- 정책 리스트 카테고리 칩: 가로 스크롤이라 문제 없음.
- 인기글/정책 리스트 row: 텍스트 `lineLimit`/`minimumScaleFactor`로 타이틀 잘림 방지.

## 6. 결정 사항 (2026-07-14 확정)

1. 날씨 상세 화면: **더미 데이터 포함 전체 구현** (API 없는 필드는 하드코딩, 후속 교체).
2. 정책 D-day 배지: **제거, "기간: {label}" 텍스트로 대체**.
3. 정책 카테고리 10종: **백엔드 검증 없이 앱 자체 enum으로 우선 진행**.
4. 정책 리스트 row 탭: **시스템 브라우저(`openURL`)**로 외부 이동.
5. 검색 아이콘/알림 아이콘: inert placeholder로 진행(이견 없으면 그대로).
6. tip 배너 문구: 정적 placeholder 문구로 진행(이견 없으면 그대로).

## 7. 완료 기준

구현 후 `docs/figma/home/HANDOFF.md` Part 5 갱신 + 아래 빌드 실행:

```bash
cd /Users/user/Project/ChamChamCham/GodsMove/frontend/ChamChamCham
xcodebuild -scheme ChamChamCham -destination 'platform=iOS Simulator,name=iPhone 17' build
```
