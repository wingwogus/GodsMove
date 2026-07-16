package com.chamchamcham.api.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.media.ImageUploader
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.stereotype.Component

@Component
class CloudinaryImageUploader(
    private val cloudinary: Cloudinary
) : ImageUploader {
    override fun upload(command: ImageUploader.UploadCommand): ImageUploader.UploadedImage {
        return try {
            val uploadResult = cloudinary.uploader().upload(
                command.bytes,
                ObjectUtils.asMap(
                    "folder", command.usageFolder,
                    "resource_type", "image"
                )
            )
            ImageUploader.UploadedImage(
                imageUrl = uploadResult["secure_url"] as String,
                cloudinaryPublicId = uploadResult["public_id"] as String
            )
        } catch (exception: Exception) {
            throw BusinessException(ErrorCode.MEDIA_UPLOAD_FAILED)
        }
    }

    override fun delete(cloudinaryPublicId: String) {
        try {
            val result = cloudinary.uploader().destroy(
                cloudinaryPublicId,
                ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true
                )
            )["result"] as? String

            when (result) {
                "ok", "not found" -> Unit
                else -> throw BusinessException(ErrorCode.MEDIA_DELETE_FAILED)
            }
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: Exception) {
            throw BusinessException(ErrorCode.MEDIA_DELETE_FAILED)
        }
    }
}
