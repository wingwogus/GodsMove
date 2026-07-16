package com.chamchamcham.api.weather.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.application.weather.DailyForecast
import com.chamchamcham.application.weather.DailyWeather
import com.chamchamcham.application.weather.DetailWeather
import com.chamchamcham.application.weather.FarmWeatherService
import com.chamchamcham.application.weather.HomeWeather
import com.chamchamcham.application.weather.PartialFailure
import com.chamchamcham.application.weather.WeatherCondition
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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

@WebMvcTest(WeatherController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class WeatherControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @MockBean private lateinit var farmWeatherService: FarmWeatherService
    @MockBean private lateinit var tokenProvider: TokenProvider

    @Test
    fun `홈 날씨를 조회한다`() {
        given(farmWeatherService.getHome(memberId, farmId)).willReturn(homeWeather())

        mockMvc.perform(
            get("/api/v1/weather/home")
                .param("farmId", farmId.toString())
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.farmId", equalTo(farmId.toString())))
            .andExpect(jsonPath("$.data.condition.code", equalTo("CLEAR")))
            .andExpect(jsonPath("$.data.condition.text", equalTo("맑음")))
    }

    @Test
    fun `farmId 생략 시 서비스에 null이 전달된다`() {
        given(farmWeatherService.getHome(memberId, null)).willReturn(homeWeather())

        mockMvc.perform(
            get("/api/v1/weather/home")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `상세 날씨를 조회하고 condition은 code text 중첩 객체다`() {
        given(farmWeatherService.getDetail(memberId, farmId)).willReturn(detailWeather())

        mockMvc.perform(
            get("/api/v1/weather/detail")
                .param("farmId", farmId.toString())
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.current.condition.code", equalTo("CLEAR")))
            .andExpect(jsonPath("$.data.current.condition.text", equalTo("맑음")))
            .andExpect(jsonPath("$.data.forecast[0].dayOfWeek", equalTo("WEDNESDAY")))
            .andExpect(jsonPath("$.data.forecast[0].condition.code", equalTo("CLEAR")))
    }

    @Test
    fun `기록 날씨를 조회한다`() {
        given(farmWeatherService.getDaily(memberId, farmId, LocalDate.of(2026, 7, 15)))
            .willReturn(DailyWeather(LocalDate.of(2026, 7, 15), WeatherCondition.CLEAR, 25, 29))

        mockMvc.perform(
            get("/api/v1/weather/daily")
                .param("farmId", farmId.toString())
                .param("date", "2026-07-15")
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.date", equalTo("2026-07-15")))
            .andExpect(jsonPath("$.data.condition.code", equalTo("CLEAR")))
            .andExpect(jsonPath("$.data.minTemperature", equalTo(25)))
            .andExpect(jsonPath("$.data.maxTemperature", equalTo(29)))
    }

    @Test
    fun `기록 조회에 date가 없으면 400`() {
        mockMvc.perform(
            get("/api/v1/weather/daily")
                .param("farmId", farmId.toString())
                .with(authenticatedMember(memberId.toString()))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `인증되지 않으면 401`() {
        mockMvc.perform(get("/api/v1/weather/home"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor =
        RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }

    private fun homeWeather(): HomeWeather =
        HomeWeather(
            farmId = farmId,
            temperature = 24,
            condition = WeatherCondition.CLEAR,
            minTemperature = 25,
            maxTemperature = 29,
            observedAt = LocalDateTime.of(2026, 7, 15, 21, 0),
            partial = PartialFailure.of()
        )

    private fun detailWeather(): DetailWeather =
        DetailWeather(
            farmId = farmId,
            address = "서울 중구 세종대로 110",
            observedAt = LocalDateTime.of(2026, 7, 15, 21, 0),
            temperature = 24,
            feelsLikeTemperature = 26,
            condition = WeatherCondition.CLEAR,
            minTemperature = 25,
            maxTemperature = 29,
            humidity = 85,
            windSpeed = 1.7,
            precipitationProbability = 0,
            uvIndex = 1,
            forecast = listOf(
                DailyForecast(LocalDate.of(2026, 7, 15), WeatherCondition.CLEAR, 25, 29)
            ),
            partial = PartialFailure.of()
        )
}
