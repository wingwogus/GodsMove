package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * 농지 날씨 유스케이스 3개. 기상청 API를 모른다 — 병렬 조립은 [WeatherParallelFetcher]에,
 * 기상청 코드 변환은 어댑터에 맡긴다.
 */
@Service
@Transactional(readOnly = true)
class FarmWeatherService(
    private val farmRepository: FarmRepository,
    private val weatherParallelFetcher: WeatherParallelFetcher,
    private val shortTermForecastPort: ShortTermForecastPort,
    private val historicalObservationPort: HistoricalObservationPort,
    private val clock: Clock
) {
    fun getHome(memberId: UUID, farmId: UUID?): HomeWeather {
        val farm = resolveFarm(memberId, farmId)
        val location = toLocation(farm)
        val sources = weatherParallelFetcher.fetchHome(location)

        return HomeWeather(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            temperature = sources.current.temperature,
            condition = resolveCondition(sources.current, sources.latest),
            minTemperature = sources.todayRange?.minTemperature,
            maxTemperature = sources.todayRange?.maxTemperature,
            observedAt = sources.current.observedAt,
            partial = sources.partial
        )
    }

    fun getDetail(memberId: UUID, farmId: UUID?): DetailWeather {
        val farm = resolveFarm(memberId, farmId)
        val location = toLocation(farm)
        val sources = weatherParallelFetcher.fetchDetail(location)
        val today = LocalDate.now(clock)

        return DetailWeather(
            farmId = requireNotNull(farm.id) { "Persisted farm id is required" },
            address = farm.roadAddress,
            observedAt = sources.current.observedAt,
            temperature = sources.current.temperature,
            feelsLikeTemperature = sources.current.feelsLikeTemperature,
            condition = resolveCondition(sources.current, sources.latest),
            minTemperature = sources.todayRange?.minTemperature,
            maxTemperature = sources.todayRange?.maxTemperature,
            humidity = sources.current.humidity,
            windSpeed = sources.current.windSpeed,
            precipitationProbability = sources.latest?.precipitationProbability,
            uvIndex = sources.uvIndex,
            forecast = buildForecast(today, sources),
            partial = sources.partial
        )
    }

    fun getDaily(memberId: UUID, farmId: UUID?, date: LocalDate): DailyWeather {
        val today = LocalDate.now(clock)
        if (date.isAfter(today)) {
            throw BusinessException(ErrorCode.WEATHER_DATE_IN_FUTURE)
        }
        if (date.isBefore(today.minusYears(1))) {
            throw BusinessException(ErrorCode.WEATHER_DATE_TOO_OLD)
        }

        val farm = resolveFarm(memberId, farmId)
        val location = toLocation(farm)

        // 오늘은 단기예보(todayRange)에서, 과거는 ASOS에서 — 기상청이 오늘 ASOS를 주지 않는다.
        return try {
            if (date == today) {
                todayDailyWeather(location, date)
            } else {
                historicalObservationPort.fetch(location, date)
                    ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
            }
        } catch (exception: BusinessException) {
            // 외부 provider 장애(해외 VPN·기상청 장애)면 서비스가 멈추지 않도록 폴백을 반환한다.
            // 입력 검증(미래/과거)과 실데이터 부재(404)는 그대로 전파한다.
            if (exception.errorCode == ErrorCode.WEATHER_PROVIDER_UNAVAILABLE) {
                logger.warn { "weather daily fallback applied (provider unavailable) date=$date" }
                WeatherFallback.dailyWeather(date)
            } else {
                throw exception
            }
        }
    }

    private fun todayDailyWeather(location: WeatherLocation, date: LocalDate): DailyWeather {
        val forecast = shortTermForecastPort.fetchTodayRange(location)
            ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
        // DailyWeather는 min/max가 non-null이라 반쪽짜리를 만들 수 없다 — 없으면 조회 실패로 취급한다.
        val minTemperature = forecast.minTemperature
            ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
        val maxTemperature = forecast.maxTemperature
            ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)

        return DailyWeather(
            date = date,
            condition = forecast.condition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    // 강수는 실황 실측이 우선이고(precipitationType), 강수가 없으면 실황엔 하늘상태 정보가 아예
    // 없으므로 단기예보 SKY로 보완한다. 둘 다 없으면 UNKNOWN.
    private fun resolveCondition(current: CurrentObservation, latest: ShortTermForecast?): WeatherCondition =
        current.precipitationType ?: latest?.currentSky ?: WeatherCondition.UNKNOWN

    // D0~D4 조립. 없는 날짜는 빼고 지어내지 않는다(계획 §2).
    private fun buildForecast(today: LocalDate, sources: DetailSources): List<DailyForecast> {
        val days = mutableListOf<DailyForecast>()
        sources.todayRange?.let { days.add(it) }

        for (offset in 1..3) {
            val date = today.plusDays(offset.toLong())
            sources.latest?.dailyForecasts?.find { it.date == date }?.let { days.add(it) }
        }

        val d4Date = today.plusDays(4)
        // 단기예보가 D4를 온전히 주면(17시 이후 발표) 더 정확한 그쪽을 쓰고, 아니면 중기예보로 간다.
        // 날짜 존재만 보면 안 된다 — 단기예보는 D4를 온도 없는 반쪽짜리로 줄 때가 있고, 그게
        // 온도를 가진 중기예보를 가려 "구름많음 null~null"이 나간다(hasTemperatureRange 참고).
        val d4 = sources.latest?.dailyForecasts?.find { it.date == d4Date && it.hasTemperatureRange }
            ?: sources.midTermD4
        d4?.let { days.add(it) }

        return days.sortedBy { it.date }
    }

    private fun resolveFarm(memberId: UUID, farmId: UUID?): Farm =
        if (farmId != null) {
            farmRepository.findByIdAndOwnerId(farmId, memberId)
                ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        } else {
            // farmId 생략 시 처음 등록한 농지를 쓴다(계획 §0).
            farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)
                ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
        }

    // Farm -> WeatherLocation 변환은 이 한 곳뿐이다(계획 §6).
    private fun toLocation(farm: Farm): WeatherLocation {
        val latitude = farm.latitude
        val longitude = farm.longitude
        if (latitude == null || longitude == null) {
            throw BusinessException(ErrorCode.WEATHER_LOCATION_REQUIRED)
        }
        return WeatherLocation(
            latitude = latitude,
            longitude = longitude,
            roadAddress = farm.roadAddress,
            pnu = farm.pnu
        )
    }
}
