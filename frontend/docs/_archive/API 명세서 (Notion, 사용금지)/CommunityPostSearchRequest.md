<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostSearchRequest

> ⬆ 상위: [API 명세서](README.md)

### Query
```javascript
GET /api/v1/community/posts?cropId=00000000-0000-0000-0000-000000000000&postType=QUESTION&keyword=발아&likedOnly=false&mineOnly=false&sort=POPULAR&cursor=<opaque-cursor>&size=20
```
### Field
- `cropId`: uuid, optional. 선택한 작물 게시판만 조회한다. 없으면 전체 작물 게시글을 조회한다.
- `postType`: string, optional. `GENERAL`, `QUESTION`. 없으면 전체 글 성격을 조회한다.
- `keyword`: string, optional. 제목과 본문을 검색한다. blank면 검색 조건으로 사용하지 않는다.
- `likedOnly`: boolean, optional. 기본값 `false`. true면 로그인 회원이 좋아요한 게시글만 조회한다.
- `mineOnly`: boolean, optional. 기본값 `false`. true면 로그인 회원이 작성한 게시글만 조회한다.
- `sort`: string, optional. 기본값 `LATEST`. `LATEST`, `LIKE`, `COMMENT`, `POPULAR`.
- `cursor`: string, optional. 이전 응답의 `nextCursor` 값을 그대로 전달한다.
- `size`: number, optional. 기본값 `20`. 1 이상의 값이어야 한다.
### Rule
- `likedOnly`와 `mineOnly`는 동시에 true로 전달할 수 있다. 이 경우 내가 좋아요한 글 중 내가 작성한 글만 조회한다.
- 커서는 opaque string이다. 클라이언트는 디코딩하거나 직접 생성하지 않고, 응답의 `nextCursor`를 다음 요청에 그대로 전달한다.
- `LATEST`는 `createdAt desc, id desc` 기준으로 정렬한다.
- `LIKE`는 좋아요 수 desc, `COMMENT`는 삭제되지 않은 댓글 수 desc, `POPULAR`은 `likeCount + commentCount` desc 기준이며 동점이면 `createdAt desc, id desc`로 정렬한다.
- 커서에 들어있는 sort와 요청 sort가 다르면 `400`으로 실패한다.
- 삭제된 게시글은 조회 결과에서 제외한다.
