package com.chamchamcham.application.policy.support

data class PolicyTagExtractionRequest(
    val title: String,
    val summary: String?,
    val eligibility: String?,
    val benefit: String?,
    val agencyName: String
)

sealed interface PolicyTagExtractionClientResult {
    data class Success(
        val targetTags: Set<String>,
        val cropTags: Set<String>,
        val regionTags: Set<String>,
        val confidence: Double
    ) : PolicyTagExtractionClientResult

    data object Failure : PolicyTagExtractionClientResult
}

fun interface PolicyTagExtractionClient {
    fun extract(request: PolicyTagExtractionRequest): PolicyTagExtractionClientResult
}
