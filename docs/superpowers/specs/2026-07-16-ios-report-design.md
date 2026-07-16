# iOS 리포트 화면 설계

## 문서 상태

- 작성일: 2026-07-16
- 대상: `frontend/ChamChamCham`
- 기준 브랜치: `dev`
- 구현 브랜치: `feat/farming-report(front)`
- 사용자 승인 범위: 리포트 목록, 필터, 작업별 상세, 그래프, 리포트 코칭

## 배경

현재 iOS 앱의 `영농 기록 > 리포트` 탭은 placeholder다. 배포 서버에는 재배 주기 기반
리포트 API가 이미 존재하므로 Figma의 리포트 목록·상세·그래프·코칭 화면을 실데이터와
연결한다. 기존 기록 탭과 기록 상세 코칭은 이번 작업에서 변경하지 않는다.

초기 조사에서 저장소의 2026-07-15 Swagger 스냅샷만 확인해 API 미배포로 판단했으나,
2026-07-16 라이브 `/v3/api-docs` 확인 결과 관련 API가 배포되어 있음을 확인했다. 구현
시작 전에 라이브 Swagger를 저장소 스냅샷으로 동기화한다.

## 근거

### Figma

파일: `awacdwTczdvkObfsG0eK3X`, `Page 1` (`0:1`)

- 리포트 기본 목록: `1:930`
- 리포트 필터 적용: `1:943`
- 그래프 양식: `1:956`
- 누적 막대 펼침: `1:1009`
- 반원 도넛 펼침: `1:1038`
- 심기 상세: `1:1097`
- 물주기 상세: `1:1169`
- 비료주기 상세: `1:1237`
- 병해충 관리 상세: `1:1325`
- 잡초 관리 상세: `1:1416`
- 가지·순 정리 상세: `1:1474`
- 수확 상세: `1:1517`
- 기타 상세: `1:1588`
- 기록 내역 전체 화면: `1:1631`
- 리포트 코칭 카드: `1:1862`

### 라이브 API

정본: `https://chamchamcham.jaehyuns.com/v3/api-docs`

- `GET /api/v1/farming-reports/work-items`
- `GET /api/v1/farming-reports/{reportId}/work-types/{workType}`
- `GET /api/v1/farming-reports/{reportId}/feedback`
- `POST /api/v1/farming-reports/{reportId}/feedback/{workType}/regenerate`

OpenAPI 생성 결과에는 서로 다른 중첩 DTO가 같은 단순 이름
(`StatusResponse`, `DetailResponse`)으로 합쳐지는 이름 충돌이 있다. 특히 리포트 코칭
응답 스키마는 기록 코칭의 동명 DTO와 합쳐지고, 재배 주기 상세와 작업별 상세도 같은
`ApiResponseDetailResponse`를 가리킨다. 클라이언트 DTO는 충돌한 OpenAPI 스키마 이름을
그대로 복사하지 않고 실제 엔드포인트 응답을 만드는 백엔드 DTO를 기준으로 리포트 전용
이름을 사용한다. 디코딩 fixture 테스트로 각 엔드포인트의 JSON 형태를 고정한다.

## 목표

1. 리포트 탭에서 서버의 작업별 리포트를 필터링하고 페이지 단위로 조회한다.
2. 여덟 작업 유형의 통계만 사용해 Figma 그래프와 요약 지표를 구성한다.
3. 완료된 재배 주기의 작업별 코칭을 표시하고 실패·오래됨 상태에서 재생성한다.
4. 네트워크가 없을 때 마지막으로 검증된 읽기 캐시를 표시하되 코칭·통계를 로컬에서
   만들어 내지 않는다.
5. iPhone 13 Figma 기준을 따르면서 iPhone SE 2/3에서도 내용과 동작이 잘리지 않게 한다.

## 비목표

- 기존 기록 목록·상세·작성 흐름 변경
- 기록 상세 코칭 API·DTO·화면 구현
- 백엔드 API나 데이터베이스 변경
- 리포트 전체를 합친 새로운 AI 코칭 생성
- 서버가 제공하지 않는 점수, 전년 대비 수치, 밭별 분포 계산
- 외부 차트 라이브러리 도입
- 리포트 주기에 포함된 기록을 추정해 보여 주는 동작
- 기존 디자인 시스템 의미 토큰 값 또는 컴포넌트 동작 변경

## 구조

### 기능 경계

