package com.quietchatter.gateway

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

class JwtTokenServiceTest {

    private lateinit var jwtTokenService: JwtTokenService
    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var valueOps: ReactiveValueOperations<String, String>

    @BeforeEach
    fun setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate::class.java)
        // Spring Data Redis's ReactiveValueOperations needs to be mocked
        valueOps = mock(ReactiveValueOperations::class.java) as ReactiveValueOperations<String, String>
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)

        // Generate a valid 256-bit (32 bytes) secure key for testing
        val testSecretKey = "a-very-secure-and-long-test-secret-key-that-is-at-least-32-chars-long"
        jwtTokenService = JwtTokenService(testSecretKey, redisTemplate)
    }

    @Test
    fun `Access Token 생성 및 검증 테스트`() {
        val testMemberId = UUID.randomUUID().toString()

        // 발급
        val token = jwtTokenService.createNewAccessToken(testMemberId)
        assertNotNull(token)

        // 검증
        val parsedMemberId = jwtTokenService.validateAndGetMemberId(token)
        assertEquals(testMemberId, parsedMemberId)
    }

    @Test
    fun `Refresh Token 생성 및 발급 테스트`() {
        val testMemberId = UUID.randomUUID().toString()
        
        // Mock Redis set
        `when`(valueOps.set(anyString(), eq(testMemberId), any(Duration::class.java)))
            .thenReturn(Mono.just(true))

        // 발급
        val tokenMono = jwtTokenService.createAndSaveRefreshToken(testMemberId)
        val token = tokenMono.block()
        assertNotNull(token)

        // UUID가 ID에 파싱되는지 검증
        val parsedTokenId = jwtTokenService.parseRefreshTokenAndGetTokenId(token!!)
        assertNotNull(parsedTokenId)

        // Redis 저장 동작이 불렸는지 검증
        verify(valueOps).set(eq("refresh_token:${parsedTokenId}"), eq(testMemberId), eq(jwtTokenService.refreshTokenLifeTime))
    }

    @Test
    fun `유효하지 않은 토큰 파싱시 예외처리 테스트`() {
        assertThrows(InvalidAuthTokenException::class.java) {
            jwtTokenService.validateAndGetMemberId("invalid-token")
        }
    }
}
