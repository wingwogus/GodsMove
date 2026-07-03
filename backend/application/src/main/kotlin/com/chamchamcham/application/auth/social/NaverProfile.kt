package com.chamchamcham.application.auth.social

import java.time.LocalDate

data class NaverProfile(
    val subject: String,
    val email: String?,
    val name: String?,
    val phone: String?,
    val birthDate: LocalDate?
)
