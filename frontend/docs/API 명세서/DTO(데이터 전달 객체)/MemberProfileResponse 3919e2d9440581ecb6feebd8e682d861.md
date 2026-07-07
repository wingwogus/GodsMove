# MemberProfileResponse

API 분류: API Response
태그: [회원] 유저 정보

## Body

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "email": null,
  "name": "홍길동",
  "phone": "+82 10-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": null,
  "region": null,
  "experienceLevel": null,
  "managementType": "UNREGISTERED"
}
```

## Field

- `id`: uuid, required. member id.
- `email`: string, nullable. 소셜 로그인에서는 provider가 email을 내려주지 않거나 이미 다른 member가 사용 중이면 `null`일 수 있다.
- `name`: string, nullable.
- `phone`: string, nullable.
- `birthDate`: date, nullable. `yyyy-MM-dd`.
- `nickname`: string, nullable.
- `region`: string, nullable.
- `experienceLevel`: string, nullable.
- `managementType`: string, required.

## Parameter

None

## Header

None

## Query

None