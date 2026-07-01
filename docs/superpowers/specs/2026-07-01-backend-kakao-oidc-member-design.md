# Backend Kakao OIDC Member Foundation Design

Date: 2026-07-01

## Purpose

Initialize the `backend` folder from `/Users/wingwogus/IdeaProjects/springboot-kotlin-initial-template` while adapting the authentication and domain language to this project.

The imported backend should keep the template's Spring Boot Kotlin multi-module foundation, Kakao OIDC app-login flow, JWT authentication, Redis-backed refresh tokens, exception handling, and test structure. The project-specific domain language is `Member`, not `User`, and service member identifiers are UUIDs stored in PostgreSQL.

## Source Context

Current workspace:

- `/Users/wingwogus/Projects/GodsMove/backend` is empty.
- `/Users/wingwogus/Projects/GodsMove/frontend` is empty.
- The workspace is not currently a git repository.
- The provided ERD is user-centered and uses plural table names. This design replaces those terms with member-centered, singular table naming.

Template source:

- `/Users/wingwogus/IdeaProjects/springboot-kotlin-initial-template`
- Modules: `api`, `application`, `domain`, `batch`
- Existing template domain already has `Member`, `ExternalIdentity`, Kakao OIDC verifier, token provider, refresh token repository, MDC logging filter, and auth controller.
- Remaining template names such as `userId` must be converted to `memberId`.

## Architecture

Use the template's module layout:

- `api`: HTTP controllers, request/response DTOs, Spring Security filter chain, JWT filter, MDC logging filter, global exception handler, Swagger config.
- `application`: auth use cases, Kakao OIDC token verification, JWT token provider, Redis repository interfaces and implementations, application exceptions.
- `domain`: JPA entities and repositories.
- `batch`: imported as a skeleton for later scheduled or batch work.

The root package must be changed from `com.example` to the project package selected during implementation. Until a package name is explicitly chosen, use `com.godsmove` as the default.

## Domain Language

`Member` is the canonical service account aggregate. The word `User` should not appear in project-owned domain classes, API fields, JWT helper names, Redis key APIs, MDC keys, or ERD-derived table/column names when it refers to a service account.

Rename project-owned identifiers:

- `User` -> `Member`
- `userId` -> `memberId`
- `getUserId()` -> `getMemberId()`
- `USER_NOT_FOUND` -> `MEMBER_NOT_FOUND`
- `userHash` log labels -> `memberHash`
- MDC key `userId` -> `memberId`

Do not rename external or framework terms:

- Spring Security types such as `UsernamePasswordAuthenticationToken`.
- OAuth/OIDC concepts such as `userinfo`.
- Kakao provider fields and documentation language.
- Library-owned names that do not represent this service's member identity.

## Identifiers

All service member IDs are UUIDs.

- `Member.id`: `java.util.UUID`
- PostgreSQL column type: `uuid`
- JWT subject: `memberId.toString()`
- JWT parsing: `UUID.fromString(subject)`
- Redis refresh token key input: `memberId: UUID`
- Controller principal variable: `memberId`
- ERD foreign keys: `member_id`, `owner_member_id`, `author_member_id`

Kakao identity must stay separate from service member identity:

- Kakao OIDC `sub` is stored as `ExternalIdentity.providerSubject`.
- Service identity is stored as `Member.id`.
- The two values must never be treated as interchangeable.

## Database

Use PostgreSQL, not MySQL.

Dependency changes:

- Remove `com.mysql:mysql-connector-j`.
- Add PostgreSQL JDBC driver.

JPA and schema choices:

- Use Hibernate 6 UUID generation, preferably `@GeneratedValue(strategy = GenerationType.UUID)`.
- Add explicit `@Table(...)` to entities so table naming is intentional and stable.
- Local profile may use schema auto-creation during early setup.
- Development and production profiles must not rely on destructive schema generation.
- Before substantial domain tables are added, introduce Flyway or another migration tool instead of relying on `ddl-auto`.

## Table Naming

Use singular table names. Entity names and table names should match conceptually.

Initial auth tables:

| Entity | Table |
| --- | --- |
| `Member` | `member` |
| `ExternalIdentity` | `external_identity` |

ERD-derived tables:

| Domain Concept | Table |
| --- | --- |
| Farm | `farm` |
| Crop | `crop` |
| MemberCrop | `member_crop` |
| PolicyProgram | `policy_program` |
| PolicyRecommendation | `policy_recommendation` |
| FarmingRecord | `farming_record` |
| WorkType | `work_type` |
| WorkTypeField | `work_type_field` |
| FarmingRecordFieldValue | `farming_record_field_value` |
| RecordMedia | `record_media` |
| VoiceRecordSession | `voice_record_session` |
| VoiceRecordTurn | `voice_record_turn` |
| CoachingFeedback | `coaching_feedback` |
| CommunityPost | `community_post` |
| CommunityComment | `community_comment` |
| NotificationPreference | `notification_preference` |
| LegalDocument | `legal_document` |
| MemberConsent | `member_consent` |

Column naming:

- `member_id` for direct member foreign keys.
- `owner_member_id` for owner relationships.
- `author_member_id` for authored content.
- Existing non-member foreign keys keep domain-specific names such as `farm_id`, `crop_id`, `record_id`, and `policy_program_id`.

## Work Type Modeling

Keep `work_type` as a table instead of an enum.

Rationale:

- Work types may grow after launch.
- Each work type can define different fields through `work_type_field`.
- The app can render record-entry forms dynamically from backend data.
- Voice parsing and AI extraction can use the same field definitions as the UI.

