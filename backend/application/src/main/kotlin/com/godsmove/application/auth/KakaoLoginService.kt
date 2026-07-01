package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.redis.KakaoNonceReplayRepository
import com.godsmove.application.redis.RefreshTokenRepository
import com.godsmove.application.security.KakaoOidcClaims
import com.godsmove.application.security.KakaoOidcTokenVerifier
import com.godsmove.application.security.TokenProvider
import com.godsmove.domain.member.AuthProvider
import com.godsmove.domain.member.ExternalIdentity
import com.godsmove.domain.member.ExternalIdentityRepository
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class KakaoLoginService(
    private val kakaoOidcTokenVerifier: KakaoOidcTokenVerifier,
    private val kakaoNonceReplayRepository: KakaoNonceReplayRepository,
    private val externalIdentityRepository: ExternalIdentityRepository,
    private val memberRepository: MemberRepository,
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${auth.kakao.oidc.nonce-replay-ttl-seconds:600}")
    nonceReplayTtlSeconds: Long,
    @Value("\${auth.kakao.oidc.allowed-clock-skew-seconds:60}")
    allowedClockSkewSeconds: Long
) {
    private val maxNonceReplayTtl = Duration.ofSeconds(nonceReplayTtlSeconds)
    private val allowedClockSkew = Duration.ofSeconds(allowedClockSkewSeconds)

    fun login(command: AuthCommand.KakaoLogin): AuthResult.TokenPair {
        val claims = kakaoOidcTokenVerifier.verify(command.idToken, command.nonce)
        reserveNonce(claims)

        val member = externalIdentityRepository
            .findByProviderAndProviderSubject(AuthProvider.KAKAO, claims.subject)
            ?.member
            ?: linkOrCreateMember(claims)

        return issueAndStoreTokens(member)
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

    private fun linkOrCreateMember(claims: KakaoOidcClaims): Member {
        val email = claims.email
            ?.takeIf { it.isNotBlank() && claims.emailVerified }
            ?: throw BusinessException(ErrorCode.KAKAO_VERIFIED_EMAIL_REQUIRED)

        val member = memberRepository.findByEmail(email)
            ?: memberRepository.save(
                Member(
                    email = email,
                    passwordHash = null
                )
            )

        externalIdentityRepository.save(
            ExternalIdentity(
                member = member,
                provider = AuthProvider.KAKAO,
                providerSubject = claims.subject,
                emailAtLinkTime = email
            )
        )

        return member
    }

    private fun issueAndStoreTokens(member: Member): AuthResult.TokenPair {
        val memberId = requirePersistedMemberId(member)
        val tokenPair = tokenProvider.generateToken(memberId, member.role)
        refreshTokenRepository.save(
            memberId,
            tokenPair.refreshToken,
            tokenProvider.getRefreshTokenValiditySeconds()
        )
        return tokenPair
    }

    private fun requirePersistedMemberId(member: Member): UUID {
        return member.id ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
    }
}
