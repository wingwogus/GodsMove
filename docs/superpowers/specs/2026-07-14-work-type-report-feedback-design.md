# 작업 타입별 완료 리포트 코칭 설계

## 배경과 문제

현재 `ReportFeedback`은 완료된 한 사이클의 모든 작업을 하나의 컨텍스트로 합친다.
리포트 전체 통계, 모든 작업 기록, 모든 작업에서 검색한 기술 문서를 한 번의 LLM
호출에 전달하고 전역 `summary`, `strengths`, `improvements`,
`nextCycleActions`를 생성한다.

이 구조는 본래 제품 목표와 다르다. 영농 리포트 화면은 이미 파종/정식, 관수,
시비, 병해충 방제, 제초, 전정, 수확, 기타를 작업별 통계로 보여 준다. AI 코칭도
같은 경계를 따라야 한다. 관수 통계와 기록은 관수 코칭에만, 시비 통계와 기록은
시비 코칭에만 사용해야 하며 잘한 점과 개선점, 다음 행동 역시 작업별로 제공해야
한다.

이는 프롬프트 문구만의 문제가 아니다. 현재 구현은 다음 계층 모두에서 사이클
전체 한 건을 전제로 한다.

- `report_feedback.report_id` 단일 유일 제약
- 작업 타입이 없는 사이클 전역 `ReportFeedback` 부모 아래에 모든 항목이 연결되는 구조
- 모든 작업을 담는 `ReportFeedbackContext`
- 모든 작업의 질의와 문서를 합치는 RAG 검색
- 전역 요약과 세 목록만 반환하는 구조화 출력
- 리포트당 단일 상태를 반환하는 조회 API와 HTML

따라서 생성 입력부터 저장, 상태 전이, 조회 응답, HTML 표현까지 작업 타입 경계로
함께 바꾼다.

## 확정된 제품 결정

- 전체 사이클 AI 요약은 제거한다.
- 실제 기록이 있는 작업 타입만 코칭을 생성한다.
- 각 작업 타입은 `summary`, `strengths`, `improvements`, `nextActions`를 가진다.
- 목록별 항목 수에는 상한과 하한을 두지 않는다. 근거가 부족한 목록은 비워 둔다.
- 작업 타입별로 `PENDING`, `READY`, `FAILED`를 독립 관리한다.
- 한 작업 실패가 다른 작업 결과의 생성·저장·노출을 막지 않는다.
- 작업별 RAG 검색과 LLM 호출을 분리한다.
- 직전 완료 리포트 비교는 같은 작업 타입끼리만 수행한다. 같은 작업 데이터가
  없으면 비교를 생략한다.
- 공식 기술 문서가 없으면 기술 권고를 억지로 생성하지 않는다. 현재 기록만으로
  가능한 기록 습관·관찰 중심 코칭은 허용한다.
- 모든 사용자 노출 문장은 친근한 존댓말로 작성한다.
- 완료 후 원본 기록 수정에 따른 stale 처리와 재생성은 이번 범위에 포함하지 않는다.
- 기록 코칭과 리포트 코칭 생성 모두 완료/저장 요청 스레드 밖에서 비동기로 수행한다.

## 선택한 도메인 모델

### ReportFeedback 한 행은 한 작업 타입을 나타낸다

별도의 전체 피드백 부모를 만들지 않는다. 기존 `report_feedback`을 작업별 생성
단위로 바꾼다.

```text
report_feedback
- id
- member_id
- report_id
- work_type
- status
- summary
- input_snapshot
- citations
- audit_status / audit_warnings
- failure_code
- model_name / embedding_model

UNIQUE(report_id, work_type)
```

`work_type`은 문자열을 새로 정의하지 않고 도메인의 `WorkType` enum을 사용한다.
`summary`와 상태, 입력 스냅샷, 인용, 모델 감사 정보는 모두 해당 작업 타입에만
속한다.

`report_feedback_item`은 현재처럼 부모와 표시 순서, 섹션, 근거, 본문만 가진다.
부모로부터 작업 타입을 알 수 있으므로 자식에 `work_type`을 중복 저장하지 않는다.
별도 작업 섹션 테이블도 만들지 않는다.

항목 섹션은 다음 이름으로 통일한다.

- `STRENGTH`
- `IMPROVEMENT`
- `NEXT_ACTION`

