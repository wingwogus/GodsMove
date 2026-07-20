<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostCreateRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "cropId": "00000000-0000-0000-0000-000000000000",
  "postType": "QUESTION",
  "title": "황기 발아율이 너무 낮아요 원인이 뭘까요?",
  "body": "올해 황기를 처음 시작한 초보입니다...",
  "farmingRecordId": "00000000-0000-0000-0000-000000000000",
  "mediaIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```
### Field
- `cropId`: uuid, required. 게시글이 속한 작물 게시판 ID.
- `postType`: enum, required. `GENERAL`, `QUESTION`.
- `title`: string, required. 최대 50자 정책은 클라이언트/서버 검증에 반영한다.
- `body`: string, required.
- `farmingRecordId`: uuid, optional. 공유할 영농일지 ID.
- `mediaIds`: uuid array, optional. 커뮤니티 게시글 이미지. 최대 5개.
### Rule
- 게시글은 반드시 하나의 작물에 속한다.
- `farmingRecordId`가 있으면 작성자 본인 기록이어야 하고, 기록의 crop이 `cropId`와 같아야 한다.
- `mediaIds`는 작성자 본인 소유의 `COMMUNITY_POST` 용도 미디어여야 하며, 아직 다른 대상에 연결되지 않은 상태여야 한다.
