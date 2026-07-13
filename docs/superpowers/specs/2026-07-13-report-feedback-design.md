# 완료 리포트 기반 코칭 설계

## 목적

완료된 영농 사이클 리포트가 생성되면, 해당 사이클의 결과와 실제 영농기록을
근거로 다음 사이클을 위한 상세 코칭을 자동 생성한다. 이 기능은 개별 기록 직후
제공하는 `RecordFeedback`과 목적·입력·출력 구조가 다르므로, `ReportFeedback`을
별도 도메인으로 둔다.

코칭은 단순 통계 설명이 아니라 다음 질문에 답해야 한다.

- 이번 사이클 전체에서 무엇을 잘했는가
- 반복되었거나 개선할 만한 작업 패턴은 무엇인가
- 다음 사이클에서 어떤 행동을 우선 실행해야 하는가

## 확정된 제품 결정

- 생성 시점은 `FarmingCycleReport`가 `COMPLETED`가 되는 즉시다.
- 생성은 자동이며, 생성 요청용 `POST` API는 만들지 않는다.
- 대상은 완료된 리포트 하나이며, 결과는 리포트당 한 건만 생성한다.
- 근거는 대상 리포트 통계, 그 사이클의 실제 영농기록, 같은 회원·밭·작물의
  직전 완료 리포트, 검색된 공식 기술 문서다.
- 직전 리포트나 기술 문서가 없으면 비교·기술 권고만 생략한다. 현재 리포트와
  기록에서 도출 가능한 코칭은 `READY`로 제공한다.
- 잘한 점·개선점·다음 사이클 실행계획은 항목 수를 제품 규칙으로 제한하지
  않는다. 다만 근거 없는 항목과 의미가 겹치는 반복 항목은 생성·검증 단계에서
  허용하지 않는다.
- 완료 뒤 원본 기록이 수정되더라도 이번 범위에서는 기존 코칭을 `STALE`로
  바꾸거나 재생성하지 않는다. 수동 재생성과 버전 비교도 제외한다.

## 선택한 구조

### 독립 모델: ReportFeedback

`CoachingFeedback` 같은 공통 부모나 `FeedbackType`은 만들지 않는다.
`RecordFeedback`은 개별 작업의 즉시 행동 제안이고, `ReportFeedback`은 한
사이클의 회고·비교·다음 사이클 계획이므로 입력과 결과를 억지로 통합하면
nullable 필드와 타입 분기만 늘어난다.

반대로 LLM 호출을 리포트 투영 트랜잭션에 넣지도 않는다. 외부 모델과 벡터 검색의
지연·실패가 리포트 완료 저장을 실패시키면 안 된다. 따라서 현재 기록 피드백의
준비·생성·조회 분리 패턴은 재사용하고, 리포트 전용 컨텍스트·프롬프트·출력 검증만
추가한다.

### 데이터 모델

`report_feedback`은 다음 책임만 가진다.

- 소유자·대상: `member_id`, `report_id` (리포트당 하나의 피드백)
- 처리 상태: `PENDING`, `READY`, `FAILED`와 실패 코드
- 고정 결과: `summary`
- 생성 감사: 모델명, 임베딩 모델명, 감사 상태
- 불변 입력·검색 근거·경고: `input_snapshot`, `citations`, `audit_warnings` JSONB

`input_snapshot`은 생성 당시의 통계, 원본 기록 요약, 비교 리포트, 검색 결과처럼
형태가 달라지고 조회 조건이 없는 감사용 입력이다. 이 값은 JSONB로 유지한다.

사용자에게 보여 주는 가변 결과 본문을 JSONB 하나로 저장하지 않는다.
`report_feedback_item` 자식 테이블에 다음을 저장한다.

- 부모와 표시 순서: `report_feedback_id`, `display_order`
- 섹션: `STRENGTH`, `IMPROVEMENT`, `NEXT_CYCLE_ACTION`
- 근거와 내용: `basis`, `text`

세 섹션은 현재 동일한 형태의 “근거 있는 코칭 항목”이므로 테이블 하나로 둔다.
이는 기록/리포트 피드백을 다시 추상화하는 것이 아니라, 리포트 안에서 늘어날 수
있는 동일 구조의 결과를 정규화하는 것이다. 섹션마다 다른 속성이 실제로 필요해질
때만 별도 엔티티로 분리한다.

`sourceRevision`과 `STALE` 상태는 의도적으로 추가하지 않는다. 완료 후 수정에
반응하지 않기로 했으므로 저장만 하고 쓰지 않는 버전 모델은 YAGNI 위반이다.

## 생성 흐름

```text
마지막 수확 기록 저장 또는 리포트 투영
  -> FarmingCycleReport가 COMPLETED로 저장됨
  -> ReportFeedbackLifecycleService가 최초 1회 PENDING 생성
  -> AFTER_COMMIT 준비 작업
  -> ReportFeedbackContext 생성 및 스냅샷 저장
  -> AFTER_COMMIT 생성 작업
  -> RAG 검색 + LLM 구조화 출력 + 검증
  -> READY 또는 FAILED
  -> GET feedback 폴링으로 상태/결과 조회
```

