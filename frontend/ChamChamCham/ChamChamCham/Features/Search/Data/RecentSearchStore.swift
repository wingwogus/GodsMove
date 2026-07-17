//
//  RecentSearchStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

/// Local "최근 검색어" storage. Presentation depends on this instead of touching `ModelContext`/
/// `RecentSearchTermRecord` directly, matching `MemberProfileCache`'s split — a synchronous,
/// `@MainActor` local store, distinct from the `async throws`/`Sendable` network `Repository`
/// convention used everywhere else, since there's no network round trip here at all.
@MainActor
protocol RecentSearchStore {
    /// Records a submitted search. Re-searching an existing keyword moves it to the front instead
    /// of duplicating it.
    func recordSearch(keyword: String)
    func fetchRecent(limit: Int) -> [RecentSearchTerm]
    func delete(id: UUID)
    func deleteAll()
}

@MainActor
final class SwiftDataRecentSearchStore: RecentSearchStore {
    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    func recordSearch(keyword: String) {
        let trimmed = keyword.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        let existing = FetchDescriptor<RecentSearchTermRecord>(
            predicate: #Predicate { $0.keyword == trimmed }
        )
        for record in (try? modelContext.fetch(existing)) ?? [] {
            modelContext.delete(record)
        }
        modelContext.insert(RecentSearchTermRecord(keyword: trimmed, searchedAt: Date()))
        try? modelContext.save()
    }

    func fetchRecent(limit: Int) -> [RecentSearchTerm] {
        var descriptor = FetchDescriptor<RecentSearchTermRecord>(
            sortBy: [SortDescriptor(\.searchedAt, order: .reverse)]
        )
        descriptor.fetchLimit = limit
        let records = (try? modelContext.fetch(descriptor)) ?? []
        return records.map { RecentSearchTerm(id: $0.id, keyword: $0.keyword, searchedAt: $0.searchedAt) }
    }

    func delete(id: UUID) {
        let descriptor = FetchDescriptor<RecentSearchTermRecord>(predicate: #Predicate { $0.id == id })
        guard let record = (try? modelContext.fetch(descriptor))?.first else { return }
        modelContext.delete(record)
        try? modelContext.save()
    }

    func deleteAll() {
        (try? modelContext.fetch(FetchDescriptor<RecentSearchTermRecord>()))?.forEach(modelContext.delete)
        try? modelContext.save()
    }
}
