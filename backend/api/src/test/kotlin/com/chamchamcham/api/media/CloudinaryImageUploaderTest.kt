package com.chamchamcham.api.media

import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class CloudinaryImageUploaderTest {
    @Mock private lateinit var cloudinary: Cloudinary
    @Mock private lateinit var uploader: Uploader

    private lateinit var adapter: CloudinaryImageUploader

    @BeforeEach
    fun setUp() {
        `when`(cloudinary.uploader()).thenReturn(uploader)
        adapter = CloudinaryImageUploader(cloudinary)
    }

    @Test
    fun `delete maps ok and invalidates cached image`() {
        `when`(uploader.destroy(eq("community/post-image"), anyMap<String, Any>()))
            .thenReturn(mapOf("result" to "ok"))

        adapter.delete("community/post-image")

        verify(uploader).destroy(
            eq("community/post-image"),
            argThat { options ->
                options["resource_type"] == "image" && options["invalidate"] == true
            }
        )
    }

    @Test
    fun `delete maps not found as terminal success`() {
        `when`(uploader.destroy(anyString(), anyMap<String, Any>()))
            .thenReturn(mapOf("result" to "not found"))

        adapter.delete("missing")
    }

    @Test
    fun `delete wraps provider failure`() {
        `when`(uploader.destroy(anyString(), anyMap<String, Any>()))
            .thenThrow(IllegalStateException("down"))

        val exception = assertThrows(BusinessException::class.java) {
            adapter.delete("community/post-image")
        }

        assertEquals(ErrorCode.MEDIA_DELETE_FAILED, exception.errorCode)
    }
}
