package com.quietchatter.gateway.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class OpenApiAggregatorService(
    @Value("\${MEMBER_SERVICE_URL:http://localhost:8083}") private val memberServiceUrl: String,
    @Value("\${BOOK_SERVICE_URL:http://localhost:8081}") private val bookServiceUrl: String,
    @Value("\${TALK_SERVICE_URL:http://localhost:8084}") private val talkServiceUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val restClient = RestClient.create()

    fun aggregate(): String {
        val services = listOf(
            ServiceSpec("Member", memberServiceUrl),
            ServiceSpec("Book", bookServiceUrl),
            ServiceSpec("Talk", talkServiceUrl)
        )

        val aggregated = mutableMapOf<String, Any>(
            "openapi" to "3.0.1",
            "info" to mapOf(
                "title" to "QuietChatter API Documentation",
                "version" to "1.0.0",
                "description" to "Integrated API documentation for QuietChatter microservices"
            ),
            "paths" to mutableMapOf<String, Any>(),
            "components" to mutableMapOf(
                "schemas" to mutableMapOf<String, Any>()
            )
        )

        services.forEach { service ->
            try {
                val specYaml = restClient.get()
                    .uri("${service.url}/api/spec")
                    .retrieve()
                    .body(String::class.java)

                if (specYaml != null) {
                    @Suppress("UNCHECKED_CAST")
                    val spec = yamlMapper.readValue(specYaml, Map::class.java) as Map<String, Any>
                    mergeSpec(aggregated, spec, service.name)
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch spec from {} at {}: {}", service.name, service.url, e.message)
            }
        }

        return yamlMapper.writeValueAsString(aggregated)
    }

    private fun mergeSpec(aggregated: MutableMap<String, Any>, spec: Map<String, Any>, serviceName: String) {
        // Merge paths
        val paths = spec["paths"] as? Map<*, *> ?: emptyMap<Any, Any>()
        @Suppress("UNCHECKED_CAST")
        val aggregatedPaths = aggregated["paths"] as MutableMap<String, Any>
        
        paths.forEach { (path, pathItem) ->
            if (path is String && pathItem != null) {
                aggregatedPaths[path] = updateRefs(pathItem, serviceName)
            }
        }

        // Merge schemas
        val components = spec["components"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val schemas = components["schemas"] as? Map<*, *> ?: emptyMap<Any, Any>()
        @Suppress("UNCHECKED_CAST")
        val componentsMap = aggregated["components"] as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        val aggregatedSchemas = componentsMap["schemas"] as MutableMap<String, Any>

        schemas.forEach { (schemaName, schema) ->
            if (schemaName is String && schema != null) {
                val prefixedName = "${serviceName}_$schemaName"
                aggregatedSchemas[prefixedName] = updateRefs(schema, serviceName)
            }
        }
    }

    private fun updateRefs(node: Any, serviceName: String): Any {
        return when (node) {
            is Map<*, *> -> {
                val updatedMap = mutableMapOf<String, Any>()
                node.forEach { (key, value) ->
                    if (key is String && value != null) {
                        if (key == "\$ref" && value is String && value.startsWith("#/components/schemas/")) {
                            val originalSchema = value.substringAfter("#/components/schemas/")
                            updatedMap[key] = "#/components/schemas/${serviceName}_$originalSchema"
                        } else {
                            updatedMap[key] = updateRefs(value, serviceName)
                        }
                    }
                }
                updatedMap
            }
            is List<*> -> {
                node.filterNotNull().map { updateRefs(it, serviceName) }
            }
            else -> node
        }
    }

    private data class ServiceSpec(val name: String, val url: String)
}
