<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
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
    {
      "latitude": 35.8461,
      "longitude": 127.1289
    }
  ],
  "dataSource": {
    "address": "JUSO",
    "coordinate": "V_WORLD_ADDRESS",
    "parcel": "V_WORLD_CADASTRAL",
    "landCharacteristic": "V_WORLD_LAND_CHARACTERISTIC"
  }
}
```
### Field
- `id`: uuid, required.
- `name`: string, required.
- `roadAddress`: string, required.
- `jibunAddress`: string, nullable.
- `latitude`: number, nullable.
- `longitude`: number, nullable.
- `pnu`: string, nullable.
- `landCategory`: string, nullable.
- `areaSqm`: number, nullable.
- `areaIsManualEntry`: boolean, required.
- `boundaryCoordinates`: FarmBoundaryCoordinateResponse array, required.
- `dataSource`: FarmDataSourceResponse, required.
### Removed
- `ownerUserId`, `region`, `city`, `street`, `farmType`은 현재 응답에서 사용하지 않는다.