리포트는 `Features/Report/{Data,Domain,Presentation}`에 별도 기능으로 둔다.

- `ReportRepository`
  - 작업별 리포트 목록·상세·코칭 조회와 실패/오래됨 코칭 재생성
  - 목록·상세 읽기 캐시와 캐시 fallback
- `ReportListViewModel`
  - 필터, cursor 페이지네이션, 새로고침, 캐시 여부 관리
- `ReportDetailViewModel`
  - 작업별 상세, 코칭 상태, bounded polling, 재생성 관리

`RecordListView`는 상단 앱 바와 `기록/리포트` 탭을 계속 소유한다. 기존 기록 상세 경로인
`UUID` navigation destination은 그대로 두고 리포트 경로만 추가한다.

```swift
enum ReportRoute: Hashable {
    case detail(reportId: UUID, workType: FarmingWorkType)
    case recordHistory(reportId: UUID, workType: FarmingWorkType)
}
```

리포트 구현이 홈·기록 작성·기록 상세 흐름에 영향을 주지 않도록 기존
`RecordRepository`에는 리포트 메서드를 추가하지 않는다.

### API와 도메인 모델 분리

DTO는 서버 키와 optional 형태를 그대로 반영하고, 화면은 별도의 도메인·표시 모델을
사용한다.

- 리포트 목록
  - report/farm/crop/work type, 주기 시작·종료, 기록 수, 최근 작업일, thumbnail
- 리포트 상세
  - 공통 통계 + 현재 작업 유형에 해당하는 optional 전용 통계
  - ACTIVE/COMPLETED 상태
  - 작업별 코칭 상태와 내용
- 분포
  - 서버가 준 `label`, `count`, `ratePct` 또는 단위별 합계를 그대로 사용
  - 배열 순서에 의존하지 않고 값 기준으로 안정 정렬

## 화면 설계

### 리포트 목록

Figma `1:930`, `1:943`을 따른다.

- 기존 `영농 기록` 앱 바와 기록/리포트 탭 재사용
- 필터: 작물, 영농 활동, 농장
- 카드: thumbnail, 작물 badge, 농장 badge, 작업 유형, 재배 주기 기간
- 필터가 적용되면 chip을 primary border/text로 표시하고 선택 이름 표시
- 상태: 최초 loading, cached loading, empty, error/retry, offline/no cache, refresh,
  loading more
- 카드는 `GET /farming-reports/work-items` 결과만 사용하고, 목록에서 코칭 상태를 얻기
  위한 N+1 요청은 하지 않는다.

필터 API는 단일 `farmId`, `cropId`, `workType`을 받으므로 UI도 각 축을 단일 선택으로
구성한다. Figma의 `작물 4` 같은 다중 선택 문구는 실제 API 계약에 맞춰 선택한 이름
하나로 표시한다.

### 리포트 상세

Figma 작업 유형별 상세 프레임을 공통 shell과 작업별 섹션으로 구성한다.

- 상단: 뒤로가기, 더보기, 작물 badge, 농장 badge, 재배 주기
- 제목: 서버 `workTypeLabel`
- 요약 지표: `statistics.common.recordCount`와 작업별 제공 값
- 상세 정보: 전용 분포가 있을 때만 표시
- 코칭: ACTIVE/COMPLETED 및 코칭 상태에 따라 표시
- 기록 내역: 별도 API 제약을 반영한 준비 상태 표시

작업별 요약·그래프 매핑:

| 작업 | 요약 | 상세 그래프 |
| --- | --- | --- |
| 심기 | 총 작업 횟수, 단위별 총 심은 양 | 심기 방법, 모종 번식법 |
| 물주기 | 총 작업 횟수, 가장 자주 기록한 물 양 | 물주기 방식, 물 준 양 |
| 비료주기 | 총 작업 횟수, 총 비료 사용량 | 비료주기 방식, 비료별 횟수, 비료별 사용량 |
| 병해충 관리 | 총 작업 횟수, 단위별 농약 사용량, 총 살포량 | 약제 종류, 약제별 사용량, 대상 병해충 |
| 잡초 관리 | 총 작업 횟수 | 잡초 관리 방식 |
| 가지·순 정리 | 총 작업 횟수 | 전용 통계 없음 |
| 수확 | 총 작업 횟수, 총 수확량 | 수확 부위, 재배 개월 범위/최종 재배 개월 |
| 기타 | 총 작업 횟수 | 전용 통계 없음 |

