# RecordFeedback 영속 모델 분리 설계

## 목적

현재 `CoachingFeedback`은 영농기록 피드백과 사이클 리포트 피드백을 하나의
엔티티와 `coaching_feedback` 테이블에 함께 담는다. 실제로 구현된 기능은
영농기록 자동 피드백뿐이며, 두 대상은 입력·생성 시점·출력 구조가 다르다.

영농기록 피드백을 `RecordFeedback`으로 독립시키고, 사이클 리포트 피드백은
구현할 때 `ReportFeedback`이라는 별도 모델로 추가한다.

## 범위

- `CoachingFeedback`과 `CoachingFeedbackRepository`를 `RecordFeedback`과
  `RecordFeedbackRepository`로 교체한다.
- 물리 테이블을 `coaching_feedback`에서 `record_feedback`으로 교체한다.
- `FeedbackType`과 `cycleReport`의 nullable 연관관계를 제거한다.
- 저장된 LLM 결과의 `structured_result` JSONB를 제거한다.
- 반복되는 다음 행동을 `record_feedback_next_action` 테이블로 정규화한다.

운영 데이터와 스키마 호환은 요구하지 않는다. 현재 환경은 새 스키마를
생성해도 된다.

## 데이터 모델

### record_feedback

- 소유자와 대상 기록: `member_id`, `record_id`
- 원본 버전과 작업 상태: `source_revision`, `status`, `failure_code`
- 고정 결과: `good_point_basis`, `good_point_text`
- 생성 추적: `model_name`, `embedding_model`, `audit_status`
- 가변 감사 데이터: `input_snapshot`, `citations`, `audit_warnings` JSONB

`input_snapshot`은 작업 종류별 상세와 날씨처럼 구조가 달라지는 불변 입력이다.
`citations`, `audit_warnings`는 현재 조회·검색하지 않는 감사 데이터다. 따라서
이 세 값은 JSONB로 유지한다.

### record_feedback_next_action

- 부모: `record_feedback_id`
- 표시 순서: `display_order`
- 고정 속성: `due`, `category`, `basis`, `text`

READY 상태의 RecordFeedback은 다음 행동을 2~3개 가져야 하며, `display_order`는
0부터 연속적이어야 한다. 이 규칙은 `RecordFeedback.markReady`에서 검증한다.

## 흐름과 API

LLM은 계속 `RecordFeedbackContent` JSON 스키마로 응답한다. 이는 생성 직후
검증용 DTO일 뿐 영속 스키마가 아니다. 검증을 통과하면 processor가 잘한 점은
`RecordFeedback` 컬럼에, 다음 행동은 `RecordFeedbackNextAction` 목록에 저장한다.

조회 API 경로와 응답 JSON은 유지한다. Query service는 엔티티와 자식 행동을
`RecordFeedbackStatusResult`로 변환하고, API DTO는 이를 외부 응답으로 매핑한다.

## 리포트 코칭 경계

현재 `farming_cycle_report`는 리포트 데이터와 통계를 독립적으로 저장한다.
리포트 AI 코칭 결과는 아직 저장하거나 생성하지 않는다. 구현 시에만
`ReportFeedback`과 `report_feedback`을 추가한다. 현재 단계에서는 추상 부모
엔티티나 `FeedbackType`을 남기지 않는다.

## 검증

- RecordFeedback 상태 전이와 새로고침/STALE 경쟁 상태 회귀 테스트
- READY 결과가 고정 잘한 점과 순서 있는 2~3개 행동으로 저장되는 테스트
- 조회 API 응답 계약 회귀 테스트
- `:application:test`, `:api:test`, `git diff --check`

## 제외

- ReportFeedback 구현
- 다음 행동 완료 체크, 알림, 개별 수정
- 근거와 항목의 완전한 관계형 정규화
- 기존 `coaching_feedback` 데이터 마이그레이션
