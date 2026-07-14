package com.chamchamcham.application.pesticide.sync

fun interface PsisPesticideHttpTransport {
    fun get(queryParams: Map<String, String>): String
}
