<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# VoiceSessionTranscriptUpdateRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- transcript: string, required.
- append: boolean, optional. true면 기존 transcript 뒤에 추가.
- clientTurnId: string, optional. 클라이언트 오프라인/재시도 중복 방지용.
### Rule
transcript는 사용자가 종료할 때까지 계속 갱신될 수 있다.