Figma의 물주기 `평균 물 준 양`은 API가 순서형 범주의 평균을 제공하지 않으므로
`가장 자주 기록한 물 양`으로 표시한다. 심기나 병해충 관리처럼 단위가 여러 개일 수
있는 합계는 서로 더하지 않고 `1080g, 100주`처럼 단위별로 나눠 표시한다.

### 그래프

외부 라이브러리 없이 iOS 17의 Swift Charts와 SwiftUI Shape/Canvas를 사용한다.

- 항목 1~3개: 누적 가로 막대
- 항목 4~6개: 반원 도넛
- 가장 큰 항목: `#38C284`에 해당하는 primary chart color
- 누적 막대 보조색: Figma lime
- 도넛 보조색 순서: green-300, yellow, lime, turquoise, blue
- 접힌 상태: 그래프 안에 가장 큰 항목의 label과 값 표시
- 펼친 상태: 중앙 label을 숨기고 아래에 color swatch, label, 값을 전부 표시
- 헤더 chevron으로 접기/펼치기, VoiceOver label과 expanded value 제공
- `count == 0`인 항목은 제거하고 전체 합이 0이면 카드 자체를 숨김
- 서버 rate 합계의 반올림 오차와 무관하게 실제 `count` 비율로 도형 길이/각도 계산
- 같은 값은 서버의 안정적인 label/code 순서로 tie-break
- SE 폭에서는 범례 label이 줄바꿈되고 값 열은 trailing에 고정

현재 Foundation에 chart 전용 공개 토큰이 없다. 기존 의미 토큰을 바꾸지 않고
`Color.Chart` namespace에 Figma의 여섯 색을 추가한다. 이 문서 승인으로 해당
Foundation 추가를 명시적으로 승인하는 것으로 간주하며, 기존 token 값이나 public
component API는 수정하지 않는다.

### 리포트 코칭

Figma는 가로 스크롤 카드 네 종류를 보여 준다.

- 잘한 점: `strengths`
- 이전 리포트와의 비교: `comparisons`가 있을 때만 표시
- 개선 필요점: `improvements`
- 추천 행동: `nextActions`
- `summary`는 카드 앞의 짧은 소개 문장으로 표시

각 배열에 여러 문장이 오면 각 문장을 독립 카드로 반복하지 않고 같은 역할 카드 안에서
문단으로 쌓아 카드 수가 무한히 늘지 않게 한다.

상태 처리:

- ACTIVE: 통계만 표시, `재배 주기가 끝나면 코칭을 제공해요`
- COMPLETED + PENDING: 코칭 skeleton과 bounded polling
- READY: 서버 내용 표시
- FAILED: 실패 안내와 재생성 버튼
- STALE: 기존 내용을 숨기고 `통계가 변경됐어요. 코칭을 다시 생성해주세요.`와
  재생성 버튼
- offline: 캐시 표시 또는 명시적 offline 상태, 재생성 비활성화

### 기록 내역

기록 내역은 기록 기능 확장이 아니라 리포트 상세 디자인의 하위 진입점으로만 유지한다.
현재 `GET /farming-records`는 crop/work type/date로는 검색할 수 있지만 farmId와
reportId 또는 정확한 재배 주기 경계를 받지 않는다. 응답에도 farmId가 없어 같은 작물을
여러 농장에서 재배하면 리포트 소속 기록을 정확히 판별할 수 없다.

따라서 이번 iOS 구현은 다음까지만 포함한다.

- 상세 하단에 Figma와 같은 `기록 내역 리스트` 헤더와 chevron 배치
- 눌렀을 때 독립 화면 shell 제공
- production에서는 `이 리포트에 포함된 기록을 불러오는 기능을 준비하고 있어요`
  상태 표시
- preview와 순수 UI 테스트에서는 주입한 기록 목록으로 Figma row 레이아웃 검증

부정확한 기록을 네트워크에서 추정해 표시하지 않는다. backend가 reportId 기반 record
slice 또는 farmId + cycle boundary 검색을 제공하면 repository만 연결할 수 있게 화면
입력 경계를 유지한다.

## 오프라인과 캐시

리포트와 코칭은 서버가 계산한 읽기 데이터이므로 클라이언트에서 새 값을 만들지 않는다.
SwiftData에 마지막 성공 응답과 갱신 시각을 저장한다.

