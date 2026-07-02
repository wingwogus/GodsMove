package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.redis.AppleNonceReplayRepository
import com.godsmove.application.security.AppleOidcClaims
import com.godsmove.application.security.AppleOidcTokenVerifier
import com.godsmove.domain.member.AuthProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional
class AppleLoginService(
    private val appleOidcTokenVerifier: AppleOidcTokenVerifier,
    private val appleNonceReplayRepository: AppleNonceReplayRepository,
    private val socialLoginSupport: SocialLoginSupport,
    @Value("\${auth.apple.oidc.allowed-clock-skew-seconds:60}")
    allowedClockSkewSeconds: Long
) {
    private val allowedClockSkew = Duration.ofSeconds(allowedClockSkewSeconds)

    fun login(command: AuthCommand.AppleLogin): AuthResult.Login {
        val claims = appleOidcTokenVerifier.verify(command.identityToken, command.nonce)
        validateUserIdentifier(command.userIdentifier, claims.subject)
        reserveNonce(claims)

        return socialLoginSupport.login(
            provider = AuthProvider.APPLE,
            providerSubject = claims.subject,
            email = claims.email?.takeIf { claims.emailVerified },
            emailRequiredErrorCode = ErrorCode.APPLE_VERIFIED_EMAIL_REQUIRED
        )
    }

    private fun validateUserIdentifier(userIdentifier: String?, subject: String) {
        if (!userIdentifier.isNullOrBlank() && userIdentifier != subject) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
    }

    private fun reserveNonce(claims: AppleOidcClaims) {
        val ttl = nonceReplayTtl(claims.expiresAt)
        if (!appleNonceReplayRepository.reserve(claims.nonce, ttl)) {
            throw BusinessException(ErrorCode.APPLE_NONCE_REPLAY)
        }
    }

    private fun nonceReplayTtl(expiresAt: Instant): Duration {
        val ttl = Duration.between(Instant.now(), expiresAt.plus(allowedClockSkew))
        if (ttl <= Duration.ZERO) {
            throw BusinessException(ErrorCode.INVALID_APPLE_TOKEN)
        }
        return ttl
    }
}
