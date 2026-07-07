package com.chamchamcham.application.policy.source

fun interface NongupEzHttpTransport {
    fun post(path: String, form: Map<String, String>): String
}
