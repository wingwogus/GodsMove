<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MyMemberProfileResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "memberId": "00000000-0000-0000-0000-000000000000",
  "email": "farmer@example.com",
  "name": "홍길동",
  "phone": "+82 10-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": "황기농부",
  "experienceLevel": 2,
  "managementType": "AGRICULTURAL_MANAGER_INDIVIDUAL",
  "profileImageUrl": "https://res.cloudinary.com/example/image/upload/profile.jpg",
  "farms": [
    {
      "farmId": "11111111-1111-1111-1111-111111111111",
      "farmName": "횡성 황기밭",
      "displayRegion": "강원특별자치도 횡성군"
    }
  ],
  "crops": [
    {
      "cropId": "22222222-2222-2222-2222-222222222222",
      "cropName": "황기"
    }
  ]
}
```
### Field
- `memberId`: uuid, required. 로그인 회원 ID.
- `email`: string | null, required. 소셜 provider가 email을 내려주지 않거나 중복이면 `null`일 수 있다.
- `name`: string | null, required.
- `phone`: string | null, required.
- `birthDate`: date | null, required. `yyyy-MM-dd`.
- `nickname`: string | null, required.
- `experienceLevel`: number | null, required. 온보딩 전에는 `null`일 수 있다.
- `managementType`: string | null, required. 온보딩 전에는 `null`일 수 있다.
- `profileImageUrl`: string | null, required. Cloudinary URL.
- `farms`: MemberProfileFarmResponse array, required. 농장이 없으면 빈 배열.
- `crops`: MemberProfileCropResponse array, required. 회원이 키우는 작물을 `cropId` 기준으로 중복 제거한다.
### Rule
- 내 프로필 응답이므로 개인 식별 정보(`email`, `name`, `phone`, `birthDate`)를 포함한다.
- 농장 주소는 전체 주소가 아니라 화면 표시용 `displayRegion`만 포함한다.
