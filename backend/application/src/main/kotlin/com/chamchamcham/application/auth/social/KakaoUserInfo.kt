package com.chamchamcham.application.auth.social

import java.time.LocalDate

interface KakaoUserInfoClient {
    fun fetch(accessToken: String): KakaoUserInfo
}

data class KakaoUserInfo(
    val subject: String,
    val email: String?,
    val name: String?,
    val phone: String?,
    val birthDate: LocalDate?
)
