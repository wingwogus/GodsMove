package com.chamchamcham.api.media.dto

import com.chamchamcham.domain.media.UploadedMediaUsageType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object MediaRequests {
    data class UploadImageRequest(
        @field:NotNull(message = "이미지 용도를 입력해주세요")
        val usageType: UploadedMediaUsageType?,

        @field:NotBlank(message = "이미지 데이터를 입력해주세요")
        val base64Image: String,

        val originalFilename: String? = null,

        val contentType: String? = null
    )
}
