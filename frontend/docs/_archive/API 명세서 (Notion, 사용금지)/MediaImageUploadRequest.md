<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MediaImageUploadRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "usageType": "COMMUNITY_POST",
  "base64Image": "<base64 image data>",
  "originalFilename": "sprout.jpg",
  "contentType": "image/jpeg"
}
```
### Field
- `usageType`: string, required. `PROFILE`, `COMMUNITY_POST`.
- `base64Image`: string, required. Base64 인코딩된 이미지 데이터.
- `originalFilename`: string, optional.
- `contentType`: string, optional. 예: `image/jpeg`, `image/png`.
### Rule
서버가 Cloudinary에 업로드하고 `uploaded_media`를 `TEMP` 상태로 생성한다. decoded image size 기본 제한은 10MB다.
### Parameter
None
### Header
Authorization: Bearer {accessToken}
### Query
None
