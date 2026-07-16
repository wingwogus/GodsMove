//
//  RecordListViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// Drives the 영농 기록 list: the 작물 / 영농 활동 / 기간 filters and cursor pagination. Every filter change
/// re-queries from the first page. Mirrors `CommunityFeedViewModel`'s network-backed, cursor-paged shape.
@MainActor
@Observable
final class RecordListViewModel {
    private(set) var records: [FarmingRecordSummary] = []
    private(set) var activeCrops: [ActiveCrop] = []
    private(set) var filter = RecordFilter()

    private(set) var isLoading = false
    private(set) var isLoadingMore = false
    private(set) var errorMessage: String?

    private var nextCursor: String?
    private let repository: any RecordRepository

    init(repository: any RecordRepository) {
        self.repository = repository
    }

    var hasMore: Bool { nextCursor != nil }

    /// Names of the crops currently filtered on, for the chip label. `nil` when no crop filter.
    var selectedCropNames: [String]? {
        guard !filter.cropIds.isEmpty else { return nil }
        return activeCrops.filter { filter.cropIds.contains($0.id) }.map(\.name)
    }

    func onAppear() async {
        guard records.isEmpty, !isLoading else { return }
        await loadActiveCrops()
        await reload()
    }

    /// Non-fatal — the list still works with no crop filter options if this fails.
    func loadActiveCrops() async {
        guard let crops = try? await repository.fetchActiveCrops() else { return }
        activeCrops = crops
    }

    func reload() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let page = try await repository.fetchRecords(RecordQuery(filter: filter))
            records = page.items
            nextCursor = page.nextCursor
        } catch {
            records = []
            nextCursor = nil
            errorMessage = RecordErrorMessage.text(for: error)
        }
    }

    func loadMoreIfNeeded(currentItem: FarmingRecordSummary) async {
        guard let cursor = nextCursor, !isLoadingMore, !isLoading,
              currentItem.id == records.last?.id else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await repository.fetchRecords(RecordQuery(filter: filter, cursor: cursor))
            records.append(contentsOf: page.items)
            nextCursor = page.nextCursor
        } catch {
            // Keep what we have; the row can retry on the next scroll trigger.
        }
    }

    // MARK: - Filters (apply on 완료)

    func applyCropFilter(_ cropIds: Set<UUID>) async {
        guard filter.cropIds != cropIds else { return }
        filter.cropIds = cropIds
        await reload()
    }

    func applyWorkTypeFilter(_ workTypes: Set<WorkType>) async {
        guard filter.workTypes != workTypes else { return }
        filter.workTypes = workTypes
        await reload()
    }

    func applyDateFilter(startDate: Date?, endDate: Date?) async {
        guard filter.startDate != startDate || filter.endDate != endDate else { return }
        filter.startDate = startDate
        filter.endDate = endDate
        await reload()
    }
}