기존 `NEXT_CYCLE_ACTION`은 작업별 범위와 맞지 않으므로 `NEXT_ACTION`으로 바꾼다.
운영 데이터가 없는 현재 단계에서는 기존 전체 피드백을 호환하거나 마이그레이션하는
계층을 만들지 않는다. 로컬의 `ddl-auto: create`와 테스트의 `create-drop` 환경은
새 구조로 재생성한다. `ddl-auto: none`인 dev/prod 환경의 외부 스키마 재구축은 이
코드 변경의 배포 범위가 아니며, 데이터가 없는 환경에서 별도로 수행해야 한다.

## 생성 대상 결정과 멱등성

`FarmingCycleReportProjectionService`는 리포트를 만들 때 이미 정확한
`CycleSlice.records`를 가지고 있다. 이 기록에서 중복을 제거한 `WorkType` 집합을
구해 `ReportFeedbackLifecycleService`에 전달한다. 통계 객체를 다시 분석하거나
작업 타입을 판별하는 별도 추상화는 만들지 않는다.

완료 리포트의 최초 스냅샷을 처리할 때 기록이 있는 모든 작업 타입의 `PENDING` 행을
하나의 트랜잭션에서 저장하고 각 행의 준비 이벤트를 발행한다. 이 최초 저장은
all-or-nothing이므로 일부 작업 행만 만들어진 채 커밋되지 않는다. 이후 같은
리포트가 다시 투영됐을 때 기존 피드백 행이 하나라도 있으면 새 행이나 이벤트를
만들지 않는다. 이는 완료 후 수정과 재생성을 다루지 않는 현재 제품 결정을 그대로
유지한다.

DB의 `(report_id, work_type)` 유일 제약은 같은 작업 타입의 중복 행을 마지막으로
방어한다. 작업 순서는 별도 `display_order` 컬럼 없이 `WorkType.entries` 선언 순서를
사용한다.

```text
PLANTING -> WATERING -> FERTILIZING -> PEST_CONTROL
-> WEEDING -> PRUNING -> HARVEST -> ETC
```

## 정확한 작업별 컨텍스트

현재 구현의 `startsAt <= workedAt <= endsAt` 기간 필터는 인접한 두 사이클의 기록
시각이 같을 때 다른 사이클 기록을 포함할 수 있다. 작업별 코칭에서는
`FarmingCyclePartitioner`를 재사용해 전체 소스 기록을 다시 분할하고,
`finalHarvestRecordId`가 대상 리포트와 같은 slice를 정확히 선택한다. 그 뒤 대상
`workType` 기록만 남긴다.

작업별 `ReportFeedbackContext`에는 다음 데이터만 담는다.

```text
- context schema version
- report id, farm name, crop name, startsAt, endsAt
- target WorkType
- 현재 리포트의 해당 작업 통계만
- 정확한 사이클에 속한 해당 작업 기록만
- 직전 완료 리포트의 동일 작업 통계(기록이 있을 때만)
- 비차단 진단 warnings
```

작업마다 통계 타입이 다르므로 컨텍스트만을 위한 8개 타입 계층을 새로 만들지 않는다.
도메인의 typed statistics에서 선택한 한 작업 통계만 `Map<String, Any?>` 형태의
생성 시점 스냅샷으로 변환한다. 전체 `CycleReportStatistics`는 작업별 컨텍스트에
넣지 않는다.

직전 완료 리포트는 같은 회원·밭·작물에서 기존 규칙으로 선택하되, 대상 작업의
`recordCount`가 0이면 이전 비교 컨텍스트를 넣지 않는다. 이전 리포트 자체가 없거나
같은 작업 기록이 없다는 사실은 실패 조건이 아니다.

## 작업별 RAG와 구조화 출력

검색 질의는 작물명, 대상 작업 타입, 해당 작업 통계에서 확인된 신호만 사용한다.
예를 들어 관수 질의에는 관수 횟수·간격·방식만 사용하고 시비량과 수확량은 넣지
않는다. 검색된 문서도 해당 작업 생성 호출에만 전달한다.

각 LLM 호출은 작업 하나에 대한 다음 구조만 반환한다.

```json
{
  "summary": "...",
  "strengths": [],
  "improvements": [],
  "nextActions": []
}
```

