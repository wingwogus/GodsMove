package com.chamchamcham.application.member

import com.chamchamcham.application.media.ImageUploader
import com.chamchamcham.application.redis.RefreshTokenRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberWithdrawalAfterCommitListenerTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val publicIds = listOf("profile/image", "community/image")

    @Mock private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock private lateinit var imageUploader: ImageUploader

    @Test
    fun `after commit removes refresh token and deletes Cloudinary originals once`() {
        val listener = MemberWithdrawalAfterCommitListener(refreshTokenRepository, imageUploader)

        listener.handle(MemberWithdrawalCommitted(memberId, publicIds))

        verify(refreshTokenRepository).delete(memberId)
        verify(imageUploader).delete("profile/image")
        verify(imageUploader).delete("community/image")
    }

    @Test
    fun `refresh cleanup failure does not block Cloudinary tasks`() {
        val listener = MemberWithdrawalAfterCommitListener(refreshTokenRepository, imageUploader)
        doThrow(IllegalStateException("redis down"))
            .`when`(refreshTokenRepository).delete(memberId)

        listener.handle(MemberWithdrawalCommitted(memberId, publicIds))

        verify(imageUploader).delete("profile/image")
        verify(imageUploader).delete("community/image")
    }

    @Test
    fun `one Cloudinary failure does not block remaining deletes`() {
        val listener = MemberWithdrawalAfterCommitListener(refreshTokenRepository, imageUploader)
        doThrow(IllegalStateException("provider down"))
            .`when`(imageUploader).delete("profile/image")

        listener.handle(MemberWithdrawalCommitted(memberId, publicIds))

        verify(imageUploader).delete("community/image")
    }
}
