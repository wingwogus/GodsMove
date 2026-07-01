🚀 Spring Boot Kotlin Initial Template

A production-grade multi-module Spring Boot 3.x + Kotlin starter template.
This project includes the essential building blocks for real-world backend services such as:

* API Response standardization
* Global exception handling
* JWT authentication
* Module separation (api / application / domain / batch)
* Logging with MDC
* JPA configuration
* Swagger UI
* Environment-specific profiles

You can run this project immediately, then customize the package name and DB settings to fit your service.


🔧 Required Customization Before Use

This template is prepared for public sharing.
If you plan to use it for your own service, you must update the following items.


1️⃣ Change Base Package (com.godsmove → your domain)
All modules (api / application / domain / batch) use package com.godsmove.
Rename it to your organization or project domain.

  IntelliJ shortcut:
Right-click package → Refactor → Rename (Shift + F6)
Imports will update automatically.

2️⃣ Configure Your Own Database
The template uses H2 for easy execution.
Replace it with your actual DB (MySQL/PostgreSQL/etc)

src/main/resources/application-local.yml:
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: your_user
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

3️⃣ Replace JWT Secret Key
A sample secret is included.
Generate a new one:

openssl rand -hex 32

Set it inside your application-local.yml / application-dev.yml.

4️⃣ Update Swagger Info
Modify SwaggerConfig.kt to match your project branding:
.info(
  Info()
    .title("Your API")
    .description("Your project description")
)

5️⃣ Redis (Optional)

Redis dependency is included.
If your service doesn’t use Redis, simply remove:

implementation("org.springframework.boot:spring-boot-starter-data-redis")

6️⃣ Kakao SDK OIDC App Login

This branch is mobile/app-SDK-first. The backend does not expose or rely on
Spring Security `oauth2Login`, `/oauth2/authorization/kakao`, or
`/login/oauth2/code/kakao`.

App flow:
1. Generate a cryptographically random nonce before Kakao login.
2. Call Kakao SDK `loginWithKakaoTalk()` and fall back to
   `loginWithKakaoAccount()`.
3. Request OIDC so Kakao returns an ID token containing the nonce.
4. Call:

POST /api/v1/auth/kakao/login

{
  "idToken": "<kakao_oidc_id_token>",
  "nonce": "<client_generated_nonce>"
}

The backend verifies Kakao JWKS-backed signature, issuer, audience, expiry,
issued-at, and nonce, then stores the nonce with Redis SETNX semantics to
prevent replay.

Required config:

auth:
  kakao:
    oidc:
      issuer: https://kauth.kakao.com
      audience: ${KAKAO_NATIVE_APP_KEY}
      discovery-uri: https://kauth.kakao.com/.well-known/openid-configuration
      allowed-clock-skew-seconds: 60
      nonce-replay-ttl-seconds: 600

Manual schema rollout:
- Change `member.password_hash` to nullable.
- Create `external_identity` with `member_id`, `provider`,
  `provider_subject`, and `email_at_link_time`.
- Add a unique constraint on `(provider, provider_subject)`.

Flyway is intentionally not included in this template branch; downstream apps
should apply the schema change with their own migration strategy.


📐 Coding Convention

Use these rules as the default convention when extending this template.

1️⃣ Module Boundaries

Keep dependencies flowing inward:

api → application → domain
batch → application → domain

- `api` owns HTTP controllers, request/response DTOs, security filters,
  exception handlers, Swagger config, and transport-specific concerns.
  Feature APIs should be grouped by feature package, such as
  `api.auth.controller` and `api.auth.dto`.
- `application` owns use cases, transactions, commands/results, business
  exceptions, ports, token/email/Redis orchestration, and application services.
- `domain` owns entities, domain repositories, enums, and core business concepts.
- `batch` owns scheduled or batch entry points and should call application
  services instead of duplicating business logic.

Do not make `domain` depend on `application` or `api`.
Do not make `application` depend on `api`.

2️⃣ DTO and Mapping Rules

- API request/response DTOs stay under `api.<feature>.dto`.
- Application use-case inputs and outputs stay in `application` as
  `*Command` and `*Result`.
- Controllers may map API DTOs to application commands/results.
- Application services must not accept API request DTOs.
- Do not expose JPA entities directly as API responses.
- Keep Bean Validation annotations on API request DTOs. Put business-rule
  validation in application services.

Example:

```kotlin
authService.login(AuthCommand.Login(request.email, request.password))
```

3️⃣ Controller Rules

Controllers should stay thin:

- Bind request data.
- Run `@Valid` request validation.
- Translate request DTOs into application commands.
- Decide HTTP status, headers, cookies, and `ApiResponse` shape.
- Delegate business work to application services.

Avoid putting business rules, repository access, password encoding, token
generation, or transaction logic in controllers.

4️⃣ Application Service Rules

- Use constructor injection.
- Put transactional use cases in `@Service` classes.
- Accept `*Command` objects for input when a use case has structured input.
- Return `*Result` objects when the caller needs data.
- Throw `BusinessException(ErrorCode.X)` for expected business failures.
- Do not return `ResponseEntity`, `ApiResponse`, servlet types, or API DTOs
  from application services.

5️⃣ Error and Response Rules

- Successful API responses should use `ApiResponse.ok(...)` or
  `ApiResponse.empty(Unit)`.
- Expected failures should use `BusinessException` with an `ErrorCode`.
- Add new error cases to `ErrorCode` instead of hardcoding response bodies.
- Let `GlobalExceptionHandler` translate exceptions into API responses.
- Use stable error codes and message keys; avoid returning raw exception
  messages to clients.

6️⃣ Logging and Security Rules

- Do not log passwords, JWTs, refresh tokens, ID tokens, auth codes, or raw
  secrets.
- Prefer hashed or indirect identifiers in logs, such as `email.hashCode()`.
- Use MDC-provided context (`traceId`, `eventId`, `userId`, `clientIp`) through
  the existing logging utilities.
- Keep cookie/header/security filter behavior in the `api` module.

7️⃣ Kotlin Style

- Prefer `val` over `var`.
- Use `data class` for DTOs, commands, and results.
- Keep names explicit: `SignUpRequest`, `SignUp`, `TokenResponse`,
  `TokenPair`.
- Keep feature code grouped by package, for example `auth`, `member`,
  `security`, `redis`.
- Avoid new dependencies unless the existing modules cannot reasonably solve
  the problem.

8️⃣ Test Rules

- Application service business rules belong in `application/src/test`.
- Controller validation and HTTP contract tests belong in `api/src/test`.
- Security filter and authentication behavior should be covered with API
  integration or MVC tests.
- Add regression tests before refactoring behavior that is not already covered.

Recommended verification:

```bash
./gradlew test
```


▶ How to Run
⭐ Recommended: IntelliJ Run Button
Runs with the application-local profile by default.

⭐ Official Method: Gradle bootRun
./gradlew :api:bootRun
