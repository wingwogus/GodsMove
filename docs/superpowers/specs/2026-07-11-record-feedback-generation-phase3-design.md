# 기록 피드백 LLM 생성 Phase 3 설계

- 날짜: 2026-07-11
- 상태: 승인됨
- 범위: 자동 기록 피드백 생성, 검증, 저장, 조회 응답

## 목적

Phase 2가 저장한 실제 영농기록 기반 `inputSnapshot`을 사용해, 기록 저장 뒤
자동으로 농부용 짧은 피드백을 생성한다. 이 단계는 완료 리포트 상세 코칭을
포함하지 않는다.

## 범위

- `PENDING` 기록 피드백의 RAG·LLM 자동 생성
- `READY` 또는 `FAILED` 상태 전이
- 잘한 점 1개와 다음 행동 2~3개의 전용 출력 계약
- 근거·길이·날씨·병해충 조건 검증 및 한 번의 재생성
- 기존 상태 조회 API에서 READY 결과 노출

다음은 범위 밖이다.

- 완료 리포트 코칭 생성
- 스케줄러, 메시지 브로커, `@Async`, 범용 backfill runner
- 이미지 내용 분석
- 입력 스냅샷 재조립 또는 과거 기록·주기 통계 참조

## 선택한 구조

기존 `TodayRecordFeedbackService`는 외부 Context와 이전 공용
`CoachingStructuredResult`를 전제로 하며 제품 API에서 사용하지 않는다. 이를
새 제품 전용 `RecordFeedbackGenerationService`로 교체한다. 검색 쿼리 계획과
공식문서 벡터 검색은 재사용하되, 출력 타입·프롬프트·검증기는 기록 피드백
계약만 책임진다.

별도의 범용 LLM 엔진은 기록과 리포트가 실제로 같은 출력 규칙을 공유할 때까지
만들지 않는다. 리포트 코칭은 이후 별도 Context·출력 타입으로 구현한다.

## 생성 흐름

```text
영농기록 생성 또는 수정
  -> RECORD/PENDING 저장
  -> AFTER_COMMIT: inputSnapshot 준비 (REQUIRES_NEW)
  -> inputSnapshot 저장 성공 후 생성 이벤트 발행
  -> AFTER_COMMIT: LLM 생성 (REQUIRES_NEW)
  -> READY 또는 FAILED 저장
```

- 생성기는 `feedbackId`, `memberId`, `recordId`, `sourceRevision`을 다시
  검증한다. 대상이 `PENDING`이 아니거나 revision이 다르면 아무 작업도 하지
  않는다.
- 생성의 유일한 사용자 입력은 저장된 `inputSnapshot`이다. 현재 엔티티를 다시
  읽어 Context를 섞지 않는다.
- 재생성은 기존 `FAILED -> PENDING` 후 같은 준비·생성 흐름을 다시 탄다.
- `STALE` fallback에서 새 현재 revision PENDING이 만들어진 경우도 같은 흐름을
  탄다.

## 출력 계약

```json
{
  "goodPoint": {
    "basis": "점적관수 방식과 토양 상태 기록",
    "text": "점적관수로 토양 상태를 확인한 점이 좋았어요.",
    "evidenceRefs": ["record:<recordId>"]
  },
  "nextActions": [
    {
      "due": "NEXT_WEEK",
      "category": "WEATHER",
      "basis": "7월 15일 비 예보",
      "text": "7월 15일 비 예보가 있어 배수로를 확인하세요.",
      "evidenceRefs": ["weather:2026-07-15"]
    }
  ]
}
```

내부 출력 타입은 `RecordFeedbackCoachingResult`, `RecordFeedbackItem`,
`RecordFeedbackNextAction`으로 정의한다.

- `goodPoint`: 정확히 1개
- `nextActions`: 2~3개
- 모든 항목: 비어 있지 않은 `basis`, `text`, `evidenceRefs`
- `text`: 15~45자, 잘라서 맞추지 않는다
- `basis`의 정규화된 핵심어가 `text`에도 하나 이상 있어야 한다
- 날씨 행동은 `WEATHER` 분류와 `weather:current` 또는 `weather:<date>` 근거가
  함께 있을 때만 허용한다
- 병해충 행동은 방제 기록의 대상 병해충 또는 공식문서 근거가 있을 때만
  허용한다
- 허용 근거는 `record:<recordId>`, snapshot 안의 날씨 ID, 검색된 공식문서 ID다

LLM은 허용된 ID만 사용할 수 있다. 신뢰도, 감사 결과, 모델 이름, 전체 인용
메타데이터는 운영 데이터로만 저장한다.

## 실패와 재시도

- 공식문서가 없거나 검증을 만족하는 다음 행동 2개를 만들 수 없으면
  `FAILED/INSUFFICIENT_EVIDENCE`
- 응답 파싱, 필수 필드, 길이, 근거 검증 실패는 같은 프롬프트로 한 번만 재생성
- 두 번째 실패는 `FAILED/STRUCTURED_OUTPUT_INVALID`
- 검색 또는 LLM 호출 실패는 `FAILED/GENERATION_FAILED`
- 실패는 영농기록·리포트·입력 스냅샷을 롤백하지 않는다

## 저장 모델

`CoachingFeedback`에 다음 상태 전이를 추가한다.

- `completeRecordFeedback(result, citations, audit, model)`:
  `PENDING -> READY`, 구조화 결과·인용·감사·모델 정보를 한 번에 저장
- 기존 `markFailed(code)`: `PENDING -> FAILED`

`inputSnapshot`은 READY가 된 뒤에도 유지한다. `structuredResult`는 제품 전용
출력 JSON이고, `citations`는 서버가 보강한 문서 메타데이터를 포함한다.

## API

기존 `GET /api/v1/farming-records/{recordId}/coaching-feedback` 응답에 nullable
`feedback`을 추가한다.

- `PENDING`, `FAILED`, `STALE`: `feedback = null`
- `READY`: `feedback.goodPoint.text`와 `feedback.nextActions[].text/due/category`
  반환

사용자 문구 안에 근거를 포함하므로 `basis`, `evidenceRefs`, 감사, 모델, 원본
문서 메타데이터는 API 응답에 추가하지 않는다.

## 검증

- 스냅샷만으로 생성하며 수정된 현재 엔티티를 다시 읽지 않음
- 이벤트 revision 불일치와 STALE/READY 이벤트 무시
- 허용되지 않은 근거 ID, 길이 초과, 근거 없는 날씨·병해충 행동 거절
- 첫 검증 실패 후 한 번만 재시도
- READY 결과의 DB 저장과 GET 응답 노출
- 검색 부족·LLM 실패가 `FAILED`로 저장되고 영농기록은 유지됨
