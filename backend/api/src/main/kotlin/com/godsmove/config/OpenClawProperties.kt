package com.godsmove.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openclaw")
data class OpenClawProperties(
    val baseUrl: String = "http://127.0.0.1:18789",
    val apiKey: String = "",
    val agentId: String = "agri-rag-coach",
    val model: String = "openclaw/agri-rag-coach"
)
