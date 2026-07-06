package com.chamchamcham.application.media

import com.chamchamcham.domain.media.UploadedMedia
import com.chamchamcham.domain.media.UploadedMediaStatus
import java.util.UUID

object MediaResult {
    data class UploadedImage(
        val mediaId: UUID,
        val imageUrl: String,
        val status: UploadedMediaStatus
    ) {
        companion object {
            fun from(media: UploadedMedia): UploadedImage =
                UploadedImage(
                    mediaId = requireNotNull(media.id) { "Persisted media id is required" },
                    imageUrl = media.fileUrl,
                    status = media.status
                )
        }
    }
}
