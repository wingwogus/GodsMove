package com.chamchamcham.application.farm

import com.chamchamcham.application.crop.CropResult
import com.chamchamcham.domain.farm.Farm
import java.math.BigDecimal
import java.util.UUID

object FarmResult {
    data class Detail(
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
        val boundaryCoordinates: List<BoundaryCoordinate>,
        val dataSource: DataSource,
        val crops: List<CropResult.CropSummary>
    ) {
        companion object {
            fun from(farm: Farm, crops: List<CropResult.CropSummary>): Detail =
                Detail(
                    farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
                    name = farm.name,
                    roadAddress = farm.roadAddress,
                    jibunAddress = farm.jibunAddress,
                    latitude = farm.latitude,
                    longitude = farm.longitude,
                    pnu = farm.pnu,
                    landCategory = farm.landCategory,
                    areaSqm = farm.areaSqm,
                    areaIsManualEntry = farm.areaIsManualEntry,
                    boundaryCoordinates = farm.boundaryCoordinates.map {
                        BoundaryCoordinate(
                            latitude = requireNotNull(it.latitude) { "Boundary latitude is required" },
                            longitude = requireNotNull(it.longitude) { "Boundary longitude is required" }
                        )
                    },
                    dataSource = DataSource(
                        address = farm.dataSource.address,
                        coordinate = farm.dataSource.coordinate,
                        parcel = farm.dataSource.parcel,
                        landCharacteristic = farm.dataSource.landCharacteristic
                    ),
                    crops = crops
                )
        }
    }

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