- 네트워크 성공: DTO 검증 후 cache 갱신, 최신 표시
- 네트워크 실패 + cache 있음: cache 표시, offline/stale 표시
- 네트워크 실패 + cache 없음: offline empty state
- pull-to-refresh: 항상 네트워크 시도, 실패 시 기존 cache 유지
- 코칭 재생성: offline에서 비활성화하고 요청을 임의 queue하지 않음

cache 모델은 API DTO를 직접 노출하지 않고 식별자, source revision/status, payload,
updatedAt만 저장한다. 서버 데이터 삭제/권한 오류에서는 stale cache를 성공처럼 유지하지
않는다.

## 오류와 동시성

- 각 ViewModel은 `@Observable @MainActor`를 사용한다.
- 필터 변경, 화면 이탈, 다른 리포트 선택 시 기존 request/polling task를 취소한다.
- cursor 중복 응답은 id로 deduplicate한다.
- 재생성 버튼은 요청 중 disabled 처리한다.
- 한 작업 유형의 코칭 실패가 목록이나 리포트 통계 표시를 막지 않는다.
- API business error는 기존 `APIError`의 사용자 메시지 규칙을 재사용한다.
- enum 확장에 대비해 DTO decode는 알려지지 않은 상태를 명시적 unsupported state로
  전환하고 화면 전체를 crash시키지 않는다.

## 접근성과 작은 화면

- 모든 chart는 동일 내용을 읽을 수 있는 접근성 summary와 legend를 제공한다.
- 색만으로 항목을 구분하지 않고 label과 값을 함께 표시한다.
- Dynamic Type에서 카드 높이를 고정하지 않는다.
- 코칭 카드의 긴 문장은 줄바꿈한다.
- iPhone SE에서는 상단 badge/date가 여러 줄 또는 세로 배치로 전환된다.
- 상세 전체와 기록 내역은 세로 scroll로 primary content가 가려지지 않게 한다.

## 테스트

### 단위·계약 테스트

- 리포트 목록 query와 cursor encode
- 여덟 작업 유형 상세 fixture decode
- 리포트 코칭 PENDING/READY/FAILED/STALE fixture decode
- OpenAPI 이름 충돌과 무관한 endpoint-specific envelope decode
- filter 변경 시 첫 페이지 reset 및 기존 task 취소
- pagination append, deduplicate, retry
- cache 성공/fallback/권한 오류 제거
- report polling 종료와 화면 이탈 cancel
- FAILED/STALE 재생성 허용 규칙
- 작업별 요약 표시 모델과 단위별 합계
- chart 1~3개/4~6개 선택, 정렬, 0 제거, tie-break, 펼침 legend
- 기존 record UUID destination과 report route가 함께 동작하는지 검증

### 빌드·런타임 검증

- `xcodebuild ... test`
- `xcodebuild ... build`
- iPhone 13 크기에서 Figma와 목록·상세·코칭·그래프 비교
- iPhone SE 2/3 크기에서 줄바꿈, scroll, chart legend, 재시도 버튼 확인
- VoiceOver로 chart summary와 expand/collapse 확인
- 네트워크 online/offline 전환, cache fallback, polling 취소 확인
- 기존 기록 목록·상세·작성 navigation 회귀 확인

## 구현 순서

1. 라이브 Swagger 스냅샷 동기화와 API 이름 충돌 기록
2. 리포트 domain/DTO/repository/cache 테스트와 구현
3. 리포트 목록·필터·route
4. 작업별 표시 모델과 summary 테스트
5. chart 표시 모델·SwiftUI chart 카드
6. 리포트 상세·코칭·기록 내역 unavailable 상태
7. 전체 테스트, 빌드, Simulator, Figma 시각 검증

## 남은 위험

- 라이브 OpenAPI의 DTO 단순 이름 충돌은 생성 문서상 결함이므로 backend가 schema name을
  분리하기 전까지 source DTO와 fixture 테스트를 함께 유지해야 한다.
- reportId 기반 기록 목록 API가 없어 기록 내역 전체 화면은 production 데이터와 연결할
  수 없다.
- Figma의 예시 코칭 문구는 여러 작업 유형에서 같은 더미를 사용한다. production에서는
  서버 문구만 표시한다.
- 통계 분포가 6개를 초과하면 Figma palette가 부족하다. 이번 서버 도메인의 고정 enum은
  주요 그래프에서 최대 6개지만, 동적 약제/비료 항목은 6개를 넘을 수 있으므로 상위
  5개와 `기타`로 합치는 표시 정책을 사용한다.
