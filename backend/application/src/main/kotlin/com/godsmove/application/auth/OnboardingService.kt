package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OnboardingService(
    private val memberRepository: MemberRepository,
    private val onboardingStatusResolver: OnboardingStatusResolver
) {
    fun complete(command: AuthCommand.CompleteOnboarding): AuthResult.OnboardingComplete {
        val member = memberRepository.findById(command.memberId).orElseThrow {
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
}
