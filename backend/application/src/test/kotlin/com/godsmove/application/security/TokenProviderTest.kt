package com.godsmove.application.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TokenProviderTest {
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000007")
    private val adminMemberId = UUID.fromString("00000000-0000-0000-0000-000000000009")

    private val tokenProvider = TokenProvider(
        "t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ=="
    )

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
}
