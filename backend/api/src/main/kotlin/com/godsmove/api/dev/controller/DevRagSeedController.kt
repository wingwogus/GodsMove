package com.godsmove.api.dev.controller

import com.godsmove.api.common.ApiResponse
import com.godsmove.api.dev.dto.DevRagSeedRequests
import com.godsmove.api.dev.dto.DevRagSeedResponses
import com.godsmove.application.coaching.rag.seed.DevRagSeedService
import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.security.TokenProvider
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/rag")
class DevRagSeedController(
    private val devRagSeedService: DevRagSeedService,
    private val tokenProvider: TokenProvider,
    @Value("\${app.dev.rag-seed-key:}")
    private val devSeedKey: String
) {
    @PostMapping("/seed")
    fun seed(
        @RequestHeader(DEV_SEED_KEY_HEADER, required = false) seedKey: String?,
        @Valid @RequestBody request: DevRagSeedRequests.SeedRequest
    ): ResponseEntity<ApiResponse<DevRagSeedResponses.SeedResponse>> {
        requireValidSeedKey(seedKey)

        val result = devRagSeedService.seed(request.toCommand())
        val accessToken = tokenProvider.createAccessToken(result.memberId, "ROLE_USER")

        return ResponseEntity.ok(
            ApiResponse.ok(DevRagSeedResponses.SeedResponse.from(result, accessToken))
        )
    }

    private fun requireValidSeedKey(seedKey: String?) {
        if (devSeedKey.isBlank() || seedKey != devSeedKey) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }
    }

    private companion object {
        const val DEV_SEED_KEY_HEADER = "X-GodsMove-Dev-Seed-Key"
    }
}
