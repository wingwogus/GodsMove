package com.chamchamcham.application.redis

import java.time.Duration

interface AppleNonceReplayRepository {
    fun reserve(nonceHash: String, ttl: Duration): Boolean
}
