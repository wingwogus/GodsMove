# Social Login Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement app-only Kakao, Apple, and Naver login with a shared `LoginResponse` and one final onboarding completion API.

**Architecture:** Keep provider-specific login endpoints because each app SDK returns different credentials. Application services return provider-neutral auth results, while API DTOs map those results into `LoginResponse`, `MemberProfileResponse`, `OnboardingResponse`, and `OnboardingCompleteResponse`. Naver profile lookup follows the existing port/adapter pattern used by `EmailSender`: application owns the interface and the `api` module owns the HTTP adapter.

**Tech Stack:** Spring Boot 3.x, Kotlin, JPA/Hibernate UUID entities, Spring Security JWT filters, Redis nonce replay storage, Nimbus JWT decoder for OIDC, Spring MVC `RestClient` in the `api` module, JUnit 5, Mockito, MockMvc.

---

## Spec And Branch Context

Spec: `docs/superpowers/specs/2026-07-02-social-login-onboarding-design.md`

Worktree: `/private/tmp/ChamChamCham-social-login`

Branch: `feat/social-login-onboarding`

Run commands from `backend/` unless noted.

Use Korean Conventional Commit messages with Lore trailers. Example:

```bash
git commit -m "feat(auth): 온보딩 상태 응답 추가" \
  -m "소셜 로그인 이후 앱이 바로 온보딩 라우팅을 결정할 수 있도록 회원 프로필과 온보딩 상태를 인증 결과에 포함한다." \
  -m "Constraint: JWT에는 온보딩 상태를 넣지 않음" \
  -m "Confidence: high" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :application:test --tests '*Onboarding*'"
```

## File Map

Domain:

- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/AuthProvider.kt`
  - Add `APPLE` and `NAVER`.
- Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
  - Add nullable `birthDate: LocalDate?`.
  - Make onboarding profile fields mutable through a focused `completeOnboarding(...)` method.

Application auth:

- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthCommand.kt`
  - Add `AppleLogin`, `NaverLogin`, and `CompleteOnboarding`.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthResult.kt`
  - Add `Login`, `MemberProfile`, `Onboarding`, `OnboardingStatus`, and `OnboardingComplete`.
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt`
  - Return `AuthResult.Login` instead of token-only `TokenPair`.
  - Use shared social-login member/token support.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/SocialLoginSupport.kt`
  - Link or create provider identities and issue token-bearing login results.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolver.kt`
  - Calculate `REQUIRED` or `COMPLETE` from current `Member` fields.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingService.kt`
  - Complete onboarding for authenticated members.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AppleLoginService.kt`
  - Verify Apple credential, link/create member, issue login response.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverLoginService.kt`
  - Fetch Naver profile through application port, link/create member, issue login response.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfileClient.kt`
  - Application port for Naver profile lookup.
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfile.kt`
  - Provider-neutral profile data returned by the port.

Application security and Redis:

- Create `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcClaims.kt`
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifier.kt`
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRepository.kt`
- Create `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepository.kt`
- Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
  - Add Apple and Naver auth error codes.

API:

- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
  - Add `AppleLoginRequest`, `NaverLoginRequest`, and `CompleteOnboardingRequest`.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
  - Add `LoginResponse`, `MemberProfileResponse`, `OnboardingResponse`, and `OnboardingCompleteResponse`.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
  - Inject Apple/Naver login services.
  - Return `LoginResponse` from social login endpoints.
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Create `backend/api/src/main/kotlin/com/chamchamcham/api/naver/NaverProfileRestClient.kt`
  - HTTP adapter using `RestClient.Builder`.
- Modify `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`
  - Publicly allow Apple/Naver login endpoints.
  - Keep onboarding completion protected.
- Modify `backend/api/src/main/resources/application-local.yml`
- Modify `backend/api/src/main/resources/application-dev.yml`
- Modify `backend/api/src/main/resources/application-prod.yml`
- Modify `backend/api/src/test/resources/application-test.yml`
  - Add Apple and Naver auth config placeholders.

Tests:

- Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolverTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingServiceTest.kt`
- Modify `backend/application/src/test/kotlin/com/chamchamcham/application/auth/KakaoLoginServiceTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifierTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepositoryTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/AppleLoginServiceTest.kt`
- Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/NaverLoginServiceTest.kt`
- Create `backend/api/src/test/kotlin/com/chamchamcham/api/naver/NaverProfileRestClientTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt`
- Create `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Modify `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`

---

### Task 1: Domain And Auth Result Foundation

**Files:**
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/AuthProvider.kt`
- Modify: `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthCommand.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthResult.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolver.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolverTest.kt`

- [ ] **Step 1: Write the failing onboarding status tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolverTest.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.domain.member.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class OnboardingStatusResolverTest {
    private val resolver = OnboardingStatusResolver()

    @Test
    fun `status is complete when required profile fields are present`() {
        val member = completeMember()

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.status)
    }

    @Test
    fun `status is required when birth date is missing`() {
        val member = completeMember(birthDate = null)

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
    }

    @Test
    fun `status is required when nickname is blank`() {
        val member = completeMember(nickname = " ")

        val result = resolver.resolve(member)

        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.status)
    }

    private fun completeMember(
        birthDate: LocalDate? = LocalDate.of(1990, 10, 1),
        nickname: String? = "농부길동"
    ): Member {
        return Member(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            email = "member@example.com",
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = birthDate,
            nickname = nickname,
            region = "전라남도 나주시",
            experienceLevel = "BEGINNER",
            passwordHash = null
        )
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.OnboardingStatusResolverTest'
```

Expected: FAIL because `birthDate`, `AuthResult.OnboardingStatus`, and `OnboardingStatusResolver` do not exist.

- [ ] **Step 3: Extend the domain model and auth result types**

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/AuthProvider.kt`:

```kotlin
package com.chamchamcham.domain.member

enum class AuthProvider {
    KAKAO,
    APPLE,
    NAVER
}
```

Modify `backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt`:

```kotlin
package com.chamchamcham.domain.member

import com.chamchamcham.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(length = 32)
    var phone: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val status: MemberStatus = MemberStatus.ACTIVE,

    @Column(length = 64)
    var name: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(length = 64)
    var nickname: String? = null,

    @Column(length = 128)
    var region: String? = null,

    @Column(name = "experience_level", length = 32)
    var experienceLevel: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "management_type", nullable = false, length = 32)
    val managementType: ManagementType = ManagementType.UNREGISTERED,

    @Column(nullable = true)
    val passwordHash: String?,

    @Column(nullable = false)
    val role: String = "ROLE_USER",

    @Column(name = "withdrawn_at")
    val withdrawnAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    fun completeOnboarding(
        name: String,
        phone: String,
        birthDate: LocalDate,
        nickname: String,
        region: String,
        experienceLevel: String
    ) {
        this.name = name
        this.phone = phone
        this.birthDate = birthDate
        this.nickname = nickname
        this.region = region
        this.experienceLevel = experienceLevel
    }

    fun prefillProfile(
        name: String? = null,
        phone: String? = null,
        birthDate: LocalDate? = null
    ) {
        if (this.name.isNullOrBlank() && !name.isNullOrBlank()) {
            this.name = name
        }
        if (this.phone.isNullOrBlank() && !phone.isNullOrBlank()) {
            this.phone = phone
        }
        if (this.birthDate == null && birthDate != null) {
            this.birthDate = birthDate
        }
    }
}
```

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthCommand.kt`:

