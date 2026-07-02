package com.chamchamcham.application.auth.social

import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.auth.common.OnboardingStatusResolver

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.KakaoNonceReplayRepository
import com.chamchamcham.application.redis.RefreshTokenRepository
import com.chamchamcham.application.security.KakaoOidcClaims
import com.chamchamcham.application.security.KakaoOidcTokenVerifier
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.member.AuthProvider
import com.chamchamcham.domain.member.ExternalIdentity
import com.chamchamcham.domain.member.ExternalIdentityRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class KakaoLoginServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val newMemberId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val externalIdentityId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Mock
    private lateinit var kakaoOidcTokenVerifier: KakaoOidcTokenVerifier

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
    private lateinit var service: KakaoLoginService

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
        service = KakaoLoginService(
            kakaoOidcTokenVerifier,
            nonceRepository,
            socialLoginSupport,
            600L,
            60L
        )
    }

    @Test
    fun `login reuses existing external identity without requiring email`() {
        val member = Member(id = memberId, email = "user@example.com", passwordHash = null, role = "ROLE_USER")
        val claims = claims(email = null, emailVerified = false)

        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce")).thenReturn(claims)
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-sub"))
            .thenReturn(
                ExternalIdentity(
                    id = externalIdentityId,
                    member = member,
                    provider = AuthProvider.KAKAO,
                    providerSubject = "kakao-sub"
                )
            )
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

        assertLoginResult(result, memberId, "user@example.com")
        assertEquals("nonce", nonceRepository.lastNonce)
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
        verify(refreshTokenRepository).save(memberId, "refresh-token", 120L)
    }

    @Test
    fun `login creates new member by provider subject without linking by email`() {
        val savedMember = Member(id = newMemberId, email = "user@example.com", passwordHash = null, role = "ROLE_USER")
        val claims = claims()

        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce")).thenReturn(claims)
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-sub"))
            .thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(externalIdentityRepository.save(Mockito.any(ExternalIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(tokenProvider.generateToken(newMemberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertEquals("user@example.com", memberCaptor.value.email)

        val identityCaptor = ArgumentCaptor.forClass(ExternalIdentity::class.java)
        verify(externalIdentityRepository).save(identityCaptor.capture())
        assertEquals(savedMember, identityCaptor.value.member)
        assertEquals(AuthProvider.KAKAO, identityCaptor.value.provider)
        assertEquals("kakao-sub", identityCaptor.value.providerSubject)
        assertLoginResult(result, newMemberId, "user@example.com")
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
    }

    @Test
    fun `login creates new member with null password hash`() {
        val savedMember = Member(id = newMemberId, email = "new@example.com", passwordHash = null, role = "ROLE_USER")

        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce")).thenReturn(claims(email = "new@example.com"))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-sub"))
            .thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(externalIdentityRepository.save(Mockito.any(ExternalIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(tokenProvider.generateToken(newMemberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertEquals("new@example.com", memberCaptor.value.email)
        assertNull(memberCaptor.value.passwordHash)
        assertLoginResult(result, newMemberId, "new@example.com")
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
    }

    @Test
    fun `login creates new member without email`() {
        val savedMember = Member(id = newMemberId, email = null, passwordHash = null, role = "ROLE_USER")

        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce"))
            .thenReturn(claims(email = null, emailVerified = false))
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-sub"))
            .thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(externalIdentityRepository.save(Mockito.any(ExternalIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(tokenProvider.generateToken(newMemberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertNull(memberCaptor.value.email)
        assertLoginResult(result, newMemberId, null)
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
    }

    @Test
    fun `login rejects replayed nonce`() {
        nonceRepository.reserveResult = false
        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce")).thenReturn(claims())

        val exception = assertThrows(BusinessException::class.java) {
            service.login(AuthCommand.KakaoLogin("id-token", "nonce"))
        }

        assertEquals(ErrorCode.KAKAO_NONCE_REPLAY, exception.errorCode)
    }

    @Test
    fun `login reserves nonce for token accepted within clock skew`() {
        val member = Member(id = memberId, email = "user@example.com", passwordHash = null, role = "ROLE_USER")

        `when`(kakaoOidcTokenVerifier.verify("id-token", "nonce")).thenReturn(
            claims(expiresAt = Instant.now().minusSeconds(10))
        )
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-sub"))
            .thenReturn(
                ExternalIdentity(
                    id = externalIdentityId,
                    member = member,
                    provider = AuthProvider.KAKAO,
                    providerSubject = "kakao-sub"
                )
            )
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.KakaoLogin("id-token", "nonce"))

        val ttl = nonceRepository.lastTtl ?: error("nonce TTL was not recorded")
        assertTrue(ttl > Duration.ZERO)
        assertTrue(ttl <= Duration.ofSeconds(60))
        assertLoginResult(result, memberId, "user@example.com")
    }

    private fun assertLoginResult(
        result: AuthResult.Login,
        expectedMemberId: UUID,
        expectedEmail: String?
    ) {
        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals(expectedMemberId, result.member.id)
        assertEquals(expectedEmail, result.member.email)
        assertEquals(AuthResult.OnboardingStatus.REQUIRED, result.onboarding.status)
    }

    private fun claims(
        email: String? = "user@example.com",
        emailVerified: Boolean = true,
        expiresAt: Instant = Instant.now().plusSeconds(300)
    ): KakaoOidcClaims {
        return KakaoOidcClaims(
            subject = "kakao-sub",
            email = email,
            emailVerified = emailVerified,
            nonce = "nonce",
            expiresAt = expiresAt
        )
    }

    private class RecordingNonceRepository : KakaoNonceReplayRepository {
        var reserveResult = true
        var lastNonce: String? = null
        var lastTtl: Duration? = null

        override fun reserve(nonce: String, ttl: Duration): Boolean {
            lastNonce = nonce
            lastTtl = ttl
            return reserveResult
        }
    }
}
