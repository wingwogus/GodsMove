package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.redis.AppleNonceReplayRepository
import com.godsmove.application.redis.RefreshTokenRepository
import com.godsmove.application.security.AppleOidcClaims
import com.godsmove.application.security.AppleOidcTokenVerifier
import com.godsmove.application.security.TokenProvider
import com.godsmove.domain.member.AuthProvider
import com.godsmove.domain.member.ExternalIdentity
import com.godsmove.domain.member.ExternalIdentityRepository
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AppleLoginServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val newMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val externalIdentityId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Mock
    private lateinit var appleOidcTokenVerifier: AppleOidcTokenVerifier

    @Mock
    private lateinit var externalIdentityRepository: ExternalIdentityRepository

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var tokenProvider: TokenProvider

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var nonceRepository: RecordingNonceRepository
    private lateinit var socialLoginSupport: SocialLoginSupport
    private lateinit var service: AppleLoginService

    @BeforeEach
    fun setUp() {
        nonceRepository = RecordingNonceRepository()
        socialLoginSupport = SocialLoginSupport(
            externalIdentityRepository,
            memberRepository,
            tokenProvider,
            refreshTokenRepository,
            OnboardingStatusResolver()
        )
        service = AppleLoginService(
            appleOidcTokenVerifier,
            nonceRepository,
            socialLoginSupport,
            60L
        )
    }

    @Test
    fun `login reuses existing external identity and reserves nonce hash`() {
        val member = Member(id = memberId, email = "user@example.com", passwordHash = null, role = "ROLE_USER")
        val claims = claims()

        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce")).thenReturn(claims)
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
            .thenReturn(existingIdentity(member))
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(command())

        assertLoginResult(result, memberId, "user@example.com")
        assertEquals("nonce-hash", nonceRepository.lastNonceHash)
        assertTrue((nonceRepository.lastTtl ?: error("nonce TTL was not recorded")) > Duration.ZERO)
        verify(refreshTokenRepository).save(memberId, "refresh-token", 120L)
    }

    @Test
    fun `login reuses existing external identity without requiring verified email`() {
        val member = Member(id = memberId, email = "user@example.com", passwordHash = null, role = "ROLE_USER")

        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce"))
            .thenReturn(claims(email = null, emailVerified = false))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
            .thenReturn(existingIdentity(member))
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(command())

        assertLoginResult(result, memberId, "user@example.com")
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
    }

    @Test
    fun `login rejects mismatched user identifier`() {
        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce")).thenReturn(claims())

        val exception = assertThrows(BusinessException::class.java) {
            service.login(command(userIdentifier = "different-sub"))
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `login rejects missing verified email for new identity`() {
        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce"))
            .thenReturn(claims(email = null, emailVerified = false))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
            .thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.login(command())
        }

        assertEquals(ErrorCode.APPLE_VERIFIED_EMAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `login rejects replayed nonce hash`() {
        nonceRepository.reserveResult = false
        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce")).thenReturn(claims())

        val exception = assertThrows(BusinessException::class.java) {
            service.login(command())
        }

        assertEquals(ErrorCode.APPLE_NONCE_REPLAY, exception.errorCode)
    }

    @Test
    fun `login rejects expired token beyond allowed skew`() {
        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce"))
            .thenReturn(claims(expiresAt = Instant.now().minusSeconds(61)))

        val exception = assertThrows(BusinessException::class.java) {
            service.login(command())
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `login creates member for new identity with verified email`() {
        val savedMember = Member(id = newMemberId, email = "new@example.com", passwordHash = null, role = "ROLE_USER")

        `when`(appleOidcTokenVerifier.verify("identity-token", "raw-nonce"))
            .thenReturn(claims(email = "new@example.com"))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-sub"))
            .thenReturn(null)
        `when`(memberRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(externalIdentityRepository.save(Mockito.any(ExternalIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(tokenProvider.generateToken(newMemberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(command(userIdentifier = ""))

        assertLoginResult(result, newMemberId, "new@example.com")
    }

    private fun assertLoginResult(
        result: AuthResult.Login,
        expectedMemberId: UUID,
        expectedEmail: String
    ) {
        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals(expectedMemberId, result.member.id)
        assertEquals(expectedEmail, result.member.email)
        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.onboarding.status)
    }

    private fun existingIdentity(member: Member): ExternalIdentity {
        return ExternalIdentity(
            id = externalIdentityId,
            member = member,
            provider = AuthProvider.APPLE,
            providerSubject = "apple-sub",
            emailAtLinkTime = "user@example.com"
        )
    }

    private fun command(userIdentifier: String? = "apple-sub"): AuthCommand.AppleLogin {
        return AuthCommand.AppleLogin(
            identityToken = "identity-token",
            nonce = "raw-nonce",
            authorizationCode = "authorization-code",
            userIdentifier = userIdentifier
        )
    }

    private fun claims(
        email: String? = "user@example.com",
        emailVerified: Boolean = true,
        expiresAt: Instant = Instant.now().plusSeconds(300)
    ): AppleOidcClaims {
        return AppleOidcClaims(
            subject = "apple-sub",
            email = email,
            emailVerified = emailVerified,
            nonce = "nonce-hash",
            expiresAt = expiresAt
        )
    }

    private class RecordingNonceRepository : AppleNonceReplayRepository {
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
