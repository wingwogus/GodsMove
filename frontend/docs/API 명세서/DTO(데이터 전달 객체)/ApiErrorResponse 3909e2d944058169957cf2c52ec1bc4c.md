# ApiErrorResponse

API 분류: API Response
태그: [공통] 공통

## 용도

모든 API 오류 응답 공통 포맷.

## Fields

- code: string, required. 예: VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, AI_PROCESSING_FAILED.
- message: string, required. 사용자 또는 클라이언트 개발자가 이해할 수 있는 오류 메시지.
- details: object | null, optional. 필드 단위 오류 또는 추가 원인.
- traceId: string, optional. 서버 로그 추적 ID.

## 기본 HTTP Status

- 400: 요청 형식 또는 필수값 오류.
- 401: 인증 없음 또는 토큰 만료.
- 403: 본인 리소스가 아니거나 권한 없음.
- 404: 대상 리소스 없음 또는 Soft Deleted.
- 409: 상태 전이 불가, 중복, 삭제 제한.
- 500: 서버 오류.