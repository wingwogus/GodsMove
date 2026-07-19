<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# PublicMemberProfileResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "memberId": "00000000-0000-0000-0000-000000000000",
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
- `memberId`: uuid, required. 조회 대상 member ID.
- `nickname`: string | null, required.
- `experienceLevel`: number | null, required.
- `managementType`: string | null, required.
- `profileImageUrl`: string | null, required. Cloudinary URL.
- `farms`: MemberProfileFarmResponse array, required. 공개 가능한 농장 요약 목록.
- `crops`: MemberProfileCropResponse array, required. 조회 대상 회원이 키우는 작물을 `cropId` 기준으로 중복 제거한다.
### Rule
- 공개 프로필이므로 `email`, `name`, `phone`, `birthDate`는 반환하지 않는다.
- 농장 주소는 전체 주소가 아니라 화면 표시용 `displayRegion`만 포함한다.
