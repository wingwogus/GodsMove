package com.chamchamcham.application.member

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.crop.MemberCropRepository
import com.chamchamcham.domain.farm.FarmRepository
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.ManagementType
import com.chamchamcham.domain.member.Member
import com.chamchamcham.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberProfileServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000301")

    @Mock private lateinit var memberRepository: MemberRepository
    @Mock private lateinit var farmRepository: FarmRepository
    @Mock private lateinit var memberCropRepository: MemberCropRepository
    @Mock private lateinit var uploadedMediaRepository: UploadedMediaRepository

    private lateinit var service: MemberProfileService
    private lateinit var member: Member

    @BeforeEach
    fun setUp() {
        member = Member(id = memberId, email = "member@example.com", passwordHash = null).also {
            it.name = "기존 이름"
            it.phone = "010-1111-1111"
            it.birthDate = LocalDate.of(1990, 1, 1)
            it.nickname = "기존닉"
            it.experienceLevel = 1
            it.managementType = ManagementType.AGRICULTURAL_INDIVIDUAL
            it.updateProfileMedia(profileMedia(it, mediaId, UploadedMediaStatus.ATTACHED))
        }
        service = MemberProfileService(
            memberRepository = memberRepository,
            farmRepository = farmRepository,
            memberCropRepository = memberCropRepository,
            uploadedMediaRepository = uploadedMediaRepository
        )
    }

    @Test
    fun `update profile changes member fields without farm mutation`() {
        stubProfileRead()
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))

        service.updateMyProfile(updateCommand(profileMediaId = mediaId))

        assertEquals("수정 이름", member.name)
        assertEquals("010-2222-2222", member.phone)
        assertEquals(LocalDate.of(1991, 2, 2), member.birthDate)
        assertEquals("수정닉", member.nickname)
        assertEquals(7, member.experienceLevel)
        assertEquals(ManagementType.NON_REGISTERED_FARMER, member.managementType)
    }

    @Test
    fun `update profile replaces profile media`() {
        val newMediaId = UUID.fromString("00000000-0000-0000-0000-000000000302")
        val newMedia = profileMedia(member, newMediaId, UploadedMediaStatus.TEMP)
        val previousMedia = requireNotNull(member.profileMedia)
        stubProfileRead()
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(uploadedMediaRepository.findById(newMediaId)).willReturn(Optional.of(newMedia))

        service.updateMyProfile(updateCommand(profileMediaId = newMediaId))

        assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
        assertEquals(newMedia, member.profileMedia)
        assertEquals(UploadedMediaStatus.ATTACHED, newMedia.status)
    }

    @Test
    fun `update profile removes profile media`() {
        val previousMedia = requireNotNull(member.profileMedia)
        stubProfileRead()
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))

        service.updateMyProfile(updateCommand(profileMediaId = null))

        assertEquals(UploadedMediaStatus.DELETED, previousMedia.status)
        assertNull(member.profileMedia)
    }

    @Test
    fun `update profile rejects another members profile media`() {
        val otherMember = Member(id = UUID.randomUUID(), email = "other@example.com", passwordHash = null)
        val otherMedia = profileMedia(otherMember, UUID.randomUUID(), UploadedMediaStatus.TEMP)
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member))
        given(uploadedMediaRepository.findById(requireNotNull(otherMedia.id))).willReturn(Optional.of(otherMedia))

        val exception = assertThrows(BusinessException::class.java) {
            service.updateMyProfile(updateCommand(profileMediaId = requireNotNull(otherMedia.id)))
        }

        assertEquals(ErrorCode.MEDIA_NOT_OWNED, exception.errorCode)
    }

    private fun stubProfileRead() {
        given(farmRepository.findByOwnerId(memberId)).willReturn(emptyList())
        given(memberCropRepository.findByMemberId(memberId)).willReturn(emptyList())
    }

    private fun updateCommand(profileMediaId: UUID?): MemberProfileCommand.UpdateMyProfile =
        MemberProfileCommand.UpdateMyProfile(
            memberId = memberId,
            name = "수정 이름",
            phone = "010-2222-2222",
            birthDate = LocalDate.of(1991, 2, 2),
            nickname = "수정닉",
            experienceLevel = 7,
            managementType = ManagementType.NON_REGISTERED_FARMER,
            profileMediaId = profileMediaId
        )

    private fun profileMedia(owner: Member, id: UUID, status: UploadedMediaStatus): UploadedMedia =
        UploadedMedia(
            id = id,
            owner = owner,
            mediaType = UploadedMediaType.IMAGE,
            usageType = UploadedMediaUsageType.PROFILE,
            fileUrl = "https://example.test/$id.jpg",
            cloudinaryPublicId = "profile/$id",
            status = status
        )
}
