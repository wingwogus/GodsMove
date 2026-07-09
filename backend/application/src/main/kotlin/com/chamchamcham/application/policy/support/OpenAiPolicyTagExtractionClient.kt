package com.chamchamcham.application.policy.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OpenAiPolicyTagExtractionClient(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val apiKey: String,
    private val model: String = "gpt-5.5",
    private val endpoint: URI = URI.create("https://api.openai.com/v1/responses")
) : PolicyTagExtractionClient {
    override fun extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult {
        if (apiKey.isBlank()) {
            return PolicyTagExtractionClientResult.Failure
        }
        return runCatching {
            val httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(request))))
                .build()
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                PolicyTagExtractionClientResult.Failure
            } else {
                val outputText = extractOutputText(objectMapper.readTree(response.body()))
                if (outputText == null) {
                    PolicyTagExtractionClientResult.Failure
                } else {
                    val tags = objectMapper.readTree(outputText)
                    val confidence = tags.path("confidence").takeIf { it.isNumber }?.asDouble()
                    if (confidence == null) {
                        PolicyTagExtractionClientResult.Failure
                    } else {
                        PolicyTagExtractionClientResult.Success(
                            targetTags = tags.stringSet("targetTags"),
                            cropTags = tags.stringSet("cropTags"),
                            regionTags = tags.stringSet("regionTags"),
                            confidence = confidence
                        )
                    }
                }
            }
        }.getOrElse {
            PolicyTagExtractionClientResult.Failure
        }
    }

    private fun requestBody(request: PolicyTagExtractionRequest): Map<String, Any> =
        mapOf(
            "model" to model,
            "input" to """
                Extract deterministic recommendation tags from this NongupEZ agricultural policy.
                Use only the enum values allowed by the JSON schema.
                Do not infer member-specific eligibility.

                title: ${request.title}
                summary: ${request.summary.orEmpty()}
                eligibility: ${request.eligibility.orEmpty()}
                benefit: ${request.benefit.orEmpty()}
                agencyName: ${request.agencyName}
            """.trimIndent(),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to "policy_tags",
                    "strict" to true,
                    "schema" to schema
                )
            )
        )

    private fun extractOutputText(root: JsonNode): String? {
        root.path("output_text").takeIf { it.isTextual }?.asText()?.let { return it }
        root.path("output")
            .filter { it.isObject }
            .flatMap { output -> output.path("content").filter { it.isObject } }
            .firstOrNull { content -> content.path("text").isTextual }
            ?.path("text")
            ?.asText()
            ?.let { return it }
        return null
    }

    private fun JsonNode.stringSet(fieldName: String): Set<String> =
        path(fieldName)
            .takeIf { it.isArray }
            ?.mapNotNull { node -> node.takeIf { it.isTextual }?.asText() }
            ?.toSet()
            ?: emptySet()

    private companion object {
        val targetTags = listOf(
            "YOUNG_FARMER",
            "REGISTERED_FARMER",
            "AGRICULTURAL_CORPORATION",
            "RETURNING_FARMER"
        )
        val cropTags = listOf("MEDICINAL_CROP", "SPECIAL_CROP")
        val regionTags = listOf(
            "전국",
            "서울특별시",
            "부산광역시",
            "대구광역시",
            "인천광역시",
            "광주광역시",
            "대전광역시",
            "울산광역시",
            "세종특별자치시",
            "경기도",
            "강원특별자치도",
            "충청북도",
            "충청남도",
            "전북특별자치도",
            "전라남도",
            "경상북도",
            "경상남도",
            "제주특별자치도"
        )
        val schema: Map<String, Any> = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("targetTags", "cropTags", "regionTags", "confidence"),
            "properties" to mapOf(
                "targetTags" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "enum" to targetTags)
                ),
                "cropTags" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "enum" to cropTags)
                ),
                "regionTags" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "string", "enum" to regionTags)
                ),
                "confidence" to mapOf(
                    "type" to "number",
                    "minimum" to 0,
                    "maximum" to 1
                )
            )
        )
    }
}
