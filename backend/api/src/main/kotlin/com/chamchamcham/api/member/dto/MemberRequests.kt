package com.chamchamcham.api.member.dto

import com.chamchamcham.domain.member.ManagementType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.UUID

object MemberRequests {
    data class UpdateMyProfileRequest(
        @field:NotBlank(message = "이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "전화번호를 입력해주세요")
        val phone: String,
        @field:NotNull(message = "생년월일을 입력해주세요")
        val birthDate: LocalDate?,
        @field:NotBlank(message = "닉네임을 입력해주세요")
        val nickname: String,
        @field:NotNull(message = "경험 수준을 입력해주세요")
        @field:Min(value = 0, message = "경험 수준은 0 이상이어야 합니다")
        @field:Max(value = 100, message = "경험 수준은 100 이하여야 합니다")
        val experienceLevel: Int?,
        @field:NotNull(message = "경영 형태를 입력해주세요")
        val managementType: ManagementType?,
        val profileMediaId: UUID? = null
    )
}
