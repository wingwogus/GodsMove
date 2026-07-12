package com.chamchamcham.application.farm

import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmBoundaryCoordinate
import com.chamchamcham.domain.farm.FarmDataSource
import com.chamchamcham.domain.member.Member
import java.math.BigDecimal
import java.util.UUID

object FarmCommand {
    data class Create(
        val memberId: UUID,
        val draft: Draft,
        val cropIds: List<UUID>
    )

    data class Replace(
        val memberId: UUID,
        val farmId: UUID,
        val draft: Draft,
        val cropIds: List<UUID>
    )

    data class Delete(
        val memberId: UUID,
        val farmId: UUID
    )

    data class Draft(
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double,
        val longitude: Double,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<BoundaryCoordinate>,
        val dataSource: DataSource
    )

    data class BoundaryCoordinate(
        val latitude: Double,
        val longitude: Double
    )

    data class DataSource(
        val address: String?,
        val coordinate: String?,
        val parcel: String?,
        val landCharacteristic: String?
    )
}

fun FarmCommand.Draft.toFarm(owner: Member): Farm =
    Farm(
        owner = owner,
        name = name,
        roadAddress = roadAddress,
        jibunAddress = jibunAddress,
        latitude = latitude,
        longitude = longitude,
        pnu = pnu,
        landCategory = landCategory,
        areaSqm = areaSqm,
        areaIsManualEntry = areaIsManualEntry,
        boundaryCoordinates = boundaryCoordinates.map {
            FarmBoundaryCoordinate(latitude = it.latitude, longitude = it.longitude)
        }.toMutableList(),
        dataSource = dataSource.toDomain()
    )

fun Farm.apply(draft: FarmCommand.Draft) {
    updateProfile(
        name = draft.name,
        roadAddress = draft.roadAddress,
        jibunAddress = draft.jibunAddress,
        latitude = draft.latitude,
        longitude = draft.longitude,
        pnu = draft.pnu,
        landCategory = draft.landCategory,
        areaSqm = draft.areaSqm,
        areaIsManualEntry = draft.areaIsManualEntry,
        boundaryCoordinates = draft.boundaryCoordinates.map {
            FarmBoundaryCoordinate(latitude = it.latitude, longitude = it.longitude)
        },
        dataSource = draft.dataSource.toDomain()
    )
}

private fun FarmCommand.DataSource.toDomain(): FarmDataSource =
    FarmDataSource(
        address = address,
        coordinate = coordinate,
        parcel = parcel,
        landCharacteristic = landCharacteristic
    )