```kotlin
package com.chamchamcham.application.auth

import java.time.LocalDate

object AuthCommand {
    data class SendVerificationCode(
        val email: String
    )

    data class VerifyEmailCode(
        val email: String,
        val code: String
    )

    data class SignUp(
        val email: String,
        val password: String
    )

    data class Login(
        val email: String,
        val password: String
    )

    data class KakaoLogin(
        val idToken: String,
        val nonce: String
    )

    data class AppleLogin(
        val identityToken: String,
        val nonce: String,
        val authorizationCode: String?,
        val userIdentifier: String?
    )

    data class NaverLogin(
        val accessToken: String
    )

    data class CompleteOnboarding(
        val memberId: String,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String,
        val region: String,
        val experienceLevel: String
    )

    data class Reissue(
        val refreshToken: String
    )
}
```

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthResult.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import java.time.LocalDate
import java.util.UUID

object AuthResult {
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )

    data class Login(
        val accessToken: String,
        val refreshToken: String,
        val member: MemberProfile,
        val onboarding: Onboarding
    )

    data class OnboardingComplete(
        val member: MemberProfile,
        val onboarding: Onboarding
    )

    data class MemberProfile(
        val id: UUID,
        val email: String,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val region: String?,
        val experienceLevel: String?,
        val managementType: ManagementType
    ) {
        companion object {
            fun from(member: Member): MemberProfile {
                return MemberProfile(
                    id = member.id ?: throw IllegalStateException("Persisted member id is required"),
                    email = member.email,
                    name = member.name,
                    phone = member.phone,
                    birthDate = member.birthDate,
                    nickname = member.nickname,
                    region = member.region,
                    experienceLevel = member.experienceLevel,
                    managementType = member.managementType
                )
            }
        }
    }

    data class Onboarding(
        val status: OnboardingStatus
    )

    enum class OnboardingStatus {
        REQUIRED,
        COMPLETE
    }
}
```

- [ ] **Step 4: Add the onboarding status resolver**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolver.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.domain.member.Member
import org.springframework.stereotype.Component

@Component
class OnboardingStatusResolver {
    fun resolve(member: Member): AuthResult.Onboarding {
        val complete = !member.name.isNullOrBlank() &&
            !member.phone.isNullOrBlank() &&
            member.birthDate != null &&
            !member.nickname.isNullOrBlank() &&
            !member.region.isNullOrBlank() &&
            !member.experienceLevel.isNullOrBlank()

        return AuthResult.Onboarding(
            status = if (complete) {
                AuthResult.OnboardingStatus.COMPLETE
            } else {
                AuthResult.OnboardingStatus.REQUIRED
            }
        )
    }
}
```

- [ ] **Step 5: Run the status tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.OnboardingStatusResolverTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add backend/domain/src/main/kotlin/com/chamchamcham/domain/member/AuthProvider.kt \
  backend/domain/src/main/kotlin/com/chamchamcham/domain/member/Member.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthCommand.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/AuthResult.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolver.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingStatusResolverTest.kt
git commit -m "feat(auth): 온보딩 상태 계산 추가"
```

---

### Task 2: Onboarding Completion Service

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingServiceTest.kt`

- [ ] **Step 1: Write the failing service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingServiceTest.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OnboardingServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Mock
    private lateinit var memberRepository: MemberRepository

    private lateinit var service: OnboardingService

    @BeforeEach
    fun setUp() {
        service = OnboardingService(memberRepository, OnboardingStatusResolver())
    }

    @Test
    fun `complete stores profile fields and returns complete status`() {
        val member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))

        val result = service.complete(
            AuthCommand.CompleteOnboarding(
                memberId = memberId.toString(),
                name = "홍길동",
                phone = "010-1234-5678",
                birthDate = LocalDate.of(1990, 10, 1),
                nickname = "농부길동",
                region = "전라남도 나주시",
                experienceLevel = "BEGINNER"
            )
        )

        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 10, 1), member.birthDate)
        assertEquals("농부길동", member.nickname)
        assertEquals("전라남도 나주시", member.region)
        assertEquals("BEGINNER", member.experienceLevel)
        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.onboarding.status)
        assertEquals(memberId, result.member.id)
        verify(memberRepository).findById(memberId)
    }

    @Test
    fun `complete rejects malformed principal member id`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.complete(
                AuthCommand.CompleteOnboarding(
                    memberId = "not-a-uuid",
                    name = "홍길동",
                    phone = "010-1234-5678",
                    birthDate = LocalDate.of(1990, 10, 1),
                    nickname = "농부길동",
                    region = "전라남도 나주시",
                    experienceLevel = "BEGINNER"
                )
            )
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
    }

    @Test
    fun `complete rejects missing member`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.complete(
                AuthCommand.CompleteOnboarding(
                    memberId = memberId.toString(),
                    name = "홍길동",
                    phone = "010-1234-5678",
                    birthDate = LocalDate.of(1990, 10, 1),
                    nickname = "농부길동",
                    region = "전라남도 나주시",
                    experienceLevel = "BEGINNER"
                )
            )
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }
}
```

- [ ] **Step 2: Run the failing service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.OnboardingServiceTest'
```

Expected: FAIL because `OnboardingService` does not exist.

