package com.chamchamcham.api.member.dto

import com.chamchamcham.domain.member.ManagementType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
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
        val profileMediaId: UUID? = null,
        @field:Valid
        @field:NotEmpty(message = "농장 정보를 하나 이상 입력해주세요")
        val farms: List<FarmRequest>
    )

    data class FarmRequest(
        val farmId: UUID? = null,
        @field:NotBlank(message = "농장 이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "도로명 주소를 입력해주세요")
        val roadAddress: String,
        val jibunAddress: String? = null,
        @field:NotNull(message = "위도를 입력해주세요")
        val latitude: Double?,
        @field:NotNull(message = "경도를 입력해주세요")
        val longitude: Double?,
        val pnu: String? = null,
        val landCategory: String? = null,
        @field:DecimalMin(value = "0.0", inclusive = false, message = "면적은 0보다 커야 합니다")
        val areaSqm: BigDecimal? = null,
        val areaIsManualEntry: Boolean = false,
        @field:Valid
        val boundaryCoordinates: List<FarmBoundaryCoordinateRequest> = emptyList(),
        @field:Valid
        val dataSource: FarmDataSourceRequest = FarmDataSourceRequest(),
        @field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
        val cropIds: List<UUID>
    )

    data class FarmBoundaryCoordinateRequest(
        @field:NotNull(message = "경계 위도를 입력해주세요")
        val latitude: Double?,
        @field:NotNull(message = "경계 경도를 입력해주세요")
        val longitude: Double?
    )

    data class FarmDataSourceRequest(
        val address: String? = null,
        val coordinate: String? = null,
        val parcel: String? = null,
        val landCharacteristic: String? = null
    )
}
