package com.chamchamcham.api.member.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.member.dto.MemberResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.member.MemberProfileService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
