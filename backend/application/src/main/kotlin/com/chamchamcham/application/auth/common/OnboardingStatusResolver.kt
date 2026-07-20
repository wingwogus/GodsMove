package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.Member
import org.springframework.stereotype.Component

@Component
class OnboardingStatusResolver {
    fun resolve(member: Member): AuthResult.Onboarding {
        val missingFields = missingFields(member)
        val status = if (missingFields.isEmpty()) {
            AuthResult.OnboardingStatus.COMPLETE
        } else {
            AuthResult.OnboardingStatus.REQUIRED
        }

        return AuthResult.Onboarding(status, missingFields)
    }

    private fun missingFields(member: Member): List<AuthResult.OnboardingField> {
        return buildList {
            if (member.experienceLevel == null) {
                add(AuthResult.OnboardingField.EXPERIENCE_LEVEL)
            }
            if (member.managementType == null) {
                add(AuthResult.OnboardingField.MANAGEMENT_TYPE)
            }
        }
    }
}