- [ ] **Step 3: Implement onboarding completion**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingService.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class OnboardingService(
    private val memberRepository: MemberRepository,
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun complete(command: AuthCommand.CompleteOnboarding): AuthResult.OnboardingComplete {
        val memberId = parseMemberId(command.memberId)
        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

        member.completeOnboarding(
            name = command.name,
            phone = command.phone,
            birthDate = command.birthDate,
            nickname = command.nickname,
            region = command.region,
            experienceLevel = command.experienceLevel
        )

        return AuthResult.OnboardingComplete(
            member = AuthResult.MemberProfile.from(member),
            onboarding = onboardingStatusResolver.resolve(member)
        )
    }

    private fun parseMemberId(memberId: String): UUID {
        return try {
            UUID.fromString(memberId)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
```

- [ ] **Step 4: Run the service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.OnboardingServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/auth/OnboardingService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/auth/OnboardingServiceTest.kt
git commit -m "feat(auth): 온보딩 완료 서비스 추가"
```

---

### Task 3: Shared Social Login Support And Kakao LoginResponse

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/SocialLoginSupport.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt`
- Modify: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/KakaoLoginServiceTest.kt`

- [ ] **Step 1: Update Kakao tests to expect `AuthResult.Login`**

In `backend/application/src/test/kotlin/com/chamchamcham/application/auth/KakaoLoginServiceTest.kt`, add a mock:

```kotlin
private lateinit var onboardingStatusResolver: OnboardingStatusResolver
```

Initialize it in `setUp()`:

```kotlin
onboardingStatusResolver = OnboardingStatusResolver()
val socialLoginSupport = SocialLoginSupport(
    externalIdentityRepository,
    memberRepository,
    tokenProvider,
    refreshTokenRepository,
    onboardingStatusResolver
)
service = KakaoLoginService(
    kakaoOidcTokenVerifier,
    nonceRepository,
    socialLoginSupport,
    600L,
    60L
)
```

Change the existing assertion in `login reuses existing external identity`:

```kotlin
val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

assertEquals("access-token", result.accessToken)
assertEquals("refresh-token", result.refreshToken)
assertEquals(memberId, result.member.id)
assertEquals("user@example.com", result.member.email)
assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.onboarding.status)
```

- [ ] **Step 2: Run the updated Kakao test**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.KakaoLoginServiceTest'
```

Expected: FAIL because `SocialLoginSupport` does not exist and `KakaoLoginService` still returns `TokenPair`.

- [ ] **Step 3: Implement shared social login support**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/SocialLoginSupport.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.RefreshTokenRepository
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.AuthProvider
import com.chamchamcham.domain.member.ExternalIdentity
import com.chamchamcham.domain.member.ExternalIdentityRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

@Component
class SocialLoginSupport(
    private val externalIdentityRepository: ExternalIdentityRepository,
    private val memberRepository: MemberRepository,
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun login(
        provider: AuthProvider,
        providerSubject: String,
        email: String?,
        emailRequiredErrorCode: ErrorCode,
        name: String? = null,
        phone: String? = null,
        birthDate: LocalDate? = null
    ): AuthResult.Login {
        val member = externalIdentityRepository
            .findByProviderAndProviderSubject(provider, providerSubject)
            ?.member
            ?: linkOrCreateMember(
                provider = provider,
                providerSubject = providerSubject,
                email = email?.takeIf { it.isNotBlank() }
                    ?: throw BusinessException(emailRequiredErrorCode),
                name = name,
                phone = phone,
                birthDate = birthDate
            )

        member.prefillProfile(name = name, phone = phone, birthDate = birthDate)

        return issueAndStoreTokens(member)
    }

    private fun linkOrCreateMember(
        provider: AuthProvider,
        providerSubject: String,
        email: String,
        name: String?,
        phone: String?,
        birthDate: LocalDate?
    ): Member {
        val member = memberRepository.findByEmail(email)
            ?: memberRepository.save(
                Member(
                    email = email,
                    name = name,
                    phone = phone,
                    birthDate = birthDate,
                    passwordHash = null
                )
            )

        externalIdentityRepository.save(
            ExternalIdentity(
                member = member,
                provider = provider,
                providerSubject = providerSubject,
                emailAtLinkTime = email
            )
        )

        return member
    }

    private fun issueAndStoreTokens(member: Member): AuthResult.Login {
        val memberId = requirePersistedMemberId(member)
        val tokenPair = tokenProvider.generateToken(memberId, member.role)
        refreshTokenRepository.save(
            memberId,
            tokenPair.refreshToken,
            tokenProvider.getRefreshTokenValiditySeconds()
        )
        return AuthResult.Login(
            accessToken = tokenPair.accessToken,
            refreshToken = tokenPair.refreshToken,
            member = AuthResult.MemberProfile.from(member),
            onboarding = onboardingStatusResolver.resolve(member)
        )
    }

    private fun requirePersistedMemberId(member: Member): UUID {
        return member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
    }
}
```

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt` constructor and `login`:

```kotlin
class KakaoLoginService(
    private val kakaoOidcTokenVerifier: KakaoOidcTokenVerifier,
    private val kakaoNonceReplayRepository: KakaoNonceReplayRepository,
    private val socialLoginSupport: SocialLoginSupport,
    @Value("\${auth.kakao.oidc.nonce-replay-ttl-seconds:600}")
    nonceReplayTtlSeconds: Long,
    @Value("\${auth.kakao.oidc.allowed-clock-skew-seconds:60}")
    allowedClockSkewSeconds: Long
) {
    fun login(command: AuthCommand.KakaoLogin): AuthResult.Login {
        val claims = kakaoOidcTokenVerifier.verify(command.idToken, command.nonce)
        reserveNonce(claims)

        return socialLoginSupport.login(
            provider = AuthProvider.KAKAO,
            providerSubject = claims.subject,
            email = claims.email?.takeIf { claims.emailVerified },
            emailRequiredErrorCode = ErrorCode.KAKAO_VERIFIED_EMAIL_REQUIRED
        )
    }
}
```

Keep the existing private nonce methods in `KakaoLoginService`.

- [ ] **Step 4: Run Kakao tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.KakaoLoginServiceTest'
```

Expected: PASS after updating all assertions from token pair to login fields.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/auth/SocialLoginSupport.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/KakaoLoginService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/auth/KakaoLoginServiceTest.kt
git commit -m "refactor(auth): 소셜 로그인 응답 조립 공통화"
```

---

### Task 4: Apple OIDC Verification And Replay Storage

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcClaims.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifier.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRepository.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepository.kt`
- Modify: `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifierTest.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepositoryTest.kt`

- [ ] **Step 1: Add failing Apple verifier tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifierTest.kt` by copying the structure of `KakaoOidcTokenVerifierTest` and changing constants:

```kotlin
package com.chamchamcham.application.security

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.Date

class AppleOidcTokenVerifierTest {
    private val keyPair = rsaKeyPair()
    private val verifier = verifierFor(keyPair)

    @Test
    fun `verify accepts valid apple identity token`() {
        val claims = verifier.verify(token(), RAW_NONCE)

        assertEquals("apple-sub", claims.subject)
        assertEquals("apple@example.com", claims.email)
        assertEquals(true, claims.emailVerified)
        assertEquals(sha256Hex(RAW_NONCE), claims.nonce)
    }

    @Test
    fun `verify rejects nonce mismatch`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(), "different-raw-nonce")
        }

        assertEquals(ErrorCode.APPLE_NONCE_MISMATCH, exception.errorCode)
    }

    @Test
    fun `verify rejects wrong audience`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(audience = "wrong-audience"), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    private fun verifierFor(keyPair: KeyPair): AppleOidcTokenVerifier {
        val decoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()
        decoder.setJwtValidator { OAuth2TokenValidatorResult.success() }
        return AppleOidcTokenVerifier(
            issuer = ISSUER,
            audience = AUDIENCE,
            allowedClockSkew = Duration.ofSeconds(30),
            jwtDecoder = decoder
        )
    }

    private fun token(
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
        subject: String = "apple-sub",
        email: String? = "apple@example.com",
        emailVerified: Any? = true,
        nonce: String = sha256Hex(RAW_NONCE),
        expiresAt: Instant = Instant.now().plusSeconds(300),
        issuedAt: Instant = Instant.now().minusSeconds(10)
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .expirationTime(Date.from(expiresAt))
            .issueTime(Date.from(issuedAt))
            .claim("nonce", nonce)
            .apply {
                if (email != null) claim("email", email)
                if (emailVerified != null) claim("email_verified", emailVerified)
            }
            .build()

        val signedJwt = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)
        signedJwt.sign(RSASSASigner(keyPair.private as RSAPrivateKey))
        return signedJwt.serialize()
    }

    private fun rsaKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
    }

    private fun sha256Hex(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ISSUER = "https://appleid.apple.com"
        private const val AUDIENCE = "com.chamchamcham.ios"
        private const val RAW_NONCE = "client-raw-nonce"
    }
}
```

- [ ] **Step 2: Add failing Apple Redis tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepositoryTest.kt`:

