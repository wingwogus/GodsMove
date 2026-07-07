package com.chamchamcham.api.common.dto

import jakarta.validation.constraints.NotNull

object FarmRequests {
    data class BoundaryCoordinateRequest(
        @field:NotNull(message = "경계 위도를 입력해주세요")
        val latitude: Double?,
        @field:NotNull(message = "경계 경도를 입력해주세요")
        val longitude: Double?
    )

    data class DataSourceRequest(
        val address: String? = null,
        val coordinate: String? = null,
        val parcel: String? = null,
        val landCharacteristic: String? = null
    )
}
