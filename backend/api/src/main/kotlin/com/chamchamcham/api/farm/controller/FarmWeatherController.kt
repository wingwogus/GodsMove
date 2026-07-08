package com.chamchamcham.api.farm.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.farm.dto.WeatherResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.FarmWeatherService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/farms")
class FarmWeatherController(
    private val farmWeatherService: FarmWeatherService
) {
    @GetMapping("/{farmId}/weather")
    fun getCurrentWeather(
        @AuthenticationPrincipal memberId: String?,
        @PathVariable farmId: UUID
    ): ResponseEntity<ApiResponse<WeatherResponses.CurrentWeatherResponse>> {
        val snapshot = farmWeatherService.getCurrentWeather(parseMemberId(memberId), farmId)
        return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.CurrentWeatherResponse.from(snapshot)))
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