```kotlin
package com.chamchamcham.application.redis

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.concurrent.TimeUnit

class AppleNonceReplayRedisRepositoryTest {
    @Test
    fun `reserve stores apple nonce hash with millisecond ttl`() {
        val redis = Mockito.mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val valueOperations = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        val repository = AppleNonceReplayRedisRepository(redis)

        `when`(redis.opsForValue()).thenReturn(valueOperations)
        `when`(
            valueOperations.setIfAbsent(
                Mockito.eq("auth:apple:nonce:nonce-hash"),
                Mockito.eq("1"),
                Mockito.eq(500L),
                Mockito.eq(TimeUnit.MILLISECONDS)
            )
        ).thenReturn(true)

        val reserved = repository.reserve("nonce-hash", Duration.ofMillis(500))

        assertTrue(reserved)
        verify(valueOperations).setIfAbsent(
            Mockito.eq("auth:apple:nonce:nonce-hash"),
            Mockito.eq("1"),
            Mockito.eq(500L),
            Mockito.eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `reserve rejects non positive ttl`() {
        val redis = Mockito.mock(StringRedisTemplate::class.java)
        val repository = AppleNonceReplayRedisRepository(redis)

        assertFalse(repository.reserve("nonce-hash", Duration.ZERO))
    }
}
```

- [ ] **Step 3: Run the failing Apple verifier and Redis tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.security.AppleOidcTokenVerifierTest' --tests 'com.chamchamcham.application.redis.AppleNonceReplayRedisRepositoryTest'
```

Expected: FAIL because Apple classes and error codes do not exist.

- [ ] **Step 4: Implement Apple error codes**

Modify `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt` by adding after `KAKAO_OIDC_UNAVAILABLE`:

```kotlin
INVALID_APPLE_TOKEN("AUTH_015", "error.invalid_apple_token", 401),
APPLE_NONCE_MISMATCH("AUTH_016", "error.apple_nonce_mismatch", 401),
APPLE_NONCE_REPLAY("AUTH_017", "error.apple_nonce_replay", 401),
APPLE_VERIFIED_EMAIL_REQUIRED("AUTH_018", "error.apple_verified_email_required", 422),
INVALID_NAVER_TOKEN("AUTH_019", "error.invalid_naver_token", 401),
NAVER_PROFILE_UNAVAILABLE("AUTH_020", "error.naver_profile_unavailable", 503),
NAVER_EMAIL_REQUIRED("AUTH_021", "error.naver_email_required", 422),
```

- [ ] **Step 5: Implement Apple verifier and nonce repository**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcClaims.kt`:

```kotlin
package com.chamchamcham.application.security

import java.time.Instant

data class AppleOidcClaims(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val nonce: String,
    val expiresAt: Instant
)
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifier.kt` using NuguSauce's pattern:

```kotlin
package com.chamchamcham.application.security

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.nimbusds.jwt.JWTParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.text.ParseException
import java.time.Duration
import java.time.Instant

@Service
class AppleOidcTokenVerifier private constructor(
    private val issuer: String,
    private val audience: String,
    private val allowedClockSkew: Duration,
    jwtDecoderProvider: () -> JwtDecoder
) {
    private val jwtDecoder: JwtDecoder by lazy(jwtDecoderProvider)

    @Autowired
    constructor(
        @Value("\${auth.apple.oidc.issuer:https://appleid.apple.com}")
        issuer: String,
        @Value("\${auth.apple.oidc.audience}")
        audience: String,
        @Value("\${auth.apple.oidc.jwks-uri:https://appleid.apple.com/auth/keys}")
        jwksUri: String,
        @Value("\${auth.apple.oidc.allowed-clock-skew-seconds:60}")
        allowedClockSkewSeconds: Long
    ) : this(
        issuer = issuer,
        audience = audience,
        allowedClockSkew = Duration.ofSeconds(allowedClockSkewSeconds),
        jwtDecoderProvider = { NimbusJwtDecoder.withJwkSetUri(jwksUri).build() }
    )

    internal constructor(
        issuer: String,
        audience: String,
        allowedClockSkew: Duration,
        jwtDecoder: JwtDecoder
    ) : this(issuer, audience, allowedClockSkew, { jwtDecoder })

    fun verify(identityToken: String, rawNonce: String): AppleOidcClaims {
        parseJwtShape(identityToken)
        val jwt = try {
            jwtDecoder.decode(identityToken)
        } catch (_: JwtException) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        } catch (_: Exception) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }

        validateIssuer(jwt)
        validateAudience(jwt)
        validateTimestamps(jwt)

        val expectedNonce = sha256Hex(rawNonce)
        val actualNonce = jwt.getClaimAsString(NONCE_CLAIM)
            ?: throw BusinessException(ErrorCode.APPLE_NONCE_MISMATCH)
        if (actualNonce != expectedNonce) {
            throw BusinessException(ErrorCode.APPLE_NONCE_MISMATCH)
        }

        return AppleOidcClaims(
            subject = jwt.subject?.takeIf { it.isNotBlank() }
                ?: throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN),
            email = jwt.getClaimAsString(EMAIL_CLAIM),
            emailVerified = emailVerified(jwt),
            nonce = actualNonce,
            expiresAt = jwt.expiresAt ?: throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        )
    }

    private fun validateIssuer(jwt: Jwt) {
        if (jwt.issuer?.toString() != issuer) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
    }

    private fun validateAudience(jwt: Jwt) {
        if (!jwt.audience.contains(audience)) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
    }

    private fun validateTimestamps(jwt: Jwt) {
        val now = Instant.now()
        val expiresAt = jwt.expiresAt ?: throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        if (expiresAt.plus(allowedClockSkew).isBefore(now)) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
        val issuedAt = jwt.issuedAt ?: throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        if (issuedAt.minus(allowedClockSkew).isAfter(now)) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
    }

    private fun emailVerified(jwt: Jwt): Boolean {
        return when (val value = jwt.claims[EMAIL_VERIFIED_CLAIM]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun parseJwtShape(identityToken: String) {
        try {
            JWTParser.parse(identityToken)
        } catch (_: ParseException) {
            throw BusinessException(ErrorCode.MALFORMED_JWT)
        }
    }

    private fun sha256Hex(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val NONCE_CLAIM = "nonce"
        private const val EMAIL_CLAIM = "email"
        private const val EMAIL_VERIFIED_CLAIM = "email_verified"
    }
}
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRepository.kt`:

```kotlin
package com.chamchamcham.application.redis

import java.time.Duration

interface AppleNonceReplayRepository {
    fun reserve(nonceHash: String, ttl: Duration): Boolean
}
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepository.kt`:

```kotlin
package com.chamchamcham.application.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.TimeUnit

@Repository
class AppleNonceReplayRedisRepository(
    private val redis: StringRedisTemplate
) : AppleNonceReplayRepository {
    override fun reserve(nonceHash: String, ttl: Duration): Boolean {
        if (ttl.isZero || ttl.isNegative) {
            return false
        }
        return redis.opsForValue().setIfAbsent(
            PREFIX + nonceHash,
            "1",
            ttl.toMillis().coerceAtLeast(1L),
            TimeUnit.MILLISECONDS
        ) == true
    }

    companion object {
        private const val PREFIX = "auth:apple:nonce:"
    }
}
```

