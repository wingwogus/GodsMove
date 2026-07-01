package com.godsmove.application.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.TimeUnit

@Repository
class RefreshTokenRedisRepository(
    private val redis: StringRedisTemplate
) : RefreshTokenRepository {

    companion object {
        private const val PREFIX = "refresh:"
    }

    override fun save(
        memberId: UUID,
        refreshToken: String,
        expiresInSeconds: Long
    ) {
        redis.opsForValue().set(
            PREFIX + memberId,
            refreshToken,
            expiresInSeconds,
            TimeUnit.SECONDS
        )
    }

    override fun get(memberId: UUID): String? {
        return redis.opsForValue().get(PREFIX + memberId)
    }

    override fun delete(memberId: UUID) {
        redis.delete(PREFIX + memberId)
    }
}