모든 항목은 내부적으로 `basis`, `text`, `evidenceRefs`를 가진다. 허용 근거는 다음으로
제한한다.

- 현재 작업 타입에 속한 대상 사이클 기록
- 직전 완료 리포트의 동일 작업 통계가 컨텍스트에 있을 때 그 리포트
- 해당 작업 질의로 검색된 공식 기술 문서

다른 작업 타입의 기록 ID는 허용 목록에 없으므로 검증에서 거부한다. 프롬프트는
기술 문서가 없을 때 기술적 권고를 만들지 않도록 명시한다. 의미를 다시 판정하기
위한 두 번째 LLM 감사기나 키워드 기반의 불안정한 의미 validator는 만들지 않는다.

출력 검증은 다음의 확인 가능한 규칙만 담당한다.

- `summary` 필수 및 친근한 존댓말 어미
- 항목의 `basis`, `text`, `evidenceRefs` 필수
- 모든 사용자 노출 `text`의 친근한 존댓말 어미
- 현재 작업 범위를 벗어난 근거 ID 거부
- 같은 섹션·근거·본문의 중복 거부
- 목록별 항목 수 제한 없음, 빈 목록 허용

세 목록이 모두 비어도 근거 있는 `summary`가 있으면 READY가 될 수 있다. 이를 위해
현재 도메인의 `items.isNotEmpty()` 불변식과 validator의 `items_empty` 규칙을
제거한다. 빈 목록을 채우려고 근거 없는 항목을 생성하는 것보다 요약만 제공하는 편을
선택한다.

구조화 출력은 작업별 최대 두 번 요청한다. 첫 출력이 실패하면 두 번째 요청에는
원문 예외나 데이터가 아니라 안전한 검증 코드만 전달한다. 두 번 모두 실패하면 해당
작업만 `STRUCTURED_OUTPUT_INVALID`로 종료한다. 검색 결과는 재시도 사이에 다시
조회하지 않고 재사용한다.

## 비동기 생명주기

현재 `AFTER_COMMIT` 리스너는 별도 `@Async`가 없어 호출 스레드에서 실행된다.
작업별 생성은 최대 8개의 RAG/LLM 호출을 만들 수 있으므로 리포트 완료 요청을
붙잡아서는 안 된다.

```text
완료 리포트 저장 트랜잭션
  -> 작업별 PENDING 행 저장 + 준비 이벤트 발행
  -> COMMIT
  -> 기존 Spring @Async 실행기로 작업별 준비 시작
      -> 정확한 컨텍스트 조립
      -> REQUIRES_NEW로 input_snapshot 저장
      -> RAG + LLM (DB 트랜잭션 밖)
      -> REQUIRES_NEW로 READY 또는 FAILED 저장
```

이벤트에는 `feedbackId`, `memberId`, `reportId`, `workType`을 모두 담는다. 준비와
최종 반영은 ID로 행을 조회·잠그고 다음이 모두 일치할 때만 수행한다.

- 상태가 `PENDING`
- 이벤트의 회원·리포트·작업 타입과 엔티티가 일치
- 생성에 사용한 입력 스냅샷과 현재 스냅샷이 일치

기존 `findByReport_Id...` 단건 조회는 제거한다. 리포트당 여러 행이 존재하면 임의의
다른 작업을 선택할 수 있기 때문이다. 한 작업의 예외는 그 작업 행에서 잡아
`FAILED`로 저장하며 다른 비동기 작업으로 전파하지 않는다.

기록 코칭도 같은 지연 문제가 있으므로 `RecordFeedbackPreparationListener`의 첫
AFTER_COMMIT 진입을 기존 `@Async` 실행기로 넘긴다. 뒤의 준비·생성 단계를 다시
중첩 비동기로 만들지 않고 첫 비동기 작업 스레드에서 이어서 실행한다. 새 큐,
메시지 브로커, 전용 executor는 추가하지 않는다. 이는 설계 협의 중 사용자가 추가로
승인한 범위다.

## 조회 API 계약

경로는 유지한다.

```http
GET /api/v1/farming-reports/{reportId}/feedback
```

조회 서비스는 로그인 회원 소유의 완료 리포트이며 `SUPERSEDED`가 아닌지 확인한 뒤
모든 작업별 피드백을 읽는다. 최상위 합성 상태는 만들지 않는다.

