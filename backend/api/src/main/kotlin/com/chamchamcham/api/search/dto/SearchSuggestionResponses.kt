package com.chamchamcham.api.search.dto

import com.chamchamcham.application.search.SearchSuggestionResult

object SearchSuggestionResponses {
    data class SuggestionsResponse(
        val keywords: List<String>,
    ) {
        companion object {
            fun from(result: SearchSuggestionResult.Suggestions): SuggestionsResponse =
                SuggestionsResponse(keywords = result.keywords)
        }
    }
}
