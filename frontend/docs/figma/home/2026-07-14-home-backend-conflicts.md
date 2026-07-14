# 홈(Home) Figma/스펙 ↔ 배포 Swagger 충돌 트래킹

Record 탭의 `2026-07-13-record-backend-conflicts.md`와 동일한 형식. 새 충돌 발견 시 이 문서에 추가.

## C-1. 날씨 상세 API 필드 전무

`GET /farms/{farmId}/weather`(`CurrentWeatherResponse`)는 `observedAt`, `temperature`,
`weatherCondition` 3개 필드만 제공. Figma 날씨 상세 화면이 요구하는 체감온도, 최저/최고
기온, 자외선 지수, 강수확률, 습도, 풍속, 5일 주간 예보, 주소 필드가 전부 없음.
홈 default 화면의 weather-card에 있는 최저/최고 기온도 동일하게 미제공.

**영향**: 날씨 상세 화면 구현 불가(전부 더미 데이터가 됨), 홈 카드 최저/최고 표시 불가.
**제안**: 날씨 상세는 이번 라운드 보류. 홈 카드는 온도+아이콘만 표시.

## C-2. weatherCondition enum 미확정

`weatherCondition`이 raw string으로만 내려오고 백엔드 enum 정의가 문서화되어 있지 않음
(Record 탭에서도 동일 이슈 이미 플래그됨, `record/2026-07-13-record-backend-conflicts.md` 참고).
Figma에서 확인된 아이콘 이름: `clear_day`, `snowflake`, `rainy`, `cloudy`, `cloud`.

**영향**: weatherCondition → 아이콘 매핑 테이블을 프론트에서 추측으로 만들어야 함.
**제안**: 백엔드에 실제 enum 값 목록 요청, 확인 전까지는 매핑 실패 시 기본 아이콘 fallback.

## C-3. 정책 추천 마감일 비구조화

`GET /policies/recommendations`의 `PolicyRecommendationItemResponse`에 구조화된 마감일
필드가 없고 `applicationPeriodLabel`(표시용 문자열)만 존재. Figma의 "D-12" 같은 D-day
배지를 정확히 계산할 방법이 없음.

**영향**: D-day 배지 구현 시 문자열 파싱에 의존해야 하며 형식이 바뀌면 깨짐.
**제안**: 이번 라운드는 D-day 배지 대신 `applicationPeriodLabel` 원문 표시. 백엔드에
구조화된 마감일 필드(`deadlineDate` 등) 추가 요청.

## C-4. 정책 카테고리 enum 미검증

`benefitCategory` 쿼리 파라미터가 스키마상 자유 문자열이고, Figma에서 확인된 10종
카테고리(지원금/융자·금융/시설·장비/교육/복지/인증/판로/창업/환경·인프라/기타)가
백엔드가 실제로 인식하는 값과 일치하는지 문서/스키마로 확인 불가.

**영향**: 프론트 enum과 백엔드 허용값이 다르면 필터링이 조용히 실패(빈 결과)할 수 있음.
**제안**: 백엔드에 실제 허용값 목록 확인 요청. 확인 전까지는 필터 결과 0건도 정상
UI(빈 상태)로 처리해 에러처럼 보이지 않게 함.

## C-5. 알림 unread 배지 API 없음

알림 관련 엔드포인트가 배포 Swagger에 전혀 없음(`docs/API 명세서/`의 알림 설정 DTO는
아카이브된 Notion 문서라 사용 금지 대상).

**영향**: 홈 top-app-bar 알림 아이콘에 unread 표시 불가.
**제안**: 아이콘은 inert placeholder로 유지, 탭 시 알림 목록 화면 자체도 별도 설계 필요.

## C-6. 최근 영농 기록 정렬 파라미터 미문서화

`GET /farming-records`에 명시적 `sort` 파라미터가 스키마에 없음. `workedAt desc`로
가정하고 `size`만 작게 줘서 "최근 N건" 프리뷰로 사용할 계획.

**영향**: 실제 정렬 기준이 다르면 "최근 기록"이 아닌 다른 순서로 보일 수 있음.
**제안**: 백엔드에 기본 정렬 기준 확인, 필요 시 명시적 정렬 파라미터 추가 요청.

## C-7. 관수 팁 배너 문구 생성 로직 미정의

"최근 관수 간격이 평균 N일" 같은 동적 문구를 만드는 로직(규칙 기반? 별도 API?)이
Business Rule/Swagger 어디에도 정의되어 있지 않음.

**영향**: 이번 라운드에 실데이터 기반 팁을 만들 방법이 없음.
**제안**: 정적 문구로 대체하거나 섹션 자체를 숨김, 로직 정의 후 후속 작업.
