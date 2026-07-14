<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CommunityBoardListResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
[
  {
    "cropId": "00000000-0000-0000-0000-000000000000",
    "cropName": "인삼"
  }
]
```
### Field
- `cropId`: uuid, required.
- `cropName`: string, required.
### Rule
로그인 회원의 `member_crop` 기준으로 노출할 작물 게시판을 반환한다. 같은 작물이 여러 농장에 등록되어 있으면 응답에서는 한 번만 반환한다. `전체` 탭은 프론트 표시용이며 서버 저장값이 아니다.
