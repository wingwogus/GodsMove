package com.chamchamcham.application.policy.source.nongupez

fun interface NongupEzHttpTransport {
    fun post(path: String, body: Map<String, Any?>): String
}
