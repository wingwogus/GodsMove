//
//  PolicyListViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

@MainActor
@Observable
final class PolicyListViewModel {
    private let repository: PolicyRepository

    private(set) var items: [PolicyRecommendation] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private var nextCursor: String?
    private(set) var selectedCategory: PolicyCategory?
    private(set) var sort: PolicySort = .recommended

    init(repository: PolicyRepository) {
        self.repository = repository
    }

    func onAppear() async {
        guard items.isEmpty, errorMessage == nil else { return }
        await reload()
    }

    func reload() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await repository.fetchRecommendations(
                PolicyRecommendationQuery(benefitCategory: selectedCategory, sort: sort, cursor: nil)
            )
            items = page.items
            nextCursor = page.nextCursor
        } catch {
            items = []
            nextCursor = nil
            errorMessage = HomeErrorMessage.text(for: error)
        }
    }

    func selectCategory(_ category: PolicyCategory?) async {
        guard category != selectedCategory else { return }
        selectedCategory = category
        await reload()
    }

    func selectSort(_ sort: PolicySort) async {
        guard self.sort != sort else { return }
        self.sort = sort
        await reload()
    }

    /// Mirrors Record/Community's `loadMoreIfNeeded`: fetches the next cursor page only when the
    /// row rendering it is the last one currently loaded.
    func loadMoreIfNeeded(currentItem: PolicyRecommendation) async {
        guard currentItem.id == items.last?.id, let nextCursor, !isLoading else { return }
        do {
            let page = try await repository.fetchRecommendations(
                PolicyRecommendationQuery(benefitCategory: selectedCategory, sort: sort, cursor: nextCursor)
            )
            items.append(contentsOf: page.items)
            self.nextCursor = page.nextCursor
        } catch {
            // Keep already-loaded items on a pagination failure; just stop paging this round.
            self.nextCursor = nil
        }
    }
}
