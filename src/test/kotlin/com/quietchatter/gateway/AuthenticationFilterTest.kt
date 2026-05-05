package com.quietchatter.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration

class AuthenticationFilterTest {

    private val jwtTokenService = mock(JwtTokenService::class.java)
    private val objectMapper = ObjectMapper()
    private val filter = AuthenticationFilter(jwtTokenService, objectMapper)
    private val request = mock(HttpServletRequest::class.java)
    private val response = mock(HttpServletResponse::class.java)
    private val filterChain = mock(FilterChain::class.java)

    @BeforeEach
    fun setUp() {
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)
        `when`(jwtTokenService.accessTokenLifeTime).thenReturn(Duration.ofMinutes(30))
        `when`(jwtTokenService.refreshTokenLifeTime).thenReturn(Duration.ofDays(30))
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `request without access token but with valid refresh token should attempt to refresh`() {
        // given
        `when`(request.requestURI).thenReturn("/api/members/me")
        val refreshCookie = Cookie("REFRESH_TOKEN", "valid-refresh-token")
        `when`(request.cookies).thenReturn(arrayOf(refreshCookie))
        `when`(request.getHeader(anyString())).thenReturn(null)

        val tokenId = "some-token-id"
        val memberId = "member-123"
        `when`(jwtTokenService.parseRefreshTokenAndGetTokenId("valid-refresh-token")).thenReturn(tokenId)
        `when`(jwtTokenService.findMemberIdByRefreshTokenId(tokenId)).thenReturn(memberId)
        `when`(jwtTokenService.createNewAccessToken(memberId)).thenReturn("new-access-token")
        `when`(jwtTokenService.createAndSaveRefreshToken(memberId)).thenReturn("new-refresh-token")

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(jwtTokenService).parseRefreshTokenAndGetTokenId("valid-refresh-token")
        verify(jwtTokenService).findMemberIdByRefreshTokenId(tokenId)
        verify(filterChain).doFilter(any(GatewayHeaderRequestWrapper::class.java), eq(response))
    }

    @Test
    fun `request without token should pass through without X-Member-Id header`() {
        // given
        `when`(request.requestURI).thenReturn("/api/auth/logout")
        `when`(request.cookies).thenReturn(null)
        `when`(request.getHeader(anyString())).thenReturn(null)

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(filterChain).doFilter(any(), eq(response))
        verify(response, never()).status = HttpStatus.UNAUTHORIZED.value()
    }

    @Test
    fun `internal path should return forbidden`() {
        // given
        `when`(request.requestURI).thenReturn("/internal/api/members/some-id")
        val writer = mock(java.io.PrintWriter::class.java)
        `when`(response.writer).thenReturn(writer)

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(response).status = HttpStatus.FORBIDDEN.value()
        verify(filterChain, never()).doFilter(any(), any())
    }
}
