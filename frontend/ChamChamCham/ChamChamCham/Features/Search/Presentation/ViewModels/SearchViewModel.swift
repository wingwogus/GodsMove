//
//  SearchViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// Orchestrates the search screen's idle/typing/results phases and 최근 검색어 CRUD. Does NOT hold
/// the "전체"/카테고리별 result arrays itself — those live in `SearchOverviewViewModel` and the 3
/// `Search<X>ListViewModel`s (one-shot 3-section fetch vs. independent cursor pagination don't fit
/// one shape), each reloaded by the view whenever `submittedKeyword` changes.
@MainActor
@Observable
final class SearchViewModel {
    /// Live text bound to `AppSearchBar`.
    var query: String = ""
    /// `nil` = idle/typing (drives `SearchHistoryView`/`SearchSuggestionListView`); non-nil = results
    /// phase (drives `SearchResultsView`).
    private(set) var submittedKeyword: String?
    private(set) var selectedCategory: SearchCategory = .all

    private(set) var recentTerms: [RecentSearchTerm] = []
    private(set) var suggestions: [String] = []

    private let repository: any SearchRepository
    private let recentSearchStore: any RecentSearchStore
    /// The in-flight (or debounce-pending) suggestions fetch. Cancelled and replaced on every
    /// keystroke so a slow older response can never overwrite a newer one.
    private var suggestionsTask: Task<Void, Never>?

    init(repository: any SearchRepository, recentSearchStore: any RecentSearchStore) {
        self.repository = repository
        self.recentSearchStore = recentSearchStore
    }

    func selectCategory(_ category: SearchCategory) {
        selectedCategory = category
    }

    func loadRecent() {
        recentTerms = recentSearchStore.fetchRecent(limit: 20)
    }

    /// Real-time search-as-you-type: called on every keystroke. Debounces 250ms so a fast typist
    /// doesn't fire one request per character, and cancels the previous fetch so its response can
    /// never land after (and overwrite) a newer one.
    func loadSuggestions(for text: String) {
        suggestionsTask?.cancel()
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            suggestions = []
            return
        }
        suggestionsTask = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            let result = (try? await repository.suggestions(keyword: trimmed)) ?? []
            guard !Task.isCancelled else { return }
            suggestions = result
        }
    }

    /// Submits a search — manual `AppSearchBar` return, a suggestion-row tap, or a recent-term chip
    /// tap all funnel through here (fill + immediately search, matching the one path Figma's capture
    /// left ambiguous between).
    func onSubmit(_ keyword: String) {
        let trimmed = keyword.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        suggestionsTask?.cancel()
        query = trimmed
        submittedKeyword = trimmed
        selectedCategory = .all
        recentSearchStore.recordSearch(keyword: trimmed)
        loadRecent()
    }

    /// Backs out of the results phase to idle (e.g. tapping back or clearing the search bar).
    func clearSubmission() {
        suggestionsTask?.cancel()
        submittedKeyword = nil
        suggestions = []
    }

    func deleteRecentTerm(_ id: UUID) {
        recentSearchStore.delete(id: id)
        loadRecent()
    }

    func clearAllRecent() {
        recentSearchStore.deleteAll()
        loadRecent()
    }
}
