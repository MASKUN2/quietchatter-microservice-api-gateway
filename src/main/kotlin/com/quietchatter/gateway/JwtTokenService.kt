package com.quietchatter.gateway

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtTokenService(
    @Value("\${jwt.secret-key}") secretKey: String
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray())
    private val jwtParser = Jwts.parser().verifyWith(key).build()

    fun validateAndGetUserId(token: String): String? {
        return try {
            val claims = jwtParser.parseSignedClaims(token).payload
            claims.subject
        } catch (e: Exception) {
            null
        }
    }
}
