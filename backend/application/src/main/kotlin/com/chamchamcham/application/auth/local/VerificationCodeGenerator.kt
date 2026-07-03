package com.chamchamcham.application.auth.local

interface VerificationCodeGenerator {
    fun generate(): String
}
