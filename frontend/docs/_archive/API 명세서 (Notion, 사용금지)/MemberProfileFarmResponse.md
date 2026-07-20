<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MemberProfileFarmResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "farmId": "11111111-1111-1111-1111-111111111111",
  "farmName": "횡성 황기밭",
  "displayRegion": "강원특별자치도 횡성군"
}
```
### Field
- `farmId`: uuid, required.
- `farmName`: string, required.
- `displayRegion`: string | null, required. 농장 주소를 공백 기준 최대 2토큰까지만 잘라 만든 화면 표시 지역.
### Rule
- `displayRegion`은 `roadAddress`를 우선 사용하고, 없으면 `jibunAddress`를 사용한다.
- 주소 토큰이 1개뿐이면 1개만 반환하고, 주소가 없으면 `null`을 반환한다.
- 전체 도로명/지번 주소와 좌표는 프로필 응답에서 노출하지 않는다.
