package com.quietchatter.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

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
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `health check path should bypass authentication`() {
        // given
        `when`(request.requestURI).thenReturn("/actuator/health")

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(filterChain).doFilter(any(), eq(response))
        verify(response, never()).status = HttpStatus.UNAUTHORIZED.value()
    }

    @Test
    fun `auth login path should bypass authentication`() {
        // given
        `when`(request.requestURI).thenReturn("/api/auth/login")

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(filterChain).doFilter(any(), eq(response))
        verify(response, never()).status = HttpStatus.UNAUTHORIZED.value()
    }

    @Test
    fun `auth signup path with subpath should bypass authentication`() {
        // given
        `when`(request.requestURI).thenReturn("/api/auth/login/naver")

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(filterChain).doFilter(any(), eq(response))
    }

    @Test
    fun `protected path without token should return unauthorized`() {
        // given
        `when`(request.requestURI).thenReturn("/api/auth/logout")
        `when`(request.cookies).thenReturn(null)
        `when`(request.getHeader(anyString())).thenReturn(null)
        
        val writer = mock(java.io.PrintWriter::class.java)
        `when`(response.writer).thenReturn(writer)

        // when
        filter.doFilter(request, response, filterChain)

        // then
        verify(response).status = HttpStatus.UNAUTHORIZED.value()
        verify(filterChain, never()).doFilter(any(), any())
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
