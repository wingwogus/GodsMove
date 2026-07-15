package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class FarmWeatherService(
    private val farmRepository: FarmRepository,
    private val weatherProvider: WeatherProvider,
    private val historicalWeatherProvider: HistoricalWeatherProvider,
    private val uvIndexProvider: UvIndexProvider
) {
    fun getCurrentWeather(memberId: UUID, farmId: UUID): FarmWeatherResult.CurrentDetail {
        val farm = farmRepository.findByIdAndOwnerId(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

        val latitude = farm.latitude
        val longitude = farm.longitude
        if (latitude == null || longitude == null) {
            throw BusinessException(ErrorCode.WEATHER_LOCATION_REQUIRED)
        }

        val snapshot = weatherProvider.fetchCurrentWeather(latitude, longitude)
        val forecast = runCatching { weatherProvider.fetchForecastPanel(latitude, longitude) }.getOrNull()
        val uvIndex = farm.pnu?.take(10)?.let { areaNo ->
            runCatching { uvIndexProvider.fetchUvIndex(areaNo) }.getOrNull()
        }
        val today = forecast?.dailyForecasts?.firstOrNull { it.date == LocalDate.now() }
        return FarmWeatherResult.CurrentDetail(
            snapshot = snapshot,
            roadAddress = farm.roadAddress,
            precipitationProbability = forecast?.precipitationProbability,
            forecast = forecast?.dailyForecasts ?: emptyList(),
            uvIndex = uvIndex,
            minTemperature = today?.minTemperature,
            maxTemperature = today?.maxTemperature
        )
    }

    fun getCurrentWeather(memberId: UUID): FarmWeatherResult.CurrentDetail =
        getCurrentWeather(memberId, resolveDefaultFarm(memberId).id!!)

    fun getDailyWeather(memberId: UUID, farmId: UUID, date: LocalDate): DailyWeatherSummary {
        val farm = farmRepository.findByIdAndOwnerId(farmId, memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

        val latitude = farm.latitude
        val longitude = farm.longitude
        if (latitude == null || longitude == null) {
            throw BusinessException(ErrorCode.WEATHER_LOCATION_REQUIRED)
        }
        if (date.isAfter(LocalDate.now())) {
            throw BusinessException(ErrorCode.WEATHER_DATE_IN_FUTURE)
        }

        if (date.isEqual(LocalDate.now())) {
            return resolveTodayWeather(latitude, longitude, date)
                ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
        }

        return historicalWeatherProvider.fetchDailySummary(latitude, longitude, date)
            ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
    }

    /**
     * ASOS 일자료(실측)는 그 날이 마감돼야 확정되는 경우가 많아 "오늘" 조회에 쓸 수 없다.
     * 대신 이미 호출 중인 단기예보 패널(getVilageFcst)의 오늘 항목을 사용한다.
     */
    private fun resolveTodayWeather(latitude: Double, longitude: Double, date: LocalDate): DailyWeatherSummary? {
        val today = runCatching { weatherProvider.fetchForecastPanel(latitude, longitude) }
            .getOrNull()
            ?.dailyForecasts
            ?.firstOrNull { it.date == date }
            ?: return null

        val skyCondition = today.skyCondition ?: return null
        val minTemperature = today.minTemperature ?: return null
        val maxTemperature = today.maxTemperature ?: return null

        return DailyWeatherSummary(
            date = date,
            skyCondition = skyCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature
        )
    }

    fun getDailyWeather(memberId: UUID, date: LocalDate): DailyWeatherSummary =
        getDailyWeather(memberId, resolveDefaultFarm(memberId).id!!, date)

    private fun resolveDefaultFarm(memberId: UUID): Farm =
        farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)
            ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)
}
