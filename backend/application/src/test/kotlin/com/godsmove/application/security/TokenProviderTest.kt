package com.godsmove.application.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Date
import java.util.UUID

class TokenProviderTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000009")
    private val secret = "t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ=="

    private val tokenProvider = TokenProvider(secret)

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
        val token = signedToken(subject = "42", tokenType = "access", role = "ROLE_USER")

        assertFalse(tokenProvider.validateToken(token))
        assertThrows(JwtException::class.java) {
            tokenProvider.getMemberId(token)
        }
    }

    @Test
    fun `validateToken rejects signed refresh token with non uuid subject`() {
        val token = signedToken(subject = "42", tokenType = "refresh")

        assertFalse(tokenProvider.validateToken(token))
        assertThrows(JwtException::class.java) {
            tokenProvider.getMemberId(token)
        }
    }

    private fun signedToken(subject: String, tokenType: String, role: String? = null): String {
        val now = Date()
        val builder = Jwts.builder()
            .setSubject(subject)
            .claim("tokenType", tokenType)
            .setIssuedAt(now)
            .setExpiration(Date(now.time + 60_000L))

        if (role != null) {
            builder.claim("role", role)
        }

        return builder
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), SignatureAlgorithm.HS512)
            .compact()
    }
}
