# WithdrawUserRequest

API 분류: API Request
태그: [회원] 유저 정보

## Fields

- reason: string, optional.
- confirm: boolean, required. true여야 탈퇴 처리.

## Rule

회원탈퇴는 물리 삭제가 아니라 users.status = WITHDRAWN, withdrawn_at 기록으로 처리한다. 영농기록은 보존하고 커뮤니티 작성자/댓글 작성자는 익명 처리한다.