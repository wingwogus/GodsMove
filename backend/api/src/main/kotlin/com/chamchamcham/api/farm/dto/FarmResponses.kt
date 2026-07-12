package com.chamchamcham.api.farm.dto

import com.chamchamcham.api.crop.dto.CropResponses
import com.chamchamcham.application.farm.FarmResult
import java.math.BigDecimal
import java.util.UUID

object FarmResponses {
    data class FarmResponse(
        val farmId: UUID,
        val name: String,
        val roadAddress: String,
        val jibunAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
        val pnu: String?,
        val landCategory: String?,
        val areaSqm: BigDecimal?,
        val areaIsManualEntry: Boolean,
        val boundaryCoordinates: List<BoundaryCoordinateResponse>,
        val dataSource: DataSourceResponse,
        val crops: List<CropResponses.CropResponse>
    ) {
        companion object {
            fun from(result: FarmResult.Detail): FarmResponse =
                FarmResponse(
                    farmId = result.farmId,
                    name = result.name,
                    roadAddress = result.roadAddress,
                    jibunAddress = result.jibunAddress,
                    latitude = result.latitude,
                    longitude = result.longitude,
                    pnu = result.pnu,
                    landCategory = result.landCategory,
                    areaSqm = result.areaSqm,
                    areaIsManualEntry = result.areaIsManualEntry,
                    boundaryCoordinates = result.boundaryCoordinates.map(BoundaryCoordinateResponse::from),
                    dataSource = DataSourceResponse.from(result.dataSource),
                    crops = result.crops.map(CropResponses.CropResponse::from)
                )
        }
    }

    data class BoundaryCoordinateResponse(
        val latitude: Double,
        val longitude: Double
    ) {
        companion object {
            fun from(result: FarmResult.BoundaryCoordinate): BoundaryCoordinateResponse =
                BoundaryCoordinateResponse(latitude = result.latitude, longitude = result.longitude)
        }
    }

    data class DataSourceResponse(
        val address: String?,
        val coordinate: String?,
        val parcel: String?,
        val landCharacteristic: String?
    ) {
        companion object {
            fun from(result: FarmResult.DataSource): DataSourceResponse =
                DataSourceResponse(
                    address = result.address,
                    coordinate = result.coordinate,
                    parcel = result.parcel,
                    landCharacteristic = result.landCharacteristic
                )
        }
    }
}
