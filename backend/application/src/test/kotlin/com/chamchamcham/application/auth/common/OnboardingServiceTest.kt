package com.chamchamcham.application.auth.common

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
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
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OnboardingServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val cropId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val secondCropId = UUID.fromString("20000000-0000-0000-0000-000000000002")

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var farmRepository: FarmRepository

    @Mock
    private lateinit var cropRepository: CropRepository

    @Mock
    private lateinit var memberCropRepository: MemberCropRepository

    private lateinit var onboardingStatusResolver: OnboardingStatusResolver
    private lateinit var service: OnboardingService

    @BeforeEach
    fun setUp() {
        onboardingStatusResolver = OnboardingStatusResolver()
        service = OnboardingService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            cropRepository = cropRepository,
            memberCropRepository = memberCropRepository,
            onboardingStatusResolver = onboardingStatusResolver
        )
    }

    @Test
    fun `complete stores profile farm and crops then returns complete`() {
        val member = member()
        val crop = crop(id = cropId, externalNo = 422, name = "참당귀")
        val command = completeOnboardingCommand(cropIds = listOf(cropId))

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
        `when`(farmRepository.save(any(Farm::class.java))).thenReturn(
            savedFarm(member, command.farm)
        )

        val result = service.complete(command)

        assertEquals(memberId, result.member.id)
        assertEquals("홍길동", member.name)
        assertEquals("010-1234-5678", member.phone)
        assertEquals(LocalDate.of(1990, 1, 1), member.birthDate)
        assertEquals("길동", member.nickname)
        assertEquals(72, member.experienceLevel)
        assertEquals(ManagementType.AGRICULTURAL_INDIVIDUAL, member.managementType)
        assertEquals(farmId, result.farm.id)
        assertEquals("서울 약초농장", result.farm.name)
        assertEquals("서울특별시 강남구 테헤란로 1", result.farm.roadAddress)
        assertEquals("서울특별시 강남구 역삼동 1", result.farm.jibunAddress)
        assertEquals("4511310200101230004", result.farm.pnu)
        assertThat(result.crops.map { it.id }).containsExactly(cropId)
        assertEquals(AuthResult.OnboardingStatus.COMPLETE, result.onboarding.status)
    }

    @Test
    fun `complete de-duplicates crop ids preserving requested order`() {
        val member = member()
        val firstCrop = crop(id = cropId, externalNo = 422, name = "참당귀")
        val secondCrop = crop(id = secondCropId, externalNo = 107, name = "작약")
        val command = completeOnboardingCommand(cropIds = listOf(cropId, secondCropId, cropId))

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(listOf(secondCrop, firstCrop))
        `when`(farmRepository.save(any(Farm::class.java))).thenReturn(
            savedFarm(member, command.farm)
        )

        val result = service.complete(command)

        assertThat(result.crops.map { it.id }).containsExactly(cropId, secondCropId)
        val savedMemberCrops = capturedMemberCrops()
        assertThat(savedMemberCrops.map { it.crop.id }).containsExactly(cropId, secondCropId)
    }

    @Test
    fun `complete rejects missing crop before farm or member crop saves`() {
        val member = member()
        val command = completeOnboardingCommand(cropIds = listOf(cropId, secondCropId))

        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(
            listOf(crop(id = cropId, externalNo = 422, name = "참당귀"))
        )

        val exception = assertThrows(BusinessException::class.java) {
            service.complete(command)
        }

        assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
        verify(farmRepository, never()).save(any(Farm::class.java))
        verifyNoInteractions(memberCropRepository)
    }

    @Test
    fun `complete rejects empty crop ids before lookups or saves`() {
        val exception = assertThrows(BusinessException::class.java) {
            service.complete(completeOnboardingCommand(cropIds = emptyList()))
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(memberRepository, cropRepository)
        verify(farmRepository, never()).save(any(Farm::class.java))
        verifyNoInteractions(memberCropRepository)
    }

    @Test
    fun `complete rejects missing member`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.complete(completeOnboardingCommand())
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(cropRepository, farmRepository, memberCropRepository)
    }

    private fun member(): Member {
        return Member(
            id = memberId,
            email = "user@example.com",
            passwordHash = null
        )
    }

    private fun crop(
        id: UUID,
        externalNo: Int,
        name: String
    ): Crop {
        return Crop(
            id = id,
            externalNo = externalNo,
            name = name,
            usePartCategory = CropUsePartCategory.ROOT_BARK
        )
    }

    private fun completeOnboardingCommand(
        cropIds: List<UUID> = listOf(cropId)
    ): AuthCommand.CompleteOnboarding {
        return AuthCommand.CompleteOnboarding(
            memberId = memberId,
            name = "홍길동",
            phone = "010-1234-5678",
            birthDate = LocalDate.of(1990, 1, 1),
            nickname = "길동",
            experienceLevel = 72,
            managementType = ManagementType.AGRICULTURAL_INDIVIDUAL,
            farm = farmCommand(),
            cropIds = cropIds
        )
    }

    private fun farmCommand(): AuthCommand.Farm {
        return AuthCommand.Farm(
            name = "서울 약초농장",
            roadAddress = "서울특별시 강남구 테헤란로 1",
            jibunAddress = "서울특별시 강남구 역삼동 1",
            latitude = 35.8465,
            longitude = 127.1292,
            pnu = "4511310200101230004",
            landCategory = "전",
            areaSqm = BigDecimal("1200.5"),
            areaIsManualEntry = false,
            boundaryCoordinates = listOf(
                AuthCommand.FarmBoundaryCoordinate(latitude = 35.8461, longitude = 127.1289),
                AuthCommand.FarmBoundaryCoordinate(latitude = 35.8463, longitude = 127.1295)
            ),
            dataSource = AuthCommand.FarmDataSource(
                address = "JUSO",
                coordinate = "V_WORLD_ADDRESS",
                parcel = "V_WORLD_CADASTRAL",
                landCharacteristic = "V_WORLD_LAND_CHARACTERISTIC"
            )
        )
    }

    private fun savedFarm(member: Member, farm: AuthCommand.Farm): Farm {
        return Farm(
            id = farmId,
            owner = member,
            name = farm.name,
            roadAddress = farm.roadAddress,
            jibunAddress = farm.jibunAddress,
            latitude = farm.latitude,
            longitude = farm.longitude,
            pnu = farm.pnu,
            landCategory = farm.landCategory,
            areaSqm = farm.areaSqm,
            areaIsManualEntry = farm.areaIsManualEntry
        )
    }

    private fun capturedMemberCrops(): List<MemberCrop> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<MemberCrop>>
        verify(memberCropRepository).saveAll(captor.capture())
        return captor.value.toList()
    }
}
