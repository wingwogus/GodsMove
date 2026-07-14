<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# RecordFieldValueRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- workTypeFieldId: uuid, required.
- valueText: string, optional.
- valueNumber: number, optional.
- valueBoolean: boolean, optional.
- valueDate: date, optional.
- valueJson: object, optional.
### Rule
work_type_[fields.data](http://fields.data)_type에 맞는 value 계열 필드 하나만 채운다.
