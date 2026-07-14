package com.chamchamcham.api.voice

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openai.realtime")
data class OpenAiRealtimeProperties(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val voice: String,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
)
