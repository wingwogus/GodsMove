<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# LoginResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
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
    "experienceLevel": null,
    "managementType": null,
    "profileImageUrl": null
  },
  "onboarding": {
    "status": "REQUIRED",
    "missingFields": ["NICKNAME", "EXPERIENCE_LEVEL", "MANAGEMENT_TYPE"]
  }
}
```
### Field
- `accessToken`: string, required.
- `refreshToken`: string, required.
- `member`: MemberProfileResponse, required.
- `onboarding`: OnboardingResponse, required.
소셜 로그인 API에서 사용한다. 로컬 이메일/비밀번호 로그인과 토큰 재발급은 `TokenResponse`를 반환한다.
### Parameter
None
### Header
None
### Query
None
