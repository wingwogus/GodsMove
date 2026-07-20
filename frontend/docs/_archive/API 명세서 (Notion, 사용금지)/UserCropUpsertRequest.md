<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# UserCropUpsertRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- farmId: uuid, required.
- cropId: uuid, required.
- plantingYear: number, conditional. 다년생 작물은 필수.
- status: string, optional. ACTIVE, PAUSED, ENDED 등.
- startedOn: date, optional.
### Rule
주요 작물은 최소 1개 이상이어야 한다.
