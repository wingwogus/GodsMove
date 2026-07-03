package com.chamchamcham.api.testsupport

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.util.Base64
import java.util.Date

const val TEST_JWT_SECRET =
    "t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ=="

fun signedTestToken(subject: String, tokenType: String, role: String? = null): String {
    val now = Date()
    val builder = Jwts.builder()
        .setSubject(subject)
        .claim("tokenType", tokenType)
        .setIssuedAt(now)
        .setExpiration(Date(now.time + 60_000L))

    if (role != null) {
        builder.claim("role", role)
    }

    return builder
        .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_JWT_SECRET)), SignatureAlgorithm.HS512)
        .compact()
}
