package com.chamchamcham.domain.policy

enum class PolicyRecommendationSort {
    RECOMMENDED,
    LATEST;

    companion object {
        fun fromKey(key: String): PolicyRecommendationSort? = entries.firstOrNull { it.name == key }
    }
}
