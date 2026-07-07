# OnboardingCompleteResponse

API 분류: API Response
태그: [회원] 유저 정보

## Body

```json
{
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": null,
    "name": "홍길동",
    "phone": "010-1234-5678",
    "birthDate": "1990-10-01",
    "nickname": "sample-grower",
    "region": "Naju",
    "experienceLevel": "BEGINNER",
    "managementType": "UNREGISTERED"
  },
  "onboarding": {
    "status": "COMPLETE",
    "missingFields": []
  }
}
```

## Field

- `member`: MemberProfileResponse, required.
- `onboarding`: OnboardingResponse, required.

온보딩 완료 성공 시 토큰은 재발급하지 않는다.

## Parameter

None

## Header

None

## Query

None