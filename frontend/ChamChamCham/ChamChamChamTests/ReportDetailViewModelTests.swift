//
//  ReportDetailViewModelTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Report detail view model", .serialized)
struct ReportDetailViewModelTests {
    @Test("cached detail is visible while a network refresh is in flight")
    func cachedDetailThenNetwork() async {
        let key = WorkReportKey(reportId: ReportFixtures.reportId, workType: .planting)
        let cachedAt = Date(timeIntervalSince1970: 100)
        let repository = StubReportRepository(
            fetchHandler: emptyPage,
            cachedDetailHandler: { _ in
                ReportResource(
                    value: ReportFixtures.domainDetail(cropName: "캐시 황기"),
                    source: .cache(updatedAt: cachedAt)
                )
            },
            detailHandler: { _ in
                try await Task.sleep(for: .milliseconds(30))
                return ReportResource(
                    value: ReportFixtures.domainDetail(cropName: "최신 황기"),
                    source: .network
                )
            },
            feedbackHandler: { _, _ in
                ReportResource(value: ReportFixtures.domainFeedback(), source: .network)
            }
        )
        let viewModel = ReportDetailViewModel(key: key, repository: repository)

        let load = Task { await viewModel.onAppear() }
        await waitUntil { viewModel.detail?.cropName == "캐시 황기" }
        #expect(viewModel.cachedAt == cachedAt)
        #expect(!viewModel.isLoading)
        await load.value

        #expect(viewModel.detail?.cropName == "최신 황기")
        #expect(viewModel.cachedAt == nil)
    }

    @Test("a first load failure without cache exposes an error")
    func noCacheFailure() async {
        let repository = StubReportRepository(
            fetchHandler: emptyPage,
            detailHandler: { _ in throw APIError.server(statusCode: 503) }
        )
        let viewModel = ReportDetailViewModel(key: key, repository: repository)

        await viewModel.onAppear()

        #expect(viewModel.detail == nil)
        #expect(viewModel.errorMessage != nil)
        #expect(!viewModel.isLoading)
    }

    @Test("an active cycle never requests coaching feedback")
    func activeSkipsFeedback() async {
        let repository = StubReportRepository(
            fetchHandler: emptyPage,
            detailHandler: { _ in
                ReportResource(value: ReportFixtures.domainDetail(status: .active), source: .network)
            }
        )
        let viewModel = ReportDetailViewModel(key: key, repository: repository)

        await viewModel.onAppear()

        #expect(viewModel.detail?.status == .active)
        #expect(viewModel.feedback == nil)
        #expect(repository.feedbackCalls.isEmpty)
        #expect(!viewModel.isPolling)
    }

    @Test("ready feedback is terminal and does not poll")
    func readyIsTerminal() async {
        let repository = completedRepository { _, _ in
            ReportResource(value: ReportFixtures.domainFeedback(), source: .network)
        }
        let viewModel = ReportDetailViewModel(key: key, repository: repository)

        await viewModel.onAppear()

        #expect(viewModel.feedback?.state == .ready)
        #expect(repository.feedbackCalls.count == 1)
        #expect(!viewModel.isPolling)
    }

    @Test("embedded pending remains pending when the bulk response is empty")
    func embeddedPendingWithEmptyBulk() async {
        let embedded = ReportFeedbackStatus(state: .pending, content: nil)
        let repository = StubReportRepository(
            fetchHandler: emptyPage,
            detailHandler: { _ in
                ReportResource(
                    value: ReportFixtures.domainDetail(feedback: embedded),
                    source: .network
                )
            },
            feedbackHandler: { _, _ in ReportResource(value: nil, source: .network) }
        )
        let viewModel = ReportDetailViewModel(
            key: key,
            repository: repository,
            sleep: { duration in try await Task.sleep(for: duration) }
        )

        await viewModel.onAppear()

        #expect(viewModel.feedback?.state == .pending)
        #expect(viewModel.isPolling)
        viewModel.onDisappear()
        await Task.yield()
        #expect(!viewModel.isPolling)
    }

