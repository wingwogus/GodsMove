<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# WorkTypeFieldResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- id: uuid, required.
- workTypeId: uuid, required.
- fieldKey: string, required.
- title: string, required.
- dataType: enum, required. TEXT, NUMBER, BOOLEAN, DATE, JSON.
- required: boolean, required.
- unit: string, optional.
- inputMethod: string, optional.
- description: string, optional.
