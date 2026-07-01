package com.godsmove.application.redis

import java.time.Duration

interface KakaoNonceReplayRepository {
    fun reserve(nonce: String, ttl: Duration): Boolean
}
