package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@ExtendWith(MockitoExtension::class)
class WeatherParallelFetcherTest {

    @Mock private lateinit var currentObservationPort: CurrentObservationPort
    @Mock private lateinit var shortTermForecastPort: ShortTermForecastPort
    @Mock private lateinit var midTermForecastPort: MidTermForecastPort
    @Mock private lateinit var uvIndexPort: UvIndexPort

    private lateinit var fetcher: WeatherParallelFetcher
    private lateinit var clock: Clock
    private lateinit var location: WeatherLocation
    private lateinit var today: LocalDate
    private lateinit var d4Date: LocalDate

    @BeforeEach
    fun setUp() {
        clock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 15, 21, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
        )
        today = LocalDate.now(clock)
        d4Date = today.plusDays(4)
        location = WeatherLocation(
            latitude = 37.5,
            longitude = 127.0,
            roadAddress = "서울특별시 중구 세종대로 110",
            pnu = "1114010100"
        )
        fetcher = WeatherParallelFetcher(
            currentObservationPort = currentObservationPort,
            shortTermForecastPort = shortTermForecastPort,
            midTermForecastPort = midTermForecastPort,
            uvIndexPort = uvIndexPort,
            clock = clock
        )
    }

    @Test
    fun `필수 소스인 실황이 BusinessException을 던지면 CompletionException이 아니라 그대로 전파된다`() {
        given(currentObservationPort.fetch(location))
            .willThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        val thrown = assertThrows(BusinessException::class.java) {
            fetcher.fetchDetail(location)
        }

        assertThat(thrown.errorCode).isEqualTo(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)
    }

    @Test
    fun `자외선이 예외를 던져도 나머지는 정상이고 missing에 uvIndex가 담긴다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        given(shortTermForecastPort.fetchLatest(location)).willReturn(shortTermForecast(includeD4 = true))
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(dailyForecast(today))
        given(uvIndexPort.fetch(location)).willThrow(RuntimeException("boom"))

        val result = fetcher.fetchDetail(location)

        assertThat(result.current).isNotNull()
        assertThat(result.latest).isNotNull()
        assertThat(result.todayRange).isNotNull()
        assertThat(result.uvIndex).isNull()
        assertThat(result.partial.missing).containsExactly("uvIndex")
        assertThat(result.partial.degraded).isTrue()
    }

    @Test
    fun `중기예보가 예외를 던져도 나머지는 정상이고 missing에 forecast D4가 담긴다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        // latest가 D4를 안 주는 상황이어야 중기 실패가 실제로 D4 손실로 이어진다.
        given(shortTermForecastPort.fetchLatest(location)).willReturn(shortTermForecast(includeD4 = false))
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(dailyForecast(today))
        given(midTermForecastPort.fetch(location, d4Date)).willThrow(RuntimeException("boom"))
        given(uvIndexPort.fetch(location)).willReturn(5)

        val result = fetcher.fetchDetail(location)

        assertThat(result.midTermD4).isNull()
        assertThat(result.partial.missing).containsExactly("forecast.D4")
        assertThat(result.partial.degraded).isTrue()
    }

    @Test
    fun `todayRange가 null이면 missing에 todayMinMax가 담긴다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        given(shortTermForecastPort.fetchLatest(location)).willReturn(shortTermForecast(includeD4 = false))
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(null)

        val result = fetcher.fetchHome(location)

        assertThat(result.todayRange).isNull()
        assertThat(result.partial.missing).containsExactly("todayMinMax")
    }

    @Test
    fun `latest가 null이면 missing에 forecast가 담긴다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        given(shortTermForecastPort.fetchLatest(location)).willReturn(null)
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(dailyForecast(today))

        val result = fetcher.fetchHome(location)

        assertThat(result.latest).isNull()
        assertThat(result.partial.missing).containsExactly("forecast")
    }

    @Test
    fun `전부 성공하면 missing이 비고 degraded는 false다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        given(shortTermForecastPort.fetchLatest(location)).willReturn(shortTermForecast(includeD4 = true))
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(dailyForecast(today))
        given(uvIndexPort.fetch(location)).willReturn(5)

        val result = fetcher.fetchDetail(location)

        assertThat(result.partial.missing).isEmpty()
        assertThat(result.partial.degraded).isFalse()
    }

    @Test
    fun `fetchHome은 자외선과 중기예보 포트를 호출하지 않는다`() {
        given(currentObservationPort.fetch(location)).willReturn(currentObservation())
        given(shortTermForecastPort.fetchLatest(location)).willReturn(shortTermForecast(includeD4 = false))
        given(shortTermForecastPort.fetchTodayRange(location)).willReturn(dailyForecast(today))

        fetcher.fetchHome(location)

        verifyNoInteractions(uvIndexPort)
        verifyNoInteractions(midTermForecastPort)
    }

    private fun currentObservation(): CurrentObservation =
        CurrentObservation(
            temperature = 24,
            precipitationType = null,
            observedAt = LocalDateTime.now(clock),
            humidity = 85,
            windSpeed = 1.7,
            feelsLikeTemperature = 26
        )

    private fun shortTermForecast(includeD4: Boolean): ShortTermForecast {
        val forecasts = mutableListOf(dailyForecast(today))
        if (includeD4) {
            forecasts.add(dailyForecast(d4Date))
        }
        return ShortTermForecast(
            currentSky = WeatherCondition.CLEAR,
            precipitationProbability = 0,
            dailyForecasts = forecasts
        )
    }

    private fun dailyForecast(date: LocalDate): DailyForecast =
        DailyForecast(
            date = date,
            condition = WeatherCondition.CLEAR,
            minTemperature = 25,
            maxTemperature = 29
        )
}
