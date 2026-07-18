package com.chamchamcham.application.auth.common

import com.chamchamcham.application.farm.FarmCommand
import com.chamchamcham.domain.member.ManagementType
import java.time.LocalDate
import java.util.UUID

object AuthCommand {
    data class SendVerificationCode(
        val email: String
    )

    data class VerifyEmailCode(
        val email: String,
        val code: String
    )

    data class SignUp(
        val email: String,
        val password: String
    )

    data class Login(
        val email: String,
        val password: String
    )

    data class KakaoLogin(
        val idToken: String,
        val nonce: String,
        val kakaoAccessToken: String? = null
    )

    data class AppleLogin(
        val identityToken: String,
        val nonce: String,
        val authorizationCode: String?,
        val userIdentifier: String?,
        val name: String? = null
    )

    data class NaverLogin(
        val accessToken: String
    )

    data class CompleteOnboarding(
        val memberId: UUID,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String?,
        val experienceLevel: Int,
        val managementType: ManagementType,
        val farm: FarmCommand.Draft,
        val cropIds: List<UUID>,
        val profileMediaId: UUID?
    )

    data class Reissue(
        val refreshToken: String
    )
}
