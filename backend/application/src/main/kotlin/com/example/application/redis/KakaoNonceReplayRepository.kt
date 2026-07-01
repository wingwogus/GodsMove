package com.example.application.redis

import java.time.Duration

interface KakaoNonceReplayRepository {
    fun reserve(nonce: String, ttl: Duration): Boolean
}
