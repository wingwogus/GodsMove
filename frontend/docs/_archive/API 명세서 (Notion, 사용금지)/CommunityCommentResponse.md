<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityCommentResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "parentCommentId": null,
  "author": {
    "memberId": "00000000-0000-0000-0000-000000000000",
    "nickname": "황기농부",
    "profileImageUrl": null
  },
  "body": "황기 종자는 스크래치 작업이 도움이 됩니다.",
  "deleted": false,
  "createdAt": "2026-07-06T10:05:00",
  "replies": []
}
```
### Field
- `id`: uuid, required.
- `parentCommentId`: uuid | null, required.
- `author.memberId`: uuid, required.
- `author.nickname`: string | null, required.
- `author.profileImageUrl`: string | null, required.
- `body`: string, required. 삭제된 댓글이면 `삭제된 댓글입니다.`로 반환한다.
- `deleted`: boolean, required.
- `createdAt`: datetime, required.
- `replies`: CommunityCommentResponse array, required. 대댓글 목록.
### Removed
- `postId`는 댓글 응답 DTO에 포함하지 않는다.
- `isDeleted`가 아니라 `deleted`를 사용한다.
- `acceptedAnswer`는 사용하지 않는다. 채택 기능은 없다.
