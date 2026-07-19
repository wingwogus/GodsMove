<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostLikeToggleResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "liked": true,
  "likeCount": 9
}
```
### Field
- `liked`: boolean, required. 토글 이후 현재 로그인 회원의 좋아요 상태.
- `likeCount`: number, required. 토글 이후 게시글 좋아요 수.
### Rule
좋아요가 없으면 생성하고, 이미 있으면 삭제한다. 삭제된 게시글에는 토글할 수 없다.
