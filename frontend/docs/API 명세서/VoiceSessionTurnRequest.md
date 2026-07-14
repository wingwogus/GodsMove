<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# VoiceSessionTurnRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- role: enum, required. USER, ASSISTANT.
- content: string, required.
- extractedFields: object, optional.
- clientTurnId: string, optional.
### Rule
필수값이 부족하면 AI는 누락된 필드를 한 번에 하나씩 질문한다. 사용자의 최신 답변만 유효하다.
