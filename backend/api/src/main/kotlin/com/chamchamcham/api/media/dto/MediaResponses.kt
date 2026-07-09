package com.chamchamcham.api.media.dto

import com.chamchamcham.application.media.MediaResult
import com.chamchamcham.domain.media.UploadedMediaStatus
import java.util.UUID

object MediaResponses {
    data class UploadedImageResponse(
        val mediaId: UUID,
        val imageUrl: String,
        val status: UploadedMediaStatus
    ) {
        companion object {
            fun from(result: MediaResult.UploadedImage): UploadedImageResponse =
                UploadedImageResponse(
                    mediaId = result.mediaId,
                    imageUrl = result.imageUrl,
                    status = result.status
                )
        }
    }
}