- [ ] **Step 6: Run Apple verifier and Redis tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.security.AppleOidcTokenVerifierTest' --tests 'com.chamchamcham.application.redis.AppleNonceReplayRedisRepositoryTest'
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcClaims.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifier.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRepository.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepository.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/security/AppleOidcTokenVerifierTest.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/redis/AppleNonceReplayRedisRepositoryTest.kt
git commit -m "feat(auth): 애플 OIDC 검증 추가"
```

---

### Task 5: Apple Login Service

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AppleLoginService.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/AppleLoginServiceTest.kt`

- [ ] **Step 1: Write failing Apple login service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/AppleLoginServiceTest.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.AppleNonceReplayRepository
import com.chamchamcham.application.redis.RefreshTokenRepository
import com.chamchamcham.application.security.AppleOidcClaims
import com.chamchamcham.application.security.AppleOidcTokenVerifier
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.AuthProvider
import com.chamchamcham.domain.member.ExternalIdentity
import com.chamchamcham.domain.member.ExternalIdentityRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AppleLoginServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Mock private lateinit var verifier: AppleOidcTokenVerifier
    @Mock private lateinit var externalIdentityRepository: ExternalIdentityRepository
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var tokenProvider: TokenProvider
    @Mock private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var nonceRepository: RecordingAppleNonceRepository
    private lateinit var service: AppleLoginService

    @BeforeEach
    fun setUp() {
        nonceRepository = RecordingAppleNonceRepository()
        val support = SocialLoginSupport(
            externalIdentityRepository,
            memberRepository,
            tokenProvider,
            refreshTokenRepository,
            OnboardingStatusResolver()
        )
        service = AppleLoginService(verifier, nonceRepository, support, 60L)
    }

    @Test
    fun `login reuses existing apple identity`() {
        val member = Member(id = memberId, email = "apple@example.com", passwordHash = null)
        `when`(verifier.verify("identity-token", "raw-nonce")).thenReturn(claims())
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
            .thenReturn(ExternalIdentity(member = member, provider = AuthProvider.APPLE, providerSubject = "apple-sub", emailAtLinkTime = "apple@example.com"))
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER")).thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.AppleLogin("identity-token", "raw-nonce", "code", "apple-sub"))

        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals("apple-hashed-nonce", nonceRepository.lastNonceHash)
        verify(refreshTokenRepository).save(memberId, "refresh-token", 120L)
    }

    @Test
    fun `login rejects mismatched user identifier`() {
        `when`(verifier.verify("identity-token", "raw-nonce")).thenReturn(claims())

        val exception = assertThrows(BusinessException::class.java) {
            service.login(AuthCommand.AppleLogin("identity-token", "raw-nonce", null, "different-sub"))
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `login rejects missing verified email for new identity`() {
        `when`(verifier.verify("identity-token", "raw-nonce")).thenReturn(claims(email = null, emailVerified = false))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub")).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.login(AuthCommand.AppleLogin("identity-token", "raw-nonce", null, null))
        }

        assertEquals(ErrorCode.APPLE_VERIFIED_EMAIL_REQUIRED, exception.errorCode)
    }

    private fun claims(email: String? = "apple@example.com", emailVerified: Boolean = true): AppleOidcClaims {
        return AppleOidcClaims(
            subject = "apple-sub",
            email = email,
            emailVerified = emailVerified,
            nonce = "apple-hashed-nonce",
            expiresAt = Instant.now().plusSeconds(300)
        )
    }

    private class RecordingAppleNonceRepository : AppleNonceReplayRepository {
        var reserveResult = true
        var lastNonceHash: String? = null
        var lastTtl: Duration? = null

        override fun reserve(nonceHash: String, ttl: Duration): Boolean {
            lastNonceHash = nonceHash
            lastTtl = ttl
            return reserveResult
        }
    }
}
```

- [ ] **Step 2: Run failing Apple login service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.AppleLoginServiceTest'
```

Expected: FAIL because `AppleLoginService` does not exist.

- [ ] **Step 3: Implement Apple login service**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/AppleLoginService.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.AppleNonceReplayRepository
import com.chamchamcham.application.security.AppleOidcClaims
import com.chamchamcham.application.security.AppleOidcTokenVerifier
import com.chamchamcham.domain.member.AuthProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional
class AppleLoginService(
    private val appleOidcTokenVerifier: AppleOidcTokenVerifier,
    private val appleNonceReplayRepository: AppleNonceReplayRepository,
    private val socialLoginSupport: SocialLoginSupport,
    @Value("\${auth.apple.oidc.allowed-clock-skew-seconds:60}")
    allowedClockSkewSeconds: Long
) {
    private val allowedClockSkew = Duration.ofSeconds(allowedClockSkewSeconds)

    fun login(command: AuthCommand.AppleLogin): AuthResult.Login {
        val claims = appleOidcTokenVerifier.verify(command.identityToken, command.nonce)
        if (!command.userIdentifier.isNullOrBlank() && command.userIdentifier != claims.subject) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
        reserveNonce(claims)

        return socialLoginSupport.login(
            provider = AuthProvider.APPLE,
            providerSubject = claims.subject,
            email = claims.email?.takeIf { claims.emailVerified },
            emailRequiredErrorCode = ErrorCode.APPLE_VERIFIED_EMAIL_REQUIRED
        )
    }

    private fun reserveNonce(claims: AppleOidcClaims) {
        val ttl = nonceReplayTtl(claims.expiresAt)
        if (!appleNonceReplayRepository.reserve(claims.nonce, ttl)) {
            throw BusinessException(ErrorCode.APPLE_NONCE_REPLAY)
        }
    }

    private fun nonceReplayTtl(expiresAt: Instant): Duration {
        val acceptedUntil = expiresAt.plus(allowedClockSkew)
        val untilExpiry = Duration.between(Instant.now(), acceptedUntil)
        if (untilExpiry <= Duration.ZERO) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
        return untilExpiry
    }
}
```

- [ ] **Step 4: Run Apple login service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.AppleLoginServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/auth/AppleLoginService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/auth/AppleLoginServiceTest.kt
git commit -m "feat(auth): 애플 로그인 서비스 추가"
```

---

### Task 6: Naver Login Port, Service, And HTTP Adapter

**Files:**
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfile.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfileClient.kt`
- Create: `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverLoginService.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/naver/NaverProfileRestClient.kt`
- Test: `backend/application/src/test/kotlin/com/chamchamcham/application/auth/NaverLoginServiceTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/naver/NaverProfileRestClientTest.kt`

- [ ] **Step 1: Write failing Naver login service tests**

Create `backend/application/src/test/kotlin/com/chamchamcham/application/auth/NaverLoginServiceTest.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.RefreshTokenRepository
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.AuthProvider
import com.chamchamcham.domain.member.ExternalIdentityRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NaverLoginServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Mock private lateinit var naverProfileClient: NaverProfileClient
    @Mock private lateinit var externalIdentityRepository: ExternalIdentityRepository
    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var tokenProvider: TokenProvider
    @Mock private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var service: NaverLoginService

    @BeforeEach
    fun setUp() {
        val support = SocialLoginSupport(
            externalIdentityRepository,
            memberRepository,
            tokenProvider,
            refreshTokenRepository,
            OnboardingStatusResolver()
        )
        service = NaverLoginService(naverProfileClient, support)
    }

    @Test
    fun `login creates member and prefills profile from naver profile`() {
        val savedMember = Member(id = memberId, email = "naver@example.com", passwordHash = null)
        `when`(naverProfileClient.fetch("naver-access-token")).thenReturn(profile())
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.NAVER, "naver-sub")).thenReturn(null)
        `when`(memberRepository.findByEmail("naver@example.com")).thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER")).thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.NaverLogin("naver-access-token"))

        assertEquals("access-token", result.accessToken)
        assertEquals("홍길동", savedMember.name)
        assertEquals("010-1234-5678", savedMember.phone)
        assertEquals(LocalDate.of(1990, 10, 1), savedMember.birthDate)
        verify(refreshTokenRepository).save(memberId, "refresh-token", 120L)
    }

    @Test
    fun `login rejects profile without email`() {
        `when`(naverProfileClient.fetch("naver-access-token")).thenReturn(profile(email = null))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.NAVER, "naver-sub")).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.login(AuthCommand.NaverLogin("naver-access-token"))
        }

        assertEquals(ErrorCode.NAVER_EMAIL_REQUIRED, exception.errorCode)
    }

    private fun profile(email: String? = "naver@example.com"): NaverProfile {
        return NaverProfile(
            subject = "naver-sub",
            email = email,
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 10, 1)
        )
    }
}
```

- [ ] **Step 2: Run failing Naver service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.NaverLoginServiceTest'
```