    @Test("pending polling stops when a terminal response arrives")
    func pendingBecomesReady() async {
        let repository = completedRepository { _, _ in
            ReportResource(value: ReportFixtures.domainFeedback(state: .pending), source: .network)
        }
        repository.feedbackHandler = { _, _ in
            let state: ReportFeedbackState = repository.feedbackCalls.count == 1 ? .pending : .ready
            return ReportResource(value: ReportFixtures.domainFeedback(state: state), source: .network)
        }
        let viewModel = ReportDetailViewModel(
            key: key,
            repository: repository,
            sleep: { _ in await Task.yield() }
        )

        await viewModel.onAppear()
        await waitUntil { viewModel.feedback?.state == .ready && !viewModel.isPolling }

        #expect(repository.feedbackCalls.count == 2)
        #expect(viewModel.feedback?.state == .ready)
        #expect(!viewModel.isPolling)
    }

    @Test("pending polling stops after twenty four attempts")
    func pollingAttemptLimit() async {
        let repository = completedRepository { _, _ in
            ReportResource(value: ReportFixtures.domainFeedback(state: .pending), source: .network)
        }
        let viewModel = ReportDetailViewModel(
            key: key,
            repository: repository,
            sleep: { _ in await Task.yield() }
        )

        await viewModel.onAppear()
        await waitUntil(limit: 1_000) { repository.feedbackCalls.count == 25 && !viewModel.isPolling }

        #expect(repository.feedbackCalls.count == 25)
        #expect(viewModel.feedback?.state == .pending)
        #expect(!viewModel.isPolling)
    }

    @Test("leaving or backgrounding the screen cancels polling")
    func lifecycleCancellation() async {
        let repository = completedRepository { _, _ in
            ReportResource(value: ReportFixtures.domainFeedback(state: .pending), source: .network)
        }
        let viewModel = ReportDetailViewModel(
            key: key,
            repository: repository,
            sleep: { duration in try await Task.sleep(for: duration) }
        )

        await viewModel.onAppear()
        #expect(viewModel.isPolling)
        viewModel.setSceneActive(false)
        await Task.yield()
        #expect(!viewModel.isPolling)

        await viewModel.refreshFeedback()
        #expect(!viewModel.isPolling)
        viewModel.setSceneActive(true)
        #expect(viewModel.isPolling)
        viewModel.onDisappear()
        await Task.yield()
        #expect(!viewModel.isPolling)
    }

    @Test("regeneration is available only for online failed or stale feedback")
    func regenerationRules() async {
        let repository = completedRepository { _, _ in
            ReportResource(value: ReportFixtures.domainFeedback(state: .failed), source: .network)
        }
        repository.regenerateHandler = { _ in ReportFixtures.domainFeedback(state: .pending) }
        let viewModel = ReportDetailViewModel(
            key: key,
            repository: repository,
            sleep: { duration in try await Task.sleep(for: duration) }
        )

        await viewModel.onAppear()
        #expect(viewModel.canRegenerate)
        await viewModel.regenerate()

        #expect(repository.regenerateCalls == [key])
        #expect(viewModel.feedback?.state == .pending)
        #expect(viewModel.isPolling)
        viewModel.onDisappear()

        let offlineAt = Date(timeIntervalSince1970: 200)
        let offlineRepository = StubReportRepository(
            fetchHandler: emptyPage,
            detailHandler: { _ in
                ReportResource(
                    value: ReportFixtures.domainDetail(),
                    source: .cache(updatedAt: offlineAt)
                )
            },
            feedbackHandler: { _, _ in
                ReportResource(
                    value: ReportFixtures.domainFeedback(state: .stale),
                    source: .cache(updatedAt: offlineAt)
                )
            }
        )
        let offline = ReportDetailViewModel(key: key, repository: offlineRepository)
        await offline.onAppear()

        #expect(offline.isOffline)
        #expect(!offline.canRegenerate)
    }

    private var key: WorkReportKey {
        WorkReportKey(reportId: ReportFixtures.reportId, workType: .planting)
    }

    private var emptyPage: StubReportRepository.FetchHandler {
        { _, _, _ in ReportResource(value: ReportFixtures.domainPage(items: []), source: .network) }
    }

    private func completedRepository(
        feedback: @escaping StubReportRepository.FeedbackHandler
    ) -> StubReportRepository {
        StubReportRepository(
            fetchHandler: emptyPage,
            detailHandler: { _ in
                ReportResource(value: ReportFixtures.domainDetail(), source: .network)
            },
            feedbackHandler: feedback
        )
    }

    private func waitUntil(
        limit: Int = 200,
        _ condition: @MainActor () -> Bool
    ) async {
        for _ in 0..<limit where !condition() {
            await Task.yield()
        }
    }
}
