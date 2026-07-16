package com.chamchamcham.application.member

import com.chamchamcham.application.media.ImageUploader
import com.chamchamcham.application.redis.RefreshTokenRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberWithdrawalAfterCommitListener(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val imageUploader: ImageUploader
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: MemberWithdrawalCommitted) {
        runCatching { refreshTokenRepository.delete(event.memberId) }
            .onFailure { logger.warn { "Refresh token cleanup failed after member withdrawal" } }
        event.cloudinaryPublicIds.forEach { publicId ->
            runCatching { imageUploader.delete(publicId) }
                .onFailure { logger.warn { "Cloudinary deletion failed after member withdrawal" } }
        }
    }
}
