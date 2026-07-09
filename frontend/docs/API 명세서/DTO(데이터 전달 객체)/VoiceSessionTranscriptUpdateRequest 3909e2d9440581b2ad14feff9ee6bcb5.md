# VoiceSessionTranscriptUpdateRequest

API 분류: API Request
태그: [음성] 세션

## Fields

- transcript: string, required.
- append: boolean, optional. true면 기존 transcript 뒤에 추가.
- clientTurnId: string, optional. 클라이언트 오프라인/재시도 중복 방지용.

## Rule

transcript는 사용자가 종료할 때까지 계속 갱신될 수 있다.