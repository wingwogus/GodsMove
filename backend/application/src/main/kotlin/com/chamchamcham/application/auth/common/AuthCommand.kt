package com.chamchamcham.application.auth.common

import com.chamchamcham.domain.member.ManagementType
import java.math.BigDecimal
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
        val userIdentifier: String?
    )

    data class NaverLogin(
        val accessToken: String
    )

    data class CompleteOnboarding(
        val memberId: UUID,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String,
        val experienceLevel: Int,
        val managementType: ManagementType,
        val farm: Farm,
        val cropIds: List<UUID>,
        val profileMediaId: UUID?
    )

    data class Farm(
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<FarmBoundaryCoordinate>,
        val dataSource: FarmDataSource
    )

    data class FarmBoundaryCoordinate(
        val latitude: Double,
        val longitude: Double
    )

    data class FarmDataSource(
        val address: String?,
        val coordinate: String?,
        val parcel: String?,
        val landCharacteristic: String?
    )

    data class Reissue(
        val refreshToken: String
    )
}
