package com.quietchatter.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class AuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper
) : GlobalFilter, Ordered {

    private val excludedPaths = listOf(
        "/api/v1/users/login",
        "/api/v1/users/signup",
        "/api/v1/users/reactivate",
        "/actuator/health",
        "/static/docs"
    )

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        
        // 인증 제외 경로 체크
        if (excludedPaths.any { path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header")
        }

        val token = authHeader.substring(7)
        val userId = jwtTokenService.validateAndGetUserId(token) 
            ?: return unauthorizedResponse(exchange, "Invalid or expired JWT token")

        // 헤더 삽입 (내부 서비스 식별용)
        val mutatedRequest = exchange.request.mutate()
            .header("X-User-Id", userId)
            .build()

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
    }

    private fun unauthorizedResponse(exchange: ServerWebExchange, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorBody = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "status" to HttpStatus.UNAUTHORIZED.value(),
            "error" to "Unauthorized",
            "message" to message,
            "path" to exchange.request.path.value()
        )
        
        val buffer: DataBuffer = response.bufferFactory().wrap(
            objectMapper.writeValueAsString(errorBody).toByteArray(StandardCharsets.UTF_8)
        )

        return response.writeWith(Mono.just(buffer))
    }

    override fun getOrder(): Int = -1
}
