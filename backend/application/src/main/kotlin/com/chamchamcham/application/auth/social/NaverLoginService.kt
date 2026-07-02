package com.chamchamcham.application.auth.social

import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.auth.common.OnboardingStatusResolver

import com.chamchamcham.application.exception.ErrorCode
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