```json
{
  "reportId": "3ce44d31-e29d-4ce4-a317-e7a9cdd00385",
  "feedbacks": [
    {
      "feedbackId": "e97b0d67-8249-4363-a5a2-68f5b49be623",
      "workType": "WATERING",
      "status": "READY",
      "inputPrepared": true,
      "failureCode": null,
      "feedback": {
        "summary": "이번 관수는 일정한 간격으로 꾸준히 진행했어요.",
        "strengths": [{ "text": "관수 간격을 꾸준히 기록해 흐름을 확인하기 좋았어요." }],
        "improvements": [],
        "nextActions": [{ "text": "다음 관수에는 물의 양도 함께 기록하세요." }]
      },
      "createdAt": "2026-07-14T10:00:00",
      "updatedAt": "2026-07-14T10:00:10"
    },
    {
      "feedbackId": "bb400f70-9267-46e2-bfe0-b27d7ce5288a",
      "workType": "FERTILIZING",
      "status": "FAILED",
      "inputPrepared": true,
      "failureCode": "STRUCTURED_OUTPUT_INVALID",
      "feedback": null,
      "createdAt": "2026-07-14T10:00:00",
      "updatedAt": "2026-07-14T10:00:12"
    }
  ]
}
```

READY 본문은 작업별 `summary`, `strengths`, `improvements`, `nextActions`만 노출한다.
`basis`와 `evidenceRefs`는 생성 감사와 검증을 위한 내부 데이터이므로 기존처럼 공개
응답에 추가하지 않는다. 응답 순서는 `WorkType.entries` 순서로 정렬한다.

클라이언트는 하나라도 `PENDING`이면 폴링을 계속하되 이미 `READY`인 작업은 즉시
표시할 수 있다. 모든 항목이 `READY` 또는 `FAILED`가 되면 폴링을 종료한다.
소유한 완료 리포트에 피드백 행이 하나도 없으면 컬렉션 계약에 따라
`200 { "reportId": "...", "feedbacks": [] }`를 반환한다. 리포트가 없거나
소유하지 않았거나 `SUPERSEDED`이면 기존처럼 `REPORT_NOT_FOUND`를 반환한다.

## HTML 동작

`frontend/dev-rag-test.html`의 기존 리포트 작업 탭을 그대로 사용한다. 코칭만을 위한
두 번째 탭 체계는 만들지 않는다.

- 관수 탭: 관수 통계와 관수 코칭
- 시비 탭: 시비 통계와 시비 코칭
- READY: 요약, 잘한 점, 개선점, 다음 행동 표시
- PENDING: 선택한 작업의 생성 중 상태 표시
- FAILED: 선택한 작업의 안전한 실패 코드 표시
- 기록 없음: 기존 기록 없음 안내, 코칭 결과 없음
- 전체 탭: 전체 AI 결과를 만들지 않고 작업 탭 선택 안내

HTML은 API의 `feedbacks`를 `workType`별로 보관한다. 탭 전환은 이미 받은 데이터를
다시 렌더링할 뿐 추가 API 요청을 만들지 않는다. 폴링은 리포트 단위 한 개만
유지하고, 준비된 작업과 실패한 작업을 섞어서 정확히 표시한다.

API enum과 HTML 통계 키는 다음처럼 명시적으로 변환한다. 단순 `lowercase()`는
`PEST_CONTROL`을 `pestControl`로 바꾸지 못하므로 사용하지 않는다.

```text
PLANTING     -> planting
WATERING     -> watering
FERTILIZING  -> fertilizing
PEST_CONTROL -> pestControl
WEEDING      -> weeding
PRUNING      -> pruning
HARVEST      -> harvest
ETC          -> etc
```

HTML 전용 `all` 탭에는 대응하는 피드백 항목이 없다.

## 오류 처리와 멱등성

- 컨텍스트 조립 실패: 해당 작업 `CONTEXT_ASSEMBLY_FAILED`
- 저장된 스냅샷 역직렬화 실패: 해당 작업 `INVALID_CONTEXT_SNAPSHOT`
- 컨텍스트 schema·대상 작업 불변식 위반: 해당 작업 `INVALID_CONTEXT`
- 검색 실패: 해당 작업 `RETRIEVAL_FAILED`
- 채팅 호출 실패: 해당 작업 `CHAT_UNAVAILABLE`
- 구조화 출력 재시도 소진: 해당 작업 `STRUCTURED_OUTPUT_INVALID`
- 예상하지 못한 런타임 오류: 해당 작업 `UNEXPECTED`
- 이전 동일 작업 또는 기술 문서 부재: 실패 아님, 관련 비교·권고 생략