Expected: FAIL because Naver classes do not exist.

- [ ] **Step 3: Implement Naver application port and service**

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfile.kt`:

```kotlin
package com.chamchamcham.application.auth

import java.time.LocalDate

data class NaverProfile(
    val subject: String,
    val email: String?,
    val name: String?,
    val phone: String?,
    val birthDate: LocalDate?
)
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfileClient.kt`:

```kotlin
package com.chamchamcham.application.auth

interface NaverProfileClient {
    fun fetch(accessToken: String): NaverProfile
}
```

Create `backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverLoginService.kt`:

```kotlin
package com.chamchamcham.application.auth

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.member.AuthProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NaverLoginService(
    private val naverProfileClient: NaverProfileClient,
    private val socialLoginSupport: SocialLoginSupport
) {
    fun login(command: AuthCommand.NaverLogin): AuthResult.Login {
        val profile = naverProfileClient.fetch(command.accessToken)
        return socialLoginSupport.login(
            provider = AuthProvider.NAVER,
            providerSubject = profile.subject,
            email = profile.email,
            emailRequiredErrorCode = ErrorCode.NAVER_EMAIL_REQUIRED,
            name = profile.name,
            phone = profile.phone,
            birthDate = profile.birthDate
        )
    }
}
```

- [ ] **Step 4: Run Naver service tests**

Run:

```bash
./gradlew :application:test --tests 'com.chamchamcham.application.auth.NaverLoginServiceTest'
```

Expected: PASS.

- [ ] **Step 5: Write failing Naver HTTP adapter tests**

Create `backend/api/src/test/kotlin/com/chamchamcham/api/naver/NaverProfileRestClientTest.kt`:

```kotlin
package com.chamchamcham.api.naver

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDate

