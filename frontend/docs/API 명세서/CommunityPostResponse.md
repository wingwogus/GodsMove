<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "cropId": "00000000-0000-0000-0000-000000000000",
  "cropName": "황기",
  "postType": "QUESTION",
  "title": "황기 발아율이 너무 낮아요 원인이 뭘까요?",
  "body": "올해 황기를 처음 시작한 초보입니다...",
  "imageUrls": [
    "https://res.cloudinary.com/example/image/upload/..."
  ],
  "farmingRecordId": "00000000-0000-0000-0000-000000000000",
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
```
### Field
- `id`: uuid, required.
- `cropId`: uuid, required.
- `cropName`: string, required.
- `postType`: enum, required. `GENERAL`, `QUESTION`.
- `title`: string, required.
- `body`: string, required.
- `imageUrls`: string array, required. 첨부 이미지 URL 목록. 최대 5개.
- `farmingRecordId`: uuid | null, required.
- `author.memberId`: uuid, required.
- `author.nickname`: string | null, required.
- `author.profileImageUrl`: string | null, required.
- `commentCount`: number, required. 조회 시 계산한다.
- `likeCount`: number, required. 조회 시 계산한다.
- `likedByMe`: boolean, required.
- `createdAt`: datetime, required.
### Removed
- `status`는 사용하지 않는다. 삭제는 내부 상태로 관리하고 삭제 게시글은 응답하지 않는다.
- `acceptedCommentId`는 사용하지 않는다. 채택 기능은 없다.
- `crop` 객체, `images` 객체 배열은 사용하지 않고 `cropId`, `cropName`, `imageUrls`로 응답한다.
