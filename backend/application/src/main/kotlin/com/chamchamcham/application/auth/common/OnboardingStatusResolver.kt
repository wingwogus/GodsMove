package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.Member
import org.springframework.stereotype.Component

@Component
class OnboardingStatusResolver {
    fun resolve(member: Member): AuthResult.Onboarding {
        val status = if (isComplete(member)) {
            AuthResult.OnboardingStatus.COMPLETE
        } else {
            AuthResult.OnboardingStatus.REQUIRED
        }

        return AuthResult.Onboarding(status)
    }

    private fun isComplete(member: Member): Boolean {
        return !member.name.isNullOrBlank() &&
            !member.phone.isNullOrBlank() &&
            member.birthDate != null &&
            !member.nickname.isNullOrBlank() &&
            !member.region.isNullOrBlank() &&
            !member.experienceLevel.isNullOrBlank()
    }
}
