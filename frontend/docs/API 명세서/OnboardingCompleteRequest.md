<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# OnboardingCompleteRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": "sample-grower",
  "profileMediaId": "00000000-0000-0000-0000-000000000000",
  "experienceLevel": 45,
  "managementType": "AGRICULTURAL_MANAGER_INDIVIDUAL",
  "farm": {
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
  "cropIds": ["00000000-0000-0000-0000-000000000000"]
}
```
### Field
- `name`: string, required.
- `phone`: string, required.
- `birthDate`: date, required. `yyyy-MM-dd`.
- `nickname`: string, required.
- `profileMediaId`: uuid, optional. 사전에 이미지 업로드 API로 생성한 `PROFILE` 용도 미디어 ID.
- `experienceLevel`: number, required. 0 이상 100 이하.
- `managementType`: string, required. `AGRICULTURAL_MANAGER_INDIVIDUAL`, `AGRICULTURAL_MANAGER_CORPORATION`, `FARMER_NON_MANAGER`.
- `farm`: FarmRequest, required. 온보딩 시 생성할 농장 정보.
- `cropIds`: uuid array, required. 하나 이상 선택해야 한다.
### Rule
- `profileMediaId`가 있으면 로그인 회원 소유의 `PROFILE` 용도 미디어여야 하며, 아직 다른 대상에 연결되지 않은 상태여야 한다.
- 온보딩 완료 시 해당 미디어는 회원 프로필 이미지로 연결되고 `ATTACHED` 상태가 된다.
### Removed
- `region`은 더 이상 받지 않는다. 지역/주소 정보는 `farm` 객체에 저장한다.
### Parameter
None
### Header
Authorization: Bearer {accessToken}
### Query
None
