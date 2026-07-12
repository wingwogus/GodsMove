package com.chamchamcham.api.farm.dto

import com.chamchamcham.application.farm.FarmCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements
import java.math.BigDecimal
import java.util.UUID

object FarmRequests {
    data class FarmDraftRequest(
        @field:NotBlank(message = "농장 이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "도로명 주소를 입력해주세요")
        val roadAddress: String,
        val jibunAddress: String? = null,
        @field:NotNull(message = "위도를 입력해주세요")
        @field:DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
        val latitude: Double?,
        @field:NotNull(message = "경도를 입력해주세요")
        @field:DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
        val longitude: Double?,
        val pnu: String? = null,
        val landCategory: String? = null,
        @field:DecimalMin(value = "0.0", inclusive = false, message = "면적은 0보다 커야 합니다")
        val areaSqm: BigDecimal? = null,
        val areaIsManualEntry: Boolean = false,
        @field:Valid
        val boundaryCoordinates: List<BoundaryCoordinateRequest> = emptyList(),
        @field:Valid
        val dataSource: DataSourceRequest = DataSourceRequest()
    )

    data class SaveFarmRequest(
        @field:NotBlank(message = "농장 이름을 입력해주세요")
        val name: String,
        @field:NotBlank(message = "도로명 주소를 입력해주세요")
        val roadAddress: String,
        val jibunAddress: String? = null,
        @field:NotNull(message = "위도를 입력해주세요")
        @field:DecimalMin(value = "-90.0", message = "위도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "90.0", message = "위도 범위가 올바르지 않습니다")
        val latitude: Double?,
        @field:NotNull(message = "경도를 입력해주세요")
        @field:DecimalMin(value = "-180.0", message = "경도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "180.0", message = "경도 범위가 올바르지 않습니다")
        val longitude: Double?,
        val pnu: String? = null,
        val landCategory: String? = null,
        @field:DecimalMin(value = "0.0", inclusive = false, message = "면적은 0보다 커야 합니다")
        val areaSqm: BigDecimal? = null,
        val areaIsManualEntry: Boolean = false,
        @field:Valid
        val boundaryCoordinates: List<BoundaryCoordinateRequest> = emptyList(),
        @field:Valid
        val dataSource: DataSourceRequest = DataSourceRequest(),
        @field:NotEmpty(message = "작물을 하나 이상 선택해주세요")
        @field:Size(max = 5, message = "작물은 최대 5개까지 선택할 수 있습니다")
        @field:UniqueElements(message = "작물은 중복해서 선택할 수 없습니다")
        val cropIds: List<UUID>
    )

    data class BoundaryCoordinateRequest(
        @field:NotNull(message = "경계 위도를 입력해주세요")
        @field:DecimalMin(value = "-90.0", message = "경계 위도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "90.0", message = "경계 위도 범위가 올바르지 않습니다")
        val latitude: Double?,
        @field:NotNull(message = "경계 경도를 입력해주세요")
        @field:DecimalMin(value = "-180.0", message = "경계 경도 범위가 올바르지 않습니다")
        @field:DecimalMax(value = "180.0", message = "경계 경도 범위가 올바르지 않습니다")
        val longitude: Double?
    )

    data class DataSourceRequest(
        val address: String? = null,
        val coordinate: String? = null,
        val parcel: String? = null,
        val landCharacteristic: String? = null
    )
}

fun FarmRequests.FarmDraftRequest.toCommandDraft(): FarmCommand.Draft =
    FarmCommand.Draft(
        name = name,
        roadAddress = roadAddress,
        jibunAddress = jibunAddress,
        latitude = requireNotNull(latitude),
        longitude = requireNotNull(longitude),
        pnu = pnu,
        landCategory = landCategory,
        areaSqm = areaSqm,
        areaIsManualEntry = areaIsManualEntry,
        boundaryCoordinates = boundaryCoordinates.map {
            FarmCommand.BoundaryCoordinate(
                latitude = requireNotNull(it.latitude),
                longitude = requireNotNull(it.longitude)
            )
        },
        dataSource = FarmCommand.DataSource(
            address = dataSource.address,
            coordinate = dataSource.coordinate,
            parcel = dataSource.parcel,
            landCharacteristic = dataSource.landCharacteristic
        )
    )

fun FarmRequests.SaveFarmRequest.toCommandDraft(): FarmCommand.Draft =
    FarmCommand.Draft(
        name = name,
        roadAddress = roadAddress,
        jibunAddress = jibunAddress,
        latitude = requireNotNull(latitude),
        longitude = requireNotNull(longitude),
        pnu = pnu,
        landCategory = landCategory,
        areaSqm = areaSqm,
        areaIsManualEntry = areaIsManualEntry,
        boundaryCoordinates = boundaryCoordinates.map {
            FarmCommand.BoundaryCoordinate(
                latitude = requireNotNull(it.latitude),
                longitude = requireNotNull(it.longitude)
            )
        },
        dataSource = FarmCommand.DataSource(
            address = dataSource.address,
            coordinate = dataSource.coordinate,
            parcel = dataSource.parcel,
            landCharacteristic = dataSource.landCharacteristic
        )
    )
