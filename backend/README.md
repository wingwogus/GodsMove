# GodsMove Backend

GodsMove backend is a Spring Boot 3.x + Kotlin multi-module service.
It provides the API layer, application use cases, domain model, and batch
entry points for the GodsMove service.

## Modules

```text
backend
├── api
├── application
├── domain
└── batch
```

Dependency direction:

```text
api -> application -> domain
batch -> application -> domain
```

- `api` owns HTTP controllers, request/response DTOs, security filters,
  exception handlers, Swagger config, and transport-specific concerns.
- `application` owns use cases, transactions, commands/results, business
  exceptions, ports, token/email/Redis orchestration, and application services.
- `domain` owns entities, domain repositories, enums, and core business concepts.
- `batch` owns scheduled or batch entry points and should call application
  services instead of duplicating business logic.

Do not make `domain` depend on `application` or `api`.
Do not make `application` depend on `api`.

## Package

All project modules use the base package `com.godsmove`.

Feature packages are grouped by feature first, then technical role:

```text
api
└── com.godsmove.api
    ├── auth
    │   ├── controller
    │   └── dto
    ├── common
    ├── exception
    └── security
```

## Run

Run from the `backend` directory.

```bash
./gradlew :api:bootRun
```

The IntelliJ run button is also supported and uses the local profile by default.

## Test

Run the full backend test suite from the `backend` directory.

```bash
./gradlew test
```

Test placement:

- Application service business rules belong in `application/src/test`.
- Controller validation and HTTP contract tests belong in `api/src/test`.
- Security filter and authentication behavior should be covered with API
  integration or MVC tests.
- Add regression tests before refactoring behavior that is not already covered.

## Configuration

Local configuration lives under each module's `src/main/resources` directory.
Keep secrets out of source control and provide them through local profile files
or the deployment environment.

JWT signing keys must be generated per environment. A suitable local secret can
be generated with:

```bash
openssl rand -hex 32
```

Redis is used by authentication flows that require replay protection and token
state. Keep Redis configuration aligned with the active Spring profile.

Swagger/OpenAPI metadata is configured in:

```text
api/src/main/kotlin/com/godsmove/config/SwaggerConfig.kt
```

## Kakao SDK OIDC App Login

The backend supports a mobile/app-SDK-first Kakao OIDC flow. It does not expose
or rely on Spring Security `oauth2Login`, `/oauth2/authorization/kakao`, or
`/login/oauth2/code/kakao`.

App flow:

1. Generate a cryptographically random nonce before Kakao login.
2. Call Kakao SDK `loginWithKakaoTalk()` and fall back to
   `loginWithKakaoAccount()`.
3. Request OIDC so Kakao returns an ID token containing the nonce.
4. Call the backend login endpoint:

```http
POST /api/v1/auth/kakao/login
```

```json
{
  "idToken": "<kakao_oidc_id_token>",
  "nonce": "<client_generated_nonce>"
}
```

The backend verifies Kakao JWKS-backed signature, issuer, audience, expiry,
issued-at, and nonce, then stores the nonce with Redis SETNX semantics to
prevent replay.

Required config:

```yaml
auth:
  kakao:
    oidc:
      issuer: https://kauth.kakao.com
      audience: ${KAKAO_NATIVE_APP_KEY}
      discovery-uri: https://kauth.kakao.com/.well-known/openid-configuration
      allowed-clock-skew-seconds: 60
      nonce-replay-ttl-seconds: 600
```

Schema requirements:

- `member.password_hash` must be nullable for external-login members.
- `external_identity` must store `member_id`, `provider`, `provider_subject`,
  and `email_at_link_time`.
- `(provider, provider_subject)` must be unique.

## Database Schema Strategy

Runtime database environments use PostgreSQL.

The local profile may recreate the schema with `ddl-auto: create` for fast
developer iteration. Dev and prod use `ddl-auto: none`; those environments
require an explicitly prepared schema before the application boots.

This initial backend has no production data migration path yet. Do not point
dev or prod at an existing schema that still has bigint/Long auth identifiers
for `member` or `external_identity` IDs. For empty non-production environments
only, a destructive rebuild may drop and recreate the schema before first boot.
Production must use a reviewed migration plan.

Flyway is not included in this backend. Add Flyway or equivalent migration
tooling before expanding beyond the auth foundation or before preserving real
data across schema changes.

## API Addition Flow

When adding an API:

1. Check whether `domain` needs an entity or repository.
2. Add service, command, and result types in `application`.
3. Add the feature package under `api`, then put controller and DTOs below it.
4. Convert request DTOs to application commands in the controller.
5. Call the application service from the controller.

Example:

```kotlin
authService.login(AuthCommand.Login(request.email, request.password))
```

## DTO and Mapping Rules

- API request/response DTOs stay under `api.<feature>.dto`.
- Application use-case inputs and outputs stay in `application` as
  `*Command` and `*Result`.
- Controllers may map API DTOs to application commands/results.
- Application services must not accept API request DTOs.
- Do not expose JPA entities directly as API responses.
- Keep Bean Validation annotations on API request DTOs. Put business-rule
  validation in application services.

Example request DTO:

```kotlin
object AuthRequests {
    data class LoginRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        val email: String,

        @field:NotBlank(message = "비밀번호를 입력해주세요")
        val password: String
    )
}
```

Example response DTO:

```kotlin
object AuthResponses {
    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String
    ) {
        companion object {
            fun from(result: AuthResult.TokenPair): TokenResponse {
                return TokenResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken
                )
            }
        }
    }
}
```

## Controller Rules

Controllers should stay thin:

- Bind request data.
- Run `@Valid` request validation.
- Translate request DTOs into application commands.
- Decide HTTP status, headers, cookies, and `ApiResponse` shape.
- Delegate business work to application services.

Avoid putting business rules, repository access, password encoding, token
generation, or transaction logic in controllers.

## Application Service Rules

- Use constructor injection.
- Put transactional use cases in `@Service` classes.
- Accept `*Command` objects for input when a use case has structured input.
- Return `*Result` objects when the caller needs data.
- Throw `BusinessException(ErrorCode.X)` for expected business failures.
- Do not return `ResponseEntity`, `ApiResponse`, servlet types, or API DTOs
  from application services.

## Error and Response Rules

- Successful API responses should use `ApiResponse.ok(...)` or
  `ApiResponse.empty(Unit)`.
- Expected failures should use `BusinessException` with an `ErrorCode`.
- Add new error cases to `ErrorCode` instead of hardcoding response bodies.
- Let `GlobalExceptionHandler` translate exceptions into API responses.
- Use stable error codes and message keys; avoid returning raw exception
  messages to clients.

## Logging and Security Rules

- Do not log passwords, JWTs, refresh tokens, ID tokens, auth codes, or raw
  secrets.
- Prefer hashed or indirect identifiers in logs, such as `email.hashCode()`.
- Use MDC-provided context (`traceId`, `eventId`, `memberId`, `clientIp`)
  through the existing logging utilities.
- Keep cookie/header/security filter behavior in the `api` module.

## Kotlin Style

- Prefer `val` over `var`.
- Use `data class` for DTOs, commands, and results.
- Keep names explicit: `SignUpRequest`, `SignUp`, `TokenResponse`,
  `TokenPair`.
- Keep feature code grouped by package, for example `auth`, `member`,
  `security`, `redis`.
- Avoid new dependencies unless the existing modules cannot reasonably solve
  the problem.
