package com.chamchamcham.application.coaching.common

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val embedding: Embedding = Embedding(),
    val chat: Chat = Chat(),
    val retrieval: Retrieval = Retrieval(),
    val execution: Execution = Execution(),
) {
    data class Embedding(
        val model: String = "bge-m3"
    )

    data class Chat(
        val model: String = "openclaw/agri-rag-coach"
    )

    data class Retrieval(
        val topKDefault: Int = 6,
        val topKMax: Int = 20,
        val lowSimilarityThreshold: Double = 0.5
    )

    data class Execution(
        val corePoolSize: Int = 2,
        val maxPoolSize: Int = 4,
        val queueCapacity: Int = 32,
        val pendingTimeoutMillis: Long = 120_000,
        val scanIntervalMillis: Long = 60_000,
    )
}
