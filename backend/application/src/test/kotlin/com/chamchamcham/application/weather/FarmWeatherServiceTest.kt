package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmWeatherServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    // 2026-07-15 21:00:00 KST 로 고정 — 계획 §9의 회귀 테스트 기준 시각 중 하나.
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-07-15T12:00:00Z"),
        ZoneId.of("Asia/Seoul")
    )
    private val today: LocalDate = LocalDate.of(2026, 7, 15)

    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var weatherParallelFetcher: WeatherParallelFetcher
    @Mock private lateinit var shortTermForecastPort: ShortTermForecastPort
    @Mock private lateinit var historicalObservationPort: HistoricalObservationPort

    private lateinit var service: FarmWeatherService
    private lateinit var member: Member
    private lateinit var farm: Farm

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        farm = Farm(
            id = farmId,
            owner = member,
            name = "황기밭",
            roadAddress = "서울 중구 세종대로 110",
            latitude = 37.5,
            longitude = 128.1,
            pnu = "1114010300100010000"
        )
        service = FarmWeatherService(
            farmRepository = farmRepository,
            weatherParallelFetcher = weatherParallelFetcher,
            shortTermForecastPort = shortTermForecastPort,
            historicalObservationPort = historicalObservationPort,
            clock = fixedClock
        )
    }

    @Test
    fun `farmId 없으면 처음 등록한 농지를 사용한다`() {
        given(farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)).willReturn(farm)
        given(weatherParallelFetcher.fetchHome(location())).willReturn(homeSources())

        val result = service.getHome(memberId, null)

        assertEquals(farmId, result.farmId)
    }

    @Test
    fun `farmId 있으면 소유 농지를 사용한다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(weatherParallelFetcher.fetchHome(location())).willReturn(homeSources())

        val result = service.getHome(memberId, farmId)

        assertEquals(farmId, result.farmId)
    }

    @Test
    fun `농지가 없으면 FARM_NOT_FOUND`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.getHome(memberId, farmId)
        }

        assertEquals(ErrorCode.FARM_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `좌표가 없으면 WEATHER_LOCATION_REQUIRED`() {
        val farmWithoutCoordinate = Farm(
            id = farmId,
            owner = member,
            name = "황기밭",
            roadAddress = "서울 중구 세종대로 110",
            latitude = null,
            longitude = 37.5
        )
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farmWithoutCoordinate)

        val exception = assertThrows(BusinessException::class.java) {
            service.getHome(memberId, farmId)
        }

        assertEquals(ErrorCode.WEATHER_LOCATION_REQUIRED, exception.errorCode)
    }

    @Test
    fun `강수형태가 있으면 하늘상태보다 우선한다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val sources = HomeSources(
            current = currentObservation(precipitationType = WeatherCondition.RAIN),
            latest = shortTermForecast(currentSky = WeatherCondition.CLEAR),
            todayRange = dailyForecast(today),
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchHome(location())).willReturn(sources)

        val result = service.getHome(memberId, farmId)

        assertEquals(WeatherCondition.RAIN, result.condition)
    }

    @Test
    fun `강수형태가 없으면 최신 단기예보의 하늘상태를 쓴다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val sources = HomeSources(
            current = currentObservation(precipitationType = null),
            latest = shortTermForecast(currentSky = WeatherCondition.PARTLY_CLOUDY),
            todayRange = dailyForecast(today),
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchHome(location())).willReturn(sources)

        val result = service.getHome(memberId, farmId)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.condition)
    }

    @Test
    fun `D4가 최신 단기예보에 있으면 중기예보 대신 그것을 쓴다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val d4Date = today.plusDays(4)
        val d4FromLatest = DailyForecast(d4Date, WeatherCondition.CLEAR, 20, 28)
        val d4FromMidTerm = DailyForecast(d4Date, WeatherCondition.CLOUDY, 10, 15)
        val sources = DetailSources(
            current = currentObservation(precipitationType = null),
            latest = ShortTermForecast(
                currentSky = WeatherCondition.CLEAR,
                precipitationProbability = 10,
                dailyForecasts = listOf(
                    dailyForecast(today.plusDays(1)),
                    dailyForecast(today.plusDays(2)),
                    dailyForecast(today.plusDays(3)),
                    d4FromLatest
                )
            ),
            todayRange = dailyForecast(today),
            midTermD4 = d4FromMidTerm,
            uvIndex = 5,
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchDetail(location())).willReturn(sources)

        val result = service.getDetail(memberId, farmId)

        val d4 = result.forecast.find { it.date == d4Date }
        assertThat(d4).isEqualTo(d4FromLatest)
    }

    /**
     * 실호출로만 잡힌 회귀. 단기예보는 예보 창 가장자리인 D4를 fcstTime=0000 슬롯 하나로만 줄 때가
     * 있어 SKY만 있고 TMN/TMX가 없다(실측: 08시 발표의 +4일). 날짜 존재만 보고 그 반쪽짜리를
     * 고르면 온도를 가진 중기예보를 가려 "구름많음 null~null"이 나간다.
     */
    @Test
    fun `D4가 최신 단기예보에 있어도 온도가 없으면 중기예보를 쓴다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val d4Date = today.plusDays(4)
        val d4Sliver = DailyForecast(d4Date, WeatherCondition.PARTLY_CLOUDY, null, null)
        val d4FromMidTerm = DailyForecast(d4Date, WeatherCondition.CLOUDY, 24, 30)
        val sources = DetailSources(
            current = currentObservation(precipitationType = null),
            latest = ShortTermForecast(
                currentSky = WeatherCondition.CLEAR,
                precipitationProbability = 10,
                dailyForecasts = listOf(
                    dailyForecast(today.plusDays(1)),
                    dailyForecast(today.plusDays(2)),
                    dailyForecast(today.plusDays(3)),
                    d4Sliver
                )
            ),
            todayRange = dailyForecast(today),
            midTermD4 = d4FromMidTerm,
            uvIndex = 5,
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchDetail(location())).willReturn(sources)

        val result = service.getDetail(memberId, farmId)

        assertThat(result.forecast.find { it.date == d4Date }).isEqualTo(d4FromMidTerm)
        assertThat(result.forecast).hasSize(5)
    }

    @Test
    fun `D4가 최신 단기예보에 없으면 중기예보를 쓴다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val d4Date = today.plusDays(4)
        val d4FromMidTerm = DailyForecast(d4Date, WeatherCondition.CLOUDY, 10, 15)
        val sources = DetailSources(
            current = currentObservation(precipitationType = null),
            latest = ShortTermForecast(
                currentSky = WeatherCondition.CLEAR,
                precipitationProbability = 10,
                dailyForecasts = listOf(
                    dailyForecast(today.plusDays(1)),
                    dailyForecast(today.plusDays(2)),
                    dailyForecast(today.plusDays(3))
                )
            ),
            todayRange = dailyForecast(today),
            midTermD4 = d4FromMidTerm,
            uvIndex = 5,
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchDetail(location())).willReturn(sources)

        val result = service.getDetail(memberId, farmId)

        val d4 = result.forecast.find { it.date == d4Date }
        assertThat(d4).isEqualTo(d4FromMidTerm)
    }

    @Test
    fun `일부 날짜가 없으면 그 날짜를 빼고 5일 미만으로 반환한다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val sources = DetailSources(
            current = currentObservation(precipitationType = null),
            latest = ShortTermForecast(
                currentSky = WeatherCondition.CLEAR,
                precipitationProbability = 10,
                dailyForecasts = listOf(dailyForecast(today.plusDays(1)))
            ),
            todayRange = dailyForecast(today),
            midTermD4 = null,
            uvIndex = null,
            partial = PartialFailure.of()
        )
        given(weatherParallelFetcher.fetchDetail(location())).willReturn(sources)

        val result = service.getDetail(memberId, farmId)

        assertThat(result.forecast).extracting("date").containsExactly(today, today.plusDays(1))
    }

    @Test
    fun `getDaily 미래 날짜는 WEATHER_DATE_IN_FUTURE`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.getDaily(memberId, farmId, today.plusDays(1))
        }

        assertEquals(ErrorCode.WEATHER_DATE_IN_FUTURE, exception.errorCode)
    }

    @Test
    fun `getDaily 1년보다 과거는 WEATHER_DATE_TOO_OLD`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.getDaily(memberId, farmId, today.minusYears(1).minusDays(1))
        }

        assertEquals(ErrorCode.WEATHER_DATE_TOO_OLD, exception.errorCode)
    }

    @Test
    fun `getDaily 오늘은 fetchTodayRange를 쓰고 ASOS는 부르지 않는다`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(shortTermForecastPort.fetchTodayRange(location())).willReturn(dailyForecast(today, min = 25, max = 29))

        val result = service.getDaily(memberId, farmId, today)

        assertEquals(25, result.minTemperature)
        assertEquals(29, result.maxTemperature)
        verifyNoInteractions(historicalObservationPort)
    }

    @Test
    fun `getDaily 오늘 min max 중 하나라도 없으면 WEATHER_DAILY_DATA_NOT_FOUND`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(shortTermForecastPort.fetchTodayRange(location()))
            .willReturn(DailyForecast(today, WeatherCondition.CLEAR, null, 29))

        val exception = assertThrows(BusinessException::class.java) {
            service.getDaily(memberId, farmId, today)
        }

        assertEquals(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `getDaily 과거는 ASOS를 쓰고 없으면 WEATHER_DAILY_DATA_NOT_FOUND`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(historicalObservationPort.fetch(location(), today.minusDays(1))).willReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.getDaily(memberId, farmId, today.minusDays(1))
        }

        assertEquals(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `getDaily 과거 정상 조회`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        val historical = DailyWeather(today.minusDays(3), WeatherCondition.CLOUDY, 20, 26)
        given(historicalObservationPort.fetch(location(), today.minusDays(3))).willReturn(historical)

        val result = service.getDaily(memberId, farmId, today.minusDays(3))

        assertEquals(historical, result)
    }

    private fun location(): WeatherLocation =
        WeatherLocation(
            latitude = 37.5,
            longitude = 128.1,
            roadAddress = "서울 중구 세종대로 110",
            pnu = "1114010300100010000"
        )

    private fun currentObservation(precipitationType: WeatherCondition?): CurrentObservation =
        CurrentObservation(
            temperature = 24,
            precipitationType = precipitationType,
            observedAt = LocalDateTime.of(2026, 7, 15, 21, 0),
            humidity = 85,
            windSpeed = 1.7,
            feelsLikeTemperature = 26
        )

    private fun shortTermForecast(currentSky: WeatherCondition): ShortTermForecast =
        ShortTermForecast(currentSky = currentSky, precipitationProbability = 0, dailyForecasts = emptyList())

    private fun dailyForecast(date: LocalDate, min: Int = 25, max: Int = 29): DailyForecast =
        DailyForecast(date = date, condition = WeatherCondition.CLEAR, minTemperature = min, maxTemperature = max)

    private fun homeSources(): HomeSources =
        HomeSources(
            current = currentObservation(precipitationType = null),
            latest = shortTermForecast(WeatherCondition.CLEAR),
            todayRange = dailyForecast(today),
            partial = PartialFailure.of()
        )
}
