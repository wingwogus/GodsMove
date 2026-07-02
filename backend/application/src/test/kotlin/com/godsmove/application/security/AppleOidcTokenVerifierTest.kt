package com.godsmove.application.security

import com.godsmove.application.exception.ErrorCode
import com.godsmove.application.exception.business.BusinessException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.Date

class AppleOidcTokenVerifierTest {

    private val keyPair = rsaKeyPair()
    private val verifier = verifierFor(keyPair)

    @Test
    fun `verify accepts valid apple oidc token`() {
        val claims = verifier.verify(token(), RAW_NONCE)

        assertEquals("apple-sub", claims.subject)
        assertEquals("user@example.com", claims.email)
        assertEquals(true, claims.emailVerified)
        assertEquals(HASHED_NONCE, claims.nonce)
    }

    @Test
    fun `verify accepts string email verified claim`() {
        val claims = verifier.verify(token(emailVerified = "true"), RAW_NONCE)

        assertEquals(true, claims.emailVerified)
    }

    @Test
    fun `verify rejects malformed jwt`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify("not-a-jwt", RAW_NONCE)
        }

        assertEquals(ErrorCode.MALFORMED_JWT, exception.errorCode)
    }

    @Test
    fun `verify rejects nonce mismatch`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(nonce = "wrong-nonce"), RAW_NONCE)
        }

        assertEquals(ErrorCode.APPLE_NONCE_MISMATCH, exception.errorCode)
    }

    @Test
    fun `verify rejects wrong audience`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(audience = "wrong-audience"), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects wrong issuer`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(issuer = "https://invalid.example.com"), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects expired token`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(expiresAt = Instant.now().minusSeconds(60)), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects future issued at`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(issuedAt = Instant.now().plusSeconds(60)), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects missing subject`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(subject = null), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects blank subject`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(subject = ""), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify rejects missing nonce`() {
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(nonce = null), RAW_NONCE)
        }

        assertEquals(ErrorCode.APPLE_NONCE_MISMATCH, exception.errorCode)
    }

    @Test
    fun `verify rejects bad signature`() {
        val otherKeyPair = rsaKeyPair()
        val exception = assertThrows(BusinessException::class.java) {
            verifier.verify(token(signingKeyPair = otherKeyPair), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    @Test
    fun `verify maps decoder invalid token to invalid apple token`() {
        val invalidVerifier = AppleOidcTokenVerifier(
            issuer = ISSUER,
            audience = AUDIENCE,
            allowedClockSkew = Duration.ofSeconds(30),
            jwtDecoder = JwtDecoder { throw JwtException("invalid") }
        )

        val exception = assertThrows(BusinessException::class.java) {
            invalidVerifier.verify(token(), RAW_NONCE)
        }

        assertEquals(ErrorCode.INVALID_APPLE_TOKEN, exception.errorCode)
    }

    private fun verifierFor(keyPair: KeyPair): AppleOidcTokenVerifier {
        val decoder = NimbusJwtDecoder
            .withPublicKey(keyPair.public as RSAPublicKey)
            .build()
        decoder.setJwtValidator { OAuth2TokenValidatorResult.success() }

        return AppleOidcTokenVerifier(
            issuer = ISSUER,
            audience = AUDIENCE,
            allowedClockSkew = Duration.ofSeconds(30),
            jwtDecoder = decoder
        )
    }

    private fun token(
        signingKeyPair: KeyPair = keyPair,
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
        subject: String? = "apple-sub",
        email: String? = "user@example.com",
        emailVerified: Any? = true,
        nonce: String? = HASHED_NONCE,
        expiresAt: Instant = Instant.now().plusSeconds(300),
        issuedAt: Instant = Instant.now().minusSeconds(10)
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .expirationTime(Date.from(expiresAt))
            .issueTime(Date.from(issuedAt))
            .apply {
                if (subject != null) {
                    subject(subject)
                }
                if (nonce != null) {
                    claim("nonce", nonce)
                }
                if (email != null) {
                    claim("email", email)
                }
                if (emailVerified != null) {
                    claim("email_verified", emailVerified)
                }
            }
            .build()

        val signedJwt = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)
        signedJwt.sign(RSASSASigner(signingKeyPair.private as RSAPrivateKey))
        return signedJwt.serialize()
    }

    private fun rsaKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
    }

    companion object {
        private const val ISSUER = "https://appleid.apple.com"
        private const val AUDIENCE = "com.godsmove.app"
        private const val RAW_NONCE = "client-nonce"
        private val HASHED_NONCE = sha256Hex(RAW_NONCE)

        private fun sha256Hex(value: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
