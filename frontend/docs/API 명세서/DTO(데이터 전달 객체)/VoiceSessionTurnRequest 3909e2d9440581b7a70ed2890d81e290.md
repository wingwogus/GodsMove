# VoiceSessionTurnRequest

API 분류: API Request
태그: [음성] 세션

## Fields

- role: enum, required. USER, ASSISTANT.
- content: string, required.
- extractedFields: object, optional.
- clientTurnId: string, optional.

## Rule

필수값이 부족하면 AI는 누락된 필드를 한 번에 하나씩 질문한다. 사용자의 최신 답변만 유효하다.