package com.chamchamcham.application.auth.social

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
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Component
@Transactional
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
        name: String? = null,
        phone: String? = null,
        birthDate: LocalDate? = null,
        beforeSideEffects: () -> Unit = {}
    ): AuthResult.Login {
        val existingIdentity = externalIdentityRepository
            .findByProviderAndProviderSubject(provider, providerSubject)
        val member = if (existingIdentity != null) {
            beforeSideEffects()
            existingIdentity.member
        } else {
            createMember(
                provider = provider,
                providerSubject = providerSubject,
                email = email,
                name = name,
                phone = phone,
                birthDate = birthDate,
                beforeSideEffects = beforeSideEffects
            )
        }

        member.prefillProfile(name, phone, birthDate)
        return issueAndStoreLogin(member)
    }

    private fun createMember(
        provider: AuthProvider,
        providerSubject: String,
        email: String?,
        name: String?,
        phone: String?,
        birthDate: LocalDate?,
        beforeSideEffects: () -> Unit
    ): Member {
        beforeSideEffects()

        val member = memberRepository.save(
            Member(
                email = email?.takeIf { it.isNotBlank() },
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
                providerSubject = providerSubject
            )
        )

        return member
    }

    private fun issueAndStoreLogin(member: Member): AuthResult.Login {
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
