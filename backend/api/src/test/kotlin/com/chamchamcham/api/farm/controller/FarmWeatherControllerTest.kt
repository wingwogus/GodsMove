package com.chamchamcham.api.farm.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.application.weather.FarmWeatherService
import com.chamchamcham.application.weather.WeatherSnapshot
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(FarmWeatherController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class FarmWeatherControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @MockBean private lateinit var farmWeatherService: FarmWeatherService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `returns current weather snapshot`() {
        `when`(farmWeatherService.getCurrentWeather(memberId, farmId)).thenReturn(
            WeatherSnapshot(
                temperature = 14,
                skyCondition = "맑음",
                observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
            )
        )

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather", farmId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.temperature", equalTo(14)))
            .andExpect(jsonPath("$.data.weatherCondition", equalTo("맑음")))
            .andExpect(jsonPath("$.data.observedAt", equalTo("2026-07-08T10:00:00")))
    }

    @Test
    fun `returns unauthorized without auth`() {
        mockMvc.perform(get("/api/v1/farms/{farmId}/weather", farmId))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    @Test
    fun `returns bad request when farm has no coordinates`() {
        `when`(farmWeatherService.getCurrentWeather(memberId, farmId))
            .thenThrow(BusinessException(ErrorCode.WEATHER_LOCATION_REQUIRED))

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather", farmId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("WEATHER_001")))
    }

    @Test
    fun `returns not found when farm is missing or not owned`() {
        `when`(farmWeatherService.getCurrentWeather(memberId, farmId))
            .thenThrow(BusinessException(ErrorCode.FARM_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather", farmId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("FARM_001")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }
}
