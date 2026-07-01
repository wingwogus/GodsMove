package com.godsmove.application.auth

object AuthResult {
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )
}
