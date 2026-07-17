//
//  SearchRecordListViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// Drives the "나의 일지" search tab: cursor-paged, keyword-scoped. Mirrors `RecordListViewModel`'s
/// shape, scoped down to what search needs (no filter axes — the search screen has no filter UI).
@MainActor
@Observable
final class SearchRecordListViewModel {
    private(set) var items: [FarmingRecordSummary] = []
    private(set) var totalCount = 0
    private(set) var isLoading = false
    private(set) var isLoadingMore = false
    private(set) var errorMessage: String?

    private var nextCursor: String?
    private var keyword: String?
    private var hasLoaded = false
    private let repository: any SearchRepository

    init(repository: any SearchRepository) {
        self.repository = repository
    }

    /// Skips redundant refetches when a tab is simply re-selected without the keyword changing —
    /// the view itself is torn down/rebuilt on tab switch, so this state has to live here.
    func loadIfNeeded(keyword: String?) async {
        guard !hasLoaded || self.keyword != keyword else { return }
        await reload(keyword: keyword)
    }

    func reload(keyword: String?) async {
        self.keyword = keyword
        hasLoaded = true
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await repository.searchRecords(keyword: keyword, cursor: nil, size: 20)
            items = page.items
            totalCount = page.totalCount
            nextCursor = page.nextCursor
        } catch {
            items = []
            totalCount = 0
            nextCursor = nil
            errorMessage = SearchErrorMessage.text(for: error)
        }
    }

    func loadMoreIfNeeded(currentItem: FarmingRecordSummary) async {
        guard let cursor = nextCursor, !isLoadingMore, !isLoading,
              currentItem.id == items.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await repository.searchRecords(keyword: keyword, cursor: cursor, size: 20)
            items.append(contentsOf: page.items)
            nextCursor = page.nextCursor
        } catch {
            // Keep what we have; the row can retry on the next scroll trigger.
        }
    }
}
