package com.chamchamcham.application.coaching.rag.record

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
import java.util.UUID

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
) {
    fun assemble(memberId: UUID, recordId: UUID): RecordFeedbackContext {
        val record = recordRepository.findContextSourceByIdAndMemberId(recordId, memberId)
            ?: throw BusinessException(ErrorCode.FARMING_RECORD_NOT_FOUND)
        val detail = record.toDetail(recordId)
        val photoCount = mediaRepository.countByRecord_Id(recordId).toInt()
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
                sourceRevision = record.sourceRevision,
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
                        materialCategory = it.materialCategory,
                        amount = it.amount,
                        amountUnit = it.amountUnit,
                        applicationMethod = it.applicationMethod,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.PEST_CONTROL -> pestControlRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    PestControlFeedbackDetail(
                        pesticideCategory = it.pesticideCategory,
                        pesticideAmount = it.pesticideAmount,
                        pesticideAmountUnit = it.pesticideAmountUnit,
                        totalSprayAmount = it.totalSprayAmount,
                        totalSprayAmountUnit = it.totalSprayAmountUnit,
                        pestTarget = it.pestTarget,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.WEEDING -> weedingRecordRepository.findByRecord_Id(recordId)
                ?.let { WeedingFeedbackDetail(it.weedingMethod) }
                ?: WeedingFeedbackDetail(null)

            WorkType.PRUNING -> CommonFeedbackDetail

            WorkType.HARVEST -> harvestRecordRepository.findByRecord_Id(recordId)
                ?.let {
                    HarvestFeedbackDetail(
                        harvestAmountKg = it.harvestAmount,
                        amountUnknown = it.harvestAmount == null,
                        medicinalPart = it.medicinalPart,
                        harvestSource = it.harvestSource,
                        growthPeriod = it.growthPeriod,
                        growthPeriodUnit = it.growthPeriodUnit,
                        isFinalHarvest = it.isFinalHarvest,
                    )
                } ?: throw BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)

            WorkType.ETC -> CommonFeedbackDetail
        }
    }

    private fun fetchWeather(record: FarmingRecord): Pair<RecordFeedbackLiveWeather?, List<String>> {
        val latitude = record.farm.latitude
        val longitude = record.farm.longitude
        if (latitude == null || longitude == null) {
            return null to listOf(WEATHER_LOCATION_UNAVAILABLE)
        }

        return try {
            weatherPort.fetch(latitude, longitude, WEATHER_LIMIT_DAYS) to emptyList()
        } catch (exception: BusinessException) {
            when (exception.errorCode) {
                ErrorCode.WEATHER_LOCATION_REQUIRED -> null to listOf(WEATHER_LOCATION_UNAVAILABLE)
                ErrorCode.WEATHER_PROVIDER_UNAVAILABLE -> null to listOf(WEATHER_PROVIDER_UNAVAILABLE)
                else -> throw exception
            }
        } catch (_: RuntimeException) {
            null to listOf(WEATHER_PROVIDER_UNAVAILABLE)
        }
    }

    private companion object {
        const val WEATHER_LIMIT_DAYS = 7
        const val WEATHER_LOCATION_UNAVAILABLE = "weather_location_unavailable"
        const val WEATHER_PROVIDER_UNAVAILABLE = "weather_provider_unavailable"
    }
}
