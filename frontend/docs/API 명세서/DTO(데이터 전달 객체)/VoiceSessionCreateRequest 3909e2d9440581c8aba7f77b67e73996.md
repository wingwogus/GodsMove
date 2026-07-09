# VoiceSessionCreateRequest

API 분류: API Request
태그: [음성] 세션

## Fields

- farmId: uuid, optional.
- cropId: uuid, optional.
- workTypeId: uuid, optional.
- startedAt: datetime, optional.

## Rule

음성 기록 시작 시 voice_record_sessions를 CREATED 상태로 생성한다.