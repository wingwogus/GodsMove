<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostListResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "items": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "cropId": "00000000-0000-0000-0000-000000000000",
      "cropName": "황기",
      "postType": "QUESTION",
      "title": "황기 발아율이 너무 낮아요 원인이 뭘까요?",
      "bodyPreview": "올해 황기를 처음 시작한 초보입니다...",
      "thumbnailUrl": "https://res.cloudinary.com/example/image/upload/...",
      "author": {
        "memberId": "00000000-0000-0000-0000-000000000000",
        "nickname": "황기농부",
        "profileImageUrl": null
      },
      "commentCount": 3,
      "likeCount": 8,
      "likedByMe": false,
      "createdAt": "2026-07-06T10:00:00"
    }
  ],
  "nextCursor": "eyJzb3J0IjoiUE9QVUxBUiIsInNjb3JlIjoxMSwiY3JlYXRlZEF0IjoiMjAyNi0wNy0wNlQxMDowMDowMCIsImlkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIn0"
}
```
### Field
- `items`: CommunityPostSummaryResponse array, required.
- `items[].id`: uuid, required.
- `items[].cropId`: uuid, required.
- `items[].cropName`: string, required.
- `items[].postType`: string, required. `GENERAL`, `QUESTION`.
- `items[].title`: string, required.
- `items[].bodyPreview`: string, required.
- `items[].thumbnailUrl`: string | null, required. 대표 이미지 URL.
- `items[].author`: AuthorResponse, required.
- `items[].commentCount`: number, required.
- `items[].likeCount`: number, required.
- `items[].likedByMe`: boolean, required.
- `items[].createdAt`: datetime, required.
- `nextCursor`: string | null, required. 다음 페이지가 없으면 `null`.
### Rule
- 목록 응답은 본문 전체와 이미지 배열을 반환하지 않는다. 상세 조회에서 확인한다.
- `nextCursor`는 opaque string이며 클라이언트가 파싱하지 않는다.
- 별도 `hasNext` 필드는 사용하지 않는다. `nextCursor != null`이면 다음 페이지가 있다.
