# 코칭 패키지 통합 설계

## 목적

`feat/report-feedback`의 완료 리포트 코칭을 `feat/coaching-rag`에 통합하면서,
기록 코칭과 리포트 코칭의 패키지 경계를 전 계층에서 동일하게 맞춘다.

현재 application은 `recordfeedback`과 `reportfeedback`이 분리되어 있지만,
domain은 두 모델이 `domain.coaching`에 평면으로 놓여 있다. API도 기록 피드백은
`api.coaching`, 리포트 피드백은 `api.report`에 있어 코드 위치만 보고 책임을
파악하기 어렵다. 또한 최상위 `rag` 패키지가 일반 채팅, 공용 설정, 인덱싱을 모두
포함해 기능 경계와 기술 경계가 섞여 있다.

## 선택한 접근

최상위 `rag` 패키지를 유지하지 않고, 실제로 여러 코칭 기능이 공유하는 타입만
`coaching.common`으로 이동한다. 일반 채팅과 인덱싱은 각각 독립 패키지로 분리하고,
기록·리포트 피드백은 전 계층에서 같은 대상 이름을 사용한다.

검토 후 제외한 대안은 다음과 같다.

- 기존 `coaching.rag` 유지: 이동량은 적지만 `rag/chat`, `rag/common`,
  `rag/indexing` 중첩이 계속 남아 기능 탐색이 어렵다.
- 모든 AI 코칭을 공통 `Feedback` 모델로 통합: 기록과 리포트의 입력·출력·생성
  시점이 달라 nullable 필드와 타입 분기가 다시 생긴다.
- 모든 RAG 관련 클래스를 `coaching.common`에 이동: `common`이 일반 채팅과
  인덱싱까지 흡수해 새 잡동사니 패키지가 된다.

## 목표 패키지 구조

### Domain

```text
domain/coaching/
  chat/
    CoachingMode
  recordfeedback/
    RecordFeedback
    RecordFeedbackActionType
    RecordFeedbackNextAction
    RecordFeedbackRepository
    RecordFeedbackStatus
  reportfeedback/
    ReportFeedback
    ReportFeedbackItem
    ReportFeedbackItemSection
    ReportFeedbackRepository
    ReportFeedbackStatus
```

`RecordFeedback`과 `ReportFeedback` 사이에는 공통 부모, `FeedbackType`, 공통
repository를 추가하지 않는다.

`CoachingMode`는 일반 채팅 기능에 속한다. 현재 실제 지원 여부를 확인해 사용되지
않는 `RECORD_AUTO`, `REPORT_MANUAL` 값은 제거한다. 지원하지 않는 미래 모드를
패키지 통합을 이유로 유지하지 않는다.

### Application

```text
application/coaching/
  common/
    RagAudit
    RagModelInfo
    RagProperties
    RagSourceType
  chat/
    CoachingContextProvider
    CoachingRagCommand
    CoachingRagResult
    CoachingRagService
    CoachingRetrievalFilterBuilder
    CoachingStructuredOutputValidator
    CoachingStructuredResult
  indexing/
    FarmingRecordDocumentFactory
  recordfeedback/
    generation/
    lifecycle/
  reportfeedback/
    generation/
    lifecycle/
```

`common`에는 둘 이상의 코칭 기능에서 참조하는 설정·모델 메타데이터·감사·출처
타입만 둔다. `CoachingStructuredResult`는 일반 채팅 출력이므로 `chat`에 둔다.

기록과 리포트의 context, query planner, prompt builder, output validator,
generation service는 각각의 패키지에 유지한다. 구현 모양이 비슷하다는 이유만으로
공통 generation abstraction을 추가하지 않는다.

### API

```text
api/coaching/
  chat/
    controller/
    dto/
  recordfeedback/
    controller/
    dto/
  reportfeedback/
    controller/
    dto/
```

코드 패키지는 코칭 기능 기준으로 통일하지만 외부 URL은 대상 리소스 기준을
유지한다.

```text
GET  /api/v1/farming-records/{recordId}/feedback
POST /api/v1/farming-records/{recordId}/feedback/regenerate
GET  /api/v1/farming-reports/{reportId}/feedback
POST /api/v1/coaching/rag/query
```

지원되는 `CHAT` 요청의 응답 JSON과 HTTP 상태는 변경하지 않는다. 실제 구현이
거부만 하던 `RECORD_AUTO`, `REPORT_MANUAL`은 enum과 거부 테스트에서 제거한다.
따라서 이 두 미지원 값을 보낸 요청의 세부 오류 코드는 달라질 수 있으며, 이는
지원 계약이 아니라 선행 설계 잔재 삭제로 기록한다.

## 병합 전략

1. `feat/coaching-rag` 최신 커밋을 새 통합 워크트리의 기준으로 사용한다.
2. `feat/report-feedback`을 병합해 의미 단위의 충돌을 먼저 해결한다.
3. 병합된 코드에서 패키지를 이동하고 package/import/test package 선언을 함께
   수정한다.
4. 이전 패키지 참조가 남지 않았는지 검색하고 전체 backend 테스트를 실행한다.

기존 `feat/coaching-rag` 작업 디렉터리에는 다른 작업자의 변경이 있을 수 있으므로
그 디렉터리에서 직접 병합하지 않는다. 최신 브랜치 커밋을 기준으로 별도 통합
워크트리를 만들고, 커밋되지 않은 다른 작업자의 파일은 가져오거나 수정하지 않는다.

병합 충돌은 대상 브랜치의 최신 기록 피드백 동작을 우선 보존하면서 리포트 기능을
추가하는 방식으로 해결한다. 충돌 파일을 한쪽 버전으로 통째로 선택하지 않고,
테스트가 표현하는 현재 계약을 기준으로 병합한다.

## 의존 방향

```text
api.coaching.recordfeedback -> application.coaching.recordfeedback -> domain.coaching.recordfeedback
api.coaching.reportfeedback -> application.coaching.reportfeedback -> domain.coaching.reportfeedback
api.coaching.chat -> application.coaching.chat -> domain.coaching.chat

recordfeedback ─┐
reportfeedback ─┼-> application.coaching.common
chat ───────────┘
```

`common`은 record/report/chat 패키지를 역으로 참조하지 않는다. recordfeedback과
reportfeedback도 서로 참조하지 않는다.

## 테스트와 검증

- 이동 전 `feat/coaching-rag`의 전체 backend 테스트 기준선을 확인한다.
- 병합 직후 패키지 이동 전 테스트를 실행해 순수 병합 충돌과 이동 오류를 분리한다.
- 패키지 이동 후 domain, application, api 테스트를 실행한다.
- 기록 피드백과 리포트 피드백 controller 테스트로 URL·응답 계약을 고정한다.
- 다음 이전 경로가 production/test 코드에 남지 않았는지 검색한다.

```text
application.coaching.rag
domain.coaching.RecordFeedback
domain.coaching.ReportFeedback
api.coaching.controller.RecordFeedbackController
api.report.controller.ReportFeedbackController
```

- `git diff --check`와 전체 staged diff를 검토한다.

## 범위 제외

- RecordFeedback 또는 ReportFeedback의 생성 규칙 변경
- 공통 Feedback 엔티티·repository·generation service 추가
- 지원되는 API URL 또는 CHAT 응답 JSON 변경
- 새 의존성 추가
- 프론트엔드 화면 변경
- 완료 리포트 수정에 따른 stale·재생성 기능 추가
