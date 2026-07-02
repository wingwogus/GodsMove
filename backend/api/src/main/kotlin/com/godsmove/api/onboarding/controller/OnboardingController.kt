package com.godsmove.api.onboarding.controller

import com.godsmove.api.auth.dto.AuthRequests
import com.godsmove.api.auth.dto.AuthResponses
import com.godsmove.api.common.ApiResponse
import com.godsmove.application.auth.AuthCommand
import com.godsmove.application.auth.OnboardingService
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService
) {
    @PostMapping("/complete")
    fun complete(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: AuthRequests.CompleteOnboardingRequest
    ): ResponseEntity<ApiResponse<AuthResponses.OnboardingCompleteResponse>> {
        val parsedMemberId = parseMemberId(memberId)
        val result = onboardingService.complete(
            AuthCommand.CompleteOnboarding(
                memberId = parsedMemberId,
                name = request.name,
                phone = request.phone,
                birthDate = requireNotNull(request.birthDate),
                nickname = request.nickname,
                region = request.region,
                experienceLevel = request.experienceLevel
            )
        )

        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.OnboardingCompleteResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
