package com.chamchamcham.application.farming

import com.chamchamcham.application.common.OpaqueCursorCodec
import com.chamchamcham.application.coaching.recordfeedback.lifecycle.RecordFeedbackLifecycleService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.report.FarmingCycleReportProjectionService
import com.chamchamcham.domain.common.BaseTimeEntity
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordMedia
import com.chamchamcham.domain.farming.FarmingRecordMediaRepository
import com.chamchamcham.domain.farming.FarmingRecordQueryRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.FertilizingRecordRepository
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.EntryMode
import com.chamchamcham.domain.farming.HarvestSource
import com.chamchamcham.domain.farming.PestControlRecord
import com.chamchamcham.domain.farming.PestControlRecordRepository
import com.chamchamcham.domain.farming.PesticideAmountUnit
import com.chamchamcham.domain.farming.PlantingMethod
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.PlantingRecordRepository
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SprayAmountUnit
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WeedingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import com.chamchamcham.domain.pesticide.Pest
import com.chamchamcham.domain.pesticide.PestRepository
import com.chamchamcham.domain.pesticide.Pesticide
import com.chamchamcham.domain.pesticide.PesticideRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingRecordServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val recordId = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val secondRecordId = UUID.fromString("00000000-0000-0000-0000-000000000302")
    private val mediaId1 = UUID.fromString("00000000-0000-0000-0000-000000000401")
    private val replacementMediaId = UUID.fromString("00000000-0000-0000-0000-000000000402")
    private val cursorCodec = OpaqueCursorCodec()

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var farmingRecordMediaRepository: FarmingRecordMediaRepository
    @Mock private lateinit var farmingRecordQueryRepository: FarmingRecordQueryRepository
    @Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository
    @Mock private lateinit var plantingRecordRepository: PlantingRecordRepository
    @Mock private lateinit var wateringRecordRepository: WateringRecordRepository
    @Mock private lateinit var fertilizingRecordRepository: FertilizingRecordRepository
    @Mock private lateinit var pestControlRecordRepository: PestControlRecordRepository
    @Mock private lateinit var weedingRecordRepository: WeedingRecordRepository
    @Mock private lateinit var harvestRecordRepository: HarvestRecordRepository
    @Mock private lateinit var pesticideRepository: PesticideRepository
    @Mock private lateinit var pestRepository: PestRepository
    @Mock private lateinit var detailValidator: FarmingRecordDetailValidator
    @Mock private lateinit var reportProjectionService: FarmingCycleReportProjectionService
    @Mock private lateinit var recordFeedbackLifecycleService: RecordFeedbackLifecycleService

    private lateinit var service: FarmingRecordService
    private lateinit var member: Member
    private lateinit var otherMember: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var media1: UploadedMedia
    private lateinit var replacementMedia: UploadedMedia

    @BeforeEach
    fun setUp() {
        service = FarmingRecordService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository,
            farmingRecordMediaRepository = farmingRecordMediaRepository,
            farmingRecordQueryRepository = farmingRecordQueryRepository,
            uploadedMediaRepository = uploadedMediaRepository,
            plantingRecordRepository = plantingRecordRepository,
            wateringRecordRepository = wateringRecordRepository,
            fertilizingRecordRepository = fertilizingRecordRepository,
            pestControlRecordRepository = pestControlRecordRepository,
            weedingRecordRepository = weedingRecordRepository,
            harvestRecordRepository = harvestRecordRepository,
            pesticideRepository = pesticideRepository,
            pestRepository = pestRepository,
            detailValidator = detailValidator,
            cursorCodec = cursorCodec,
            reportProjectionService = reportProjectionService,
            recordFeedbackLifecycleService = recordFeedbackLifecycleService,
        )
        member = Member(id = memberId, email = "$memberId@example.com", passwordHash = null)
        otherMember = Member(id = otherMemberId, email = "$otherMemberId@example.com", passwordHash = null)
        farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
        crop = Crop(id = cropId, externalNo = cropId.hashCode(), name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
        media1 = uploadedMedia(mediaId1)
        replacementMedia = uploadedMedia(replacementMediaId)
    }

    private fun baseCommand(
        workType: WorkType,
        planting: FarmingRecordCommand.PlantingDetail? = null,
        harvest: FarmingRecordCommand.HarvestDetail? = null,
        pestControl: FarmingRecordCommand.PestControlDetail? = null,
        mediaIds: List<UUID> = emptyList(),
    ) = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = workType,
        workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 20,
        memo = "memo",
        planting = planting,
        harvest = harvest,
        pestControl = pestControl,
        mediaIds = mediaIds,
    )

    private fun updateCommand(
        workType: WorkType,
        planting: FarmingRecordCommand.PlantingDetail? = null,
        harvest: FarmingRecordCommand.HarvestDetail? = null,
        mediaIds: List<UUID> = emptyList(),
    ) = FarmingRecordCommand.Update(
        memberId = memberId,
        recordId = recordId,
        farmId = farmId,
        cropId = cropId,
        workType = workType,
        workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        weatherCondition = "맑음",
        weatherTemperature = 20,
        memo = "memo",
        planting = planting,
        harvest = harvest,
        mediaIds = mediaIds,
    )

    private fun existingRecord(
        id: UUID = recordId,
        owner: Member = member,
        workType: WorkType = WorkType.PRUNING,
        workedAt: LocalDateTime = LocalDateTime.of(2026, 6, 1, 9, 0),
    ): FarmingRecord = FarmingRecord(
        id = id,
        member = owner,
        farm = farm,
        crop = crop,
        workType = workType,
        workedAt = workedAt,
        weatherCondition = "맑음",
        weatherTemperature = 20,
        memo = "memo",
        entryMode = EntryMode.MANUAL,
    )

    private fun uploadedMedia(
        id: UUID,
        usageType: UploadedMediaUsageType = UploadedMediaUsageType.FARMING_RECORD,
        status: UploadedMediaStatus = UploadedMediaStatus.TEMP,
    ): UploadedMedia = UploadedMedia(
        id = id,
        owner = member,
        mediaType = UploadedMediaType.IMAGE,
        usageType = usageType,
        fileUrl = "https://example.test/$id.jpg",
        cloudinaryPublicId = "farming/$id",
        status = status,
    )

    private fun stubFarmingRecordSave() {
        `when`(farmingRecordRepository.save(any(FarmingRecord::class.java))).thenAnswer { invocation ->
            val toSave = invocation.arguments[0] as FarmingRecord
            FarmingRecord(
                id = UUID.randomUUID(),
                member = toSave.member,
                farm = toSave.farm,
                crop = toSave.crop,
                workType = toSave.workType,
                workedAt = toSave.workedAt,
                weatherCondition = toSave.weatherCondition,
                weatherTemperature = toSave.weatherTemperature,
                memo = toSave.memo,
                entryMode = toSave.entryMode,
            )
        }
    }

    private fun capturedFarmingRecordMedia(): List<FarmingRecordMedia> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<FarmingRecordMedia>>
        verify(farmingRecordMediaRepository).saveAll(captor.capture())
        return captor.value.toList()
    }

    private fun setTimestamps(entity: BaseTimeEntity, dateTime: LocalDateTime) {
        BaseTimeEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(entity, dateTime)
        }
        BaseTimeEntity::class.java.getDeclaredField("updatedAt").apply {
            isAccessible = true
            set(entity, dateTime)
        }
    }

    @Test
    fun `create saves planting detail when workType is PLANTING`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                plantingMethod = PlantingMethod.SEED,
                seedAmount = BigDecimal.TEN,
                seedAmountUnit = SeedAmountUnit.G,
            ),
        )

        val result = service.create(command)

        assertEquals(WorkType.PLANTING, result.workType)

        val captor = ArgumentCaptor.forClass(PlantingRecord::class.java)
        verify(plantingRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.TEN, captor.value.seedAmount)
        assertEquals(SeedAmountUnit.G, captor.value.seedAmountUnit)
        verifyNoInteractions(harvestRecordRepository, wateringRecordRepository, fertilizingRecordRepository, pestControlRecordRepository, weedingRecordRepository)
    }

    @Test
    fun `create saves pest control detail when workType is PEST_CONTROL`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        val pesticideId = UUID.randomUUID()
        val pestId = UUID.randomUUID()
        val pesticide = Pesticide(id = pesticideId, itemName = "만코제브 수화제", brandName = "가가방")
        val pest = Pest(id = pestId, name = "역병")
        `when`(pesticideRepository.findById(pesticideId)).thenReturn(Optional.of(pesticide))
        `when`(pestRepository.findById(pestId)).thenReturn(Optional.of(pest))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.PEST_CONTROL,
            pestControl = FarmingRecordCommand.PestControlDetail(
                pesticideId = pesticideId,
                pesticideAmount = BigDecimal.ONE,
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal.TEN,
                totalSprayAmountUnit = SprayAmountUnit.L,
                pestId = pestId,
            ),
        )

        service.create(command)

        val captor = ArgumentCaptor.forClass(PestControlRecord::class.java)
        verify(pestControlRecordRepository).save(captor.capture())
        assertEquals(pesticideId, captor.value.pesticide.id)
        assertEquals(pestId, captor.value.pest?.id)
        verifyNoInteractions(harvestRecordRepository, wateringRecordRepository, fertilizingRecordRepository, plantingRecordRepository, weedingRecordRepository)
    }

    @Test
    fun `create throws when pesticide id does not exist`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        val pesticideId = UUID.randomUUID()
        `when`(pesticideRepository.findById(pesticideId)).thenReturn(Optional.empty())
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.PEST_CONTROL,
            pestControl = FarmingRecordCommand.PestControlDetail(
                pesticideId = pesticideId,
                pesticideAmount = BigDecimal.ONE,
                pesticideAmountUnit = PesticideAmountUnit.ML,
                totalSprayAmount = BigDecimal.TEN,
                totalSprayAmountUnit = SprayAmountUnit.L,
            ),
        )

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.PESTICIDE_NOT_FOUND, exception.errorCode)
        verify(pestControlRecordRepository, never()).save(any(PestControlRecord::class.java))
    }

    @Test
    fun `create persists weather fields`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        service.create(baseCommand(workType = WorkType.PRUNING))

        val captor = ArgumentCaptor.forClass(FarmingRecord::class.java)
        verify(farmingRecordRepository).save(captor.capture())
        assertEquals("맑음", captor.value.weatherCondition)
        assertEquals(20, captor.value.weatherTemperature)
    }

    @Test
    fun `create attaches up to five media`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(uploadedMediaRepository.findAllById(listOf(mediaId1))).thenReturn(listOf(media1))
        stubFarmingRecordSave()

        service.create(baseCommand(workType = WorkType.PRUNING, mediaIds = listOf(mediaId1)))

        assertEquals(UploadedMediaStatus.ATTACHED, media1.status)
        assertThat(capturedFarmingRecordMedia().map { it.uploadedMedia.id }).containsExactly(mediaId1)
    }

    @Test
    fun `create saves harvest detail when workType is HARVEST`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        )

        service.create(command)

        val captor = ArgumentCaptor.forClass(HarvestRecord::class.java)
        verify(harvestRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.TEN, captor.value.harvestAmount)
        assertEquals(CropUsePartCategory.ROOT_BARK, captor.value.medicinalPart)
    }

    @Test
    fun `create stores null harvest amount when amount is unknown`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = null,
                amountUnknown = true,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = false,
            ),
        )

        service.create(command)

        val captor = ArgumentCaptor.forClass(HarvestRecord::class.java)
        verify(harvestRecordRepository).save(captor.capture())
        assertEquals(null, captor.value.harvestAmount)
    }

    @Test
    fun `create saves no detail row when workType is PRUNING`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(workType = WorkType.PRUNING)

        val result = service.create(command)

        assertEquals(WorkType.PRUNING, result.workType)
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    @Test
    fun `create saves no detail row when workType is ETC`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(workType = WorkType.ETC)

        val result = service.create(command)

        assertEquals(WorkType.ETC, result.workType)
        verifyNoInteractions(
            plantingRecordRepository,
            wateringRecordRepository,
            fertilizingRecordRepository,
            pestControlRecordRepository,
            weedingRecordRepository,
            harvestRecordRepository,
        )
    }

    @Test
    fun `create rejects fertilizing without required detail and saves nothing`() {
        val command = baseCommand(workType = WorkType.FERTILIZING)
        `when`(detailValidator.validate(command))
            .thenThrow(BusinessException(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED))

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
        verify(farmingRecordRepository, never()).save(any(FarmingRecord::class.java))
    }

    @Test
    fun `create throws when member not found`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val command = baseCommand(workType = WorkType.PRUNING)

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `create throws when farm is not owned by member`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(null)

        val command = baseCommand(workType = WorkType.PRUNING)

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.FARM_NOT_FOUND, exception.errorCode)
        verify(farmingRecordRepository, never()).save(any(FarmingRecord::class.java))
    }

    @Test
    fun `create throws BusinessException not IllegalArgumentException when detail is missing despite validator passing`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()
        // detailValidator is a mock that does not throw here, simulating a validator/service drift
        // where validate() no longer catches a missing detail that saveDetail still requires.

        val command = baseCommand(workType = WorkType.FERTILIZING)

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
    }

    @Test
    fun `update replaces detail row when workType changes`() {
        val record = existingRecord(workType = WorkType.PLANTING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))

        val result = service.update(
            updateCommand(
                workType = WorkType.HARVEST,
                harvest = FarmingRecordCommand.HarvestDetail(
                    harvestAmount = BigDecimal.TEN,
                    medicinalPart = CropUsePartCategory.ROOT_BARK,
                    growthPeriod = 2,
                    growthPeriodUnit = GrowthPeriodUnit.YEAR,
                    isLastHarvest = false,
                ),
            )
        )

        assertEquals(WorkType.HARVEST, result.workType)
        assertEquals(WorkType.HARVEST, record.workType)
        verify(plantingRecordRepository).deleteByRecord(record)
        val captor = ArgumentCaptor.forClass(HarvestRecord::class.java)
        verify(harvestRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.TEN, captor.value.harvestAmount)
        verifyNoInteractions(wateringRecordRepository, fertilizingRecordRepository, pestControlRecordRepository, weedingRecordRepository)
    }

    @Test
    fun `update replaces detail row when workType is unchanged`() {
        val record = existingRecord(workType = WorkType.PLANTING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))

        service.update(
            updateCommand(
                workType = WorkType.PLANTING,
                planting = FarmingRecordCommand.PlantingDetail(
                    plantingMethod = PlantingMethod.SEED,
                    seedAmount = BigDecimal.ONE,
                    seedAmountUnit = SeedAmountUnit.G,
                ),
            )
        )

        verify(plantingRecordRepository).deleteByRecord(record)
        val captor = ArgumentCaptor.forClass(PlantingRecord::class.java)
        verify(plantingRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.ONE, captor.value.seedAmount)
    }

    @Test
    fun `update replaces media associations`() {
        val record = existingRecord(workType = WorkType.PRUNING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)
        `when`(farmRepository.findByIdAndOwnerId(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        `when`(uploadedMediaRepository.findAllById(listOf(replacementMediaId))).thenReturn(listOf(replacementMedia))

        service.update(updateCommand(workType = WorkType.PRUNING, mediaIds = listOf(replacementMediaId)))

        verify(farmingRecordMediaRepository).deleteByRecord(record)
        assertEquals(UploadedMediaStatus.ATTACHED, replacementMedia.status)
    }

    @Test
    fun `update throws not found for missing or deleted record`() {
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.update(updateCommand(workType = WorkType.PRUNING))
        }

        assertEquals(ErrorCode.FARMING_RECORD_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `update throws forbidden for record owned by another member`() {
        val record = existingRecord(owner = otherMember, workType = WorkType.PRUNING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)

        val exception = assertThrows(BusinessException::class.java) {
            service.update(updateCommand(workType = WorkType.PRUNING))
        }

        assertEquals(ErrorCode.FARMING_RECORD_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `delete sets isDeleted without removing the row`() {
        val record = existingRecord(workType = WorkType.PRUNING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)

        service.delete(FarmingRecordCommand.Delete(memberId = memberId, recordId = recordId))

        assertTrue(record.isDeleted)
        verify(farmingRecordRepository, never()).delete(any(FarmingRecord::class.java))
    }

    @Test
    fun `delete throws not found for missing or deleted record`() {
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            service.delete(FarmingRecordCommand.Delete(memberId = memberId, recordId = recordId))
        }

        assertEquals(ErrorCode.FARMING_RECORD_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `delete throws forbidden for record owned by another member`() {
        val record = existingRecord(owner = otherMember, workType = WorkType.PRUNING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)

        val exception = assertThrows(BusinessException::class.java) {
            service.delete(FarmingRecordCommand.Delete(memberId = memberId, recordId = recordId))
        }

        assertEquals(ErrorCode.FARMING_RECORD_FORBIDDEN, exception.errorCode)
        assertFalse(record.isDeleted)
    }

    @Test
    fun `getDetail returns full detail with images and type specific detail`() {
        val record = existingRecord(workType = WorkType.HARVEST)
        setTimestamps(record, LocalDateTime.of(2026, 6, 1, 9, 0))
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)
        `when`(harvestRecordRepository.findByRecord_Id(recordId)).thenReturn(
            HarvestRecord(
                record = record,
                harvestAmount = BigDecimal.TEN,
                medicinalPart = CropUsePartCategory.ROOT_BARK,
                harvestSource = HarvestSource.CULTIVATED,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
                isLastHarvest = true,
            )
        )
        `when`(farmingRecordMediaRepository.findByRecord_Id(recordId)).thenReturn(
            listOf(FarmingRecordMedia(record = record, uploadedMedia = media1, displayOrder = 0))
        )

        val detail = service.getDetail(memberId, recordId)

        assertEquals(recordId, detail.id)
        assertEquals(cropId, detail.cropId)
        assertEquals(farmId, detail.farmId)
        assertEquals(WorkType.HARVEST, detail.workType)
        assertEquals("맑음", detail.weatherCondition)
        assertEquals(20, detail.weatherTemperature)
        assertEquals(BigDecimal.TEN, detail.harvest?.harvestAmount)
        assertEquals(true, detail.harvest?.isLastHarvest)
        assertThat(detail.imageUrls).containsExactly(media1.fileUrl)
    }

    @Test
    fun `getDetail throws not found for missing or deleted record`() {
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) { service.getDetail(memberId, recordId) }

        assertEquals(ErrorCode.FARMING_RECORD_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `getDetail throws forbidden for record owned by another member`() {
        val record = existingRecord(owner = otherMember, workType = WorkType.PRUNING)
        `when`(farmingRecordRepository.findByIdAndIsDeletedFalse(recordId)).thenReturn(record)

        val exception = assertThrows(BusinessException::class.java) { service.getDetail(memberId, recordId) }

        assertEquals(ErrorCode.FARMING_RECORD_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `search maps application condition to query repository and returns next cursor`() {
        val requestedSize = 1
        val newestRecord = existingRecord(id = recordId, workedAt = LocalDateTime.of(2026, 6, 12, 10, 0))
        val overflowRecord = existingRecord(id = secondRecordId, workedAt = LocalDateTime.of(2026, 6, 12, 9, 0))

        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = cropId,
                    workType = WorkType.PRUNING,
                    workedAtFrom = null,
                    workedAtTo = null,
                    cursor = null,
                    size = requestedSize + 1
                )
            )
        ).thenReturn(
            FarmingRecordQueryRepository.SearchResult(
                rows = listOf(
                    FarmingRecordQueryRepository.Row(record = newestRecord, thumbnailUrl = "https://example.test/1.jpg"),
                    FarmingRecordQueryRepository.Row(record = overflowRecord, thumbnailUrl = null),
                )
            )
        )

        val page = service.search(
            FarmingRecordSearchCondition(
                memberId = memberId,
                cropId = cropId,
                workType = WorkType.PRUNING,
                startDate = null,
                endDate = null,
                cursor = null,
                size = requestedSize
            )
        )

        assertThat(page.items).hasSize(1)
        assertEquals(recordId, page.items.single().id)
        assertThat(page.nextCursor).isNotBlank()
        val nextCursor = cursorCodec.decode(page.nextCursor!!, FarmingRecordCursorPayload::class.java)
        assertEquals(newestRecord.workedAt, nextCursor.workedAt)
        assertEquals(recordId, nextCursor.id)
    }

    @Test
    fun `search converts start and end date to datetime bounds`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = LocalDateTime.of(2026, 6, 1, 0, 0),
                    workedAtTo = LocalDateTime.of(2026, 7, 1, 0, 0),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(
            FarmingRecordSearchCondition(
                memberId = memberId,
                cropId = null,
                workType = null,
                startDate = LocalDate.of(2026, 6, 1),
                endDate = LocalDate.of(2026, 6, 30),
                cursor = null,
                size = 20
            )
        )

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `search rejects zero size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = 0))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(farmingRecordQueryRepository)
    }

    @Test
    fun `search rejects negative size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = -1))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(farmingRecordQueryRepository)
    }

    @Test
    fun `search rejects max integer size`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(size = Int.MAX_VALUE))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(farmingRecordQueryRepository)
    }

    @Test
    fun `search rejects malformed cursor`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.search(searchCondition(cursor = "not-a-valid-cursor"))
        }

        assertEquals(ErrorCode.INVALID_CURSOR, exception.errorCode)
        verifyNoInteractions(farmingRecordQueryRepository)
    }

    @Test
    fun `search resolves keyword into matched work type label`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = "수확",
                    matchedWorkTypes = listOf(WorkType.HARVEST),
                    matchedParts = emptyList(),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "수확"))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `search resolves keyword into matched harvest part label`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = "잎",
                    matchedWorkTypes = emptyList(),
                    matchedParts = listOf(CropUsePartCategory.LEAF),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "잎"))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `search passes blank keyword as null with no matched labels`() {
        `when`(
            farmingRecordQueryRepository.search(
                FarmingRecordQueryRepository.SearchCondition(
                    memberId = memberId,
                    cropId = null,
                    workType = null,
                    workedAtFrom = null,
                    workedAtTo = null,
                    keyword = null,
                    matchedWorkTypes = emptyList(),
                    matchedParts = emptyList(),
                    cursor = null,
                    size = 21
                )
            )
        ).thenReturn(FarmingRecordQueryRepository.SearchResult(emptyList()))

        val page = service.search(searchCondition(keyword = "   "))

        assertThat(page.items).isEmpty()
    }

    private fun searchCondition(
        cropId: UUID? = null,
        workType: WorkType? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        keyword: String? = null,
        cursor: String? = null,
        size: Int = 20,
    ) = FarmingRecordSearchCondition(
        memberId = memberId,
        cropId = cropId,
        workType = workType,
        startDate = startDate,
        endDate = endDate,
        keyword = keyword,
        cursor = cursor,
        size = size,
    )
}
