package com.godsmove.application.auth

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.godsmove.application.redis.EmailVerificationRepository
import com.godsmove.application.redis.RefreshTokenRepository
import com.godsmove.application.security.TokenProvider
import com.godsmove.domain.member.Member
import com.godsmove.domain.member.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val tokenProvider: TokenProvider,
    private val emailSender: EmailSender,
    private val verificationCodeGenerator: VerificationCodeGenerator,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${spring.mail.auth-code-expiration-millis}")
    private val codeTtlMillis: Long,
    @Value("\${spring.mail.verified-state-expiration-millis:\${spring.mail.auth-code-expiration-millis}}")
    private val verifiedTtlMillis: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendVerificationCode(command: AuthCommand.SendVerificationCode) {
        if (memberRepository.existsByEmail(command.email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        val code = verificationCodeGenerator.generate()
        emailSender.sendVerificationCode(command.email, code)
        emailVerificationRepository.saveCode(
            command.email,
            code,
            Duration.ofMillis(codeTtlMillis)
        )
        logger.info("Verification code sent. emailHash={}", command.email.hashCode())
    }

    fun verifyEmailCode(command: AuthCommand.VerifyEmailCode) {
        if (memberRepository.existsByEmail(command.email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        val storedCode = emailVerificationRepository.getCode(command.email)
            ?: throw BusinessException(ErrorCode.AUTH_CODE_NOT_FOUND)

        if (storedCode != command.code) {
            throw BusinessException(ErrorCode.AUTH_CODE_MISMATCH)
        }

        emailVerificationRepository.markVerified(
            command.email,
            Duration.ofMillis(verifiedTtlMillis)
        )
        emailVerificationRepository.deleteCode(command.email)
        logger.info("Email verified. emailHash={}", command.email.hashCode())
    }

    fun signUp(command: AuthCommand.SignUp) {
        if (!emailVerificationRepository.isVerified(command.email)) {
            throw BusinessException(ErrorCode.EMAIL_NOT_VERIFIED)
        }

        if (memberRepository.existsByEmail(command.email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        memberRepository.save(
            Member(
                email = command.email,
                passwordHash = passwordEncoder.encode(command.password)
            )
        )
        emailVerificationRepository.deleteCode(command.email)
        emailVerificationRepository.deleteVerified(command.email)
        logger.info("Member signed up. emailHash={}", command.email.hashCode())
    }

    fun login(command: AuthCommand.Login): AuthResult.TokenPair {
        val member = memberRepository.findByEmail(command.email)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val passwordHash = member.passwordHash
            ?: throw BusinessException(ErrorCode.SOCIAL_ONLY_MEMBER_LOCAL_LOGIN_FORBIDDEN)

        if (!passwordEncoder.matches(command.password, passwordHash)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return issueAndStoreTokens(member)
    }

    fun reissue(command: AuthCommand.Reissue): AuthResult.TokenPair {
        if (!tokenProvider.validateToken(command.refreshToken) || !tokenProvider.isRefreshToken(command.refreshToken)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val memberId = tokenProvider.getMemberId(command.refreshToken)
        val storedRefreshToken = refreshTokenRepository.get(memberId)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        if (storedRefreshToken != command.refreshToken) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val member = memberRepository.findById(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        }

        return issueAndStoreTokens(member)
    }

    fun logout(memberId: String) {
        val parsedMemberId = parseMemberId(memberId)

        if (refreshTokenRepository.get(parsedMemberId) == null) {
            throw BusinessException(ErrorCode.ALREADY_LOGGED_OUT)
        }

        refreshTokenRepository.delete(parsedMemberId)
        logger.info("Member logged out. memberHash={}", memberId.hashCode())
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

    private fun parseMemberId(memberId: String): UUID {
        return try {
            UUID.fromString(memberId)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }
}
