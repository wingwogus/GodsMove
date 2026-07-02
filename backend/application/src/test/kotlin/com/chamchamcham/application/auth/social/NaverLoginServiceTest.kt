package com.chamchamcham.application.auth.social

import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.auth.common.OnboardingStatusResolver

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.RefreshTokenRepository
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
import org.mockito.ArgumentCaptor
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
    private val existingMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val externalIdentityId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Mock
    private lateinit var externalIdentityRepository: ExternalIdentityRepository

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var tokenProvider: TokenProvider

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var profileClient: StubNaverProfileClient
    private lateinit var service: NaverLoginService

    @BeforeEach
    fun setUp() {
        profileClient = StubNaverProfileClient()
        val socialLoginSupport = SocialLoginSupport(
            externalIdentityRepository,
            memberRepository,
            tokenProvider,
            refreshTokenRepository,
            OnboardingStatusResolver()
        )
        service = NaverLoginService(profileClient, socialLoginSupport)
    }

    @Test
    fun `login creates member and pre-fills profile from Naver`() {
        val birthDate = LocalDate.of(1990, 5, 12)
        val savedMember = Member(id = memberId, email = "new@example.com", passwordHash = null, role = "ROLE_USER")
        profileClient.profile = profile(email = "new@example.com", name = "네이버회원", phone = "010-1234-5678", birthDate = birthDate)

        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.NAVER, "naver-sub"))
            .thenReturn(null)
        `when`(memberRepository.findByEmail("new@example.com")).thenReturn(null)
        `when`(memberRepository.save(Mockito.any(Member::class.java))).thenReturn(savedMember)
        `when`(externalIdentityRepository.save(Mockito.any(ExternalIdentity::class.java)))
            .thenAnswer { it.getArgument(0) }
        `when`(tokenProvider.generateToken(memberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.NaverLogin("access-token"))

        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertEquals("new@example.com", memberCaptor.value.email)
        assertEquals("네이버회원", memberCaptor.value.name)
        assertEquals("010-1234-5678", memberCaptor.value.phone)
        assertEquals(birthDate, memberCaptor.value.birthDate)

        val identityCaptor = ArgumentCaptor.forClass(ExternalIdentity::class.java)
        verify(externalIdentityRepository).save(identityCaptor.capture())
        assertEquals(AuthProvider.NAVER, identityCaptor.value.provider)
        assertEquals("naver-sub", identityCaptor.value.providerSubject)
        assertEquals("new@example.com", identityCaptor.value.emailAtLinkTime)

        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals(memberId, result.member.id)
        assertEquals("네이버회원", result.member.name)
        assertEquals("010-1234-5678", result.member.phone)
        assertEquals(birthDate, result.member.birthDate)
        verify(refreshTokenRepository).save(memberId, "refresh-token", 120L)
    }

    @Test
    fun `login rejects missing email for new identity`() {
        profileClient.profile = profile(email = null)
        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.NAVER, "naver-sub"))
            .thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.login(AuthCommand.NaverLogin("access-token"))
        }

        assertEquals(ErrorCode.NAVER_EMAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `login reuses existing identity without requiring email`() {
        val member = Member(id = existingMemberId, email = "existing@example.com", passwordHash = null, role = "ROLE_USER")
        profileClient.profile = profile(email = null, name = "네이버회원")

        `when`(externalIdentityRepository.findByProviderAndProviderSubject(AuthProvider.NAVER, "naver-sub"))
            .thenReturn(
                ExternalIdentity(
                    id = externalIdentityId,
                    member = member,
                    provider = AuthProvider.NAVER,
                    providerSubject = "naver-sub",
                    emailAtLinkTime = "existing@example.com"
                )
            )
        `when`(tokenProvider.generateToken(existingMemberId, "ROLE_USER"))
            .thenReturn(AuthResult.TokenPair("access-token", "refresh-token"))
        `when`(tokenProvider.getRefreshTokenValiditySeconds()).thenReturn(120L)

        val result = service.login(AuthCommand.NaverLogin("access-token"))

        assertEquals(existingMemberId, result.member.id)
        assertEquals("existing@example.com", result.member.email)
        assertEquals("네이버회원", result.member.name)
        verify(memberRepository, Mockito.never()).findByEmail(Mockito.anyString())
    }

    private fun profile(
        email: String?,
        name: String? = null,
        phone: String? = null,
        birthDate: LocalDate? = null
    ): NaverProfile {
        return NaverProfile(
            subject = "naver-sub",
            email = email,
            name = name,
            phone = phone,
            birthDate = birthDate
        )
    }

    private class StubNaverProfileClient : NaverProfileClient {
        lateinit var profile: NaverProfile
        var lastAccessToken: String? = null

        override fun fetch(accessToken: String): NaverProfile {
            lastAccessToken = accessToken
            return profile
        }
    }
}
