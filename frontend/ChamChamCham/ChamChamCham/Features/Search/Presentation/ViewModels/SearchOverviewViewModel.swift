//
//  SearchOverviewViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// Drives the "전체" tab: one `searchAll` call per keyword, no pagination (the backend caps each
/// section to 3 items server-side — "더보기" on a section switches the parent's selected tab
/// instead of paging this view model).
@MainActor
@Observable
final class SearchOverviewViewModel {
    private(set) var result: SearchAllResult?
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let repository: any SearchRepository
    private var loadedKeyword: String?
    private var hasLoaded = false

    init(repository: any SearchRepository) {
        self.repository = repository
    }

    /// Skips a redundant refetch when the "전체" tab is simply re-selected without the keyword
    /// changing.
    func loadIfNeeded(keyword: String?) async {
        guard !hasLoaded || loadedKeyword != keyword else { return }
        await reload(keyword: keyword)
    }

    func reload(keyword: String?) async {
        loadedKeyword = keyword
        hasLoaded = true
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            result = try await repository.searchAll(keyword: keyword)
        } catch {
            result = nil
            errorMessage = SearchErrorMessage.text(for: error)
        }
    }
}
