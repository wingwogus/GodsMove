package com.godsmove.application.security

import com.godsmove.application.testsupport.signedTestToken
import com.godsmove.application.testsupport.testTokenProvider
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TokenProviderTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000009")

    private val tokenProvider = testTokenProvider()

    @Test
    fun `generateToken embeds member id and role in access token`() {
        val tokenPair = tokenProvider.generateToken(memberId, "ROLE_USER")

        assertTrue(tokenProvider.validateToken(tokenPair.accessToken))
        assertTrue(tokenProvider.isAccessToken(tokenPair.accessToken))
        assertEquals(memberId, tokenProvider.getMemberId(tokenPair.accessToken))
        assertEquals("ROLE_USER", tokenProvider.getRole(tokenPair.accessToken))
    }

    @Test
    fun `refresh token is bound to the member id`() {
        val refreshToken = tokenProvider.createRefreshToken(adminMemberId)
        val authentication = tokenProvider.getAuthentication(
            tokenProvider.createAccessToken(adminMemberId, "ROLE_ADMIN")
        )

        assertTrue(tokenProvider.validateToken(refreshToken))
        assertTrue(tokenProvider.isRefreshToken(refreshToken))
        assertEquals(adminMemberId, tokenProvider.getMemberId(refreshToken))
        assertEquals(adminMemberId.toString(), authentication.principal)
        assertEquals("ROLE_ADMIN", authentication.authorities.first().authority)
    }

    @Test
    fun `validateToken rejects signed access token with non uuid subject`() {
        val token = signedTestToken(subject = "42", tokenType = "access", role = "ROLE_USER")

        assertFalse(tokenProvider.validateToken(token))
        assertThrows(JwtException::class.java) {
            tokenProvider.getMemberId(token)
        }
    }

    @Test
    fun `validateToken rejects signed refresh token with non uuid subject`() {
        val token = signedTestToken(subject = "42", tokenType = "refresh")

        assertFalse(tokenProvider.validateToken(token))
        assertThrows(JwtException::class.java) {
            tokenProvider.getMemberId(token)
        }
    }

}
