package com.chamchamcham.application.policy.support

import com.chamchamcham.application.policy.source.nongupez.NongupEzPolicyDetail
import org.springframework.stereotype.Component

@Component
class NongupEzPolicyTagExtractor {
    fun extract(detail: NongupEzPolicyDetail): ExtractedPolicyTags {
        val text = listOfNotNull(detail.title, detail.summary, detail.eligibility, detail.benefit, detail.agencyName)
            .joinToString(" ")
        val targetTags = linkedSetOf<String>()
        val cropTags = linkedSetOf<String>()
        val regionTags = linkedSetOf<String>()

        if (text.contains("청년")) targetTags += "YOUNG_FARMER"
        if (text.contains("귀농") || text.contains("귀촌")) targetTags += "RETURNING_FARMER"
        if (text.contains("농업경영정보") || text.contains("농업경영체")) targetTags += "REGISTERED_FARMER"
        if (text.contains("법인")) targetTags += "AGRICULTURAL_CORPORATION"
        if (text.contains("약용작물")) cropTags += "MEDICINAL_CROP"
        if (text.contains("특용작물")) cropTags += "SPECIAL_CROP"
        if (text.contains("친환경")) cropTags += "ECO_FRIENDLY"

        regionTags += provinceNames.filter { text.contains(it) }
        if (regionTags.isEmpty() && detail.agencyName.contains("농림축산식품부")) {
            regionTags += "전국"
        }

        return ExtractedPolicyTags(
            targetTags = targetTags,
            cropTags = cropTags,
            regionTags = regionTags.ifEmpty { linkedSetOf("전국") }
        )
    }

    private val provinceNames = listOf(
        "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시", "울산광역시",
        "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도",
        "전라남도", "경상북도", "경상남도", "제주특별자치도"
    )
}

data class ExtractedPolicyTags(
    val targetTags: Set<String>,
    val cropTags: Set<String>,
    val regionTags: Set<String>
)
