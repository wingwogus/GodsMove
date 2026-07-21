package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.FertilizingRecordRepository
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.PestControlRecordRepository
import com.chamchamcham.domain.farming.PlantingRecordRepository
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WeedingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.application.weather.WeatherFallback
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Component
class RecordFeedbackContextAssembler(
    private val recordRepository: FarmingRecordRepository,
    private val mediaRepository: FarmingRecordMediaRepository,
    private val plantingRecordRepository: PlantingRecordRepository,
    private val wateringRecordRepository: WateringRecordRepository,
    private val fertilizingRecordRepository: FertilizingRecordRepository,
    private val pestControlRecordRepository: PestControlRecordRepository,
    private val weedingRecordRepository: WeedingRecordRepository,
    private val harvestRecordRepository: HarvestRecordRepository,
    private val weatherPort: RecordFeedbackWeatherPort,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Transactional(readOnly = true)
    fun assemble(memberId: UUID, recordId: UUID, sourceRevision: Long): RecordFeedbackContext {
        val record = recordRepository.findByIdAndMember_Id(recordId, memberId)
            ?.takeUnless(FarmingRecord::isDeleted)
            ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)
        val detail = record.toDetail(recordId)
        val photoCount = mediaRepository.findByRecord_Id(recordId).size
        val (weather, warnings) = fetchWeather(record)

        return RecordFeedbackContext(
            member = RecordFeedbackMemberContext(
                memberId = record.member.id ?: memberId,
                experienceLevel = record.member.experienceLevel,
                managementType = record.member.managementType,
            ),
            farm = RecordFeedbackFarmContext(
                farmId = record.farm.id ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND),
                name = record.farm.name,
                roadAddress = record.farm.roadAddress,
                latitude = record.farm.latitude,
                longitude = record.farm.longitude,
            ),
            crop = RecordFeedbackCropContext(
                cropId = record.crop.id ?: throw BusinessException(ErrorCode.CROP_NOT_FOUND),
                name = record.crop.name,
                usePartCategory = record.crop.usePartCategory,
            ),
            record = RecordFeedbackRecordContext(
                recordId = record.id ?: recordId,
                sourceRevision = sourceRevision,
                workedAt = record.workedAt,
                workType = record.workType,
                detail = detail,
                recordedWeatherCondition = record.weatherCondition,
                recordedTemperatureC = record.weatherTemperature,
                memo = record.memo,
                photoCount = photoCount,
            ),
            weather = weather,
            warnings = warnings,
        )
    }

    private fun FarmingRecord.toDetail(recordId: UUID): RecordFeedbackWorkDetail {
        return when (workType) {
            WorkType.PLANTING -> plantingRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    PlantingFeedbackDetail(
                        plantingMethod = it.plantingMethod,
                        seedAmount = it.seedAmount,
                        seedAmountUnit = it.seedAmountUnit,
                        seedlingCount = it.seedlingCount,
                        seedlingUnit = it.seedlingUnit,
                        propagationMethod = it.propagationMethod,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.WATERING -> wateringRecordRepository.findByRecord_Id(recordId)
                ?.let { WateringFeedbackDetail(it.irrigationAmount, it.irrigationMethod) }
                ?: WateringFeedbackDetail(null, null)

            WorkType.FERTILIZING -> fertilizingRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    FertilizingFeedbackDetail(
                        materialName = it.materialName,
                        amount = it.amount,
                        amountUnit = it.amountUnit,
                        applicationMethod = it.applicationMethod,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.PEST_CONTROL -> pestControlRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    PestControlFeedbackDetail(
                        pesticideName = it.pesticide.brandName,
                        pesticideAmount = it.pesticideAmount,
                        pesticideAmountUnit = it.pesticideAmountUnit,
                        totalSprayAmount = it.totalSprayAmount,
                        totalSprayAmountUnit = it.totalSprayAmountUnit.name,
                        pestName = it.pest?.name,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.WEEDING -> weedingRecordRepository.findByRecord_Id(recordId)
                ?.let { WeedingFeedbackDetail(it.weedingMethod) }
                ?: WeedingFeedbackDetail(null)

            WorkType.PRUNING -> CommonFeedbackDetail

            WorkType.HARVEST -> harvestRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    HarvestFeedbackDetail(
                        harvestAmount = it.harvestAmount,
                        amountUnknown = it.harvestAmount == null,
                        medicinalPart = it.medicinalPart,
                        harvestSource = it.harvestSource,
                        growthPeriod = it.growthPeriod,
                        growthPeriodUnit = "MONTH",
                        isLastHarvest = it.isLastHarvest,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.ETC -> CommonFeedbackDetail
        }
    }

    private fun fetchWeather(record: FarmingRecord): Pair<RecordFeedbackLiveWeather?, List<String>> {
        val today = LocalDate.now(clock)
        val workedOn = record.workedAt.toLocalDate()
        if (workedOn.isBefore(today.minusDays(WEATHER_LIMIT_DAYS.toLong())) || workedOn.isAfter(today)) {
            return null to listOf(WEATHER_SKIPPED_FOR_HISTORICAL_RECORD)
        }

        val latitude = record.farm.latitude
        val longitude = record.farm.longitude
        if (latitude == null || longitude == null) {
            return null to listOf(WEATHER_LOCATION_UNAVAILABLE)
        }

        return try {
            weatherPort.fetch(latitude, longitude, WEATHER_LIMIT_DAYS) to emptyList()
        } catch (exception: BusinessException) {
            when (exception.errorCode) {
                // 좌표 문제는 외부 API 장애가 아니라 데이터 부재라 폴백 대상이 아니다(null 유지).
                ErrorCode.WEATHER_LOCATION_REQUIRED -> null to listOf(WEATHER_LOCATION_UNAVAILABLE)
                // 외부 provider 장애는 서비스가 멈추지 않도록 폴백 날씨를 제공한다. degraded 사유는 남긴다.
                ErrorCode.WEATHER_PROVIDER_UNAVAILABLE -> fallbackWeather() to listOf(WEATHER_PROVIDER_UNAVAILABLE)
                else -> throw exception
            }
        } catch (_: RuntimeException) {
            fallbackWeather() to listOf(WEATHER_PROVIDER_UNAVAILABLE)
        }
    }

    // 외부 날씨 API 장애 시의 고정 폴백 라이브 날씨. 가짜 데이터에 위험경보를 붙이지 않으려고
    // forecastDays는 비우고 riskFlags도 없다. source로 폴백임을 추적 가능하게 한다.
    private fun fallbackWeather(): RecordFeedbackLiveWeather =
        RecordFeedbackLiveWeather(
            current = RecordFeedbackCurrentWeather(
                temperatureC = WeatherFallback.TEMPERATURE,
                skyCondition = WeatherFallback.CONDITION.text,
                observedAt = LocalDateTime.now(clock),
            ),
            forecastDays = emptyList(),
            source = FALLBACK_SOURCE,
        )

    private companion object {
        const val WEATHER_LIMIT_DAYS = 7
        const val WEATHER_LOCATION_UNAVAILABLE = "weather_location_unavailable"
        const val WEATHER_PROVIDER_UNAVAILABLE = "weather_provider_unavailable"
        const val WEATHER_SKIPPED_FOR_HISTORICAL_RECORD = "weather_skipped_for_historical_record"
        const val FALLBACK_SOURCE = "FALLBACK"
    }
}
