//
//  ReportListViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Report list view model", .serialized)
struct ReportListViewModelTests {
    @Test("first appearance loads farm options and the first report page")
    func initialLoad() async {
        let repository = StubReportRepository { _, _, _ in
            ReportResource(value: ReportFixtures.domainPage(nextCursor: "next"), source: .network)
        }
        let viewModel = ReportListViewModel(repository: repository) {
            ReportFixtures.farms()
        }

        await viewModel.onAppear()

        #expect(viewModel.reports.map(\.cropName) == ["황기"])
        #expect(viewModel.farms.map(\.name) == ["북쪽 밭", "남쪽 밭"])
        #expect(viewModel.hasMore)
        #expect(repository.fetchCalls == [ReportFetchCall(filter: ReportFilter(), cursor: nil, size: 20)])
    }

    @Test("cached data exposes its timestamp and a network refresh clears the offline state")
    func cacheThenNetworkRefresh() async {
        let cachedAt = Date(timeIntervalSince1970: 100)
        let repository = StubReportRepository { _, _, _ in
            ReportResource(value: ReportFixtures.domainPage(), source: .cache(updatedAt: cachedAt))
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { [] })

        await viewModel.onAppear()
        #expect(viewModel.cachedAt == cachedAt)
        #expect(viewModel.isShowingCachedData)

        repository.fetchHandler = { _, _, _ in
            ReportResource(
                value: ReportFixtures.domainPage(items: [ReportFixtures.summary(cropName: "당귀")]),
                source: .network
            )
        }
        await viewModel.refresh()

        #expect(viewModel.reports.map(\.cropName) == ["당귀"])
        #expect(viewModel.cachedAt == nil)
        #expect(!viewModel.isShowingCachedData)
    }

    @Test("refresh failure keeps already visible cached items")
    func cachedRefreshFailure() async {
        let cachedAt = Date(timeIntervalSince1970: 100)
        let repository = StubReportRepository { _, _, _ in
            ReportResource(value: ReportFixtures.domainPage(), source: .cache(updatedAt: cachedAt))
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { [] })
        await viewModel.onAppear()

        repository.fetchHandler = { _, _, _ in throw APIError.server(statusCode: 503) }
        await viewModel.refresh()

        #expect(viewModel.reports.count == 1)
        #expect(viewModel.cachedAt == cachedAt)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("an initial failure without data exposes a full retry error")
    func initialFailure() async {
        let repository = StubReportRepository { _, _, _ in
            throw APIError.apiError(code: "REPORT_NOT_FOUND", message: "리포트를 찾지 못했어요")
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { [] })

        await viewModel.onAppear()

        #expect(viewModel.reports.isEmpty)
        #expect(viewModel.errorMessage == "리포트를 찾지 못했어요")
        #expect(!viewModel.isLoading)
    }

    @Test("cursor loading replaces cumulative data and removes composite duplicates")
    func loadMore() async {
        let secondReportId = UUID(uuidString: "66666666-6666-6666-6666-666666666666")!
        let first = ReportFixtures.summary(workType: .planting)
        let second = ReportFixtures.summary(reportId: secondReportId, workType: .watering)
        let repository = StubReportRepository { _, cursor, _ in
            if cursor == nil {
                return ReportResource(
                    value: ReportFixtures.domainPage(items: [first], nextCursor: "cursor-2"),
                    source: .network
                )
            }
            return ReportResource(
                value: ReportFixtures.domainPage(items: [first, second], nextCursor: nil),
                source: .network
            )
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { [] })
        await viewModel.onAppear()

        await viewModel.loadMoreIfNeeded(currentItem: first)

        #expect(viewModel.reports.map(\.id) == [first.id, second.id])
        #expect(!viewModel.hasMore)
        #expect(repository.fetchCalls.map(\.cursor) == [nil, "cursor-2"])
        #expect(!viewModel.isLoadingMore)
    }

    @Test("farm changes clear an incompatible crop and stale responses cannot overwrite the latest filter")
    func filterCancellationAndStaleResponse() async {
        let farms = ReportFixtures.farms()
        let firstFarmId = farms[0].id
        let firstCropId = farms[0].crops[0].id
        let secondFarmId = farms[1].id
        let repository = StubReportRepository { filter, _, _ in
            if filter.farmId == firstFarmId {
                try? await Task.sleep(for: .milliseconds(80))
                return ReportResource(
                    value: ReportFixtures.domainPage(items: [ReportFixtures.summary(cropName: "늦은 황기")]),
                    source: .network
                )
            }
            return ReportResource(
                value: ReportFixtures.domainPage(items: [ReportFixtures.summary(cropName: "최신 당귀")]),
                source: .network
            )
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { farms })
        await viewModel.onAppear()
        await viewModel.applyCropFilter(firstCropId)

        let staleTask = Task { await viewModel.applyFarmFilter(firstFarmId) }
        await Task.yield()
        await viewModel.applyFarmFilter(secondFarmId)
        await staleTask.value

        #expect(viewModel.filter.farmId == secondFarmId)
        #expect(viewModel.filter.cropId == nil)
        #expect(viewModel.reports.map(\.cropName) == ["최신 당귀"])
    }

    @Test("loading flags reflect an in-flight empty first page")
    func loadingAndEmptyState() async {
        let repository = StubReportRepository { _, _, _ in
            try await Task.sleep(for: .milliseconds(50))
            return ReportResource(value: ReportFixtures.domainPage(items: []), source: .network)
        }
        let viewModel = ReportListViewModel(repository: repository, farmCropLoader: { [] })

        let load = Task { await viewModel.onAppear() }
        await waitUntil { viewModel.isLoading }
        #expect(viewModel.isLoading)
        await load.value

        #expect(!viewModel.isLoading)
        #expect(viewModel.reports.isEmpty)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("report error copy never exposes raw backend failure keys")
    func errorCopy() {
        #expect(ReportErrorMessage.text(for: APIError.unauthorized) == "로그인이 필요해요.")
        #expect(ReportErrorMessage.text(for: APIError.network(URLError(.timedOut))) == "네트워크 연결을 확인해주세요.")
        #expect(ReportErrorMessage.text(for: APIError.apiError(code: "X", message: "리포트가 없어요")) == "리포트가 없어요")
        #expect(ReportErrorMessage.text(for: APIError.apiError(code: "X", message: "error.report_missing"))
            == "문제가 발생했어요. 잠시 후 다시 시도해주세요.")
    }

    private func waitUntil(_ condition: @MainActor () -> Bool) async {
        for _ in 0..<100 where !condition() {
            await Task.yield()
        }
    }
}
