# 리포트 피드백 HTML 콘솔 설계

## 목적

로컬 개발용 `frontend/dev-rag-test.html`에서 선택한 완료 리포트의 AI 피드백을 바로 확인한다. 리포트 통계와 피드백을 한 화면에 연결하되, 서버 기능이나 공개 API 계약은 변경하지 않는다.

## 범위

- 리포트 통계 화면에 리포트 피드백 카드 추가
- 선택한 `COMPLETED` 리포트의 피드백 자동 조회
- `PENDING` 상태 자동 폴링
- `READY`, `FAILED`, 오류 상태 표시
- 수동 새로고침과 폴링 중지
- 기록 피드백 호출 경로를 현재 API인 `/feedback`으로 교정

다음 항목은 포함하지 않는다.

- 리포트 피드백 재생성
- 백엔드 API 또는 DTO 변경
- iOS 화면 변경
- 개발 콘솔을 다시 Git 추적 대상으로 전환
- 별도 프론트엔드 프레임워크나 의존성 추가

## UI 구조

기존 `리포트 통계` 탭에서 통계 대시보드 아래에 `리포트 피드백` 카드를 둔다.

카드는 다음 요소로 구성한다.

- 상태 배지: `선택 없음`, `완료 전`, `PENDING`, `READY`, `FAILED`, `ERROR`
- 상태 정보: input prepared, failure code, poll attempts
- 제어 버튼: `피드백 새로고침`, `폴링 중지`
- 결과 영역:
  - 요약
  - 잘한 점
  - 개선점
  - 다음 주기 행동

항목 배열에는 API의 `text` 값만 표시한다. 생성 근거나 내부 감사 데이터는 공개 응답에 없으므로 UI에도 추가하지 않는다.

## 데이터 흐름

1. 사용자가 현재·직전·상세 리포트를 조회하거나 스냅샷 탭을 전환한다.
2. 선택된 리포트가 없으면 피드백 UI를 초기화한다.
3. 선택된 리포트가 `ACTIVE`이면 API를 호출하지 않고 완료 후 생성된다는 안내를 표시한다.
4. 선택된 리포트가 `COMPLETED`이면 `GET /api/v1/farming-reports/{reportId}/feedback`을 호출한다.
5. 응답이 `PENDING`이면 2초 뒤 다시 조회한다.
6. `READY` 또는 `FAILED`이면 폴링을 중지한다.
7. 60회에 도달하면 자동 폴링을 중지하고 수동 새로고침 안내를 표시한다.

리포트 조회 함수 자체에 피드백 호출을 중복 삽입하지 않는다. 기존 `renderReport()`가 선택 스냅샷을 확정한 뒤, 선택된 리포트 ID와 상태를 기준으로 피드백 동기화 함수 하나를 호출한다.

## 상태 격리와 경쟁 조건

리포트 피드백은 기록 피드백과 별도의 상태를 사용한다.

- `reportFeedbackTimer`
- `reportFeedbackRunId`
- `reportFeedbackReportId`
- `reportFeedbackStatus`
- `reportFeedbackPollAttempts`

새 리포트를 선택하거나 화면 상태가 초기화되면 기존 타이머를 취소하고 run ID를 증가시킨다. 네트워크 응답이 돌아왔을 때 run ID와 report ID가 현재 선택과 다르면 렌더링하지 않는다. 따라서 이전 리포트의 느린 응답이 새 리포트 피드백을 덮지 않는다.

기록 피드백의 `pollTimer`, `pollRunId`, `pollAttempts`는 그대로 유지한다.

## 오류 처리

- 인증 실패나 HTTP 오류는 카드 내부 오류 영역에 표시한다.
- 오류가 발생하면 해당 자동 폴링 실행을 중지한다.
- `FAILED` 응답은 HTTP 오류와 구분하여 failure code를 표시한다.
- 응답에 항목이 없으면 빈 목록 안내를 표시한다.
- 수동 새로고침은 현재 선택된 완료 리포트에 대해서만 동작한다.

## API 호환성

리포트 피드백:

```text
GET /api/v1/farming-reports/{reportId}/feedback
```

기록 피드백:

```text
GET  /api/v1/farming-records/{recordId}/feedback
POST /api/v1/farming-records/{recordId}/feedback/regenerate
```

HTML에 남아 있는 `/coaching-feedback` 호출은 위 경로로 교정한다.

## 파일 소유와 배포

`frontend/dev-rag-test.html`은 `.gitignore`에 등록된 로컬 개발 도구다. 이번 구현은 현재 로컬 파일을 수정하지만 추적 대상으로 되살리지 않는다. 공유 가능한 결정과 동작 계약만 이 설계 문서와 구현 계획에 커밋한다.

## 검증

- HTML 내 중복 ID 검사
- 인라인 JavaScript 문법 검사
- 기록·리포트 피드백 URL 정적 검색
- `PENDING`, `READY`, `FAILED`, `ACTIVE`, 선택 없음 렌더링 경로 점검
- 리포트 전환 시 이전 폴링 취소 코드 점검
- 가능하면 로컬 브라우저에서 fixture 또는 실제 API 응답 렌더링 확인

## YAGNI 판단

리포트 통계 화면과 별도 탭을 만들지 않는다. 선택 스냅샷과 피드백의 관계가 이미 일대일이므로 같은 화면의 카드가 가장 직접적이다. 재생성, 편집, 저장, 추가 필터, 공통 폴링 클래스도 현재 요구에 필요하지 않아 추가하지 않는다.
