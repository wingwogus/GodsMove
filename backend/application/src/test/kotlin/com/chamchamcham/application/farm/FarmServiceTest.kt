package com.chamchamcham.application.farm

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.farming.FarmingRecordRepository
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FarmServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val oldCropId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val newCropId = UUID.fromString("00000000-0000-0000-0000-000000000203")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var farmingRecordRepository: FarmingRecordRepository

    private lateinit var service: FarmService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var oldCrop: Crop
    private lateinit var newCrop: Crop

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "member@example.com", passwordHash = null)
        farm = Farm(
            id = farmId,
            owner = member,
            name = "기존 농장",
            roadAddress = "강원특별자치도 횡성군 둔내면 1",
            latitude = 37.5,
            longitude = 128.1
        )
        crop = crop(cropId, "황기")
        oldCrop = crop(oldCropId, "당귀")
        newCrop = crop(newCropId, "작약")
        service = FarmService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            memberCropRepository = memberCropRepository,
            farmingRecordRepository = farmingRecordRepository
        )
    }

    @Test
    fun `create saves owned farm and requested crop links`() {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(cropRepository.findAllById(listOf(cropId))).willReturn(listOf(crop))
        given(farmRepository.save(any(Farm::class.java))).willReturn(farm)

        val result = service.create(createCommand(cropIds = listOf(cropId)))

        assertEquals(farmId, result.farmId)
        assertThat(result.crops.map { it.id }).containsExactly(cropId)
        assertThat(capturedSavedMemberCrops().map { it.crop.id }).containsExactly(cropId)
    }

    @Test
    fun `create rejects missing crop`() {
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(cropRepository.findAllById(listOf(cropId))).willReturn(emptyList())

        val exception = assertThrows(BusinessException::class.java) {
            service.create(createCommand(cropIds = listOf(cropId)))
        }

        assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
        verify(farmRepository, never()).save(any(Farm::class.java))
    }

    @Test
    fun `replace removes unused crop link and adds requested crop link`() {
        val oldLink = memberCrop(farm, oldCrop)
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(cropRepository.findAllById(listOf(newCropId))).willReturn(listOf(newCrop))
        given(memberCropRepository.findAllWithCropByMemberIdAndFarmId(memberId, farmId))
            .willReturn(listOf(oldLink))
        given(farmingRecordRepository.existsByMember_IdAndFarm_IdAndCrop_Id(memberId, farmId, oldCropId))
            .willReturn(false)

        val result = service.replace(replaceCommand(cropIds = listOf(newCropId)))

        assertEquals("수정 농장", farm.name)
        assertThat(result.crops.map { it.id }).containsExactly(newCropId)
        verify(memberCropRepository).deleteAll(listOf(oldLink))
        assertThat(capturedSavedMemberCrops().map { it.crop.id }).containsExactly(newCropId)
    }

    @Test
    fun `replace rejects removal of crop with farming record`() {
        val oldLink = memberCrop(farm, oldCrop)
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(cropRepository.findAllById(listOf(newCropId))).willReturn(listOf(newCrop))
        given(memberCropRepository.findAllWithCropByMemberIdAndFarmId(memberId, farmId))
            .willReturn(listOf(oldLink))
        given(farmingRecordRepository.existsByMember_IdAndFarm_IdAndCrop_Id(memberId, farmId, oldCropId))
            .willReturn(true)

        val exception = assertThrows(BusinessException::class.java) {
            service.replace(replaceCommand(cropIds = listOf(newCropId)))
        }

        assertEquals(ErrorCode.FARM_CROP_IN_USE, exception.errorCode)
        verify(memberCropRepository, never()).deleteAll(any<Iterable<MemberCrop>>())
    }

    @Test
    fun `delete rejects farm referenced by farming record`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(farmingRecordRepository.existsByFarm_Id(farmId)).willReturn(true)

        val exception = assertThrows(BusinessException::class.java) {
            service.delete(FarmCommand.Delete(memberId = memberId, farmId = farmId))
        }

        assertEquals(ErrorCode.FARM_IN_USE, exception.errorCode)
        verify(farmRepository, never()).delete(farm)
    }

    @Test
    fun `delete removes crop links before deleting unreferenced farm`() {
        given(farmRepository.findByIdAndOwnerId(farmId, memberId)).willReturn(farm)
        given(farmingRecordRepository.existsByFarm_Id(farmId)).willReturn(false)

        service.delete(FarmCommand.Delete(memberId = memberId, farmId = farmId))

        verify(memberCropRepository).deleteByMemberIdAndFarmId(memberId, farmId)
        verify(farmRepository).delete(farm)
    }

    private fun createCommand(cropIds: List<UUID>): FarmCommand.Create =
        FarmCommand.Create(memberId = memberId, draft = draft(), cropIds = cropIds)

    private fun replaceCommand(cropIds: List<UUID>): FarmCommand.Replace =
        FarmCommand.Replace(memberId = memberId, farmId = farmId, draft = draft(name = "수정 농장"), cropIds = cropIds)

    private fun draft(name: String = "새 농장"): FarmCommand.Draft =
        FarmCommand.Draft(
            name = name,
            roadAddress = "강원특별자치도 횡성군 둔내면 2",
            jibunAddress = null,
            latitude = 37.5,
            longitude = 128.1,
            pnu = "4273031021101010000",
            landCategory = "전",
            areaSqm = BigDecimal("1200.5"),
            areaIsManualEntry = false,
            boundaryCoordinates = listOf(FarmCommand.BoundaryCoordinate(37.5, 128.1)),
            dataSource = FarmCommand.DataSource("JUSO", "VWORLD", "VWORLD", "VWORLD")
        )

    private fun crop(id: UUID, name: String): Crop =
        Crop(id = id, externalNo = id.hashCode(), name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)

    private fun memberCrop(farm: Farm, crop: Crop): MemberCrop =
        MemberCrop(member = member, farm = farm, crop = crop)

    private fun capturedSavedMemberCrops(): List<MemberCrop> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<MemberCrop>>
        verify(memberCropRepository).saveAll(captor.capture())
        return captor.value.toList()
    }
}
