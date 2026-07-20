<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# [보류] CommunityCommentUpdateRequest 미사용

> ⬆ 상위: [API 명세서](README.md)

### Status
커뮤니티 1차 범위에서는 댓글 수정 기능을 제공하지 않는다.
### Rule
- 댓글은 작성자 본인 삭제만 가능하다.
- 삭제 시 row는 유지하고 `isDeleted = true`로 처리한다.
- 댓글 목록 응답에서는 삭제된 댓글의 `body`를 `삭제된 댓글입니다.`로 반환한다.
### Related API
- 댓글 삭제: `DELETE /api/v1/community/comments/{commentId}`
