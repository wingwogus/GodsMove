# LoginResponse

API 분류: API Response
태그: [인증] 계정

## Body

```json
{
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": null,
    "name": "홍길동",
    "phone": "+82 10-1234-5678",
    "birthDate": "1990-10-01",
    "nickname": null,
    "region": null,
    "experienceLevel": null,
    "managementType": "UNREGISTERED"
  },
  "onboarding": {
    "status": "REQUIRED",
    "missingFields": ["NICKNAME", "REGION", "EXPERIENCE_LEVEL"]
  }
}
```

## Field

- `accessToken`: string, required.
- `refreshToken`: string, required.
- `member`: MemberProfileResponse, required.
- `onboarding`: OnboardingResponse, required.

소셜 로그인 API에서 사용한다. 로컬 이메일/비밀번호 로그인과 토큰 재발급은 `TokenResponse`를 반환한다.

## Parameter

None

## Header

None

## Query

None