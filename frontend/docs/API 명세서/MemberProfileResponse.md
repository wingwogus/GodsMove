<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MemberProfileResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "email": null,
  "name": "홍길동",
  "phone": "+82 10-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": null,
  "experienceLevel": null,
  "managementType": null,
  "profileImageUrl": null
}
```
### Field
- `id`: uuid, required. member id.
- `email`: string | null, required. 소셜 로그인에서는 provider가 email을 내려주지 않거나 이미 다른 member가 사용 중이면 `null`일 수 있다.
- `name`: string | null, required.
- `phone`: string | null, required.
- `birthDate`: date | null, required. `yyyy-MM-dd`.
- `nickname`: string | null, required.
- `experienceLevel`: number | null, required. 온보딩 전에는 `null`, 온보딩 완료 후 0~100.
- `managementType`: string | null, required. 온보딩 전에는 `null`.
- `profileImageUrl`: string | null, required. 온보딩 프로필 이미지가 연결되면 Cloudinary URL을 반환한다.
### Removed
- `region`은 member에서 제거했다. 지역/주소 정보는 farm에서 관리한다.
### Parameter
None
### Header
None
### Query
None
