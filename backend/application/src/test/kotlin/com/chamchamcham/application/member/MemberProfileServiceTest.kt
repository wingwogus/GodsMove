package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberProfileServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val secondFarmId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondCropId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository

    private lateinit var service: MemberProfileService
    private lateinit var member: Member
    private lateinit var farm: Farm
    private lateinit var crop: Crop
    private lateinit var secondCrop: Crop

    @BeforeEach
    fun setUp() {
        member = member()
        member.updateProfileMedia(profileMedia(member))
        farm = farm(farmId, roadAddress = "강원특별자치도 횡성군 둔내면 샘물로 12")
        crop = crop(cropId, "황기")
        secondCrop = crop(secondCropId, "인삼")
        service = MemberProfileService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            memberCropRepository = memberCropRepository
        )
    }

    @Test
    fun `get my profile returns private member fields farms and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(
            listOf(memberCrop(farm, crop), memberCrop(farm, crop), memberCrop(farm, secondCrop))
        )

        val profile = service.getMyProfile(memberId)

        assertEquals(memberId, profile.memberId)
        assertEquals("hwanggi@example.com", profile.email)
        assertEquals("이황기", profile.name)
        assertEquals("010-1000-0001", profile.phone)
        assertEquals(LocalDate.of(1986, 3, 12), profile.birthDate)
        assertEquals("황기농부", profile.nickname)
        assertEquals(2, profile.experienceLevel)
        assertEquals("AGRICULTURAL_INDIVIDUAL", profile.managementType)
        assertEquals("https://example.test/profile.jpg", profile.profileImageUrl)
        assertEquals("강원특별자치도 횡성군", profile.farms.single().displayRegion)
        assertEquals("강원특별자치도 횡성군 둔내면 샘물로 12", profile.farms.single().roadAddress)
        assertThat(profile.crops.map { it.cropId }).containsExactly(cropId, secondCropId)
    }

    @Test
    fun `get public profile excludes private fields and returns region and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(listOf(memberCrop(farm, crop)))

        val profile = service.getPublicProfile(memberId)

        assertEquals(memberId, profile.memberId)
        assertEquals("황기농부", profile.nickname)
        assertEquals(2, profile.experienceLevel)
        assertEquals("AGRICULTURAL_INDIVIDUAL", profile.managementType)
        assertEquals("https://example.test/profile.jpg", profile.profileImageUrl)
        assertEquals("강원특별자치도 횡성군", profile.farms.single().displayRegion)
        assertThat(profile.crops.map { it.cropName }).containsExactly("황기")
    }

    @Test
    fun `display region uses first two tokens and falls back to jibun address`() {
        val blankRoadFarm = farm(
            secondFarmId,
            roadAddress = " ",
            jibunAddress = "제주특별자치도 서귀포시 색달동 1"
        )
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(blankRoadFarm))
        `when`(memberCropRepository.findByMember_Id(memberId)).thenReturn(emptyList())

        val profile = service.getPublicProfile(memberId)

        assertEquals("제주특별자치도 서귀포시", profile.farms.single().displayRegion)
    }

    @Test
    fun `get profile rejects missing member`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.getPublicProfile(memberId)
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(farmRepository)
        verifyNoInteractions(memberCropRepository)
    }

    private fun member(): Member =
        Member(id = memberId, email = "hwanggi@example.com", passwordHash = null).also {
            it.name = "이황기"
            it.phone = "010-1000-0001"
            it.birthDate = LocalDate.of(1986, 3, 12)
            it.nickname = "황기농부"
            it.experienceLevel = 2
            it.managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
        }

    private fun profileMedia(owner: Member): UploadedMedia =
        UploadedMedia(
            id = mediaId,
            owner = owner,
            mediaType = UploadedMediaType.IMAGE,
            usageType = UploadedMediaUsageType.PROFILE,
            fileUrl = "https://example.test/profile.jpg",
            cloudinaryPublicId = "profile/member",
            status = UploadedMediaStatus.ATTACHED
        )

    private fun farm(
        id: UUID,
        roadAddress: String,
        jibunAddress: String? = "강원특별자치도 횡성군 둔내면 현천리 101"
    ): Farm =
        Farm(
            id = id,
            owner = member,
            name = "횡성 황기밭",
            roadAddress = roadAddress,
            jibunAddress = jibunAddress
        )

    private fun crop(id: UUID, name: String): Crop =
        Crop(id = id, externalNo = id.hashCode(), name = name, usePartCategory = CropUsePartCategory.ROOT_BARK)

    private fun memberCrop(farm: Farm, crop: Crop): MemberCrop =
        MemberCrop(member = member, farm = farm, crop = crop)
}
