package com.chamchamcham.application.member

import com.chamchamcham.domain.member.ManagementType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object MemberProfileCommand {
    data class UpdateMyProfile(
        val memberId: UUID,
        val name: String,
        val phone: String,
        val birthDate: LocalDate,
        val nickname: String,
        val experienceLevel: Int,
        val managementType: ManagementType,
        val profileMediaId: UUID?,
        val farms: List<Farm>
    )

    data class Farm(
        val farmId: UUID?,
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
        val dataSource: FarmDataSource,
        val cropIds: List<UUID>
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
}
