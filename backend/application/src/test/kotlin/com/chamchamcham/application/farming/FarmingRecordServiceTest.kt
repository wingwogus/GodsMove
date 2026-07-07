package com.chamchamcham.application.farming

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecord
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.farming.FertilizingRecordRepository
import com.chamchamcham.domain.farming.HarvestAmountUnit
import com.chamchamcham.domain.farming.HarvestRecord
import com.chamchamcham.domain.farming.HarvestRecordRepository
import com.chamchamcham.domain.farming.PestControlRecordRepository
import com.chamchamcham.domain.farming.PlantingRecord
import com.chamchamcham.domain.farming.PlantingRecordRepository
import com.chamchamcham.domain.farming.SeedAmountUnit
import com.chamchamcham.domain.farming.SeedSource
import com.chamchamcham.domain.farming.WateringRecordRepository
import com.chamchamcham.domain.farming.WeedingRecordRepository
import com.chamchamcham.domain.farming.WorkType
import com.chamchamcham.domain.farming.GrowthPeriodUnit
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmingRecordServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository
    @Mock private lateinit var plantingRecordRepository: PlantingRecordRepository
    @Mock private lateinit var wateringRecordRepository: WateringRecordRepository
    @Mock private lateinit var fertilizingRecordRepository: FertilizingRecordRepository
    @Mock private lateinit var pestControlRecordRepository: PestControlRecordRepository
    @Mock private lateinit var weedingRecordRepository: WeedingRecordRepository
    @Mock private lateinit var harvestRecordRepository: HarvestRecordRepository
    @Mock private lateinit var detailValidator: FarmingRecordDetailValidator

    private lateinit var service: FarmingRecordService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop

    @BeforeEach
    fun setUp() {
        service = FarmingRecordService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            farmingRecordRepository = farmingRecordRepository,
            plantingRecordRepository = plantingRecordRepository,
            wateringRecordRepository = wateringRecordRepository,
            fertilizingRecordRepository = fertilizingRecordRepository,
            pestControlRecordRepository = pestControlRecordRepository,
            weedingRecordRepository = weedingRecordRepository,
            harvestRecordRepository = harvestRecordRepository,
            detailValidator = detailValidator,
        )
        member = Member(id = memberId, email = "$memberId@example.com", passwordHash = null)
        farm = Farm(id = farmId, owner = member, name = "약초농장", roadAddress = "서울시 강남구")
        crop = Crop(id = cropId, externalNo = cropId.hashCode(), name = "황기", usePartCategory = CropUsePartCategory.ROOT_BARK)
    }

    private fun baseCommand(
        workType: WorkType,
        planting: FarmingRecordCommand.PlantingDetail? = null,
        harvest: FarmingRecordCommand.HarvestDetail? = null,
    ) = FarmingRecordCommand.Create(
        memberId = memberId,
        farmId = farmId,
        cropId = cropId,
        workType = workType,
        workedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        memo = "memo",
        planting = planting,
        harvest = harvest,
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
                memo = toSave.memo,
                entryMode = toSave.entryMode,
            )
        }
    }

    @Test
    fun `create saves planting detail when workType is PLANTING`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.PLANTING,
            planting = FarmingRecordCommand.PlantingDetail(
                seedAmount = BigDecimal.TEN,
                seedAmountUnit = SeedAmountUnit.KG,
                seedSource = SeedSource.SELF_COLLECTED,
            ),
        )

        val result = service.create(command)

        assertEquals(WorkType.PLANTING, result.workType)

        val captor = ArgumentCaptor.forClass(PlantingRecord::class.java)
        verify(plantingRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.TEN, captor.value.seedAmount)
        assertEquals(SeedAmountUnit.KG, captor.value.seedAmountUnit)
        verifyNoInteractions(harvestRecordRepository, wateringRecordRepository, fertilizingRecordRepository, pestControlRecordRepository, weedingRecordRepository)
    }

    @Test
    fun `create saves harvest detail when workType is HARVEST`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()

        val command = baseCommand(
            workType = WorkType.HARVEST,
            harvest = FarmingRecordCommand.HarvestDetail(
                harvestAmount = BigDecimal.TEN,
                harvestAmountUnit = HarvestAmountUnit.KG,
                growthPeriod = 2,
                growthPeriodUnit = GrowthPeriodUnit.YEAR,
            ),
        )

        service.create(command)

        val captor = ArgumentCaptor.forClass(HarvestRecord::class.java)
        verify(harvestRecordRepository).save(captor.capture())
        assertEquals(BigDecimal.TEN, captor.value.harvestAmount)
        assertEquals(CropUsePartCategory.ROOT_BARK, captor.value.medicinalPart)
    }

    @Test
    fun `create saves no detail row when workType is PRUNING`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(farm)
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
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(null)

        val command = baseCommand(workType = WorkType.PRUNING)

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.FARM_NOT_FOUND, exception.errorCode)
        verify(farmingRecordRepository, never()).save(any(FarmingRecord::class.java))
    }

    @Test
    fun `create throws BusinessException not IllegalArgumentException when detail is missing despite validator passing`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByIdAndOwner_Id(farmId, memberId)).thenReturn(farm)
        `when`(cropRepository.findById(cropId)).thenReturn(Optional.of(crop))
        stubFarmingRecordSave()
        // detailValidator is a mock that does not throw here, simulating a validator/service drift
        // where validate() no longer catches a missing detail that saveDetail still requires.

        val command = baseCommand(workType = WorkType.FERTILIZING)

        val exception = assertThrows(BusinessException::class.java) { service.create(command) }

        assertEquals(ErrorCode.FARMING_RECORD_DETAIL_REQUIRED, exception.errorCode)
    }
}
