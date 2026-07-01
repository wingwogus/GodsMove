package com.godsmove.application.auth

interface VerificationCodeGenerator {
    fun generate(): String
}
