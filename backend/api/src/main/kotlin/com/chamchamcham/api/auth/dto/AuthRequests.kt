package com.chamchamcham.api.auth.dto

import com.chamchamcham.api.farm.dto.FarmRequests
import com.chamchamcham.domain.member.ManagementType
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.time.LocalDate
import java.time.Period
import java.util.UUID

object AuthRequests {
    data class SendVerificationCodeRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        val email: String
    )

    data class VerifyEmailCodeRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        val email: String,
        @field:NotBlank(message = "인증번호를 입력해주세요")
        @field:Size(min = 6, max = 6, message = "인증번호는 6자리여야 합니다")
        val code: String
    )

    data class SignUpRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요")
        @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
        val password: String
    )

    data class LoginRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요")
        val password: String
    )

    data class KakaoLoginRequest(
        @field:NotBlank(message = "카카오 ID 토큰을 입력해주세요")
        val idToken: String,
        @field:NotBlank(message = "nonce를 입력해주세요")
        val nonce: String,
        val kakaoAccessToken: String? = null
    )

    data class AppleLoginRequest(
        @field:NotBlank(message = "애플 identity token을 입력해주세요")
        val identityToken: String,
        @field:NotBlank(message = "nonce를 입력해주세요")
        val nonce: String,
        val authorizationCode: String? = null,
        val userIdentifier: String? = null
    )

    data class NaverLoginRequest(
        @field:NotBlank(message = "네이버 access token을 입력해주세요")
        val accessToken: String
    )

    data class CompleteOnboardingRequest(
        val name: String? = null,
        val phone: String? = null,
        val birthDate: LocalDate? = null,
        val nickname: String? = null,
        @field:NotNull(message = "경험 수준을 입력해주세요")
        @field:Min(value = 0, message = "경험 수준은 0 이상이어야 합니다")
        @field:Max(value = 100, message = "경험 수준은 100 이하여야 합니다")
        val experienceLevel: Int?,
        @field:NotNull(message = "경영 형태를 입력해주세요")
        val managementType: ManagementType?,
        @field:Valid
        @field:NotNull(message = "농장 정보를 입력해주세요")
        val farm: FarmRequests.FarmDraftRequest?,
        @field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
        @field:Size(max = 5, message = "작물은 최대 5개까지 선택할 수 있습니다")
        @field:UniqueElements(message = "작물은 중복해서 선택할 수 없습니다")
        val cropIds: List<UUID>,
        val profileMediaId: UUID? = null
    ) {
        @AssertTrue(message = "귀농연차는 만 나이를 초과할 수 없습니다")
        fun isExperienceLevelWithinAge(): Boolean {
            val age = birthDate?.let { Period.between(it, LocalDate.now()).years }
            return age == null || experienceLevel == null || experienceLevel <= age
        }
    }

    data class ReissueRequest(
        val refreshToken: String? = null
    )
}
