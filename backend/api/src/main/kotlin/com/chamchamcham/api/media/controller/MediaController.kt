package com.chamchamcham.api.media.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.media.dto.MediaRequests
import com.chamchamcham.api.media.dto.MediaResponses
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.media.MediaCommand
import com.chamchamcham.application.media.MediaUploadService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaController(
    private val mediaUploadService: MediaUploadService
) {
    @PostMapping("/images")
    fun uploadImage(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: MediaRequests.UploadImageRequest
    ): ResponseEntity<ApiResponse<MediaResponses.UploadedImageResponse>> {
        val result = mediaUploadService.upload(
            MediaCommand.UploadImage(
                memberId = parseMemberId(memberId),
                usageType = requireNotNull(request.usageType),
                base64Image = request.base64Image,
                originalFilename = request.originalFilename,
                contentType = request.contentType
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(MediaResponses.UploadedImageResponse.from(result)))
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
