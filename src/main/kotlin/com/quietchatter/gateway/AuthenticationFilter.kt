package com.quietchatter.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class AuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper
) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(javaClass)

    private val bypassPaths = listOf("/v1/auth/login", "/v1/auth/signup", "/v1/auth/reactivate", "/v1/customer")
    private val optionalPaths = listOf("/v1/books", "/v1/talks", "/v1/auth/me")

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()

        // 1. 외부 주입 X-Member-Id 헤더 제거 (보안)
        val cleanRequest = exchange.request.mutate()
            .headers { it.remove("X-Member-Id") }
            .build()
        val cleanExchange = exchange.mutate().request(cleanRequest).build()

        // 2. 인증 불필요 경로 확인
        if (bypassPaths.any { path.startsWith(it) }) {
            return chain.filter(cleanExchange)
        }

        // 3. 토큰 추출 (쿠키 먼저, 헤더 폴백)
        val accessToken = extractAccessToken(cleanExchange)

        if (accessToken == null) {
            // 토큰이 없는 경우
            if (optionalPaths.any { path.startsWith(it) }) {
                // 인증 선택 경로는 X-Member-Id 없이 그대로 통과
                return chain.filter(cleanExchange)
            }
            // 필수 경로인데 토큰이 없으면 401
            return errorResponse(cleanExchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.")
        }

        // 4. Access Token 검증
        return try {
            val memberId = jwtTokenService.validateAndGetMemberId(accessToken)
            forwardWithMemberId(cleanExchange, chain, memberId)
        } catch (e: ExpiredAuthTokenException) {
            // 만료 시 Refresh Token 흐름
            handleRefreshToken(cleanExchange, chain)
        } catch (e: InvalidAuthTokenException) {
            errorResponse(cleanExchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 토큰입니다.")
        }
    }

    private fun handleRefreshToken(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val refreshToken = extractRefreshToken(exchange)
            ?: return errorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "접근 권한이 만료되었습니다.")

        return try {
            val tokenId = jwtTokenService.parseRefreshTokenAndGetTokenId(refreshToken)

            jwtTokenService.findMemberIdByRefreshTokenId(tokenId)
                .flatMap { memberId ->
                    // 새 Access Token 발급
                    val newAccessToken = jwtTokenService.createNewAccessToken(memberId)
                    
                    // 새 Refresh Token 발급 및 저장 (기존 것 삭제 후)
                    jwtTokenService.deleteRefreshToken(tokenId)
                        .then(jwtTokenService.createAndSaveRefreshToken(memberId))
                        .flatMap { newRefreshToken ->
                            // 쿠키 갱신
                            addTokenCookies(exchange, newAccessToken, newRefreshToken)
                            // 요청 진행
                            forwardWithMemberId(exchange, chain, memberId)
                        }
                }
                .switchIfEmpty(
                    errorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "인증 정보가 만료되었습니다.")
                )
        } catch (e: Exception) {
            errorResponse(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "유효하지 않은 갱신 토큰입니다.")
        }
    }

    private fun forwardWithMemberId(exchange: ServerWebExchange, chain: GatewayFilterChain, memberId: String): Mono<Void> {
        val request = exchange.request.mutate()
            .header("X-Member-Id", memberId)
            .build()
        return chain.filter(exchange.mutate().request(request).build())
    }

    private fun addTokenCookies(exchange: ServerWebExchange, accessToken: String, refreshToken: String) {
        val isSecure = exchange.request.uri.scheme == "https" // 개발시 false, 운영시 true 가정
        
        val accessCookie = ResponseCookie.from("ACCESS_TOKEN", accessToken)
            .path("/")
            .httpOnly(true)
            .secure(isSecure)
            .sameSite("Strict")
            .maxAge(jwtTokenService.accessTokenLifeTime)
            .build()

        val refreshCookie = ResponseCookie.from("REFRESH_TOKEN", refreshToken)
            .path("/")
            .httpOnly(true)
            .secure(isSecure)
            .sameSite("Strict")
            .maxAge(jwtTokenService.refreshTokenLifeTime)
            .build()

        exchange.response.addCookie(accessCookie)
        exchange.response.addCookie(refreshCookie)
    }

    private fun extractAccessToken(exchange: ServerWebExchange): String? {
        val cookie = exchange.request.cookies["ACCESS_TOKEN"]?.firstOrNull()?.value
        if (cookie != null) return cookie

        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }
        return null
    }

    private fun extractRefreshToken(exchange: ServerWebExchange): String? {
        return exchange.request.cookies["REFRESH_TOKEN"]?.firstOrNull()?.value
    }

    private fun errorResponse(
        exchange: ServerWebExchange,
        status: HttpStatus,
        code: String,
        message: String
    ): Mono<Void> {
        val response = exchange.response
        if (response.isCommitted) return Mono.empty()
        
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorBody = GatewayErrorResponse(code = code, message = message)
        
        val buffer: DataBuffer = response.bufferFactory().wrap(
            objectMapper.writeValueAsString(errorBody).toByteArray(StandardCharsets.UTF_8)
        )

        return response.writeWith(Mono.just(buffer))
    }

    override fun getOrder(): Int = -1
}
