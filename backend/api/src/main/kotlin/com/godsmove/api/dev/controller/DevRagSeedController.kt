package com.godsmove.api.dev.controller

import com.godsmove.api.common.ApiResponse
import com.godsmove.api.dev.dto.DevRagSeedRequests
import com.godsmove.api.dev.dto.DevRagSeedResponses
import com.godsmove.application.coaching.rag.seed.DevRagSeedService
import com.godsmove.application.security.TokenProvider
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/rag")
class DevRagSeedController(
    private val devRagSeedService: DevRagSeedService,
    private val tokenProvider: TokenProvider
) {
    @PostMapping("/seed")
    fun seed(
        @Valid @RequestBody request: DevRagSeedRequests.SeedRequest
    ): ResponseEntity<ApiResponse<DevRagSeedResponses.SeedResponse>> {
        val result = devRagSeedService.seed(request.toCommand())
        val accessToken = tokenProvider.createAccessToken(result.memberId, "ROLE_USER")

        return ResponseEntity.ok(
            ApiResponse.ok(DevRagSeedResponses.SeedResponse.from(result, accessToken))
        )
    }
}
