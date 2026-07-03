package com.chamchamcham.application.auth.social

import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.AuthResult
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.redis.KakaoNonceReplayRepository
import com.chamchamcham.application.security.KakaoOidcClaims
import com.chamchamcham.application.security.KakaoOidcTokenVerifier
import com.chamchamcham.domain.member.AuthProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional
class KakaoLoginService(
    private val kakaoOidcTokenVerifier: KakaoOidcTokenVerifier,
    private val kakaoUserInfoClient: KakaoUserInfoClient,
    private val kakaoNonceReplayRepository: KakaoNonceReplayRepository,
    private val socialLoginSupport: SocialLoginSupport,
    @Value("\${auth.kakao.oidc.nonce-replay-ttl-seconds:600}")
    nonceReplayTtlSeconds: Long,
    @Value("\${auth.kakao.oidc.allowed-clock-skew-seconds:60}")
    allowedClockSkewSeconds: Long
) {
    private val maxNonceReplayTtl = Duration.ofSeconds(nonceReplayTtlSeconds)
    private val allowedClockSkew = Duration.ofSeconds(allowedClockSkewSeconds)

    fun login(command: AuthCommand.KakaoLogin): AuthResult.Login {
        val claims = kakaoOidcTokenVerifier.verify(command.idToken, command.nonce)
        val userInfo = command.kakaoAccessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { kakaoUserInfoClient.fetch(it) }

        if (userInfo != null && userInfo.subject != claims.subject) {
            throw BusinessException(ErrorCode.INVALID_KAKAO_TOKEN)
        }

        return socialLoginSupport.login(
            provider = AuthProvider.KAKAO,
            providerSubject = claims.subject,
            email = userInfo?.email ?: claims.email?.takeIf { claims.emailVerified },
            name = userInfo?.name,
            phone = userInfo?.phone,
            birthDate = userInfo?.birthDate,
            beforeSideEffects = { reserveNonce(claims) }
        )
    }

    private fun reserveNonce(claims: KakaoOidcClaims) {
        val ttl = nonceReplayTtl(claims.expiresAt)
        if (!kakaoNonceReplayRepository.reserve(claims.nonce, ttl)) {
            throw BusinessException(ErrorCode.KAKAO_NONCE_REPLAY)
        }
    }

    private fun nonceReplayTtl(expiresAt: Instant): Duration {
        val acceptedUntil = expiresAt.plus(allowedClockSkew)
        val untilExpiry = Duration.between(Instant.now(), acceptedUntil)
        if (untilExpiry <= Duration.ZERO) {
            throw BusinessException(ErrorCode.INVALID_KAKAO_TOKEN)
        }
        return if (untilExpiry < maxNonceReplayTtl) untilExpiry else maxNonceReplayTtl
    }
}
