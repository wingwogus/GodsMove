# 리포트 피드백 STALE 수동 재생성 설계

## 배경

완료 리포트에 포함된 영농기록도 수정·추가·삭제할 수 있고, 리포트 통계는 해당 변경을 즉시 반영한다.
반면 통계가 바뀔 때마다 OpenClaw 코칭을 자동 재생성하면 불필요한 호출과 대기 작업이 늘어난다.

따라서 통계는 즉시 갱신하되 기존 코칭은 `STALE`로 무효화하고, 사용자가 명시적으로 요청할 때만 다시 생성한다.

## 고려한 방식

### 선택: 기존 피드백 한 행을 STALE로 전환

- `(report_id, work_type)` 한 행을 유지한다.
- 입력이 달라지면 기존 상태를 `STALE`로 바꾸고 자동 생성 이벤트는 발행하지 않는다.
- 사용자가 재생성하면 같은 행이 `PENDING`을 거쳐 새 `READY` 결과로 교체된다.
- 과거 코칭 이력이 필요하지 않은 현재 제품 범위에 가장 단순하다.

### 제외: 입력 revision별 피드백 행 누적

- 생성 이력을 보존할 수 있지만 unique key, 조회, 보관 정책과 마이그레이션이 복잡해진다.
- 사용자에게 과거 코칭을 보여주는 요구가 없어 채택하지 않는다.

### 제외: 상태 없이 항상 재생성 버튼 노출

- 현재 코칭이 최신 통계 기준인지 API와 화면이 판별할 수 없다.
- 오래된 코칭을 최신 결과로 오인할 수 있어 채택하지 않는다.

## 상태 전이

```text
리포트 최초 완료:        PENDING -> READY
초기 생성 실패:          PENDING -> FAILED
통계/입력 변경:          PENDING|READY|FAILED -> STALE
사용자 재생성:           STALE|FAILED -> PENDING -> READY|FAILED
```

- `STALE` 전환에서는 OpenClaw 이벤트를 발행하지 않는다.
- `STALE` 응답의 `content`는 항상 `null`이다. 기존 문구는 DB에 남아 있어도 API와 화면에서 숨긴다.
- 화면은 `STALE`을 받으면 “통계가 변경됐어요. 코칭을 다시 생성해주세요.”와 재생성 버튼을 표시한다.
- `READY` 재생성은 계속 허용하지 않는다.

## 입력 freshness

`report_feedback.source_fingerprint varchar(64) null`을 추가한다.

fingerprint는 실제 OpenClaw 입력인 `ReportFeedbackContext`를 canonical JSON으로 직렬화한 뒤 SHA-256으로 계산한다.
따라서 다음 변경을 모두 감지한다.

- 현재 리포트 통계와 기간
- 대상 작업유형의 기록, 메모, 날씨, 상세값
- 직전 완료 리포트의 비교 통계
- context schema version

`sourceFingerprint`는 현재 행의 PENDING/READY/FAILED 결과가 어떤 입력에 대응하는지를 뜻한다.
STALE로 바뀔 때는 기존 값을 유지하고, 사용자가 재생성을 요청할 때 최신 fingerprint로 교체한다.

기존 배포 데이터의 fingerprint는 `null`로 둔다. 기존 READY는 그대로 제공하며, 이후 해당 리포트 입력이 변경되는 첫 시점에 STALE이 된다.

## lifecycle 동작

`FarmingCycleReportProjectionService`는 기존처럼 기록 변경 뒤 통계를 재계산하고 완료 리포트마다 feedback lifecycle을 호출한다.

### 최초 완료

- 해당 report의 피드백 행이 하나도 없으면 현재 work type마다 PENDING을 생성한다.
- 현재 context fingerprint를 저장하고 preparation 이벤트를 발행한다.
- 기존 최초 자동 코칭 동작을 유지한다.

### 완료 리포트 입력 변경

- work type별 최신 context fingerprint를 계산한다.
- 기존 행의 fingerprint와 다르면 해당 행을 STALE로 바꾼다.
- 이벤트는 발행하지 않는다.
- 새로 추가된 work type은 fingerprint가 없는 STALE placeholder를 만들고 수동 생성을 기다린다.
- 삭제된 work type 행은 STALE로 보존하지만 현재 report feedback 목록에서는 제외한다.

### 사용자 재생성

- `FAILED`와 `STALE`만 허용한다.
- 현재 context와 fingerprint를 다시 계산한다.
- 상태를 PENDING으로 바꾸고 failure/input snapshot을 초기화한다.
- preparation 이벤트를 발행한다.

## 비동기 경합 방지

preparation 단계에서 조립한 context fingerprint가 행의 `sourceFingerprint`와 같은지 확인한다.
generation 최종 저장은 기존 id/status/inputSnapshot 검사와 fingerprint 검사를 함께 수행한다.

생성 중 기록이 다시 바뀌면 lifecycle이 행을 STALE로 전환하므로 이전 작업은 READY/FAILED를 저장하지 못한다.

## API 계약

- `ReportFeedbackStatus`에 `STALE`을 추가한다.
- GET 응답 shape은 유지한다.
- status가 STALE이면 `inputPrepared=false`, `failureCode=null`, `content=null`을 반환한다.
- regenerate endpoint는 FAILED와 STALE을 허용한다.
- 현재 report에 존재하는 work type만 목록에 포함한다.

## 데이터베이스 변경

서비스 중단이 가능한 개발 서버 기준으로 다음 DDL을 먼저 적용한다.

```sql
alter table report_feedback
  add column if not exists source_fingerprint varchar(64);

alter table report_feedback
  drop constraint if exists report_feedback_status_check;

alter table report_feedback
  drop constraint if exists ck_report_feedback_status;

alter table report_feedback
  add constraint report_feedback_status_check
  check (status in ('PENDING', 'READY', 'FAILED', 'STALE'));
```

기존 `(report_id, work_type)` unique constraint는 유지한다.

## 최소 검증 범위

속도를 위해 각 커밋마다 전체 테스트를 반복하지 않는다. 다음 핵심 회귀 테스트만 구현 중 실행한다.

1. 최초 완료는 PENDING을 만들고 이벤트를 발행한다.
2. READY 입력 변경은 STALE이 되고 이벤트를 발행하지 않는다.
3. 새 work type은 STALE placeholder가 된다.
4. STALE GET은 content를 숨긴다.
5. STALE 재생성은 최신 fingerprint로 PENDING이 된다.
6. 생성 중 입력 변경 후 도착한 이전 결과는 저장되지 않는다.

작업 마지막에 `./gradlew test`와 `./gradlew build`를 각각 한 번 실행하고 전체 diff 코드 리뷰를 한 번 수행한다.

## 제외 범위

- 과거 피드백 revision 이력 보관
- READY 강제 재생성
- 자동 background 재생성
- report feedback 삭제 API
- FARMING_RECORD 벡터 색인