리포트 투영 서비스는 완료 리포트를 저장한 뒤 `ReportFeedbackLifecycleService`에
전달한다. lifecycle은 해당 `report_id`의 피드백이 이미 있으면 아무 작업도 하지
않으므로, 완료 리포트가 다시 투영돼도 중복 LLM 호출을 만들지 않는다.

준비 단계는 대상 리포트를 읽어 다음 컨텍스트를 만든다.

1. 대상 리포트의 범위와 `CycleReportStatistics`
2. 그 범위 안의 실제 영농기록 시간순 요약
3. 같은 회원·밭·작물 조합에서 바로 앞선 완료 리포트의 통계와 종료 정보
4. 작물·작업 유형·리포트에서 드러난 신호를 질의로 한 `TECH_DOCUMENT` 검색 결과

생성 단계는 이 스냅샷만 사용한다. 프롬프트는 다음 네 종류의 결과를 요구한다.

- 사이클 전체의 종합 요약 한 건
- 근거가 있는 잘한 점 목록
- 근거가 있는 개선점 목록
- 실행 시점과 방법이 드러나는 다음 사이클 계획 목록

출력 검증기는 섹션별 근거와 본문을 확인하고, 현재 입력에 없는 수치·사실을
만들어내는 항목, 중복되는 항목, 근거 없는 이전 리포트 비교·기술 권고를 제거한다.
기술 문서 검색이 비어 있거나 직전 완료 리포트가 없다는 사실만으로 실패시키지
않는다. 컨텍스트 조립 실패, 구조화 출력 재시도 소진, 예기치 못한 처리 오류만
`FAILED`가 된다.

## API 계약과 이름

API는 대상 리소스 아래의 같은 이름으로 통일한다.

- `GET /api/v1/farming-records/{recordId}/feedback`
- `GET /api/v1/farming-reports/{reportId}/feedback`

첫 번째 경로는 기존 `coaching-feedback` 경로를 대체한다. 두 응답 모두
`PENDING`, `READY`, `FAILED` 상태를 공통으로 노출하지만, READY 본문은 각 피드백의
실제 결과 구조를 유지한다. ReportFeedback의 READY 본문에는 `summary`와 세 섹션의
순서 있는 항목 목록이 있다.

컨트롤러와 DTO도 `RecordFeedbackController` / `RecordFeedbackResponses`,
`ReportFeedbackController` / `ReportFeedbackResponses`처럼 대상 기준으로 명명한다.
`FarmingRecordFeedback`, `RecordFeedbackCoaching`처럼 두 도메인 명사를 섞은 이름은
새로 만들지 않는다.

조회 서비스는 로그인한 `member`가 해당 리포트의 소유자인지 확인한다. 리포트 또는
피드백이 없을 때와 타인 리포트일 때의 오류 처리는 기존 리포트/기록 조회 계약을
따른다. PENDING은 정상 200 응답이므로 클라이언트는 기존 기록 피드백과 같은 방식으로
폴링한다.

## 모듈과 책임

| 계층 | 책임 |
| --- | --- |
| domain | `ReportFeedback`, `ReportFeedbackItem`, 상태·섹션 enum, repository. READY 전이와 항목 순서를 보장한다. |
| application | 자동 생성 lifecycle, 컨텍스트 조립, RAG 질의 계획·검색, 프롬프트/구조화 출력 검증, 상태 조회를 담당한다. |
| api | 리포트 피드백 조회 controller와 응답 DTO, 통일된 기록 피드백 경로를 담당한다. |

기존 RAG 설정, `ChatClient`, 벡터 저장소, 모델 감사 메타데이터, AFTER_COMMIT 처리
패턴은 재사용한다. `RecordFeedbackGenerationService` 자체를 범용 서비스로 바꾸지
않는다. 리포트와 기록의 사실 모델·출력 검증 규칙이 달라서, 공통화를 위해 분기를
넣는 것보다 리포트 전용 생성기를 두는 편이 명확하다.

## 테스트와 완료 기준

- 완료 리포트 저장 시 PENDING 피드백이 한 번만 만들어지는 application/integration
  테스트
- 준비 컨텍스트가 대상 통계·사이클 기록·직전 리포트·기술 문서를 조합하는 테스트
- 직전 리포트 또는 기술 문서가 없어도 가능한 결과가 READY가 되는 테스트
- READY 결과가 summary와 섹션별 순서 있는 `ReportFeedbackItem`으로 저장되는 테스트
- 구조화 출력 오류와 컨텍스트 조립 오류가 FAILED 상태·안전한 실패 코드로 남는 테스트
- ReportFeedback 조회 API의 PENDING/READY/FAILED 계약과 소유권 테스트
- 기록 피드백 API가 통일된 `/feedback` 경로에서 기존 응답 계약을 유지하는 회귀 테스트
- `./gradlew :domain:test :application:test :api:test`, `git diff --check`

## 범위 제외

- 완료 리포트 수정에 따른 stale 처리·자동 재생성·수동 재생성
- 리포트 코칭을 위한 공통 `Feedback` 부모 또는 타입 컬럼
- 새 작업 큐, 비동기 실행기, 메시지 브로커
- 알림, 항목 완료 체크, 피드백 편집
- 프론트엔드 화면 개편 및 항목 개수 제품 상한
