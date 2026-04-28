package com.quietchatter.gateway.adaptor.`in`.web

import com.quietchatter.gateway.application.OpenApiAggregatorService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OpenApiController(
    private val openApiAggregatorService: OpenApiAggregatorService
) {

    @GetMapping("/api/docs/openapi.yaml", produces = ["application/x-yaml"])
    fun getAggregatedSpec(): String {
        return openApiAggregatorService.aggregate()
    }
}
