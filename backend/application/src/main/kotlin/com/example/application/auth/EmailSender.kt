package com.example.application.auth

interface EmailSender {
    fun sendVerificationCode(email: String, code: String)
}
