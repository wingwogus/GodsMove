<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityCommentListResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
[
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
    "replies": [
      {
        "id": "00000000-0000-0000-0000-000000000001",
        "parentCommentId": "00000000-0000-0000-0000-000000000000",
        "author": {
          "memberId": "00000000-0000-0000-0000-000000000000",
          "nickname": "황기농부",
          "profileImageUrl": null
        },
        "body": "감사합니다!",
        "deleted": false,
        "createdAt": "2026-07-06T10:10:00",
        "replies": []
      }
    ]
  }
]
```
### Field
- root item: CommunityCommentResponse, required.
- `replies`: CommunityCommentResponse array, required. 대댓글 목록.
### Rule
- 삭제된 댓글은 row를 유지하고 `body`를 `삭제된 댓글입니다.`로 반환한다.
- 삭제 여부 필드는 `deleted`다. `isDeleted`는 응답 필드로 사용하지 않는다.
