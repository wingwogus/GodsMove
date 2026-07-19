<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# OnboardingCompleteResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": null,
    "name": "홍길동",
    "phone": "010-1234-5678",
    "birthDate": "1990-10-01",
    "nickname": "sample-grower",
    "experienceLevel": 45,
    "managementType": "AGRICULTURAL_MANAGER_INDIVIDUAL",
    "profileImageUrl": "https://res.cloudinary.com/example/image/upload/..."
  },
  "farm": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "참참농장",
    "roadAddress": "전북특별자치도 전주시 덕진구 예시로 12",
    "jibunAddress": "전북특별자치도 전주시 덕진구 예시동 123-4",
    "latitude": 35.8465,
    "longitude": 127.1292,
    "pnu": "4511310200101230004",
    "landCategory": "전",
    "areaSqm": 1200.5,
    "areaIsManualEntry": false,
    "boundaryCoordinates": [
      { "latitude": 35.8461, "longitude": 127.1289 },
      { "latitude": 35.8463, "longitude": 127.1295 }
    ],
    "dataSource": {
      "address": "JUSO",
      "coordinate": "V_WORLD_ADDRESS",
      "parcel": "V_WORLD_CADASTRAL",
      "landCharacteristic": "V_WORLD_LAND_CHARACTERISTIC"
    }
  },
  "crops": [
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "externalNo": 101,
      "name": "감초",
      "usePartCategory": "ROOT_BARK",
      "usePartCategoryLabel": "뿌리/껍질"
    }
  ],
  "onboarding": {
    "status": "COMPLETE",
    "missingFields": []
  }
}
```
### Field
- `member`: MemberProfileResponse, required. 온보딩 완료 후 프로필 이미지가 있으면 `profileImageUrl`을 포함한다.
- `farm`: FarmResponse, required. 온보딩으로 생성된 농장.
- `crops`: CropResponse array, required. 온보딩에서 선택한 작물 목록.
- `onboarding`: OnboardingResponse, required.
온보딩 완료 성공 시 토큰은 재발급하지 않는다.
### Parameter
None
### Header
None
### Query
None
