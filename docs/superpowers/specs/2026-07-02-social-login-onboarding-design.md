# Social Login And Onboarding Design

Date: 2026-07-02

## Purpose

Add app-only Kakao, Apple, and Naver social login to the GodsMove backend and
connect it to a one-shot onboarding completion API.

The MVP excludes terms and privacy-policy consent handling. The backend should
create or reuse a `Member` during social login, issue GodsMove tokens, return a
simple onboarding status, and let the app collect any missing profile fields
locally before submitting one final onboarding request.

## Current Context

The backend already has a mobile-first Kakao OIDC login foundation:

- `POST /api/v1/auth/kakao/login`
- `KakaoLoginService`
- `KakaoOidcTokenVerifier`
- `KakaoNonceReplayRepository`
- `ExternalIdentity` with unique `(provider, provider_subject)`
- JWT access and refresh tokens using service `memberId` as subject

The current auth response is token-only. Social login needs to return the token
pair plus member profile and onboarding state.

The existing `Member` has profile fields useful for onboarding:

- `name`
- `phone`
- `nickname`
- `region`
- `experienceLevel`
- `managementType`

It does not yet have `birthDate`, so the implementation needs a nullable
`birth_date` column and `LocalDate?` field if birth date remains part of the
MVP onboarding form.

## Product Decisions

- The client surface is app-only. Do not add web OAuth redirect endpoints for
  this MVP.
- Keep provider-specific login endpoints because provider credentials differ.
- Use one shared login response DTO name: `LoginResponse`.
- Use one onboarding completion endpoint for the final profile submit.
- Do not support backend draft saves. The app owns intermediate onboarding
  state.
- Do not include `missingFields` in API responses.
- Do not include terms consent in MVP onboarding.
- Do not put onboarding status in JWT claims.

## Public API

### Kakao Login

```http
POST /api/v1/auth/kakao/login
```

Request:

```json
{
  "idToken": "<kakao_oidc_id_token>",
  "nonce": "<client_generated_raw_nonce>"
}
```

Backend behavior:

1. Verify Kakao ID token signature, issuer, audience, timestamps, and nonce.
2. Reserve the token nonce in Redis with SETNX semantics to prevent replay.
3. Find `ExternalIdentity(provider=KAKAO, providerSubject=sub)`.
4. If no identity exists, find or create a `Member` by verified provider email.
5. Persist the new `ExternalIdentity` if needed.
6. Prefill `Member` fields only when the provider supplies trusted values.
7. Issue GodsMove access and refresh tokens.
8. Return `LoginResponse`.

### Apple Login

```http
POST /api/v1/auth/apple/login
```

Request:

```json
{
  "identityToken": "<apple_identity_token>",
  "nonce": "<client_generated_raw_nonce>",
  "authorizationCode": "<optional_apple_authorization_code>",
  "userIdentifier": "<optional_apple_user_identifier>"
}
```

Backend behavior:

1. Verify Apple ID token signature with Apple JWKS.
2. Validate issuer `https://appleid.apple.com`, audience, timestamps, subject,
   and nonce.
3. Hash the raw nonce with SHA-256 hex and compare it to the token `nonce`
   claim.
4. If `userIdentifier` is present, require it to match the token subject.
5. Reserve the hashed nonce in Redis to prevent replay.
6. Find or create `ExternalIdentity(provider=APPLE, providerSubject=sub)`.
7. Find or create `Member` by verified Apple email when a new identity is
   linked.
8. Do not prefill profile fields from Apple in this MVP. Apple does not provide
   phone or birth date through Sign in with Apple, and Apple name is client-side
   first-login data rather than an ID-token claim.
9. Issue tokens and return `LoginResponse`.

This follows the NuguSauce pattern but omits NuguSauce-only refresh-token
storage and account deletion behavior.

### Naver Login

```http
POST /api/v1/auth/naver/login
```

Request:

```json
{
  "accessToken": "<naver_access_token>"
}
```

Backend behavior:

1. Call Naver profile API `GET https://openapi.naver.com/v1/nid/me` with
   `Authorization: Bearer <accessToken>`.
2. Require success result and `response.id`.
3. Store `response.id` as `ExternalIdentity.providerSubject`.
4. Use Naver `email` to find or create `Member` when a new identity is linked.
5. Prefill available profile fields:
   - `name` from `response.name`
   - `phone` from `response.mobile`
   - `birthDate` from `response.birthyear` + `response.birthday` when both are
     present and parseable
6. Issue tokens and return `LoginResponse`.

Naver provider data is for account setup convenience, not identity-proof for
regulated real-name verification. If GodsMove later needs legally reliable
identity, add a separate identity-verification provider.

### Login Response

All provider login endpoints return the same top-level DTO:

```json
{
  "accessToken": "<godsmove_access_token>",
  "refreshToken": "<godsmove_refresh_token>",
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "member@example.com",
    "name": "홍길동",
    "phone": "010-1234-5678",
    "birthDate": "1990-10-01",
    "nickname": null,
    "region": null,
    "experienceLevel": null,
    "managementType": "UNREGISTERED"
  },
  "onboarding": {
    "status": "REQUIRED"
  }
}
```

