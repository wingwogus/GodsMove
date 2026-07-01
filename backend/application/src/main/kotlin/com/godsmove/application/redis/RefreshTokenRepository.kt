package com.godsmove.application.redis

import java.util.UUID

interface RefreshTokenRepository {
    fun save(memberId: UUID, refreshToken: String, expiresInSeconds: Long)

    fun get(memberId: UUID): String?

    fun delete(memberId: UUID)
}
