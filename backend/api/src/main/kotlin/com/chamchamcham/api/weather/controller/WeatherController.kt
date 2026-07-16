package com.chamchamcham.api.weather.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.weather.dto.WeatherResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.weather.FarmWeatherService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/weather")
class WeatherController(
    private val farmWeatherService: FarmWeatherService
) {
    @GetMapping("/home")
    fun home(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) farmId: UUID?
    ): ResponseEntity<ApiResponse<WeatherResponses.HomeResponse>> {
        val result = farmWeatherService.getHome(parseMemberId(memberId), farmId)
        return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.HomeResponse.from(result)))
    }

    @GetMapping("/detail")
    fun detail(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) farmId: UUID?
    ): ResponseEntity<ApiResponse<WeatherResponses.DetailResponse>> {
        val result = farmWeatherService.getDetail(parseMemberId(memberId), farmId)
        return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.DetailResponse.from(result)))
    }

    @GetMapping("/daily")
    fun daily(
        @AuthenticationPrincipal memberId: String?,
        @RequestParam(required = false) farmId: UUID?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<ApiResponse<WeatherResponses.DailyResponse>> {
        val result = farmWeatherService.getDaily(parseMemberId(memberId), farmId, date)
        return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.DailyResponse.from(result)))
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
