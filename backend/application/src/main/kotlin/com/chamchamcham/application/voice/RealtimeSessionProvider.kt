package com.chamchamcham.application.voice

import java.time.LocalDateTime

interface RealtimeSessionProvider {
    fun createEphemeralSession(request: RealtimeSessionRequest): RealtimeSessionResult
}

data class RealtimeSessionRequest(
    val instructions: String,
    val tools: List<Map<String, Any?>>,
    val expiresAfterSeconds: Int,
)

data class RealtimeSessionResult(
    val clientSecret: String,
    val expiresAt: LocalDateTime,
    val model: String,
)
