# Test iOS 영농 주기 리포트 조회 화면 설계

## 목적

내부 iOS QA 하네스에서 로그인한 member가 선택한 재배지와 작물의 영농 주기
리포트를 바로 확인한다. 이 화면은 제품용 리포트 UI가 아니라 백엔드의 공개
조회 계약을 빠르게 검증하는 도구다.

## 검토한 방식

1. 원본 JSON만 입력·표시하는 범용 요청 화면은 가장 빠르지만, farm/crop 선택과
   현재·완료·상세 리포트의 관계를 매번 수동으로 맞춰야 한다.
2. 현재 리포트만 보여 주는 최소 화면은 구현량은 작지만, 완료 목록과 상세
   스냅샷 계약을 검증할 수 없다.
3. **선택한 방식:** 실제 세 조회 API를 타입으로 표현하고, 재배지/작물 선택,
   현재 조회, 완료 목록, 선택 상세를 한 QA 화면에 둔다. 공개 계약을 모두
   검증하면서도 생성·수정·코칭 같은 아직 없는 기능을 만들지 않아 YAGNI에 맞다.

## 범위

- `GET /api/v1/farming-reports/current?farmId&cropId`로 현재·직전 스냅샷 조회
- `GET /api/v1/farming-reports?farmId&cropId&cursor&size`로 완료 리포트 목록 조회
- `GET /api/v1/farming-reports/{reportId}`로 선택 리포트와 직전 스냅샷 조회
- 기존 `Load Farms`와 farm 안의 crop 선택기를 재사용
- 스냅샷의 메타데이터, 최종 수확 기록 ID, 통계 스키마 버전, source revision,
  작업별 recordCount 및 수확량/coverage를 표시
- 모든 호출의 최신 경로, 원본 응답, 오류를 기존 하네스 방식으로 표시

## 제외 범위

- 리포트 생성, 재생성, 삭제, 갱신 요청
- 리포트 AI 피드백·코칭 (공개 API가 아직 없다)
- 모든 통계 분포를 위한 별도 탭·그래프·범용 JSON 편집기
- 토큰 저장 또는 외부 의존성 추가

## 구조와 흐름

`BackendAPIClient`가 세 HTTP 계약과 query path 생성을 담당한다.
`HarnessState`가 세 응답, 페이지 cursor, 인증 토큰 확인, 요청 수명과 raw/error
상태를 담당한다. `FarmingReportTestView`는 선택 값과 버튼 상태만 가지고
`HarnessState`의 명시적 비동기 동작을 호출한다.

```
Load Farms -> farm/crop 선택
                    |-> Load Current -> current + previous snapshot
                    |-> Load Completed -> metadata page -> report row 선택
                                                          -> detail + previous snapshot
```

현재 리포트의 current/previous와 상세 리포트의 selected/previous는 동일한
`FarmingCycleReportSnapshotDTO`로 표현한다. 완료 목록은 통계 없이 메타데이터만
받으므로 `FarmingCycleReportMetadataDTO`를 별도로 둔다.

## 오류와 검증

- 세 조회 모두 bearer token이 없으면 기존 하네스의 `Missing access token` 오류로
  종료한다.
- farm/crop을 고르기 전에는 조회 버튼을 비활성화한다.
- 현재 조회에서 두 스냅샷이 모두 `null`인 것은 오류가 아닌 "No active or
  previous report" 상태로 보인다.
- XCTest는 JSON 디코딩과 세 request path/query/bearer 계약을 먼저 실패시킨 뒤
  구현한다.
- 전체 하네스 XCTest와 `xcodebuild -list`를 실행한다.

## 제약

- `test-ios/`는 저장소에서 무시되므로 Swift 소스는 로컬 QA 도구로만 유지하고
  강제 add 하지 않는다.
- 기존 사용자 변경인 `backend/api/src/main/resources/application-local.yml`은
  읽거나 수정하거나 commit하지 않는다.
