package com.example.application.auth

interface VerificationCodeGenerator {
    fun generate(): String
}
