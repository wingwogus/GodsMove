<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityPostUpdateRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "cropId": "00000000-0000-0000-0000-000000000000",
  "postType": "GENERAL",
  "title": "인삼 밭 두둑 만드는 방법 공유합니다",
  "body": "작업 도구와 시기를 잘 맞추는 게 핵심입니다...",
  "farmingRecordId": null,
  "mediaIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```
### Field
- `cropId`: uuid, required. 수정 후 게시글이 속할 작물 게시판 ID.
- `postType`: enum, required. `GENERAL`, `QUESTION`.
- `title`: string, required. 최대 50자.
- `body`: string, required.
- `farmingRecordId`: uuid | null, optional. null이면 영농일지 공유 해제.
- `mediaIds`: uuid array, optional. 수정 후 게시글에 남길 최종 이미지 ID 목록. 최대 5개. 빈 배열이면 첨부 이미지 없음.
### Rule
- 게시글 작성자만 수정 가능하다.
- 작물, Q&A 여부, 제목, 본문, 영농일지, 이미지를 모두 수정할 수 있다.
- `farmingRecordId`가 있으면 작성자 본인 기록이어야 하고, 기록의 crop이 `cropId`와 같아야 한다.
- `mediaIds`는 수정 후 최종 이미지 목록이다. 기존 첨부 이미지를 유지하려면 해당 media ID를 목록에 포함해야 한다.
- 기존 첨부 이미지 중 `mediaIds`에서 빠진 이미지는 게시글에서 제거하고 `uploaded_media.status = DELETED`로 처리한다.
- 새로 추가하는 이미지는 작성자 본인 소유의 `COMMUNITY_POST` 용도 `TEMP` 미디어여야 한다.
- 이미지 순서는 `mediaIds` 배열 순서를 따른다.
- Cloudinary 원본 삭제는 이 API에서 수행하지 않는다.
- 삭제된 게시글은 수정할 수 없다.
