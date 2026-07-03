# OnboardingCompleteRequest

API 분류: API Request
태그: [회원] 유저 정보

## Body

```json
{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": "sample-grower",
  "region": "Naju",
  "experienceLevel": "BEGINNER"
}
```

## Field

- `name`: string, required.
- `phone`: string, required.
- `birthDate`: date, required. `yyyy-MM-dd`.
- `nickname`: string, required.
- `region`: string, required.
- `experienceLevel`: string, required.

## Parameter

None

## Header

Authorization: Bearer {accessToken}

## Query

None