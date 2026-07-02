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
            if (member.name.isNullOrBlank()) {
                add(AuthResult.OnboardingField.NAME)
            }
            if (member.phone.isNullOrBlank()) {
                add(AuthResult.OnboardingField.PHONE)
            }
            if (member.birthDate == null) {
                add(AuthResult.OnboardingField.BIRTH_DATE)
            }
            if (member.nickname.isNullOrBlank()) {
                add(AuthResult.OnboardingField.NICKNAME)
            }
            if (member.region.isNullOrBlank()) {
                add(AuthResult.OnboardingField.REGION)
            }
            if (member.experienceLevel.isNullOrBlank()) {
                add(AuthResult.OnboardingField.EXPERIENCE_LEVEL)
            }
        }
    }
}
