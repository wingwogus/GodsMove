<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# MemberProfileCropResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "cropId": "22222222-2222-2222-2222-222222222222",
  "cropName": "황기"
}
```
### Field
- `cropId`: uuid, required.
- `cropName`: string, required.
### Rule
- 회원이 여러 농장에서 같은 작물을 키우더라도 프로필 응답에서는 `cropId` 기준으로 한 번만 반환한다.
- 농장별 작물 상세 정보는 프로필 응답 범위에 포함하지 않는다.
