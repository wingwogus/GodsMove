//
//  ReportListViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

@MainActor
@Observable
final class ReportListViewModel {
    private enum LoadKind {
        case initial
        case refresh
    }

    private(set) var reports: [FarmingWorkReportSummary] = []
    private(set) var farms: [ReportFarmFilterOption] = []
    private(set) var filter = ReportFilter()

    private(set) var isLoading = false
    private(set) var isRefreshing = false
    private(set) var isLoadingMore = false
    private(set) var errorMessage: String?
    private(set) var cachedAt: Date?

    private var hasLoaded = false
    private var nextCursor: String?
    private var generation = 0
    private var loadTask: Task<ReportResource<FarmingWorkReportPage>, Error>?
    private var loadMoreTask: Task<ReportResource<FarmingWorkReportPage>, Error>?

    private let repository: any ReportRepository
    private let farmCropLoader: @Sendable () async throws -> [FarmWithCrops]
    private let pageSize: Int

    init(
        repository: any ReportRepository,
        pageSize: Int = 20,
        farmCropLoader: @escaping @Sendable () async throws -> [FarmWithCrops]
    ) {
        self.repository = repository
        self.pageSize = pageSize
        self.farmCropLoader = farmCropLoader
    }

    var hasMore: Bool { nextCursor != nil }
    var isShowingCachedData: Bool { cachedAt != nil }

    var selectedFarmNames: [String]? {
        guard !filter.farmIds.isEmpty else { return nil }
        return farms.filter { filter.farmIds.contains($0.id) }.map(\.name)
    }

    var selectedCropNames: [String]? {
        guard !filter.cropIds.isEmpty else { return nil }
        return farms.flatMap(\.crops).filter { filter.cropIds.contains($0.id) }.map(\.name)
    }

    var availableCrops: [ReportCropFilterOption] {
        guard !filter.farmIds.isEmpty else { return farms.flatMap(\.crops) }
        return farms.filter { filter.farmIds.contains($0.id) }.flatMap(\.crops)
    }

    func onAppear() async {
        guard !hasLoaded else { return }
        hasLoaded = true
        await loadFarmOptions()
        await load(kind: .initial)
    }

    func refresh() async {
        await load(kind: .refresh)
    }

    func retry() async {
        await load(kind: .initial)
    }

    func loadMoreIfNeeded(currentItem: FarmingWorkReportSummary) async {
        guard let cursor = nextCursor,
              !isLoading,
              !isRefreshing,
              !isLoadingMore,
              currentItem.id == reports.last?.id
        else { return }

        let token = generation
        let currentFilter = filter
        isLoadingMore = true
        let task = Task { @MainActor [repository, pageSize] in
            try await repository.fetchReports(
                filter: currentFilter,
                cursor: cursor,
                size: pageSize
            )
        }
        loadMoreTask = task

        defer {
            if token == generation {
                isLoadingMore = false
                loadMoreTask = nil
            }
        }

        do {
            let resource = try await task.value
            guard token == generation, !task.isCancelled else { return }
            reports = deduplicated(reports + resource.value.items)
            nextCursor = resource.value.nextCursor
            apply(source: resource.source)
        } catch {
            // Existing rows remain visible and the next scroll trigger can retry.
        }
    }

    func applyFarmFilter(_ farmIds: Set<UUID>) async {
        guard filter.farmIds != farmIds else { return }
        filter.farmIds = farmIds
        if !farmIds.isEmpty {
            let validCropIds = Set(farms.filter { farmIds.contains($0.id) }.flatMap(\.crops).map(\.id))
            filter.cropIds.formIntersection(validCropIds)
        }
        await load(kind: .initial)
    }

    func applyCropFilter(_ cropIds: Set<UUID>) async {
        guard filter.cropIds != cropIds else { return }
        filter.cropIds = cropIds
        await load(kind: .initial)
    }

    func applyWorkTypeFilter(_ workTypes: Set<WorkType>) async {
        guard filter.workTypes != workTypes else { return }
        filter.workTypes = workTypes
        await load(kind: .initial)
    }

    private func loadFarmOptions() async {
        guard let farmCrops = try? await farmCropLoader() else { return }
        farms = farmCrops.map(ReportFarmFilterOption.init(farm:))
    }

    private func load(kind: LoadKind) async {
        loadTask?.cancel()
        loadMoreTask?.cancel()
        generation += 1
        let token = generation
        let currentFilter = filter

        isLoading = kind == .initial
        isRefreshing = kind == .refresh
        isLoadingMore = false
        errorMessage = nil
        nextCursor = nil

        let task = Task { @MainActor [repository, pageSize] in
            try await repository.fetchReports(
                filter: currentFilter,
                cursor: nil,
                size: pageSize
            )
        }
        loadTask = task

        defer {
            if token == generation {
                isLoading = false
                isRefreshing = false
                loadTask = nil
            }
        }

        do {
            let resource = try await task.value
            guard token == generation, !task.isCancelled else { return }
            reports = deduplicated(resource.value.items)
            nextCursor = resource.value.nextCursor
            apply(source: resource.source)
        } catch {
            guard token == generation, !task.isCancelled else { return }
            if reports.isEmpty {
                nextCursor = nil
                errorMessage = ReportErrorMessage.text(for: error)
            }
        }
    }

    private func apply(source: ReportResource<FarmingWorkReportPage>.Source) {
        switch source {
        case .network:
            cachedAt = nil
        case let .cache(updatedAt):
            cachedAt = updatedAt
        }
    }

    private func deduplicated(
        _ items: [FarmingWorkReportSummary]
    ) -> [FarmingWorkReportSummary] {
        var seen = Set<WorkReportKey>()
        return items.filter { seen.insert($0.id).inserted }
    }
}
