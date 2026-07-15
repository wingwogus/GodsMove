package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmWeatherServiceTest {

    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var weatherProvider: WeatherProvider
    @Mock private lateinit var historicalWeatherProvider: HistoricalWeatherProvider
    @Mock private lateinit var uvIndexProvider: UvIndexProvider
    @Mock private lateinit var midTermForecastProvider: MidTermForecastProvider

    private lateinit var service: FarmWeatherService

    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val member = Member(id = memberId, email = "member@example.com", passwordHash = null)

    @BeforeEach
    fun setUp() {
        service = FarmWeatherService(
            farmRepository,
            weatherProvider,
            historicalWeatherProvider,
            uvIndexProvider,
            midTermForecastProvider
        )
    }

    @Test
    fun `농지 좌표로 날씨 스냅샷을 조회한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.snapshot).isEqualTo(snapshot)
        assertThat(result.roadAddress).isEqualTo("서울시 강남구")
    }

    @Test
    fun `농지가 없거나 소유자가 아니면 FARM_NOT_FOUND를 던진다`() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(null)

        assertThatThrownBy { service.getCurrentWeather(memberId, farmId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARM_NOT_FOUND)

        verifyNoInteractions(weatherProvider)
    }

    @Test
    fun `농지에 좌표가 없으면 WEATHER_LOCATION_REQUIRED를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구"
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)

        assertThatThrownBy { service.getCurrentWeather(memberId, farmId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_LOCATION_REQUIRED)

        verifyNoInteractions(weatherProvider)
    }

    @Test
    fun `농지 ID 없이 조회하면 가장 먼저 등록한 농지의 날씨를 반환한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)).thenReturn(farm)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)

        val result = service.getCurrentWeather(memberId)

        assertThat(result.snapshot).isEqualTo(snapshot)
        assertThat(result.roadAddress).isEqualTo("서울시 강남구")
    }

    @Test
    fun `농지 ID 없이 조회할 때 농지가 없으면 FARM_NOT_FOUND를 던진다`() {
        `when`(farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)).thenReturn(null)

        assertThatThrownBy { service.getCurrentWeather(memberId) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARM_NOT_FOUND)

        verifyNoInteractions(weatherProvider)
    }

    @Test
    fun `예보 조회에 성공하면 강수확률과 예보 목록을 채운다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val dailyForecasts = listOf(
            DailyForecast(
                date = java.time.LocalDate.of(2026, 7, 8),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            )
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = dailyForecasts))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.precipitationProbability).isEqualTo(30)
        assertThat(result.forecast).isEqualTo(dailyForecasts)
    }

    @Test
    fun `예보 목록에 오늘 날짜 항목이 있으면 오늘의 최저_최고 기온을 채운다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val dailyForecasts = listOf(
            DailyForecast(
                date = LocalDate.now(),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            ),
            DailyForecast(
                date = LocalDate.now().plusDays(1),
                minTemperature = 20,
                maxTemperature = 31,
                skyCondition = "구름많음"
            )
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = dailyForecasts))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.minTemperature).isEqualTo(18)
        assertThat(result.maxTemperature).isEqualTo(29)
    }

    @Test
    fun `예보 목록에 오늘 날짜 항목이 없으면 최저_최고 기온은 null이다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val dailyForecasts = listOf(
            DailyForecast(
                date = LocalDate.now().plusDays(1),
                minTemperature = 20,
                maxTemperature = 31,
                skyCondition = "구름많음"
            )
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = dailyForecasts))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.minTemperature).isNull()
        assertThat(result.maxTemperature).isNull()
    }

    @Test
    fun `예보 조회가 실패해도 현재 날씨 응답은 성공하고 예보는 빈 상태로 채워진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.snapshot).isEqualTo(snapshot)
        assertThat(result.roadAddress).isEqualTo("서울시 강남구")
        assertThat(result.precipitationProbability).isNull()
        assertThat(result.forecast).isEmpty()
    }

    @Test
    fun `단기예보가 5일을 모두 반환하면 중기예보를 호출하지 않는다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val fiveDayForecasts = (0..4L).map { offset ->
            DailyForecast(
                date = LocalDate.now().plusDays(offset),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            )
        }
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = fiveDayForecasts))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.forecast).hasSize(5)
        verifyNoInteractions(midTermForecastProvider)
    }

    @Test
    fun `단기예보가 4일만 반환하면 5일차를 중기예보로 채워 5개를 반환한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val fourDayForecasts = (0..3L).map { offset ->
            DailyForecast(
                date = LocalDate.now().plusDays(offset),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            )
        }
        val backfilledDay = DailyForecast(
            date = LocalDate.now().plusDays(4),
            minTemperature = 20,
            maxTemperature = 30,
            skyCondition = "비"
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = fourDayForecasts))
        `when`(midTermForecastProvider.fetchDayForecast(37.5665, 126.9780, LocalDate.now().plusDays(4)))
            .thenReturn(backfilledDay)

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.forecast).hasSize(5)
        assertThat(result.forecast.last()).isEqualTo(backfilledDay)
    }

    @Test
    fun `중기예보 백필이 실패해도 예외 없이 있는 만큼만 반환한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        val fourDayForecasts = (0..3L).map { offset ->
            DailyForecast(
                date = LocalDate.now().plusDays(offset),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            )
        }
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = 30, dailyForecasts = fourDayForecasts))
        `when`(midTermForecastProvider.fetchDayForecast(37.5665, 126.9780, LocalDate.now().plusDays(4)))
            .thenThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.forecast).hasSize(4)
    }

    @Test
    fun `농지에 pnu가 있고 제공자가 값을 반환하면 자외선지수가 채워진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780,
            pnu = "1100000000123456789"
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(uvIndexProvider.fetchUvIndex("1100000000")).thenReturn(7)

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.uvIndex).isEqualTo(7)
    }

    @Test
    fun `농지에 pnu가 없으면 자외선지수는 null이다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780,
            pnu = null
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.uvIndex).isNull()
        verifyNoInteractions(uvIndexProvider)
    }

    @Test
    fun `자외선지수 제공자가 예외를 던져도 현재 날씨 응답은 성공하고 자외선지수는 null이다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780,
            pnu = "1100000000123456789"
        )
        val snapshot = WeatherSnapshot(
            temperature = 14,
            skyCondition = "맑음",
            observedAt = LocalDateTime.of(2026, 7, 8, 10, 0)
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchCurrentWeather(37.5665, 126.9780)).thenReturn(snapshot)
        `when`(uvIndexProvider.fetchUvIndex("1100000000"))
            .thenThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        val result = service.getCurrentWeather(memberId, farmId)

        assertThat(result.snapshot).isEqualTo(snapshot)
        assertThat(result.uvIndex).isNull()
    }

    @Test
    fun `과거 날씨 조회 시 농지가 없거나 소유자가 아니면 FARM_NOT_FOUND를 던진다`() {
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(null)

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1)) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARM_NOT_FOUND)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `과거 날씨 조회 시 농지에 좌표가 없으면 WEATHER_LOCATION_REQUIRED를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구"
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, LocalDate.of(2026, 7, 1)) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_LOCATION_REQUIRED)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `과거 날씨 조회 시 미래 날짜이면 WEATHER_DATE_IN_FUTURE를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)

        assertThatThrownBy {
            service.getDailyWeather(memberId, farmId, LocalDate.now().plusDays(1))
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_DATE_IN_FUTURE)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `과거 날씨 조회 시 조회 대상 날짜가 오늘이면 ASOS 대신 예보 패널의 오늘 항목을 사용한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val dailyForecasts = listOf(
            DailyForecast(
                date = LocalDate.now(),
                minTemperature = 18,
                maxTemperature = 29,
                skyCondition = "맑음"
            )
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = null, dailyForecasts = dailyForecasts))

        val result = service.getDailyWeather(memberId, farmId, LocalDate.now())

        assertThat(result).isEqualTo(
            DailyWeatherSummary(
                date = LocalDate.now(),
                skyCondition = "맑음",
                minTemperature = 18,
                maxTemperature = 29
            )
        )
        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `오늘 날씨 조회 시 예보 패널에 오늘 항목이 없으면 WEATHER_DAILY_DATA_NOT_FOUND를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = null, dailyForecasts = emptyList()))

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, LocalDate.now()) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `오늘 날씨 조회 시 오늘 항목의 최저_최고 기온이 없으면 WEATHER_DAILY_DATA_NOT_FOUND를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val dailyForecasts = listOf(
            DailyForecast(
                date = LocalDate.now(),
                minTemperature = null,
                maxTemperature = null,
                skyCondition = "맑음"
            )
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenReturn(WeatherForecast(precipitationProbability = null, dailyForecasts = dailyForecasts))

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, LocalDate.now()) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `오늘 날씨 조회 시 예보 패널 조회가 실패해도 WEATHER_DAILY_DATA_NOT_FOUND로 처리한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(weatherProvider.fetchForecastPanel(37.5665, 126.9780))
            .thenThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, LocalDate.now()) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)

        verifyNoInteractions(historicalWeatherProvider)
    }

    @Test
    fun `과거 날씨 조회 시 제공자가 null을 반환하면 WEATHER_DAILY_DATA_NOT_FOUND를 던진다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val date = LocalDate.of(2026, 7, 1)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(historicalWeatherProvider.fetchDailySummary(37.5665, 126.9780, date)).thenReturn(null)

        assertThatThrownBy { service.getDailyWeather(memberId, farmId, date) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
    }

    @Test
    fun `과거 날씨 조회에 성공하면 제공자가 반환한 요약을 그대로 반환한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val date = LocalDate.of(2026, 7, 1)
        val summary = DailyWeatherSummary(
            date = date,
            skyCondition = "흐림",
            minTemperature = 20,
            maxTemperature = 27
        )
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(historicalWeatherProvider.fetchDailySummary(37.5665, 126.9780, date)).thenReturn(summary)

        val result = service.getDailyWeather(memberId, farmId, date)

        assertThat(result).isEqualTo(summary)
    }

    @Test
    fun `농지 ID 없이 과거 날씨를 조회하면 가장 먼저 등록한 농지의 날씨를 반환한다`() {
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "약초농장",
            roadAddress = "서울시 강남구",
            latitude = 37.5665,
            longitude = 126.9780
        )
        val date = LocalDate.of(2026, 7, 1)
        val summary = DailyWeatherSummary(
            date = date,
            skyCondition = "맑음",
            minTemperature = 18,
            maxTemperature = 29
        )
        `when`(farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)).thenReturn(farm)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(historicalWeatherProvider.fetchDailySummary(37.5665, 126.9780, date)).thenReturn(summary)

        val result = service.getDailyWeather(memberId, date)

        assertThat(result).isEqualTo(summary)
    }

    @Test
    fun `농지 ID 없이 과거 날씨를 조회할 때 농지가 없으면 FARM_NOT_FOUND를 던진다`() {
        `when`(farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)).thenReturn(null)

        assertThatThrownBy { service.getDailyWeather(memberId, LocalDate.of(2026, 7, 1)) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FARM_NOT_FOUND)

        verifyNoInteractions(historicalWeatherProvider)
    }
}
