<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MediaImageUploadResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "mediaId": "00000000-0000-0000-0000-000000000000",
  "imageUrl": "https://res.cloudinary.com/example/image/upload/...",
  "status": "TEMP"
}
```
### Field
- `mediaId`: uuid, required. 업로드된 미디어 ID.
- `imageUrl`: string, required. Cloudinary 접근 URL.
- `status`: string, required. 업로드 직후 `TEMP`.
### Rule
온보딩 프로필 이미지나 커뮤니티 게시글 이미지 연결 시 이 `mediaId`를 사용한다.