DTO shape:

```kotlin
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val member: MemberProfileResponse,
    val onboarding: OnboardingResponse
)
```

`MemberProfileResponse` and `OnboardingResponse` are shared with onboarding
responses. The top-level login response remains separate because it includes
tokens.

### Complete Onboarding

```http
POST /api/v1/onboarding/complete
Authorization: Bearer <accessToken>
```

Request:

```json
{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "birthDate": "1990-10-01",
  "nickname": "농부길동",
  "region": "전라남도 나주시",
  "experienceLevel": "BEGINNER"
}
```

Response:

```json
{
  "member": {
    "id": "00000000-0000-0000-0000-000000000000",
    "email": "member@example.com",
    "name": "홍길동",
    "phone": "010-1234-5678",
    "birthDate": "1990-10-01",
    "nickname": "농부길동",
    "region": "전라남도 나주시",
    "experienceLevel": "BEGINNER",
    "managementType": "UNREGISTERED"
  },
  "onboarding": {
    "status": "COMPLETE"
  }
}
```

DTO shape:

```kotlin
data class OnboardingCompleteResponse(
    val member: MemberProfileResponse,
    val onboarding: OnboardingResponse
)
```

The completion endpoint validates the final required profile fields and updates
the authenticated `Member`. It does not issue new tokens because onboarding
status is not stored in JWT claims.

## Onboarding Status Calculation

Do not add `onboardingCompleted` for the MVP. Calculate status from current
member data:

```text
COMPLETE when:
- name is not blank
- phone is not blank
- birthDate is not null
- nickname is not blank
- region is not blank
- experienceLevel is not blank
```

Otherwise return `REQUIRED`.

`managementType` is not part of MVP completion unless the product decides that
farm registration or management-type selection must block first use. It can
stay `UNREGISTERED` after onboarding completion.

## Access Control

For MVP, app routing can rely on the login response and the complete-onboarding
response. Backend enforcement should be added only where needed:

- Always allow auth endpoints.
- Always allow `POST /api/v1/onboarding/complete`.
- Allow token reissue and logout for authenticated or refresh-token flows.
- For features that require a completed profile, check onboarding status in the
  application service or a focused request guard and reject incomplete members.

If a generic guard is introduced, prefer HTTP 428 Precondition Required for
authenticated members whose profile onboarding is incomplete.

## Provider Data Rules

Kakao:

- Keep the existing ID-token plus raw nonce app flow.
- Continue to require verified email for first link.
- Additional profile fields may be absent unless the Kakao app has the required
  business/permission configuration.

Apple:

- Use the ID token as the server authority.
- Compare SHA-256 raw nonce to token nonce.
- Apple can provide email in the ID token, but not phone or birth date.
- Apple name is normally first-login-only client data. Keep it out of the MVP
  backend login request unless a later app design explicitly needs it as
  editable prefill.

Naver:

- Use the app SDK access token and backend profile lookup.
- Use `response.id`, not the Naver account ID, as provider subject.
- Prefill name, phone, and birth date when available.
- If profile permission is missing or the user declines fields, onboarding
  completion collects the values directly.

## Error Handling

Add provider-specific auth errors while preserving the existing `ApiResponse`
envelope:

- `INVALID_APPLE_TOKEN`
- `APPLE_NONCE_MISMATCH`
- `APPLE_NONCE_REPLAY`
- `APPLE_VERIFIED_EMAIL_REQUIRED`
- `INVALID_NAVER_TOKEN`
- `NAVER_PROFILE_UNAVAILABLE`
- `NAVER_EMAIL_REQUIRED`

Validation failures in onboarding use existing request validation handling.

## Testing

Minimum tests:

- Apple token verifier accepts valid token and rejects malformed token,
  issuer/audience/timestamp mismatch, nonce mismatch, and missing subject.
- Apple login service reuses existing identity, links/creates by verified
  email, rejects replayed nonce, and rejects mismatched `userIdentifier`.
- Naver profile client maps success response and provider failures.
- Naver login service reuses existing identity, links/creates by email,
  prefills available fields, and rejects missing provider subject or email.
- Auth controller exposes Kakao, Apple, and Naver login endpoints and maps
  all three to `LoginResponse`.
- Onboarding service updates the authenticated member and returns `COMPLETE`.
- Onboarding completion rejects blank required fields and invalid birth date.
- Onboarding status calculation returns `REQUIRED` before profile completion
  and `COMPLETE` after required fields are present.
- Security config keeps social login endpoints public and protects onboarding
  completion with JWT.

## Out Of Scope

- Terms and privacy-policy consent.
- Backend onboarding draft saves.
- Web OAuth callback endpoints.
- Provider account unlinking.
- Apple refresh-token exchange, encryption, storage, and revocation.
- Legal identity verification.
- Farm registration as part of onboarding completion.
