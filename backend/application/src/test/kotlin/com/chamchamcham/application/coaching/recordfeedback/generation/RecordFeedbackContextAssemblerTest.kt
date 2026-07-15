package com.chamchamcham.application.coaching.recordfeedback.generation

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordMedia
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.FertilizerAmountUnit
import com.chamchamcham.domain.farming.FertilizingMethod
import com.chamchamcham.domain.farming.FertilizingRecord
import com.chamchamcham.domain.farming.FertilizingRecordRepository
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.IrrigationAmount
import com.chamchamcham.domain.farming.IrrigationMethod
import com.chamchamcham.domain.farming.PestControlRecord
import com.chamchamcham.domain.farming.PestControlRecordRepository
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.PlantingRecordRepository
import com.chamchamcham.domain.farming.PropagationMethod
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedlingUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WateringRecord
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WeedingMethod
import com.chamchamcham.domain.farming.WeedingRecord
import com.chamchamcham.domain.farming.WeedingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.Pesticide
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RecordFeedbackContextAssemblerTest {
    @Mock private lateinit var recordRepository: FarmingRecordRepository
    @Mock private lateinit var mediaRepository: FarmingRecordMediaRepository
    @Mock private lateinit var plantingRecordRepository: PlantingRecordRepository
    @Mock private lateinit var wateringRecordRepository: WateringRecordRepository
    @Mock private lateinit var fertilizingRecordRepository: FertilizingRecordRepository
    @Mock private lateinit var pestControlRecordRepository: PestControlRecordRepository
    @Mock private lateinit var weedingRecordRepository: WeedingRecordRepository
    @Mock private lateinit var harvestRecordRepository: HarvestRecordRepository
    @Mock private lateinit var weatherPort: RecordFeedbackWeatherPort

    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var assembler: RecordFeedbackContextAssembler
    private lateinit var record: FarmingRecord

    @BeforeEach
    fun setUp() {
        assembler = RecordFeedbackContextAssembler(
            recordRepository = recordRepository,
            mediaRepository = mediaRepository,
            plantingRecordRepository = plantingRecordRepository,
            wateringRecordRepository = wateringRecordRepository,
            fertilizingRecordRepository = fertilizingRecordRepository,
            pestControlRecordRepository = pestControlRecordRepository,
            weedingRecordRepository = weedingRecordRepository,
            harvestRecordRepository = harvestRecordRepository,
            weatherPort = weatherPort,
            clock = clock,
        )
    }

    @Test
    fun `planting maps planting method and optional propagation fields`() {
        stubOwnedRecord(WorkType.PLANTING)
        `when`(plantingRecordRepository.findByRecord_Id(recordId)).thenReturn(
            PlantingRecord(
                record = record,
                plantingMethod = PlantingMethod.SEEDLING,
                seedlingCount = 18,
                seedlingUnit = SeedlingUnit.JU,
                propagationMethod = PropagationMethod.CUTTING,
            )
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertBaseContext(context)
        assertThat(context.record.workType).isEqualTo(WorkType.PLANTING)
        assertThat(context.record.detail).isEqualTo(
            PlantingFeedbackDetail(
                plantingMethod = PlantingMethod.SEEDLING,
                seedAmount = null,
                seedAmountUnit = null,
                seedlingCount = 18,
                seedlingUnit = SeedlingUnit.JU,
                propagationMethod = PropagationMethod.CUTTING,
            )
        )
        verify(plantingRecordRepository).findByRecord_Id(recordId)
        verifyOnlyPlantingRepositoryUsed()
    }

    @Test
    fun `watering maps nullable amount and method and missing row becomes empty detail`() {
        stubOwnedRecord(WorkType.WATERING)
        `when`(wateringRecordRepository.findByRecord_Id(recordId)).thenReturn(
            WateringRecord(
                record = record,
                irrigationAmount = IrrigationAmount.NORMAL,
                irrigationMethod = IrrigationMethod.DRIP,
            )
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.WATERING)
        assertThat(context.record.detail).isEqualTo(
            WateringFeedbackDetail(IrrigationAmount.NORMAL, IrrigationMethod.DRIP)
        )
        verify(wateringRecordRepository).findByRecord_Id(recordId)
        verifyOnlyWateringRepositoryUsed()
    }

    @Test
    fun `watering missing row returns empty detail`() {
        stubOwnedRecord(WorkType.WATERING)
        `when`(wateringRecordRepository.findByRecord_Id(recordId)).thenReturn(null)
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.detail).isEqualTo(WateringFeedbackDetail(null, null))
        verify(wateringRecordRepository).findByRecord_Id(recordId)
        verifyOnlyWateringRepositoryUsed()
    }

    @Test
    fun `fertilizing maps actual material amount unit and method without statistics`() {
        stubOwnedRecord(WorkType.FERTILIZING)
        `when`(fertilizingRecordRepository.findByRecord_Id(recordId)).thenReturn(
            FertilizingRecord(
                record = record,
                materialName = "유기질비료",
                amount = BigDecimal("2500.0000"),
                amountUnit = FertilizerAmountUnit.G,
                applicationMethod = FertilizingMethod.SOIL,
            )
        )
        stubSharedContext(photoCount = 2)

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.FERTILIZING)
        assertThat(context.record.detail).isEqualTo(
            FertilizingFeedbackDetail(
                "유기질비료",
                BigDecimal("2500.0000"),
                FertilizerAmountUnit.G,
                FertilizingMethod.SOIL,
            )
        )
        assertThat(context.record.photoCount).isEqualTo(2)
        assertThat(RecordFeedbackContext::class.java.declaredFields.map { it.name })
            .doesNotContain("recentRecords", "workTypeStats", "cropCycle")
        verify(fertilizingRecordRepository).findByRecord_Id(recordId)
        verify(mediaRepository).findByRecord_Id(recordId)
        verifyOnlyFertilizingRepositoryUsed()
    }

    @Test
    fun `pest control maps pesticide amounts spray amount and pest`() {
        stubOwnedRecord(WorkType.PEST_CONTROL)
        `when`(pestControlRecordRepository.findByRecord_Id(recordId)).thenReturn(
            PestControlRecord(
                record = record,
                pesticide = Pesticide(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                    itemName = "만코제브 수화제",
                    brandName = "가가방",
                ),
                pesticideAmount = BigDecimal("120.0000"),
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal("20.0000"),
                totalSprayAmountUnit = SprayAmountUnit.ML,
                pest = Pest(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                    name = "점무늬병",
                ),
            )
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.PEST_CONTROL)
        assertThat(context.record.detail).isEqualTo(
            PestControlFeedbackDetail(
                "가가방",
                BigDecimal("120.0000"),
                PesticideAmountUnit.ML,
                BigDecimal("20.0000"),
                "ML",
                "점무늬병",
            )
        )
        verify(pestControlRecordRepository).findByRecord_Id(recordId)
        verifyOnlyPestControlRepositoryUsed()
    }

    @Test
    fun `weeding maps nullable method and missing row becomes empty detail`() {
        stubOwnedRecord(WorkType.WEEDING)
        `when`(weedingRecordRepository.findByRecord_Id(recordId)).thenReturn(
            WeedingRecord(record = record, weedingMethod = WeedingMethod.HAND)
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.WEEDING)
        assertThat(context.record.detail).isEqualTo(WeedingFeedbackDetail(WeedingMethod.HAND))
        verify(weedingRecordRepository).findByRecord_Id(recordId)
        verifyOnlyWeedingRepositoryUsed()
    }

    @Test
    fun `weeding missing row returns empty detail`() {
        stubOwnedRecord(WorkType.WEEDING)
        `when`(weedingRecordRepository.findByRecord_Id(recordId)).thenReturn(null)
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.detail).isEqualTo(WeedingFeedbackDetail(null))
        verify(weedingRecordRepository).findByRecord_Id(recordId)
        verifyOnlyWeedingRepositoryUsed()
    }

    @Test
    fun `pruning uses common detail and no detail repository call`() {
        stubOwnedRecord(WorkType.PRUNING)
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.PRUNING)
        assertThat(context.record.detail).isEqualTo(CommonFeedbackDetail)
        verifyNoDetailRepositoryInteractions()
    }

    @Test
    fun `harvest maps amount part source growth period and final flag`() {
        stubOwnedRecord(WorkType.HARVEST)
        `when`(harvestRecordRepository.findByRecord_Id(recordId)).thenReturn(
            HarvestRecord(
                record = record,
                harvestAmount = BigDecimal("4.2000"),
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 18,
                isLastHarvest = true,
            )
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.HARVEST)
        assertThat(context.record.detail).isEqualTo(
            HarvestFeedbackDetail(
                harvestAmount = BigDecimal("4.2000"),
                amountUnknown = false,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 18,
                growthPeriodUnit = "MONTH",
                isLastHarvest = true,
            )
        )
        verify(harvestRecordRepository).findByRecord_Id(recordId)
        verifyOnlyHarvestRepositoryUsed()
    }

    @Test
    fun `harvest null amount marks amount unknown while keeping required growth period`() {
        stubOwnedRecord(WorkType.HARVEST)
        `when`(harvestRecordRepository.findByRecord_Id(recordId)).thenReturn(
            HarvestRecord(
                record = record,
                harvestAmount = null,
                medicinalPart = null,
                harvestSource = HarvestSource.FORAGED,
                growthPeriod = 12,
                isLastHarvest = false,
            )
        )
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.detail).isEqualTo(
            HarvestFeedbackDetail(
                harvestAmount = null,
                amountUnknown = true,
                medicinalPart = null,
                harvestSource = HarvestSource.FORAGED,
                growthPeriod = 12,
                growthPeriodUnit = "MONTH",
                isLastHarvest = false,
            )
        )
        verifyOnlyHarvestRepositoryUsed()
    }

    @Test
    fun `etc uses common detail and memo only without inferred category`() {
        stubOwnedRecord(WorkType.ETC)
        stubSharedContext()

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.record.workType).isEqualTo(WorkType.ETC)
        assertThat(context.record.memo).isEqualTo("오전 작업 기록")
        assertThat(context.record.detail).isEqualTo(CommonFeedbackDetail)
        verifyNoDetailRepositoryInteractions()
    }

    @Test
    fun `another member record returns farming record not found`() {
        `when`(recordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(null)

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_NOT_FOUND)
            }
        verifyNoDetailRepositoryInteractions()
        verifyNoInteractions(mediaRepository, weatherPort)
    }

    @Test
    fun `soft deleted record returns farming record not found`() {
        stubOwnedRecord(WorkType.PRUNING)
        record.softDelete()

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_NOT_FOUND)
            }
    }

    @Test
    fun `missing required detail row remains data error`() {
        stubOwnedRecord(WorkType.FERTILIZING)
        `when`(fertilizingRecordRepository.findByRecord_Id(recordId)).thenReturn(null)

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            }
        verify(fertilizingRecordRepository).findByRecord_Id(recordId)
        verifyNoInteractions(mediaRepository, weatherPort)
    }

    @Test
    fun `missing planting detail row remains data error`() {
        stubOwnedRecord(WorkType.PLANTING)
        `when`(plantingRecordRepository.findByRecord_Id(recordId)).thenReturn(null)

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            }
        verify(plantingRecordRepository).findByRecord_Id(recordId)
        verifyNoInteractions(mediaRepository, weatherPort)
    }

    @Test
    fun `missing pest control detail row remains data error`() {
        stubOwnedRecord(WorkType.PEST_CONTROL)
        `when`(pestControlRecordRepository.findByRecord_Id(recordId)).thenReturn(null)

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            }
        verify(pestControlRecordRepository).findByRecord_Id(recordId)
        verifyNoInteractions(mediaRepository, weatherPort)
    }

    @Test
    fun `missing harvest detail row remains data error`() {
        stubOwnedRecord(WorkType.HARVEST)
        `when`(harvestRecordRepository.findByRecord_Id(recordId)).thenReturn(null)

        assertThatThrownBy { assembler.assemble(memberId, recordId, SOURCE_REVISION) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED)
            }
        verify(harvestRecordRepository).findByRecord_Id(recordId)
        verifyNoInteractions(mediaRepository, weatherPort)
    }

    @Test
    fun `no coordinate weather produces warning without fetching provider`() {
        stubOwnedRecord(WorkType.PRUNING, latitude = null, longitude = null)
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_location_unavailable")
        verifyNoInteractions(weatherPort)
    }

    @Test
    fun `provider business failure produces reduced context warning`() {
        stubOwnedRecord(WorkType.PRUNING)
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())
        `when`(weatherPort.fetch(37.1, 128.2, 7))
            .thenThrow(BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE))

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_provider_unavailable")
    }

    @Test
    fun `provider runtime failure produces reduced context warning`() {
        stubOwnedRecord(WorkType.PRUNING)
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())
        `when`(weatherPort.fetch(37.1, 128.2, 7)).thenThrow(IllegalStateException("provider down"))

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_provider_unavailable")
    }

    @Test
    fun `record from exactly seven days ago still fetches live weather`() {
        stubOwnedRecord(
            workType = WorkType.PRUNING,
            workedAt = LocalDateTime.of(2026, 7, 1, 9, 0),
        )
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())
        `when`(weatherPort.fetch(37.1, 128.2, 7)).thenReturn(liveWeather())

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isEqualTo(liveWeather())
        assertThat(context.warnings).isEmpty()
        verify(weatherPort).fetch(37.1, 128.2, 7)
    }

    @Test
    fun `record older than seven days skips live weather`() {
        stubOwnedRecord(
            workType = WorkType.PRUNING,
            workedAt = LocalDateTime.of(2026, 6, 30, 9, 0),
        )
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_skipped_for_historical_record")
        verifyNoInteractions(weatherPort)
    }

    @Test
    fun `future record skips live weather`() {
        stubOwnedRecord(
            workType = WorkType.PRUNING,
            workedAt = LocalDateTime.of(2026, 7, 9, 9, 0),
        )
        `when`(mediaRepository.findByRecord_Id(recordId)).thenReturn(emptyList())

        val context = assembler.assemble(memberId, recordId, SOURCE_REVISION)

        assertThat(context.weather).isNull()
        assertThat(context.warnings).containsExactly("weather_skipped_for_historical_record")
        verifyNoInteractions(weatherPort)
    }

    private fun stubOwnedRecord(
        workType: WorkType,
        latitude: Double? = 37.1,
        longitude: Double? = 128.2,
        workedAt: LocalDateTime = LocalDateTime.of(2026, 7, 1, 8, 30),
    ) {
        val member = Member(
            id = memberId,
            email = "farmer@example.test",
            passwordHash = null,
            experienceLevel = 3,
            managementType = ManagementType.NON_REGISTERED_FARMER,
        )
        val farm = Farm(
            id = farmId,
            owner = member,
            name = "청년약초밭",
            roadAddress = "강원특별자치도 평창군",
            latitude = latitude,
            longitude = longitude,
        )
        val crop = Crop(
            id = cropId,
            externalNo = 1001,
            name = "참당귀",
            usePartCategory = CropUsePartCategory.ROOT_BARK,
        )
        record = FarmingRecord(
            id = recordId,
            member = member,
            farm = farm,
            crop = crop,
            workType = workType,
            workedAt = workedAt,
            weatherCondition = "맑음",
            weatherTemperature = 27,
            memo = "오전 작업 기록",
            entryMode = com.chamchamcham.domain.farming.EntryMode.MANUAL,
            isDeleted = false,
        )
        `when`(recordRepository.findByIdAndMember_Id(recordId, memberId)).thenReturn(record)
    }

    private fun stubSharedContext(photoCount: Int = 0) {
        `when`(mediaRepository.findByRecord_Id(recordId))
            .thenReturn(List(photoCount) { mock(FarmingRecordMedia::class.java) })
        `when`(weatherPort.fetch(37.1, 128.2, 7)).thenReturn(liveWeather())
    }

    private fun assertBaseContext(context: RecordFeedbackContext) {
        assertThat(context.schemaVersion).isEqualTo("record-feedback-context.v2")
        assertThat(context.member.memberId).isEqualTo(memberId)
        assertThat(context.member.experienceLevel).isEqualTo(3)
        assertThat(context.member.managementType).isEqualTo(ManagementType.NON_REGISTERED_FARMER)
        assertThat(context.farm.farmId).isEqualTo(farmId)
        assertThat(context.farm.name).isEqualTo("청년약초밭")
        assertThat(context.farm.roadAddress).isEqualTo("강원특별자치도 평창군")
        assertThat(context.farm.latitude).isEqualTo(37.1)
        assertThat(context.farm.longitude).isEqualTo(128.2)
        assertThat(context.crop.cropId).isEqualTo(cropId)
        assertThat(context.crop.name).isEqualTo("참당귀")
        assertThat(context.crop.usePartCategory).isEqualTo(CropUsePartCategory.ROOT_BARK)
        assertThat(context.record.recordId).isEqualTo(recordId)
        assertThat(context.record.sourceRevision).isEqualTo(SOURCE_REVISION)
        assertThat(context.record.workedAt).isEqualTo(LocalDateTime.of(2026, 7, 1, 8, 30))
        assertThat(context.record.recordedWeatherCondition).isEqualTo("맑음")
        assertThat(context.record.recordedTemperatureC).isEqualTo(27)
        assertThat(context.record.memo).isEqualTo("오전 작업 기록")
        assertThat(context.record.photoCount).isEqualTo(0)
        assertThat(context.weather).isEqualTo(liveWeather())
        assertThat(context.warnings).isEmpty()
    }

    private fun liveWeather(): RecordFeedbackLiveWeather {
        return RecordFeedbackLiveWeather(
            current = RecordFeedbackCurrentWeather(
                temperatureC = 25,
                skyCondition = "구름많음",
                observedAt = LocalDateTime.of(2026, 7, 1, 9, 0),
            ),
            forecastDays = listOf(
                RecordFeedbackForecastDay(
                    date = LocalDate.of(2026, 7, 2),
                    rainfallMm = BigDecimal("12.5"),
                    rainProbabilityPct = 60,
                    maxTemperatureC = BigDecimal("29.5"),
                    minTemperatureC = BigDecimal("20.0"),
                    humidityPct = BigDecimal("81.0"),
                    windSpeedMs = BigDecimal("3.5"),
                    riskFlags = listOf("RAIN"),
                )
            ),
            source = "fake",
        )
    }

    private companion object {
        const val SOURCE_REVISION = 4L
    }

    private fun verifyOnlyPlantingRepositoryUsed() {
        verifyNoInteractions(
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    private fun verifyOnlyWateringRepositoryUsed() {
        verifyNoInteractions(
            plantingRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    private fun verifyOnlyFertilizingRepositoryUsed() {
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    private fun verifyOnlyPestControlRepositoryUsed() {
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    private fun verifyOnlyWeedingRepositoryUsed() {
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            harvestRecordRepository,
        )
    }

    private fun verifyOnlyHarvestRepositoryUsed() {
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
        )
    }

    private fun verifyNoDetailRepositoryInteractions() {
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }
}
