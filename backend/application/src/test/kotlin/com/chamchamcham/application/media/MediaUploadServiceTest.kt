package com.chamchamcham.application.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
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
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MediaUploadServiceTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000501")

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var uploadedMediaRepository: UploadedMediaRepository

    private lateinit var uploader: FakeImageUploader
    private lateinit var service: MediaUploadService

    @BeforeEach
    fun setUp() {
        uploader = FakeImageUploader()
        service = MediaUploadService(
            memberRepository = memberRepository,
            uploadedMediaRepository = uploadedMediaRepository,
            imageUploader = uploader,
            maxImageBytes = 10 * 1024 * 1024
        )
    }

    @Test
    fun `upload stores temp image media owned by member`() {
        val member = member()
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member))
        `when`(uploadedMediaRepository.save(any(UploadedMedia::class.java)))
            .thenAnswer { invocation ->
                val media = invocation.arguments[0] as UploadedMedia
                UploadedMedia(
                    id = mediaId,
                    owner = media.owner,
                    mediaType = media.mediaType,
                    usageType = media.usageType,
                    fileUrl = media.fileUrl,
                    cloudinaryPublicId = media.cloudinaryPublicId,
                    status = media.status
                )
            }

        val result = service.upload(
            MediaCommand.UploadImage(
                memberId = memberId,
                usageType = UploadedMediaUsageType.COMMUNITY_POST,
                base64Image = "aGVsbG8=",
                originalFilename = "sprout.jpg",
                contentType = "image/jpeg"
            )
        )

        assertEquals(mediaId, result.mediaId)
        assertEquals("https://example.test/sprout.jpg", result.imageUrl)
        assertEquals(UploadedMediaStatus.TEMP, result.status)
        val saved = capturedMedia()
        assertEquals(member, saved.owner)
        assertEquals(UploadedMediaType.IMAGE, saved.mediaType)
        assertEquals(UploadedMediaUsageType.COMMUNITY_POST, saved.usageType)
        assertEquals("cloud/community/sprout", saved.cloudinaryPublicId)
        assertEquals(UploadedMediaStatus.TEMP, saved.status)
    }

    @Test
    fun `upload rejects missing member before calling uploader`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.empty())

        val exception = assertThrows(BusinessException::class.java) {
            service.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.PROFILE,
                    base64Image = "aGVsbG8=",
                    originalFilename = "profile.jpg",
                    contentType = "image/jpeg"
                )
            )
        }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
        assertEquals(0, uploader.calls)
        verifyNoInteractions(uploadedMediaRepository)
    }

    @Test
    fun `upload rejects image larger than configured decoded byte limit`() {
        `when`(memberRepository.findById(memberId)).thenReturn(Optional.of(member()))

        val exception = assertThrows(BusinessException::class.java) {
            service.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.COMMUNITY_POST,
                    base64Image = "aGVsbG8=",
                    originalFilename = "too-large.jpg",
                    contentType = "image/jpeg"
                ),
                decodedBytesForTest = ByteArray(11 * 1024 * 1024)
            )
        }

        assertEquals(ErrorCode.MEDIA_TOO_LARGE, exception.errorCode)
        assertEquals(0, uploader.calls)
        verifyNoInteractions(uploadedMediaRepository)
    }

    private fun member(): Member =
        Member(id = memberId, email = "farmer@example.com", passwordHash = null)

    private fun capturedMedia(): UploadedMedia {
        val captor = ArgumentCaptor.forClass(UploadedMedia::class.java)
        verify(uploadedMediaRepository).save(captor.capture())
        return captor.value
    }

    private class FakeImageUploader : ImageUploader {
        var calls = 0

        override fun upload(command: ImageUploader.UploadCommand): ImageUploader.UploadedImage {
            calls += 1
            return ImageUploader.UploadedImage(
                imageUrl = "https://example.test/sprout.jpg",
                cloudinaryPublicId = "cloud/community/sprout"
            )
        }

        override fun delete(cloudinaryPublicId: String) = Unit
    }
}
