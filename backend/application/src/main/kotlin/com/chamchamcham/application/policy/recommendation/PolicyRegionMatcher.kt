package com.chamchamcham.application.policy.recommendation

import org.springframework.stereotype.Component

@Component
class PolicyRegionMatcher {
    fun extractRegionTokens(addresses: Collection<String?>): Set<String> {
        val joined = addresses.filterNotNull().joinToString(" ")
        val tokens = linkedSetOf<String>()
        provinceAliases.forEach { (canonical, aliases) ->
            if (aliases.any(joined::contains)) {
                tokens += canonical
            }
        }
        cityPattern.findAll(joined).forEach { match -> tokens += match.value }
        return tokens
    }

    fun matches(memberRegions: Set<String>, policyRegions: Set<String>): Boolean {
        return policyRegions.contains("전국") || memberRegions.any(policyRegions::contains)
    }

    private val provinceAliases = linkedMapOf(
        "충청북도" to listOf("충청북도", "충북"),
        "충청남도" to listOf("충청남도", "충남"),
        "경기도" to listOf("경기도", "경기"),
        "강원특별자치도" to listOf("강원특별자치도", "강원"),
        "전북특별자치도" to listOf("전북특별자치도", "전북"),
        "전라남도" to listOf("전라남도", "전남"),
        "경상북도" to listOf("경상북도", "경북"),
        "경상남도" to listOf("경상남도", "경남"),
        "제주특별자치도" to listOf("제주특별자치도", "제주")
    )
    private val cityPattern = Regex("[가-힣]+(?:시|군|구)")
}
