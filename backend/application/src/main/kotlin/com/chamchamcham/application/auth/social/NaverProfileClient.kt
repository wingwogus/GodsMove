package com.chamchamcham.application.auth.social

interface NaverProfileClient {
    fun fetch(accessToken: String): NaverProfile
}
