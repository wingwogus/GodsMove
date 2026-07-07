<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmDataSourceRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "address": "JUSO",
  "coordinate": "V_WORLD_ADDRESS",
  "parcel": "V_WORLD_CADASTRAL",
  "landCharacteristic": "V_WORLD_LAND_CHARACTERISTIC"
}
```
### Field
- `address`: string, optional. 주소 데이터 출처.
- `coordinate`: string, optional. 좌표 데이터 출처.
- `parcel`: string, optional. 필지 데이터 출처.
- `landCharacteristic`: string, optional. 토지 특성 데이터 출처.
