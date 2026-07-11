package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.Crop
import com.chamchamcham.domain.crop.CropRepository
import com.chamchamcham.domain.crop.CropUsePartCategory
import com.chamchamcham.domain.crop.MemberCrop
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.Farm
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
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
class MemberProfileServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val farmId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val secondFarmId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val cropId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondCropId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var cropRepository: CropRepository
    @Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository

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
            memberCropRepository = memberCropRepository,
            cropRepository = cropRepository,
            uploadedMediaRepository = uploadedMediaRepository
        )
    }

    @Test
    fun `get my profile returns private member fields farms and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(
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
        assertEquals("https://example.test/$mediaId.jpg", profile.profileImageUrl)
        assertEquals("강원특별자치도 횡성군", profile.farms.single().displayRegion)
        assertEquals("강원특별자치도 횡성군 둔내면 샘물로 12", profile.farms.single().roadAddress)
        assertThat(profile.crops.map { it.cropId }).containsExactly(cropId, secondCropId)
    }

    @Test
    fun `get my farm crops groups crops by farm`() {
        val secondFarm = farm(secondFarmId, roadAddress = "제주특별자치도 서귀포시 색달동 1")
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm, secondFarm))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(
            listOf(memberCrop(farm, crop), memberCrop(farm, secondCrop))
        )

        val farmCrops = service.getMyFarmCrops(memberId)

        assertEquals(2, farmCrops.size)
        val firstFarmCrops = farmCrops.first { it.farmId == farmId }
        assertThat(firstFarmCrops.crops.map { it.id }).containsExactly(cropId, secondCropId)
        val secondFarmCrops = farmCrops.first { it.farmId == secondFarmId }
        assertThat(secondFarmCrops.crops).isEmpty()
    }

    @Test
    fun `get public profile excludes private fields and returns region and crops`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(listOf(memberCrop(farm, crop)))

        val profile = service.getPublicProfile(memberId)

        assertEquals(memberId, profile.memberId)
        assertEquals("황기농부", profile.nickname)
        assertEquals(2, profile.experienceLevel)
        assertEquals("AGRICULTURAL_INDIVIDUAL", profile.managementType)
        assertEquals("https://example.test/$mediaId.jpg", profile.profileImageUrl)
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
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(emptyList())

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

    @Test
    fun `update my profile changes basic fields keeps current image updates farm and replaces farm crops`() {
        val currentProfileMedia = requireNotNull(member.profileMedia)
        val command = updateCommand(
            profileMediaId = currentProfileMedia.id,
            farms = listOf(updateFarmCommand(farmId, listOf(cropId, secondCropId)))
        )
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
        `when`(cropRepository.findAllById(listOf(cropId, secondCropId))).thenReturn(listOf(crop, secondCrop))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(
            listOf(memberCrop(farm, crop), memberCrop(farm, secondCrop))
        )
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

        val profile = service.updateMyProfile(command)

        assertEquals("수정 이름", member.name)
        assertEquals("010-2000-0002", member.phone)
        assertEquals(LocalDate.of(1990, 4, 5), member.birthDate)
        assertEquals("수정닉", member.nickname)
        assertEquals(7, member.experienceLevel)
        assertEquals(ManagementType.NON_REGISTERED_FARMER, member.managementType)
        assertEquals(UploadedMediaStatus.ATTACHED, currentProfileMedia.status)
        assertEquals("수정 농장", farm.name)
        assertEquals("강원특별자치도 평창군 새로 2", farm.roadAddress)
        verify(memberCropRepository).deleteByMemberIdAndFarmId(memberId, farmId)
        assertThat(capturedMemberCrops().map { it.crop.id }).containsExactly(cropId, secondCropId)
        assertEquals(memberId, profile.memberId)
    }

    @Test
    fun `update my profile replaces profile image and deletes previous image`() {
        val previousMedia = requireNotNull(member.profileMedia)
        val newMediaId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val newMedia = profileMedia(member, newMediaId, status = UploadedMediaStatus.TEMP)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(uploadedMediaRepository.findById(newMediaId)).thenReturn(Optional.of(newMedia))
        `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
        `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(listOf(memberCrop(farm, crop)))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

        service.updateMyProfile(updateCommand(profileMediaId = newMediaId, farms = listOf(updateFarmCommand(farmId))))

        assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
        assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
        assertEquals(newMedia, member.profileMedia)
    }

    @Test
    fun `update my profile removes profile image when profile media id is null`() {
        val previousMedia = requireNotNull(member.profileMedia)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
        `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(listOf(crop))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(listOf(memberCrop(farm, crop)))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm))

        service.updateMyProfile(updateCommand(profileMediaId = null, farms = listOf(updateFarmCommand(farmId))))

        assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
        assertEquals(null, member.profileMedia)
    }

    @Test
    fun `update my profile adds new farm and keeps omitted existing farms`() {
        val newFarmId = UUID.fromString("00000000-0000-0000-0000-000000000103")
        val savedFarm = farm(newFarmId, roadAddress = "강원특별자치도 평창군 새로 2")
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.save(any(Farm::class.java))).thenReturn(savedFarm)
        `when`(cropRepository.findAllById(listOf(secondCropId))).thenReturn(listOf(secondCrop))
        `when`(memberCropRepository.findByMemberId(memberId)).thenReturn(listOf(memberCrop(savedFarm, secondCrop)))
        `when`(farmRepository.findByOwnerId(memberId)).thenReturn(listOf(farm, savedFarm))

        service.updateMyProfile(
            updateCommand(
                profileMediaId = requireNotNull(member.profileMedia).id,
                farms = listOf(updateFarmCommand(null, listOf(secondCropId)))
            )
        )

        verify(farmRepository, never()).delete(any(Farm::class.java))
        verify(memberCropRepository).deleteByMemberIdAndFarmId(memberId, newFarmId)
        assertThat(capturedMemberCrops().map { it.crop.id }).containsExactly(secondCropId)
    }

    @Test
    fun `update my profile rejects another member farm`() {
        val otherMember = Member(id = otherMemberId, email = "other@example.com", passwordHash = null)
        val otherFarm = Farm(id = farmId, owner = otherMember, name = "남의 농장", roadAddress = "서울특별시 강남구")
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(otherFarm))

        val exception = assertThrows(BusinessException::class.java) {
            service.updateMyProfile(
                updateCommand(
                    profileMediaId = requireNotNull(member.profileMedia).id,
                    farms = listOf(updateFarmCommand(farmId))
                )
            )
        }

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(cropRepository)
    }

    @Test
    fun `update my profile rejects missing crop`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(farmRepository.findById(farmId)).thenReturn(Optional.of(farm))
        `when`(cropRepository.findAllById(listOf(cropId))).thenReturn(emptyList())

        val exception = assertThrows(BusinessException::class.java) {
            service.updateMyProfile(
                updateCommand(
                    profileMediaId = requireNotNull(member.profileMedia).id,
                    farms = listOf(updateFarmCommand(farmId))
                )
            )
        }

        assertEquals(ErrorCode.CROP_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `update my profile rejects another member profile image`() {
        val otherMediaId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val otherMember = Member(id = otherMemberId, email = "other@example.com", passwordHash = null)
        val otherMedia = profileMedia(otherMember, otherMediaId, status = UploadedMediaStatus.TEMP)
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(uploadedMediaRepository.findById(otherMediaId)).thenReturn(Optional.of(otherMedia))

        val exception = assertThrows(BusinessException::class.java) {
            service.updateMyProfile(
                updateCommand(
                    profileMediaId = otherMediaId,
                    farms = listOf(updateFarmCommand(farmId))
                )
            )
        }

        assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
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
        profileMedia(owner, mediaId, status = UploadedMediaStatus.ATTACHED)

    private fun profileMedia(
        owner: Member,
        id: UUID,
        usageType: UploadedMediaUsageType = UploadedMediaUsageType.PROFILE,
        status: UploadedMediaStatus = UploadedMediaStatus.ATTACHED
    ): UploadedMedia =
        UploadedMedia(
            id = id,
            owner = owner,
            mediaType = UploadedMediaType.IMAGE,
            usageType = usageType,
            fileUrl = "https://example.test/$id.jpg",
            cloudinaryPublicId = "profile/$id",
            status = status
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

    private fun updateCommand(
        profileMediaId: UUID?,
        farms: List<MemberProfileCommand.Farm>
    ): MemberProfileCommand.UpdateMyProfile =
        MemberProfileCommand.UpdateMyProfile(
            memberId = memberId,
            name = "수정 이름",
            phone = "010-2000-0002",
            birthDate = LocalDate.of(1990, 4, 5),
            nickname = "수정닉",
            experienceLevel = 7,
            managementType = ManagementType.NON_REGISTERED_FARMER,
            profileMediaId = profileMediaId,
            farms = farms
        )

    private fun updateFarmCommand(
        farmId: UUID?,
        cropIds: List<UUID> = listOf(cropId)
    ): MemberProfileCommand.Farm =
        MemberProfileCommand.Farm(
            farmId = farmId,
            name = "수정 농장",
            roadAddress = "강원특별자치도 평창군 새로 2",
            jibunAddress = null,
            latitude = 37.2,
            longitude = 128.2,
            pnu = "new-pnu",
            landCategory = "답",
            areaSqm = BigDecimal("200.50"),
            areaIsManualEntry = true,
            boundaryCoordinates = listOf(MemberProfileCommand.FarmBoundaryCoordinate(37.2, 128.2)),
            dataSource = MemberProfileCommand.FarmDataSource("KAKAO", "KAKAO", "PUBLIC_DATA", null),
            cropIds = cropIds
        )

    private fun capturedMemberCrops(): List<MemberCrop> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<MemberCrop>>
        verify(memberCropRepository).saveAll(captor.capture())
        return captor.value.toList()
    }
}
