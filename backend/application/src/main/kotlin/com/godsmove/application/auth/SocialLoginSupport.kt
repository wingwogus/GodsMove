package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.redis.RefreshTokenRepository
import com.godsmove.application.security.TokenProvider
import com.godsmove.domain.member.AuthProvider
import com.godsmove.domain.member.ExternalIdentity
import com.godsmove.domain.member.ExternalIdentityRepository
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
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
        emailRequiredErrorCode: ErrorCode,
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
            linkOrCreateMember(
                provider = provider,
                providerSubject = providerSubject,
                email = email,
                emailRequiredErrorCode = emailRequiredErrorCode,
                name = name,
                phone = phone,
                birthDate = birthDate,
                beforeSideEffects = beforeSideEffects
            )
        }

        member.prefillProfile(name, phone, birthDate)
        return issueAndStoreLogin(member)
    }

    private fun linkOrCreateMember(
        provider: AuthProvider,
        providerSubject: String,
        email: String?,
        emailRequiredErrorCode: ErrorCode,
        name: String?,
        phone: String?,
        birthDate: LocalDate?,
        beforeSideEffects: () -> Unit
    ): Member {
        val requiredEmail = email
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(emailRequiredErrorCode)

        val existingMember = memberRepository.findByEmail(requiredEmail)
        beforeSideEffects()

        val member = existingMember
            ?: memberRepository.save(
                Member(
                    email = requiredEmail,
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
                emailAtLinkTime = requiredEmail
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