The table-based design has more initial complexity than an enum, but it protects the expected future need for configurable farming record fields.

## Kakao OIDC Flow

Client flow:

1. Generate a nonce before Kakao login.
2. Use Kakao SDK app login.
3. Request OIDC so Kakao returns an ID token containing the nonce.
4. Send `idToken` and `nonce` to the backend.

Backend endpoint:

```http
POST /api/v1/auth/kakao/login
Content-Type: application/json

{
  "idToken": "<kakao_oidc_id_token>",
  "nonce": "<nonce_sent_to_kakao>"
}
```

Backend handling:

1. Verify the ID token signature using Kakao discovery/JWKS.
2. Verify issuer, audience, expiry, and nonce.
3. Reserve nonce in Redis to prevent replay.
4. Find `ExternalIdentity` by provider `KAKAO` and Kakao `providerSubject`.
5. If found, use the linked `Member`.
6. If not found, require a verified Kakao email.
7. Find an existing `Member` by email or create a new one.
8. Save a new `ExternalIdentity`.
9. Issue access and refresh tokens with the service `memberId` as JWT subject.
10. Store the refresh token by `memberId` in Redis.

## Auth Token Design

JWT access and refresh tokens continue to use the template's shape:

- Subject: service `memberId` as UUID string.
- Claims: role and token type.
- Access token: for protected API calls.
- Refresh token: stored in Redis and returned to clients according to the existing controller behavior.

Required API changes:

- `TokenProvider.createAccessToken(memberId: UUID, role: String)`
- `TokenProvider.createRefreshToken(memberId: UUID)`
- `TokenProvider.generateToken(memberId: UUID, role: String)`
- `TokenProvider.getMemberId(token: String): UUID`
- `RefreshTokenRepository.save(memberId: UUID, refreshToken: String, expiresInSeconds: Long)`
- `RefreshTokenRepository.get(memberId: UUID): String?`
- `RefreshTokenRepository.delete(memberId: UUID)`

## Logging

MDC logging should use member terminology:

- MDC key: `memberId`
- Guest value: `GUEST`
- Log pattern segment: `[memberId=%X{memberId}]`
- Request logs should refresh the member ID after authentication has run.

The previous `userId` MDC key must not remain in application logs, tests, or documentation except when quoting old template behavior.

## Error Handling

Keep the template's structured `ErrorCode` and `BusinessException` design.

Auth-related error names should use member terminology where they refer to service accounts:

- `MEMBER_NOT_FOUND`
- `DUPLICATE_EMAIL`
- `UNAUTHORIZED`
- `ALREADY_LOGGED_OUT`
- `SOCIAL_ONLY_MEMBER_LOCAL_LOGIN_FORBIDDEN`
- `INVALID_KAKAO_TOKEN`
- `KAKAO_NONCE_MISMATCH`
- `KAKAO_NONCE_REPLAY`
- `KAKAO_VERIFIED_EMAIL_REQUIRED`

Kakao-specific error names remain Kakao-specific.

## Testing

Minimum verification after implementation:

- `TokenProviderTest`: UUID member ID token creation, parsing, access/refresh token type checks.
- `AuthServiceTest`: signup, login, reissue, logout using UUID member IDs.
- `KakaoLoginServiceTest`: existing external identity login, new member creation, existing email linking, missing verified email failure, nonce replay failure.
- `AuthControllerValidationTest`: validation errors still map cleanly.
- `AuthControllerBusinessTest`: Kakao login and auth errors map to expected HTTP responses.
- `AuthSecurityIntegrationTest`: public auth endpoints remain public, protected endpoints require JWT.
- `MDCLoggingFilterTest`: `memberId` is set, logged, and cleared.
- PostgreSQL configuration should be verified with either local PostgreSQL or Testcontainers. If Testcontainers is not added in the first pass, document that integration-level database verification remains a follow-up.

## Migration From ERD Terms

The provided ERD is the domain source but not the final naming source. During implementation, apply these naming rules:

- Convert account-related `user` names to `member`.
- Convert table names to singular snake case.
- Preserve non-account domain names.
- Keep provider-specific identity in `external_identity`.

Examples:

- `users.id` -> `member.id`
- `farms.owner_user_id` -> `farm.owner_member_id`
- `user_crops.user_id` -> `member_crop.member_id`
- `farming_records.user_id` -> `farming_record.member_id`
- `community_posts.author_user_id` -> `community_post.author_member_id`
- `user_consents.user_id` -> `member_consent.member_id`

## Implementation Guardrails

- Do not perform blind global replacement from `user` to `member`.
- Replace project-owned identifiers intentionally and compile after each broad rename pass.
- Keep Kakao `providerSubject` separate from service `memberId`.
- Keep table naming singular from the first committed backend schema.
- Keep PostgreSQL and UUID decisions aligned across entity IDs, FK columns, JWT subject parsing, Redis keys, and tests.
- Do not add new dependencies beyond PostgreSQL unless the implementation step explicitly chooses a migration/test strategy.

## Acceptance Criteria

The design is satisfied when:

- Backend template files are copied into `backend`.
- The project builds with PostgreSQL dependencies.
- `Member.id` and all service member IDs use UUID.
- No project-owned auth/domain code uses `userId` for service account identity.
- Auth logs use `memberId`.
- Initial table names are singular.
- Kakao OIDC login issues tokens whose subject is the service `memberId`.
- Tests pass or any skipped verification is clearly documented with the reason.
