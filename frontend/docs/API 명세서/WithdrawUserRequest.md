<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# WithdrawUserRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- reason: string, optional.
- confirm: boolean, required. true여야 탈퇴 처리.
### Rule
회원탈퇴는 물리 삭제가 아니라 users.status = WITHDRAWN, withdrawn_at 기록으로 처리한다. 영농기록은 보존하고 커뮤니티 작성자/댓글 작성자는 익명 처리한다.
