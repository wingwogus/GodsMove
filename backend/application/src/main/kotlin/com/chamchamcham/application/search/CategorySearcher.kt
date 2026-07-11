package com.chamchamcham.application.search

interface CategorySearcher {
    fun category(): SearchCategory
    fun search(query: SearchQuery): SearchResult.Page
}
