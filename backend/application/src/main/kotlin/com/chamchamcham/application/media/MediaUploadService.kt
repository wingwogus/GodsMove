package com.chamchamcham.application.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaRepository
import com.chamchamcham.domain.media.UploadedMediaType
import com.chamchamcham.domain.media.UploadedMediaUsageType
import com.chamchamcham.domain.member.MemberRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@Service
@Transactional
class MediaUploadService(
    private val memberRepository: MemberRepository,
    private val uploadedMediaRepository: UploadedMediaRepository,
    private val imageUploader: ImageUploader,
    @Value("\${app.media.max-image-bytes:10485760}")
    private val maxImageBytes: Int
) {
    fun upload(
        command: MediaCommand.UploadImage,
        decodedBytesForTest: ByteArray? = null
    ): MediaResult.UploadedImage {
        val member = memberRepository.findById(command.memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }
        val decoded = decodedBytesForTest ?: decodeBase64(command.base64Image)
        if (decoded.size > maxImageBytes) {
            throw BusinessException(ErrorCode.MEDIA_TOO_LARGE)
        }

        val uploaded = imageUploader.upload(
            ImageUploader.UploadCommand(
                bytes = decoded,
                originalFilename = command.originalFilename,
                contentType = command.contentType,
                usageFolder = command.usageType.folderName()
            )
        )

        val media = uploadedMediaRepository.save(
            UploadedMedia(
                owner = member,
                mediaType = UploadedMediaType.IMAGE,
                usageType = command.usageType,
                fileUrl = uploaded.imageUrl,
                cloudinaryPublicId = uploaded.cloudinaryPublicId
            )
        )

        return MediaResult.UploadedImage.from(media)
    }

    private fun decodeBase64(value: String): ByteArray {
        return try {
            val payload = value.substringAfter(",", value)
            Base64.getDecoder().decode(payload)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun UploadedMediaUsageType.folderName(): String =
        when (this) {
            UploadedMediaUsageType.PROFILE -> "profiles"
            UploadedMediaUsageType.COMMUNITY_POST -> "community-posts"
        }
}
