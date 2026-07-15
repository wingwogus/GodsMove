package com.chamchamcham.api.farm.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.DailyWeatherSummary
import com.chamchamcham.application.weather.FarmWeatherResult
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
import java.time.LocalDate
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
            FarmWeatherResult.CurrentDetail(
                snapshot = WeatherSnapshot(
                    temperature = 14,
                    skyCondition = "맑음",
                    observedAt = LocalDateTime.of(2026, 7, 8, 10, 0),
                    humidity = 65,
                    windSpeed = 2.3
                ),
                roadAddress = "서울시 강남구",
                precipitationProbability = null,
                forecast = emptyList(),
                uvIndex = 7
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
            .andExpect(jsonPath("$.data.address", equalTo("서울시 강남구")))
            .andExpect(jsonPath("$.data.humidity", equalTo(65)))
            .andExpect(jsonPath("$.data.windSpeed", equalTo(2.3)))
            .andExpect(jsonPath("$.data.uvIndex", equalTo(7)))
    }

    @Test
    fun `returns current weather for default farm without farmId`() {
        `when`(farmWeatherService.getCurrentWeather(memberId)).thenReturn(
            FarmWeatherResult.CurrentDetail(
                snapshot = WeatherSnapshot(
                    temperature = 14,
                    skyCondition = "맑음",
                    observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
                ),
                roadAddress = "서울시 강남구",
                precipitationProbability = null,
                forecast = emptyList(),
                uvIndex = null
            )
        )

        mockMvc.perform(
            get("/api/v1/farms/weather")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.temperature", equalTo(14)))
            .andExpect(jsonPath("$.data.weatherCondition", equalTo("맑음")))
            .andExpect(jsonPath("$.data.observedAt", equalTo("2026-07-08T10:00:00")))
            .andExpect(jsonPath("$.data.address", equalTo("서울시 강남구")))
    }

    @Test
    fun `returns forecast and precipitation probability in response`() {
        `when`(farmWeatherService.getCurrentWeather(memberId, farmId)).thenReturn(
            FarmWeatherResult.CurrentDetail(
                snapshot = WeatherSnapshot(
                    temperature = 14,
                    skyCondition = "맑음",
                    observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
                ),
                roadAddress = "서울시 강남구",
                precipitationProbability = 30,
                forecast = listOf(
                    DailyForecast(
                        date = LocalDate.of(2026, 7, 8),
                        minTemperature = 18,
                        maxTemperature = 29,
                        skyCondition = "맑음"
                    )
                ),
                uvIndex = null
            )
        )

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather", farmId)
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.precipitationProbability", equalTo(30)))
            .andExpect(jsonPath("$.data.forecast[0].date", equalTo("2026-07-08")))
            .andExpect(jsonPath("$.data.forecast[0].weatherCondition", equalTo("맑음")))
            .andExpect(jsonPath("$.data.forecast[0].minTemperature", equalTo(18)))
            .andExpect(jsonPath("$.data.forecast[0].maxTemperature", equalTo(29)))
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

    @Test
    fun `returns daily weather summary for explicit farm`() {
        `when`(farmWeatherService.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1))).thenReturn(
            DailyWeatherSummary(
                date = LocalDate.of(2026, 7, 1),
                skyCondition = "흐림",
                minTemperature = 18,
                maxTemperature = 27
            )
        )

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather/daily", farmId)
                .param("date", "2026-07-01")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.date", equalTo("2026-07-01")))
            .andExpect(jsonPath("$.data.weatherCondition", equalTo("흐림")))
            .andExpect(jsonPath("$.data.minTemperature", equalTo(18)))
            .andExpect(jsonPath("$.data.maxTemperature", equalTo(27)))
    }

    @Test
    fun `returns daily weather summary for default farm`() {
        `when`(farmWeatherService.getDailyWeather(memberId, LocalDate.of(2026, 7, 1))).thenReturn(
            DailyWeatherSummary(
                date = LocalDate.of(2026, 7, 1),
                skyCondition = "흐림",
                minTemperature = 18,
                maxTemperature = 27
            )
        )

        mockMvc.perform(
            get("/api/v1/farms/weather/daily")
                .param("date", "2026-07-01")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.date", equalTo("2026-07-01")))
            .andExpect(jsonPath("$.data.weatherCondition", equalTo("흐림")))
            .andExpect(jsonPath("$.data.minTemperature", equalTo(18)))
            .andExpect(jsonPath("$.data.maxTemperature", equalTo(27)))
    }

    @Test
    fun `returns bad request when daily weather date is malformed`() {
        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather/daily", farmId)
                .param("date", "not-a-date")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("COMMON_001")))
    }

    @Test
    fun `returns bad request when daily weather date is in the future`() {
        `when`(farmWeatherService.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1)))
            .thenThrow(BusinessException(ErrorCode.WEATHER_DATE_IN_FUTURE))

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather/daily", farmId)
                .param("date", "2026-07-01")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code", equalTo("WEATHER_003")))
    }

    @Test
    fun `returns not found when daily weather farm is missing or not owned`() {
        `when`(farmWeatherService.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1)))
            .thenThrow(BusinessException(ErrorCode.FARM_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather/daily", farmId)
                .param("date", "2026-07-01")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("FARM_001")))
    }

    @Test
    fun `returns not found when daily weather data is unavailable`() {
        `when`(farmWeatherService.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1)))
            .thenThrow(BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND))

        mockMvc.perform(
            get("/api/v1/farms/{farmId}/weather/daily", farmId)
                .param("date", "2026-07-01")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code", equalTo("WEATHER_004")))
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
