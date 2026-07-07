<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# AcceptCommentResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- postId: uuid, required.
- acceptedCommentId: uuid, required.
### Rule
QUESTION 게시글만 답변 채택 가능하며 작성자만 채택할 수 있다. 채택 댓글은 1개만 허용한다.
