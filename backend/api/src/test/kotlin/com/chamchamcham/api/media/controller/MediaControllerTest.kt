package com.chamchamcham.api.media.controller

import com.chamchamcham.api.exception.GlobalExceptionHandler
import com.chamchamcham.application.media.MediaCommand
import com.chamchamcham.application.media.MediaResult
import com.chamchamcham.application.media.MediaUploadService
import com.chamchamcham.application.security.TokenProvider
import com.chamchamcham.domain.media.UploadedMediaStatus
import com.chamchamcham.domain.media.UploadedMediaUsageType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(MediaController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler::class)
class MediaControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000501")

    @MockBean
    private lateinit var mediaUploadService: MediaUploadService

    @MockBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    fun `upload image returns uploaded media response`() {
        `when`(
            mediaUploadService.upload(
                MediaCommand.UploadImage(
                    memberId = memberId,
                    usageType = UploadedMediaUsageType.COMMUNITY_POST,
                    base64Image = "aGVsbG8=",
                    originalFilename = "sprout.jpg",
                    contentType = "image/jpeg"
                )
            )
        ).thenReturn(
            MediaResult.UploadedImage(
                mediaId = mediaId,
                imageUrl = "https://example.test/sprout.jpg",
                status = UploadedMediaStatus.TEMP
            )
        )

        mockMvc.perform(
            post("/api/v1/media/images")
                .with(authenticatedMember(memberId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "usageType": "COMMUNITY_POST",
                      "base64Image": "aGVsbG8=",
                      "originalFilename": "sprout.jpg",
                      "contentType": "image/jpeg"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mediaId", equalTo(mediaId.toString())))
            .andExpect(jsonPath("$.data.imageUrl", equalTo("https://example.test/sprout.jpg")))
            .andExpect(jsonPath("$.data.status", equalTo("TEMP")))
    }

    private fun authenticatedMember(memberId: String): RequestPostProcessor {
        return RequestPostProcessor { request ->
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            request
        }
    }
}
