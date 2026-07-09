package com.chamchamcham.application.policy.support

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient

@Configuration
class PolicyTagExtractionConfig {
    @Bean
    @ConditionalOnProperty(
        prefix = "policy.tag-extraction.openai",
        name = ["enabled"],
        havingValue = "true"
    )
    fun openAiPolicyTagExtractionClient(
        objectMapper: ObjectMapper,
        @Value("\${policy.tag-extraction.openai.api-key}") apiKey: String,
        @Value("\${policy.tag-extraction.openai.model}") model: String
    ): PolicyTagExtractionClient =
        OpenAiPolicyTagExtractionClient(
            httpClient = HttpClient.newHttpClient(),
            objectMapper = objectMapper,
            apiKey = apiKey,
            model = model,
            endpoint = URI.create("https://api.openai.com/v1/responses")
        )

    @Bean
    @ConditionalOnMissingBean(PolicyTagExtractionClient::class)
    fun ruleOnlyPolicyTagExtractionClient(): PolicyTagExtractionClient =
        PolicyTagExtractionClient { PolicyTagExtractionClientResult.Failure }
}