class NaverProfileRestClientTest {
    @Test
    fun `fetch maps successful naver profile`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = NaverProfileRestClient(builder, "https://openapi.naver.com/v1/nid/me")

        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andRespond(
                withSuccess(
                    """{"resultcode":"00","message":"success","response":{"id":"naver-sub","email":"naver@example.com","name":"홍길동","mobile":"010-1234-5678","birthyear":"1990","birthday":"10-01"}}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = client.fetch("access-token")

        assertEquals("naver-sub", result.subject)
        assertEquals("naver@example.com", result.email)
        assertEquals("홍길동", result.name)
        assertEquals("010-1234-5678", result.phone)
        assertEquals(LocalDate.of(1990, 10, 1), result.birthDate)
        server.verify()
    }

    @Test
    fun `fetch maps provider failure to unavailable`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = NaverProfileRestClient(builder, "https://openapi.naver.com/v1/nid/me")

        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
            .andRespond(withServerError())

        val exception = assertThrows(BusinessException::class.java) {
            client.fetch("access-token")
        }

        assertEquals(ErrorCode.NAVER_PROFILE_UNAVAILABLE, exception.errorCode)
    }
}
```

- [ ] **Step 6: Run failing Naver HTTP adapter tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.naver.NaverProfileRestClientTest'
```

Expected: FAIL because `NaverProfileRestClient` does not exist.

- [ ] **Step 7: Implement Naver HTTP adapter**

Create `backend/api/src/main/kotlin/com/chamchamcham/api/naver/NaverProfileRestClient.kt`:

```kotlin
package com.chamchamcham.api.naver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.chamchamcham.application.auth.NaverProfile
import com.chamchamcham.application.auth.NaverProfileClient
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class NaverProfileRestClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${auth.naver.profile-uri:https://openapi.naver.com/v1/nid/me}")
    private val profileUri: String
) : NaverProfileClient {
    private val restClient = restClientBuilder.build()

    override fun fetch(accessToken: String): NaverProfile {
        val response = try {
            restClient.get()
                .uri(profileUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(NaverProfileResponse::class.java)
        } catch (_: RestClientException) {
            throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)
        } ?: throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)

        if (response.resultcode != "00") {
            throw BusinessException(ErrorCode.INVALID_NAVER_TOKEN)
        }

        val profile = response.response ?: throw BusinessException(ErrorCode.NAVER_PROFILE_UNAVAILABLE)
        val subject = profile.id?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_NAVER_TOKEN)

        return NaverProfile(
            subject = subject,
            email = profile.email,
            name = profile.name,
            phone = profile.mobile,
            birthDate = parseBirthDate(profile.birthyear, profile.birthday)
        )
    }

    private fun parseBirthDate(birthyear: String?, birthday: String?): LocalDate? {
        if (birthyear.isNullOrBlank() || birthday.isNullOrBlank()) {
            return null
        }
        return runCatching {
            LocalDate.parse("$birthyear-$birthday", DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NaverProfileResponse(
        val resultcode: String? = null,
        val message: String? = null,
        val response: NaverProfileBody? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NaverProfileBody(
        val id: String? = null,
        val email: String? = null,
        val name: String? = null,
        val mobile: String? = null,
        val birthyear: String? = null,
        val birthday: String? = null
    )
}
```

- [ ] **Step 8: Run Naver API adapter tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.naver.NaverProfileRestClientTest'
```

Expected: PASS.

- [ ] **Step 9: Commit Task 6**

```bash
git add backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfile.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverProfileClient.kt \
  backend/application/src/main/kotlin/com/chamchamcham/application/auth/NaverLoginService.kt \
  backend/application/src/test/kotlin/com/chamchamcham/application/auth/NaverLoginServiceTest.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/naver/NaverProfileRestClient.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/naver/NaverProfileRestClientTest.kt
git commit -m "feat(auth): 네이버 앱 로그인 추가"
```

---

### Task 7: API DTOs, Controllers, And Security

**Files:**
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Create: `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`
- Modify: `backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`
- Test: `backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt`

- [ ] **Step 1: Update controller tests for LoginResponse and new endpoints**

In `AuthControllerBusinessTest`, add mocks:

```kotlin
@MockBean
private lateinit var appleLoginService: AppleLoginService

@MockBean
private lateinit var naverLoginService: NaverLoginService
```

Add a helper:

```kotlin
private fun loginResult(): AuthResult.Login {
    return AuthResult.Login(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        member = AuthResult.MemberProfile(
            id = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            email = "member@example.com",
            name = null,
            phone = null,
            birthDate = null,
            nickname = null,
            region = null,
            experienceLevel = null,
            managementType = com.chamchamcham.domain.member.ManagementType.UNREGISTERED
        ),
        onboarding = AuthResult.Onboarding(AuthResult.OnboardingStatus.REQUIRED)
    )
}
```

Change `kakao login returns token pair from service` to assert:

```kotlin
`when`(kakaoLoginService.login(AuthCommand.KakaoLogin("id-token", "nonce")))
    .thenReturn(loginResult())

mockMvc.perform(
    post("/api/v1/auth/kakao/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"idToken":"id-token","nonce":"nonce"}""")
)
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
    .andExpect(jsonPath("$.data.refreshToken", equalTo("refresh-token")))
    .andExpect(jsonPath("$.data.member.id", equalTo("00000000-0000-0000-0000-000000000001")))
    .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
```

Add Apple and Naver endpoint tests with the same response assertions:

```kotlin
@Test
fun `apple login returns LoginResponse from service`() {
    `when`(appleLoginService.login(AuthCommand.AppleLogin("identity-token", "nonce", "code", "apple-sub")))
        .thenReturn(loginResult())

    mockMvc.perform(
        post("/api/v1/auth/apple/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"identityToken":"identity-token","nonce":"nonce","authorizationCode":"code","userIdentifier":"apple-sub"}""")
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
        .andExpect(jsonPath("$.data.member.email", equalTo("member@example.com")))
        .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
}

@Test
fun `naver login returns LoginResponse from service`() {
    `when`(naverLoginService.login(AuthCommand.NaverLogin("naver-access-token")))
        .thenReturn(loginResult())

    mockMvc.perform(
        post("/api/v1/auth/naver/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"accessToken":"naver-access-token"}""")
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.accessToken", equalTo("access-token")))
        .andExpect(jsonPath("$.data.member.email", equalTo("member@example.com")))
        .andExpect(jsonPath("$.data.onboarding.status", equalTo("REQUIRED")))
}
```

- [ ] **Step 2: Write onboarding controller test**

Create `backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt`:

```kotlin
package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.auth.AuthCommand
import com.chamchamcham.application.auth.AuthResult
import com.chamchamcham.application.auth.OnboardingService
import com.chamchamcham.domain.member.ManagementType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class AuthControllerBusinessTest(
    @Autowired private val mockMvc: MockMvc
) {
    @MockBean
    private lateinit var onboardingService: OnboardingService

    @Test
    fun `complete returns updated member and onboarding status`() {
        val memberId = "00000000-0000-0000-0000-000000000001"
        `when`(
            onboardingService.complete(
                AuthCommand.CompleteOnboarding(
                    memberId = memberId,
                    name = "홍길동",
                    phone = "010-1234-5678",
                    birthDate = LocalDate.of(1990, 10, 1),
                    nickname = "농부길동",
                    region = "전라남도 나주시",
                    experienceLevel = "BEGINNER"
                )
            )
        ).thenReturn(
            AuthResult.OnboardingComplete(
                member = AuthResult.MemberProfile(
                    id = UUID.fromString(memberId),
                    email = "member@example.com",
                    name = "홍길동",
                    phone = "010-1234-5678",
                    birthDate = LocalDate.of(1990, 10, 1),
                    nickname = "농부길동",
                    region = "전라남도 나주시",
                    experienceLevel = "BEGINNER",
                    managementType = ManagementType.UNREGISTERED
                ),
                onboarding = AuthResult.Onboarding(AuthResult.OnboardingStatus.COMPLETE)
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .with(user(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"홍길동","phone":"010-1234-5678","birthDate":"1990-10-01","nickname":"농부길동","region":"전라남도 나주시","experienceLevel":"BEGINNER"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.member.id", equalTo(memberId)))
            .andExpect(jsonPath("$.data.member.birthDate", equalTo("1990-10-01")))
            .andExpect(jsonPath("$.data.onboarding.status", equalTo("COMPLETE")))
    }

    @Test
    fun `complete rejects blank nickname`() {
        mockMvc.perform(
            post("/api/v1/auth/onboarding/complete")
                .with(user("00000000-0000-0000-0000-000000000001"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"홍길동","phone":"010-1234-5678","birthDate":"1990-10-01","nickname":"","region":"전라남도 나주시","experienceLevel":"BEGINNER"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }
}
```

- [ ] **Step 3: Run failing API tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.auth.controller.AuthControllerBusinessTest' --tests 'com.chamchamcham.api.auth.controller.AuthControllerBusinessTest'
```

Expected: FAIL because DTOs and controller wiring are not updated.

- [ ] **Step 4: Implement API request and response DTOs**

Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt` by adding:

```kotlin
data class AppleLoginRequest(
    @field:NotBlank(message = "Apple ID 토큰을 입력해주세요")
    val identityToken: String,
    @field:NotBlank(message = "nonce를 입력해주세요")
    val nonce: String,
    val authorizationCode: String? = null,
    val userIdentifier: String? = null
)

data class NaverLoginRequest(
    @field:NotBlank(message = "네이버 액세스 토큰을 입력해주세요")
    val accessToken: String
)

data class CompleteOnboardingRequest(
    @field:NotBlank(message = "이름을 입력해주세요")
    val name: String,
    @field:NotBlank(message = "전화번호를 입력해주세요")
    val phone: String,
    @field:NotNull(message = "생년월일을 입력해주세요")
    val birthDate: LocalDate?,
    @field:NotBlank(message = "닉네임을 입력해주세요")
    val nickname: String,
    @field:NotBlank(message = "지역을 입력해주세요")
    val region: String,
    @field:NotBlank(message = "경험 수준을 입력해주세요")
    val experienceLevel: String
)
```

Add imports:

```kotlin
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
```

Modify `backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt`:

```kotlin
package com.chamchamcham.api.auth.dto

import com.chamchamcham.application.auth.AuthResult
import com.chamchamcham.domain.member.ManagementType
import java.time.LocalDate
import java.util.UUID

object AuthResponses {
    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String
    ) {
        companion object {
            fun from(result: AuthResult.TokenPair): TokenResponse {
                return TokenResponse(result.accessToken, result.refreshToken)
            }
        }
    }

    data class LoginResponse(
        val accessToken: String,
        val refreshToken: String,
        val member: MemberProfileResponse,
        val onboarding: OnboardingResponse
    ) {
        companion object {
            fun from(result: AuthResult.Login): LoginResponse {
                return LoginResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    member = MemberProfileResponse.from(result.member),
                    onboarding = OnboardingResponse.from(result.onboarding)
                )
            }
        }
    }

    data class OnboardingCompleteResponse(
        val member: MemberProfileResponse,
        val onboarding: OnboardingResponse
    ) {
        companion object {
            fun from(result: AuthResult.OnboardingComplete): OnboardingCompleteResponse {
                return OnboardingCompleteResponse(
                    member = MemberProfileResponse.from(result.member),
                    onboarding = OnboardingResponse.from(result.onboarding)
                )
            }
        }
    }

    data class MemberProfileResponse(
        val id: UUID,
        val email: String,
        val name: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val nickname: String?,
        val region: String?,
        val experienceLevel: String?,
        val managementType: ManagementType
    ) {
        companion object {
            fun from(result: AuthResult.MemberProfile): MemberProfileResponse {
                return MemberProfileResponse(
                    id = result.id,
                    email = result.email,
                    name = result.name,
                    phone = result.phone,
                    birthDate = result.birthDate,
                    nickname = result.nickname,
                    region = result.region,
                    experienceLevel = result.experienceLevel,
                    managementType = result.managementType
                )
            }
        }
    }

    data class OnboardingResponse(
        val status: AuthResult.OnboardingStatus
    ) {
        companion object {
            fun from(result: AuthResult.Onboarding): OnboardingResponse {
                return OnboardingResponse(status = result.status)
            }
        }
    }
}
```

- [ ] **Step 5: Implement controllers and security endpoint list**

Modify `AuthController`:

```kotlin
class AuthController(
    private val authService: AuthService,
    private val kakaoLoginService: KakaoLoginService,
    private val appleLoginService: AppleLoginService,
    private val naverLoginService: NaverLoginService,
    private val tokenProvider: TokenProvider,
    @Value("\${app.auth.refresh-cookie-secure:true}")
    private val refreshCookieSecure: Boolean
)
```

Change Kakao login return type and body:

```kotlin
@PostMapping("/kakao/login")
fun kakaoLogin(
    @Valid @RequestBody request: AuthRequests.KakaoLoginRequest
): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
    val result = kakaoLoginService.login(AuthCommand.KakaoLogin(request.idToken, request.nonce))
    return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
}
```

Add Apple and Naver endpoints:

```kotlin
@PostMapping("/apple/login")
fun appleLogin(
    @Valid @RequestBody request: AuthRequests.AppleLoginRequest
): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
    val result = appleLoginService.login(
        AuthCommand.AppleLogin(
            identityToken = request.identityToken,
            nonce = request.nonce,
            authorizationCode = request.authorizationCode,
            userIdentifier = request.userIdentifier
        )
    )
    return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
}

@PostMapping("/naver/login")
fun naverLogin(
    @Valid @RequestBody request: AuthRequests.NaverLoginRequest
): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
    val result = naverLoginService.login(AuthCommand.NaverLogin(request.accessToken))
    return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
}
```

Create `backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt`:

```kotlin
package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.auth.dto.AuthRequests
import com.chamchamcham.api.auth.dto.AuthResponses
import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.application.auth.AuthCommand
import com.chamchamcham.application.auth.OnboardingService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val onboardingService: OnboardingService
) {
    @PostMapping("/complete")
    fun complete(
        @AuthenticationPrincipal memberId: String,
        @Valid @RequestBody request: AuthRequests.CompleteOnboardingRequest
    ): ResponseEntity<ApiResponse<AuthResponses.OnboardingCompleteResponse>> {
        val result = onboardingService.complete(
            AuthCommand.CompleteOnboarding(
                memberId = memberId,
                name = request.name,
                phone = request.phone,
                birthDate = requireNotNull(request.birthDate),
                nickname = request.nickname,
                region = request.region,
                experienceLevel = request.experienceLevel
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.OnboardingCompleteResponse.from(result)))
    }
}
```

Modify `SecurityConfig.PUBLIC_ENDPOINTS`:

```kotlin
"/api/v1/auth/apple/login",
"/api/v1/auth/naver/login",
```

Keep `/api/v1/auth/onboarding/complete` out of public endpoints.

- [ ] **Step 6: Run API tests**

Run:

```bash
./gradlew :api:test --tests 'com.chamchamcham.api.auth.controller.AuthControllerBusinessTest' --tests 'com.chamchamcham.api.auth.controller.AuthControllerValidationTest' --tests 'com.chamchamcham.api.auth.controller.AuthControllerBusinessTest' --tests 'com.chamchamcham.api.security.AuthSecurityIntegrationTest'
```

Expected: PASS after adding required mocks in affected WebMvc tests.

- [ ] **Step 7: Commit Task 7**

```bash
git add backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthRequests.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/auth/dto/AuthResponses.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/api/auth/controller/AuthController.kt \
  backend/api/src/main/kotlin/com/chamchamcham/config/SecurityConfig.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerValidationTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/auth/controller/AuthControllerBusinessTest.kt \
  backend/api/src/test/kotlin/com/chamchamcham/api/security/AuthSecurityIntegrationTest.kt
git commit -m "feat(auth): 소셜 로그인 API 응답 확장"
```

---

### Task 8: Profile Config And Final Verification

**Files:**
- Modify: `backend/api/src/main/resources/application-local.yml`
- Modify: `backend/api/src/main/resources/application-dev.yml`
- Modify: `backend/api/src/main/resources/application-prod.yml`
- Modify: `backend/api/src/test/resources/application-test.yml`

- [ ] **Step 1: Add auth config placeholders**

Add this under `auth:` in all API resource YAML files, preserving existing Kakao config:

```yaml
  apple:
    oidc:
      issuer: https://appleid.apple.com
      audience: ${APPLE_CLIENT_ID:com.chamchamcham.ios}
      jwks-uri: https://appleid.apple.com/auth/keys
      allowed-clock-skew-seconds: 60
  naver:
    profile-uri: ${NAVER_PROFILE_URI:https://openapi.naver.com/v1/nid/me}
```

For `application-dev.yml` and `application-prod.yml`, use `${APPLE_CLIENT_ID}` without a default if deployment should fail fast when missing:

```yaml
      audience: ${APPLE_CLIENT_ID}
```

- [ ] **Step 2: Run full backend tests**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: Run compile check if full tests are too slow**

If `./gradlew test` is blocked by local services or takes too long, run:

```bash
./gradlew :domain:compileKotlin :application:compileKotlin :api:compileKotlin
```

Expected: PASS. Record any skipped full-test reason in the final report and commit `Not-tested` trailer.

- [ ] **Step 4: Commit Task 8**

```bash
git add backend/api/src/main/resources/application-local.yml \
  backend/api/src/main/resources/application-dev.yml \
  backend/api/src/main/resources/application-prod.yml \
  backend/api/src/test/resources/application-test.yml
git commit -m "chore(auth): 소셜 로그인 설정 추가"
```

---

## Final Verification Checklist

- [ ] `./gradlew test` passes from `backend/`.
- [ ] Social login endpoints return `LoginResponse`.
- [ ] Local email/password login still returns token-only `TokenResponse`.
- [ ] `POST /api/v1/auth/onboarding/complete` is authenticated.
- [ ] Apple/Naver login endpoints are public.
- [ ] No backend onboarding draft-save API exists.
- [ ] No terms consent logic is added.
- [ ] No `missingFields` response field exists.
- [ ] No `onboardingCompleted` field is added to `Member`.
- [ ] No onboarding status is added to JWT claims.

## Known Implementation Risks

- `Member` currently used immutable `val` profile fields. Task 1 intentionally changes only onboarding profile fields to `var` and provides explicit mutation methods so JPA dirty checking can persist completion updates.
- Naver login uses access-token profile lookup rather than OIDC. This matches the app-only MVP decision and avoids backend OAuth redirect/token-exchange state.
- Apple first-login name is not accepted in MVP. The app can ask the user to enter or confirm their name during onboarding.
- Dev/prod use `ddl-auto: none`; adding `birth_date` needs a reviewed schema migration before non-local deployment because the project does not yet include Flyway.
