package com.chamchamcham.application.coaching.common

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val embedding: Embedding = Embedding(),
    val chat: Chat = Chat(),
    val retrieval: Retrieval = Retrieval(),
    val timeoutMillis: Long = 30_000
) {
    data class Embedding(
        val model: String = "bge-m3",
        val dimension: Int = 1024
    )

    data class Chat(
        val model: String = "openclaw/agri-rag-coach"
    )

    data class Retrieval(
        val topKDefault: Int = 6,
        val topKMax: Int = 20,
        val lowSimilarityThreshold: Double = 0.55
    )
}
