# RecordFieldValueRequest

API 분류: API Request
태그: [영농일지] 기록

## Fields

- workTypeFieldId: uuid, required.
- valueText: string, optional.
- valueNumber: number, optional.
- valueBoolean: boolean, optional.
- valueDate: date, optional.
- valueJson: object, optional.

## Rule

work_type_[fields.data](http://fields.data)_type에 맞는 value 계열 필드 하나만 채운다.