# 홈 -> 날씨 상세

- Captured: 2026-07-14
- Source: `mcp__TalkToFigma__get_selection` → `read_my_design` → `export_node_as_image` (PNG, scale 2)
- Figma node: `1247:25659`, frame name `홈 -> 날씨 상세`
- Frame size: 390 × 844
- Background: `#ffffff`
- 진입 지점: 홈 화면 weather-card의 `icon/arrow_forward` 탭 (추정, 홈 default 캡처의 weather-card header 화살표와 동일 패턴)
- PNG: 인라인 이미지로만 확인, 디스크 미저장

## 구조 개요

```
홈 -> 날씨 상세 (390×844, #ffffff)
├─ Status Bar
├─ top-app-bar (390×60) — back 아이콘 + "날씨 상세" 타이틀 (trailing 빈 슬롯)
└─ content (390×601)
    ├─ today (390×160) — 오늘 날씨 요약 + tip 배너
    ├─ info (390×198) — 2×2 detail-card 그리드(자외선/강수확률/습도/풍속)
    ├─ divider
    └─ weekly-forecast (390×169) — 5일 주간 예보
```

## Section 1 — top-app-bar

- `icon-leading` = `icon/arrow_back_ios_new` (32×32, 48×48 탭 영역) — 뒤로가기
- 타이틀 "날씨 상세": Pretendard SemiBold 28 / line-height 36.4 / letter-spacing -0.28 / `#242428`, 중앙 정렬
- `icon-trailing`: 빈 프레임(48×48) — trailing 아이콘 없음, 타이틀 중앙 정렬을 위한 대칭용 spacer로 추정
- 홈 default의 top-app-bar(타이틀 좌측 정렬 + 아이콘 2개)와 다른 variant — back 버튼 있는 서브 페이지용 `AppTopAppBar` variant로 추정, 코드에 해당 variant 존재 확인 필요

## Section 2 — today (390×160)

- `icon/clear_day` (96×96, 홈 default 날씨 아이콘보다 큼)
- "29°": Pretendard **Bold** 32 / `#1a1a1a` (홈 default weather-card는 SemiBold였음 — 굵기 차이 확인 필요)
- "체감 30°" (Medium 16 / `#4f4f4f`) · 세로 divider(`#acacac`) · "최저 19° - 최고 31°" (각 파트 Medium 16 / `#4f4f4f`, "-"도 별도 텍스트 노드)
- "서울 중구 퇴계로 128": Medium 15 / `#878787` — 농장 주소 표시 (Farm 위치 데이터 매핑 추정)
- tip 배너: 배경 `#e6f7bf`, radius 8, 텍스트 "오늘은 관수하기 좋은 날씨에요!" (Medium 18 / `#27865c`, 중앙 정렬) — 홈 default의 tip-card와 톤 동일, 문구는 날씨 상세 전용으로 다름

## Section 3 — info (2×2 detail-card 그리드, 390×198)

4개 `detail-card` (각 169×93, 흰 배경, border `#e0e0e0`, radius 12):

| 카드 | 라벨 (SemiBold 15 / `#878787`) | 값 (SemiBold 20 / `#4f4f4f`) |
|---|---|---|
| 좌상 | 자외선 지수 | 높음 |
| 우상 | 강수확률 | 0% |
| 좌하 | 습도 | 45% |
| 우하 | 풍속 | 2.1m/s |

## Section 4 — weekly-forecast (390×169)

- 컨테이너: 배경 `#fafafa`, radius 16 (보더 `visible: false`)
- 헤더 "주간 예보": SemiBold 20 / `#4f4f4f`
- 5일 가로 배열(오늘/토/일/월/화), 각 컬럼:
  - 요일 라벨: "오늘"만 SemiBold 15 / `#1a1a1a`, 나머지는 Medium 15 / `#4f4f4f`
  - 날씨 아이콘 (40×40): `icon/snowflake`(오늘), `icon/clear_day`(토), `icon/rainy`(일), `icon/cloudy`(월), `icon/cloud`(화) — **5종 날씨 아이콘 이름 확정**
  - 기온: Medium 16 / `#1a1a1a`
- ⚠️ **샘플 데이터 불일치**: "오늘" 컬럼 아이콘이 `icon/snowflake`(31°)인데, 화면 상단 today 섹션은 `icon/clear_day`(29°) — 같은 "오늘"인데 다른 날씨로 표시됨. Figma 목업의 placeholder 데이터 불일치로 판단, 실제 구현에서는 하나의 데이터 소스로 통일

## 색상/타이포 추가 확인 사항

- "29°"가 이 화면에서는 Bold, 홈 default weather-card에서는 SemiBold였음 — 동일 시맨틱(날씨 온도 텍스트 스타일)인지 재확인 필요, 폰트 웨이트 불일치 가능성
- detail-card 라벨 컬러 `#878787` + 값 컬러 `#4f4f4f` 조합은 홈 default weather-card의 "최저/최고" 라벨과 동일 톤

## 디자인 시스템 대조 후보

- `AppTopAppBar` — back 아이콘 + 중앙 타이틀 + trailing spacer variant (다른 화면의 상세 진입 top-app-bar와 대조 필요, 예: Record 상세)
- `detail-card` 2×2 그리드는 신규 컴포넌트 후보 — 다른 화면에 동일 "라벨+값 카드" 패턴 있는지 먼저 확인(예: MyPage 프로필 카드)
- weekly-forecast 카드(연회색 `#fafafa` 컨테이너 + 요일별 아이콘/기온 컬럼)도 신규 후보

## 미해결 질문 / 백엔드 협의 필요

1. **날씨 API 필드**: 체감온도/자외선지수/강수확률/습도/풍속/주간예보(5일) 전부 백엔드 응답에 있는지 확인 필요 — Record 탭에서 쓰는 `GET /farms/{id}/weather`가 이 정도 필드를 주는지 Swagger 대조 필요
2. **농장 주소 표시**: "서울 중구 퇴계로 128" — Farm 엔티티의 주소 필드 사용 여부, 다중 농장일 때 어느 농장 주소를 보여줄지(BR 확인 필요)
3. **주간 예보 아이콘 매핑**: `snowflake`/`clear_day`/`rainy`/`cloudy`/`cloud` 등 날씨 상태값 ↔ 아이콘 매핑 테이블이 백엔드 enum과 일치하는지 확인 필요
4. **관수 팁 문구 로직**: 홈 default의 "관수 간격" 팁과 이 화면의 "관수하기 좋은 날씨" 팁이 같은 로직/문구 소스인지, 별도 생성 로직인지 확인 필요
5. **empty/loading/error 상태**: 날씨 조회 실패 시 UI 미캡처

## 다음 캡처 후보

- 날씨 상세 로딩/오류 상태
- SE 2/3 대응 여부 (특히 2×2 detail-card 그리드, 주간예보 5일 가로 배치)
