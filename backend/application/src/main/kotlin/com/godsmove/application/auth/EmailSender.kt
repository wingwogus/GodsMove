package com.godsmove.application.auth

interface EmailSender {
    fun sendVerificationCode(email: String, code: String)
}
