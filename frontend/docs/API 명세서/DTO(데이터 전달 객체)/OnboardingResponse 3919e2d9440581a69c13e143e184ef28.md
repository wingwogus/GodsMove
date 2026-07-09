# OnboardingResponse

API 분류: API Response
태그: [인증] 계정

## Body

```json
{
  "status": "REQUIRED",
  "missingFields": ["NICKNAME", "REGION", "EXPERIENCE_LEVEL"]
}
```

## Field

- `status`: string, required. `REQUIRED` 또는 `COMPLETE`.
- `missingFields`: string array, required. 온보딩 필수 필드 중 비어 있는 필드 목록.

## missingFields 값

- `NAME`
- `PHONE`
- `BIRTH_DATE`
- `NICKNAME`
- `REGION`
- `EXPERIENCE_LEVEL`

`missingFields`가 비어 있으면 `status`는 `COMPLETE`다.

## Parameter

None

## Header

None

## Query

None