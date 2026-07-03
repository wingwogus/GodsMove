package com.chamchamcham.application.auth.local

interface EmailSender {
    fun sendVerificationCode(email: String, code: String)
}
