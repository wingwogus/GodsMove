<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityCommentCreateRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "parentCommentId": "00000000-0000-0000-0000-000000000000",
  "body": "황기 종자는 스크래치 작업이 도움이 됩니다."
}
```
### Field
- `parentCommentId`: uuid, optional. null이면 최상위 댓글.
- `body`: string, required.
### Rule
- 댓글 또는 1단계 대댓글을 작성한다.
- `parentCommentId`가 있으면 같은 게시글의 최상위 댓글이어야 한다.
- 대댓글의 대댓글은 허용하지 않는다.
- 삭제된 댓글에는 대댓글을 작성할 수 없다.
- 삭제된 게시글에는 댓글을 작성할 수 없다.
