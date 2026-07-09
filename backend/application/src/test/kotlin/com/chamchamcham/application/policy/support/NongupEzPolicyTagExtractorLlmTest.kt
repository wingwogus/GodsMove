package com.chamchamcham.application.policy.support

import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyDetail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NongupEzPolicyTagExtractorLlmTest {
    @Test
    fun `valid llm tags are used when schema and enums are valid`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(
                PolicyTagExtractionClientResult.Success(
                    targetTags = setOf("YOUNG_FARMER"),
                    cropTags = setOf("MEDICINAL_CROP"),
                    regionTags = setOf("충청북도"),
                    confidence = 0.88
                )
            )
        )

        val tags = extractor.extract(detail(title = "청년 약용작물 지원"))

        assertThat(tags.targetTags).containsExactly("YOUNG_FARMER")
        assertThat(tags.cropTags).containsExactly("MEDICINAL_CROP")
        assertThat(tags.regionTags).containsExactly("충청북도")
    }

    @Test
    fun `invalid llm tags fall back to rules`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(
                PolicyTagExtractionClientResult.Success(
                    targetTags = setOf("UNKNOWN_TARGET"),
                    cropTags = emptySet(),
                    regionTags = emptySet(),
                    confidence = 0.90
                )
            )
        )

        val tags = extractor.extract(detail(title = "청년 귀농 약용작물 지원"))

        assertThat(tags.targetTags).contains("YOUNG_FARMER", "RETURNING_FARMER")
        assertThat(tags.cropTags).contains("MEDICINAL_CROP")
    }

    @Test
    fun `llm failure falls back to rules`() {
        val extractor = NongupEzPolicyTagExtractor(
            llmClient = FakePolicyTagExtractionClient(PolicyTagExtractionClientResult.Failure)
        )

        val tags = extractor.extract(detail(title = "특용작물 지원"))

        assertThat(tags.cropTags).contains("SPECIAL_CROP")
    }

    private class FakePolicyTagExtractionClient(
        private val result: PolicyTagExtractionClientResult
    ) : PolicyTagExtractionClient {
        override fun extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult = result
    }

    private fun detail(title: String): NongupEzPolicyDetail =
        NongupEzPolicyDetail(
            externalId = "AB000009",
            sourceYear = "2026",
            title = title,
            purpose = null,
            summary = title,
            eligibility = title,
            benefit = title,
            applyStartsOn = null,
            applyEndsOn = null,
            applicationMethod = null,
            requiredDocuments = null,
            selectionCriteria = null,
            agencyName = "농림축산식품부",
            contacts = emptyList(),
            attachments = emptyList(),
            rawJson = "{}"
        )
}
