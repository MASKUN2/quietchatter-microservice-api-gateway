package com.quietchatter.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

@Component
class AuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathMatcher = AntPathMatcher()

    private val bypassPaths = listOf("/api/auth/login", "/api/auth/signup", "/api/auth/reactivate", "/api/support", "/actuator/health")
    private val optionalPaths = listOf("/api/books", "/api/talks", "/api/auth/me", "/api/members/me")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val wrappedRequest = GatewayHeaderRequestWrapper(request)

        // 0. 내부 경로(/internal) 외부 접근 차단
        if (path.startsWith("/internal")) {
            errorResponse(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "내부 경로로의 접근이 금지되었습니다.")
            return
        }

        // 1. 인증 불필요 경로 확인
        if (bypassPaths.any { pathMatcher.match("$it/**", path) || pathMatcher.match(it, path) }) {
            filterChain.doFilter(wrappedRequest, response)
            return
        }

        // 2. 토큰 추출
        val accessToken = extractAccessToken(request)

        if (accessToken == null) {
            handleNoToken(path, wrappedRequest, response, filterChain)
            return
        }

        // 3. Access Token 검증 및 처리
        try {
            val memberId = jwtTokenService.validateAndGetMemberId(accessToken)
            wrappedRequest.setMemberIdHeader(memberId)
            filterChain.doFilter(wrappedRequest, response)
        } catch (e: ExpiredAuthTokenException) {
            handleRefreshToken(wrappedRequest, response, filterChain)
        } catch (e: InvalidAuthTokenException) {
            errorResponse(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 토큰입니다.")
        }
    }

    private fun handleNoToken(
        path: String,
        request: GatewayHeaderRequestWrapper,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (optionalPaths.any { pathMatcher.match("$it/**", path) || pathMatcher.match(it, path) }) {
            filterChain.doFilter(request, response)
            return
        }
        errorResponse(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.")
    }

    private fun handleRefreshToken(
        request: GatewayHeaderRequestWrapper,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val refreshToken = extractRefreshToken(request)
        if (refreshToken == null) {
            errorResponse(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "접근 권한이 만료되었습니다.")
            return
        }

        try {
            val tokenId = jwtTokenService.parseRefreshTokenAndGetTokenId(refreshToken)
            val memberId = jwtTokenService.findMemberIdByRefreshTokenId(tokenId)

            if (memberId != null) {
                // 토큰 갱신
                val newAccessToken = jwtTokenService.createNewAccessToken(memberId)
                jwtTokenService.deleteRefreshToken(tokenId)
                val newRefreshToken = jwtTokenService.createAndSaveRefreshToken(memberId)

                // 쿠키 갱신 및 요청 처리
                addTokenCookies(request, response, newAccessToken, newRefreshToken)
                request.setMemberIdHeader(memberId)
                filterChain.doFilter(request, response)
            } else {
                errorResponse(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "인증 정보가 만료되었습니다.")
            }
        } catch (e: Exception) {
            log.error("Refresh token validation failed: {}", e.message)
            errorResponse(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "유효하지 않은 갱신 토큰입니다.")
        }
    }

    private fun addTokenCookies(request: HttpServletRequest, response: HttpServletResponse, accessToken: String, refreshToken: String) {
        val isSecure = request.isSecure

        // Jakarta Servlet Cookie는 SameSite를 직접 지원하지 않으므로 헤더를 직접 설정하거나 
        // 응답 래퍼를 사용해야 하지만, 여기서는 가장 안정적인 Set-Cookie 헤더 직접 추가 방식을 사용합니다.
        
        val accessCookieHeader = StringBuilder("ACCESS_TOKEN=$accessToken; Path=/; HttpOnly; Max-Age=${jwtTokenService.accessTokenLifeTime.seconds}")
        if (isSecure) accessCookieHeader.append("; Secure")
        accessCookieHeader.append("; SameSite=Lax")
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookieHeader.toString())

        val refreshCookieHeader = StringBuilder("REFRESH_TOKEN=$refreshToken; Path=/; HttpOnly; Max-Age=${jwtTokenService.refreshTokenLifeTime.seconds}")
        if (isSecure) refreshCookieHeader.append("; Secure")
        refreshCookieHeader.append("; SameSite=Lax")
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieHeader.toString())
    }

    private fun extractAccessToken(request: HttpServletRequest): String? {
        val cookie = request.cookies?.find { it.name == "ACCESS_TOKEN" }?.value
        if (cookie != null) return cookie

        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }
        return null
    }

    private fun extractRefreshToken(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == "REFRESH_TOKEN" }?.value
    }

    private fun errorResponse(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val problemDetail = org.springframework.http.ProblemDetail.forStatusAndDetail(status, message).apply {
            title = code
            instance = URI.create(ServletUriComponentsBuilder.fromCurrentRequest().toUriString())
        }
        response.writer.write(objectMapper.writeValueAsString(problemDetail))
    }
}
