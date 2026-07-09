package com.chamchamcham.application.policy.support

import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyDetail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NongupEzPolicyTagExtractorTest {
    private val extractor = NongupEzPolicyTagExtractor()

    @Test
    fun `extracts young farmer return farmer medicinal and national tags`() {
        val detail = detail(
            title = "청년농 귀농 약용작물 지원",
            eligibility = "만 40세 미만 청년농업인 및 귀농인",
            benefit = "약용작물 재배시설 지원",
            agencyName = "농림축산식품부"
        )

        val tags = extractor.extract(detail)

        assertThat(tags.targetTags).contains("YOUNG_FARMER", "RETURNING_FARMER")
        assertThat(tags.cropTags).contains("MEDICINAL_CROP")
        assertThat(tags.regionTags).contains("전국")
    }

    @Test
    fun `extracts regional tag from agency name`() {
        val detail = detail(
            title = "충청북도 특용작물 지원",
            eligibility = "충청북도 농업인",
            benefit = "특용작물 생산 지원",
            agencyName = "충청북도"
        )

        val tags = extractor.extract(detail)

        assertThat(tags.cropTags).contains("SPECIAL_CROP")
        assertThat(tags.regionTags).contains("충청북도")
    }

    private fun detail(title: String, eligibility: String, benefit: String, agencyName: String): NongupEzPolicyDetail =
        NongupEzPolicyDetail(
            externalId = "AB",
            sourceYear = "2026",
            title = title,
            purpose = null,
            summary = null,
            eligibility = eligibility,
            benefit = benefit,
            applyStartsOn = null,
            applyEndsOn = null,
            applicationMethod = null,
            requiredDocuments = null,
            selectionCriteria = null,
            agencyName = agencyName,
            contacts = emptyList(),
            attachments = emptyList(),
            rawJson = "{}"
        )
}
