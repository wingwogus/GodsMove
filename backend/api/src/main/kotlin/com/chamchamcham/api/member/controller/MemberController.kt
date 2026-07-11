package com.chamchamcham.api.member.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.member.dto.MemberRequests
import com.chamchamcham.api.member.dto.MemberResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.member.MemberProfileCommand
import com.chamchamcham.application.member.MemberProfileService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberProfileService: MemberProfileService
) {
    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<MemberResponses.MyProfileResponse>> {
        val profile = memberProfileService.getMyProfile(parseMemberId(memberId))
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.MyProfileResponse.from(profile)))
    }

    @GetMapping("/me/farm-crops")
    fun getMyFarmCrops(
        @AuthenticationPrincipal memberId: String?
    ): ResponseEntity<ApiResponse<List<MemberResponses.FarmCropsResponse>>> {
        val farmCrops = memberProfileService.getMyFarmCrops(parseMemberId(memberId))
        return ResponseEntity.ok(ApiResponse.ok(farmCrops.map(MemberResponses.FarmCropsResponse::from)))
    }

    @PutMapping("/me/profile")
    fun updateMyProfile(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: MemberRequests.UpdateMyProfileRequest
    ): ResponseEntity<ApiResponse<MemberResponses.MyProfileResponse>> {
        val profile = memberProfileService.updateMyProfile(
            MemberProfileCommand.UpdateMyProfile(
                memberId = parseMemberId(memberId),
                name = request.name,
                phone = request.phone,
                birthDate = requireNotNull(request.birthDate),
                nickname = request.nickname,
                experienceLevel = requireNotNull(request.experienceLevel),
                managementType = requireNotNull(request.managementType),
                profileMediaId = request.profileMediaId,
                farms = request.farms.map { farm ->
                    MemberProfileCommand.Farm(
                        farmId = farm.farmId,
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
                            MemberProfileCommand.FarmBoundaryCoordinate(
                                latitude = requireNotNull(it.latitude),
                                longitude = requireNotNull(it.longitude)
                            )
                        },
                        dataSource = MemberProfileCommand.FarmDataSource(
                            address = farm.dataSource.address,
                            coordinate = farm.dataSource.coordinate,
                            parcel = farm.dataSource.parcel,
                            landCharacteristic = farm.dataSource.landCharacteristic
                        ),
                        cropIds = farm.cropIds
                    )
                }
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.MyProfileResponse.from(profile)))
    }

    @GetMapping("/{memberId}/profile")
    fun getPublicProfile(
        @AuthenticationPrincipal authenticatedMemberId: String?,
        @PathVariable memberId: UUID
    ): ResponseEntity<ApiResponse<MemberResponses.PublicProfileResponse>> {
        parseMemberId(authenticatedMemberId)
        val profile = memberProfileService.getPublicProfile(memberId)
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.PublicProfileResponse.from(profile)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