처리 코드가 포착한 예상 밖 예외에는 해당 작업을 `UNEXPECTED`로 저장하는
best-effort 처리를 한다. 다만 executor가 작업을 받기 전 거부하거나 프로세스가
종료되거나 DB 자체가 실패하면 상태를 저장할 수 없어 `PENDING`이 남을 수 있다.

모든 오류 로그에는 안전한 실패 코드와 허용된 검증 진단만 남긴다. 프롬프트,
기록 메모, 문서 본문, 모델 원문 응답은 실패 로그에 출력하지 않는다.

## 테스트와 완료 기준

### Domain

- 서로 다른 작업 타입 피드백 생성과 작업 타입 보존
- READY 전이 시 작업별 요약과 순서 있는 항목 저장
- 빈 항목 목록을 허용하고 빈 요약·불완전 항목은 거부
- `(report_id, work_type)` 유일 제약의 스키마 확인

### Application

- 실제 slice에 기록이 있는 타입만 최초 PENDING 생성
- 같은 리포트 재호출 시 행·이벤트 중복 없음
- 동일 시각 인접 사이클을 최종 수확 ID로 정확히 분리
- 대상 작업 기록과 통계만 컨텍스트에 포함
- 직전 리포트의 동일 작업이 없을 때 비교 컨텍스트 생략
- RAG 질의·프롬프트·허용 evidenceRefs의 작업 경계
- 빈 목록 허용, 말투·근거·중복 검증
- 작업별 구조화 출력 재시도와 안전한 진단 전달
- 한 작업 실패가 다른 작업 상태를 바꾸지 않음
- 이벤트 ID·리포트·작업 타입·스냅샷 불일치 시 결과 미반영
- 기록 및 리포트 피드백의 첫 AFTER_COMMIT 단계 비동기 실행

### API와 HTML

- 소유권, 완료 상태, SUPERSEDED 제외
- PENDING/READY/FAILED 혼합 응답과 READY 본문 조건
- `WorkType.entries` 기반 안정적인 순서
- `nextActions` 이름과 작업별 응답 직렬화
- 선택한 작업 탭의 코칭만 표시
- 하나라도 PENDING이면 폴링 유지, 모두 종료 상태면 중지
- 전체·기록 없음·실패 상태 안내

최종 검증 명령은 다음과 같다.

```bash
cd backend
./gradlew :domain:test :application:test :api:test
git diff --check
```

HTML은 실제 서버 응답으로 작업 탭 전환, 혼합 상태, 폴링 종료를 수동 확인한다.

## 범위 제외와 잔여 위험

이번 변경에서는 다음을 추가하지 않는다.

- iOS 화면 변경
- 전체 사이클 AI 요약과 합성 상태
- 기록 없는 작업의 AI 코칭
- 전체 피드백 부모, 작업 섹션 테이블, item의 중복 work type
- 항목 개수 제한
- 범용 코칭 validator/lifecycle 부모 추상화
- 수동 재생성 API, stale/revision 이력, 완료 후 수정 반영
- outbox, 메시지 브로커, PENDING 복구 스케줄러
- 전용 비동기 executor와 동시 호출 제한

이에 따라 다음 위험은 명시적으로 남는다.

1. in-memory 이벤트 처리 중 서버가 종료되면 일부 작업이 `PENDING`에 남을 수 있다.
2. 완료 리포트의 원본 기록을 수정·삭제하면 기존 작업별 코칭이 오래되거나 이미
   사라진 작업 타입 결과가 남을 수 있다. 수정으로 새 작업 타입이 추가돼도 그 작업의
   피드백은 새로 만들어지지 않는다.
3. 한 리포트에서 최대 8개 작업이 동시에 검색·모델 호출을 시작하고, 각 작업의
   구조화 출력 재시도까지 포함하면 호출량이 늘어날 수 있다.

현재는 실제 요구가 없는 복구·버전·스로틀링 구조를 미리 만들지 않는다. 운영 전환,
수정 반영 요구, 모델 과부하가 실제로 확인되는 시점에 각각 별도 설계한다.
