package com.quietchatter.gateway

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "app.cors")
data class AppCorsProperties(val allowedOrigins: List<String>)

@Configuration
class CorsConfig(private val appCorsProperties: AppCorsProperties) {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration()
        config.allowedOrigins = appCorsProperties.allowedOrigins
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        config.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return CorsWebFilter(source)
    }
}
