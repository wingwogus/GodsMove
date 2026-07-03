package com.chamchamcham.api.auth.controller

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.api.auth.dto.AuthRequests
import com.chamchamcham.api.auth.dto.AuthResponses
import com.chamchamcham.application.auth.common.AuthCommand
import com.chamchamcham.application.auth.common.OnboardingService
import com.chamchamcham.application.auth.local.AuthService
import com.chamchamcham.application.auth.social.AppleLoginService
import com.chamchamcham.application.auth.social.KakaoLoginService
import com.chamchamcham.application.auth.social.NaverLoginService
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import com.chamchamcham.application.security.TokenProvider
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val kakaoLoginService: KakaoLoginService,
    private val appleLoginService: AppleLoginService,
    private val naverLoginService: NaverLoginService,
    private val onboardingService: OnboardingService,
    private val tokenProvider: TokenProvider,
    @Value("\${app.auth.refresh-cookie-secure:true}")
    private val refreshCookieSecure: Boolean
) {

    @PostMapping("/email/send-code")
    fun sendVerificationCode(
        @Valid @RequestBody request: AuthRequests.SendVerificationCodeRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.sendVerificationCode(AuthCommand.SendVerificationCode(request.email))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/email/verify-code")
    fun verifyEmailCode(
        @Valid @RequestBody request: AuthRequests.VerifyEmailCodeRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.verifyEmailCode(AuthCommand.VerifyEmailCode(request.email, request.code))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/signup")
    fun signUp(
        @Valid @RequestBody request: AuthRequests.SignUpRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.signUp(AuthCommand.SignUp(request.email, request.password))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.empty(Unit))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AuthRequests.LoginRequest
    ): ResponseEntity<ApiResponse<AuthResponses.TokenResponse>> {
        val result = authService.login(AuthCommand.Login(request.email, request.password))
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.TokenResponse.from(result)))
    }

    @PostMapping("/kakao/login")
    fun kakaoLogin(
        @Valid @RequestBody request: AuthRequests.KakaoLoginRequest
    ): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
        val result = kakaoLoginService.login(
            AuthCommand.KakaoLogin(
                idToken = request.idToken,
                nonce = request.nonce,
                kakaoAccessToken = request.kakaoAccessToken
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
    }

    @PostMapping("/apple/login")
    fun appleLogin(
        @Valid @RequestBody request: AuthRequests.AppleLoginRequest
    ): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
        val result = appleLoginService.login(
            AuthCommand.AppleLogin(
                identityToken = request.identityToken,
                nonce = request.nonce,
                authorizationCode = request.authorizationCode,
                userIdentifier = request.userIdentifier
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
    }

    @PostMapping("/naver/login")
    fun naverLogin(
        @Valid @RequestBody request: AuthRequests.NaverLoginRequest
    ): ResponseEntity<ApiResponse<AuthResponses.LoginResponse>> {
        val result = naverLoginService.login(AuthCommand.NaverLogin(request.accessToken))
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.LoginResponse.from(result)))
    }

    @PostMapping("/onboarding/complete")
    fun completeOnboarding(
        @AuthenticationPrincipal memberId: String?,
        @Valid @RequestBody request: AuthRequests.CompleteOnboardingRequest
    ): ResponseEntity<ApiResponse<AuthResponses.OnboardingCompleteResponse>> {
        val result = onboardingService.complete(
            AuthCommand.CompleteOnboarding(
                memberId = parseMemberId(memberId),
                name = request.name,
                phone = request.phone,
                birthDate = requireNotNull(request.birthDate),
                nickname = request.nickname,
                experienceLevel = requireNotNull(request.experienceLevel),
                managementType = requireNotNull(request.managementType),
                farmName = request.farmName,
                farmAddress = request.farmAddress,
                cropIds = request.cropIds
            )
        )

        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.OnboardingCompleteResponse.from(result)))
    }

    @PostMapping("/reissue")
    fun reissue(
        @RequestBody(required = false) request: AuthRequests.ReissueRequest?,
        @CookieValue(name = "refreshToken", required = false) refreshTokenCookie: String?
    ): ResponseEntity<ApiResponse<AuthResponses.TokenResponse>> {
        val refreshToken = request?.refreshToken ?: refreshTokenCookie
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val result = authService.reissue(AuthCommand.Reissue(refreshToken))
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken).toString())
            .body(ApiResponse.ok(AuthResponses.TokenResponse.from(result)))
    }

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal memberId: String
    ): ResponseEntity<ApiResponse<Unit>> {
        authService.logout(memberId)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie().toString())
            .body(ApiResponse.empty(Unit))
    }

    private fun refreshTokenCookie(refreshToken: String): ResponseCookie {
        return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(tokenProvider.getRefreshTokenValiditySeconds())
            .build()
    }

    private fun parseMemberId(memberId: String?): UUID {
        if (memberId.isNullOrBlank()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        return try {
            UUID.fromString(memberId)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
    }

    private fun clearRefreshTokenCookie(): ResponseCookie {
        return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(0)
            .build()
    }
}
