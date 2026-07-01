# GodsMove Agent Guide

This file is the local operating guide for AI coding agents working in this repository.

## Project Structure

The backend is based on a Spring Boot Kotlin multi-module template:

```text
backend
├── api
├── application
├── domain
└── batch
```

Default dependency direction:

```text
api -> application -> domain
batch -> application -> domain
```

Do not introduce reverse dependencies from `domain` to `application` or from `application` to `api`.

## Package Structure

Split packages by feature first, then by technical role under the feature.

```text
api
└── com.godsmove.api
    ├── auth
    │   ├── controller
    │   └── dto
    ├── common
    ├── exception
    └── security

application
└── com.godsmove.application
    ├── auth
    ├── exception
    ├── redis
    ├── security
    └── common

domain
└── com.godsmove.domain
    └── member

batch
└── com.godsmove
```

The imported template used `com.example`; project code should use `com.godsmove`.

## API Addition Flow

When adding an API:

1. Check whether `domain` needs an entity or repository.
2. Add service, command, and result types in `application`.
3. Add the feature package under `api`, then put controller and DTOs below it.
4. Convert request DTOs to application commands in the controller.
5. Call the application service from the controller.

Example:

```text
com.godsmove.api.auth.controller.AuthController
com.godsmove.api.auth.dto.AuthRequests
com.godsmove.api.auth.dto.AuthResponses
```

```kotlin
authService.login(AuthCommand.Login(request.email, request.password))
```

## Commit Message Rules

Use Conventional Commits:

```text
type(scope): title
```

Examples:

```text
feat(auth): add email login API
fix(auth): fix refreshToken reissue cookie handling
docs(readme): update project structure docs
refactor(auth): split token issuing logic
test(auth): add login failure tests
chore(gradle): clean up test configuration
```

Main types:

- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation
- `refactor`: behavior-preserving code change
- `test`: test addition or update
- `chore`: build, configuration, or miscellaneous work

## Branch Strategy

Use a simple branch strategy:

```text
main
dev
feat/feature-name
```

Development flow:

1. Create feature branches from `dev`.
2. Open a PR from the feature branch to `dev`.
3. Merge to `dev` after review.
4. Merge `dev` to `main` for release.
5. Add `(front)` for frontend branches when useful, for example `feat/map(front)`.

Example:

```bash
git checkout dev
git pull origin dev
git checkout -b feat/kakao-login
```

## Exception Handling

Business exceptions are handled through `ErrorCode`, `BusinessException`, and `GlobalExceptionHandler`.

Rules:

1. Add new business failure cases to `ErrorCode.kt`.
2. Throw `BusinessException` from services when business rules are violated.
3. Controllers should not directly handle business exceptions.
4. `GlobalExceptionHandler` converts exceptions to failure `ApiResponse` values.

Example:

```kotlin
if (memberRepository.existsByEmail(command.email)) {
    throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
}
```

Do not:

- Throw standard exceptions like `IllegalArgumentException` for business failures.
- Convert business exceptions to responses with controller `try-catch` blocks.
- Expose raw exception messages or stack traces to clients.

## Authentication Identity Rules

The project domain term is `Member`, not `User`.

Current authenticated member ID is received by controllers through `@AuthenticationPrincipal` and explicitly passed into application services.

Example:

```kotlin
@PostMapping("/logout")
fun logout(@AuthenticationPrincipal memberId: String): ResponseEntity<ApiResponse<Unit>> {
    authService.logout(memberId)
    return ResponseEntity.ok(ApiResponse.empty(Unit))
}
```

Rules:

- Service classes must not depend on controller or servlet types.
- If a service needs the current member ID, pass it through a command or method parameter.
- Before adding a utility for current-member lookup inside services, first check whether explicit parameter passing solves the use case.
- Project-owned identity names should be `memberId`, not `userId`.

## DTO Rules

Rules:

- Separate request DTOs and response DTOs.
- Put DTOs under the feature package, for example `api.<feature>.dto`.
- Put Bean Validation on request DTOs.
- Do not expose entities directly in responses.
- Application services receive commands, not API DTOs.

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

Controller example:

```kotlin
@PostMapping("/login")
fun login(
    @Valid @RequestBody request: AuthRequests.LoginRequest
): ResponseEntity<ApiResponse<AuthResponses.TokenResponse>> {
    val result = authService.login(AuthCommand.Login(request.email, request.password))
    return ResponseEntity.ok(ApiResponse.ok(AuthResponses.TokenResponse.from(result)))
}
```
