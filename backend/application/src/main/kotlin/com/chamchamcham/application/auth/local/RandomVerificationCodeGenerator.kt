package com.chamchamcham.application.auth.local

import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class RandomVerificationCodeGenerator : VerificationCodeGenerator {
    override fun generate(): String {
        return (1..6).joinToString("") {
            Random.nextInt(0, 10).toString()
        }
    }
}
