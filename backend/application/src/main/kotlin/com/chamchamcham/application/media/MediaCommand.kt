package com.chamchamcham.application.media

import com.chamchamcham.domain.media.UploadedMediaUsageType
import java.util.UUID

object MediaCommand {
    data class UploadImage(
        val memberId: UUID,
        val usageType: UploadedMediaUsageType,
        val base64Image: String,
        val originalFilename: String?,
        val contentType: String?
    )
}
