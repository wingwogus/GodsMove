//
//  ReportRepositoryTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Report repository", .serialized)
struct ReportRepositoryTests {
    @Test("successful first page, detail, and feedback responses replace their caches")
    func successfulResponsesUpdateCache() async throws {
        let (cache, container) = try makeCache()
        let filter = ReportFilter(farmId: ReportFixtures.farmId)
        let key = WorkReportKey(reportId: ReportFixtures.reportId, workType: .planting)
        let remote = StubReportRemoteDataSource(
            pageResults: [.success(ReportFixtures.page(nextCursor: "next"))],
            detailResults: [.success(ReportFixtures.detail())],
            feedbackResults: [.success(ReportFixtures.feedbackList())]
        )
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        let page = try await repository.fetchReports(filter: filter, cursor: nil, size: 20)
        let detail = try await repository.fetchDetail(key)
        let feedback = try await repository.fetchFeedback(reportId: key.reportId, workType: key.workType)

        #expect(page.source == .network)
        #expect(detail.source == .network)
        #expect(feedback.source == .network)
        #expect(feedback.value?.workType == .planting)
        #expect(try decode(FarmingWorkReportPageResponseDTO.self, cache.list(for: filter)).nextCursor == "next")
        #expect(try decode(FarmingWorkReportDetailResponseDTO.self, cache.detail(for: key)).reportId == key.reportId)
        #expect(try decode(ReportFeedbackListResponseDTO.self, cache.feedback(reportId: key.reportId)).feedbacks.count == 1)
        withExtendedLifetime(container) {}
    }

    @Test("cursor pages are accumulated and de-duplicated by report and work type")
    func cursorPageAccumulation() async throws {
        let (cache, container) = try makeCache()
        let first = ReportFixtures.item(workType: .planting)
        let second = ReportFixtures.item(workType: .watering)
        let remote = StubReportRemoteDataSource(pageResults: [
            .success(ReportFixtures.page(items: [first], nextCursor: "cursor-2")),
            .success(ReportFixtures.page(items: [first, second], nextCursor: nil)),
        ])
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        _ = try await repository.fetchReports(filter: ReportFilter(), cursor: nil, size: 20)
        let accumulated = try await repository.fetchReports(
            filter: ReportFilter(),
            cursor: "cursor-2",
            size: 20
        )

        #expect(accumulated.value.items.map(\.key.workType) == [.planting, .watering])
        let cached = try decode(FarmingWorkReportPageResponseDTO.self, cache.list(for: ReportFilter()))
        #expect(cached.items.map(\.workType) == ["PLANTING", "WATERING"])
        #expect(cached.nextCursor == nil)
        withExtendedLifetime(container) {}
    }

    @Test("network failures return valid cached values with their timestamp")
    func networkFallback() async throws {
        let (cache, container) = try makeCache()
        let filter = ReportFilter(workType: .watering)
        let key = WorkReportKey(reportId: ReportFixtures.reportId, workType: .watering)
        let updatedAt = Date(timeIntervalSince1970: 123)
        cache.saveList(try JSONEncoder().encode(ReportFixtures.page(items: [ReportFixtures.item(workType: .watering)])), for: filter, updatedAt: updatedAt)
        cache.saveDetail(try JSONEncoder().encode(ReportFixtures.detail(workType: .watering)), for: key, updatedAt: updatedAt)
        cache.saveFeedback(try JSONEncoder().encode(ReportFixtures.feedbackList(feedbacks: [ReportFixtures.feedbackItem(workType: .watering)])), reportId: key.reportId, updatedAt: updatedAt)
        let networkError = APIError.network(URLError(.notConnectedToInternet))
        let remote = StubReportRemoteDataSource(
            pageResults: [.failure(networkError)],
            detailResults: [.failure(networkError)],
            feedbackResults: [.failure(networkError)]
        )
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        let page = try await repository.fetchReports(filter: filter, cursor: nil, size: 20)
        let detail = try await repository.fetchDetail(key)
        let feedback = try await repository.fetchFeedback(reportId: key.reportId, workType: key.workType)

        #expect(page.source == .cache(updatedAt: updatedAt))
        #expect(detail.source == .cache(updatedAt: updatedAt))
        #expect(feedback.source == .cache(updatedAt: updatedAt))
        #expect(feedback.value?.workType == .watering)
        withExtendedLifetime(container) {}
    }

    @Test("a network failure without cache preserves the original error")
    func networkFailureWithoutCache() async throws {
        let (cache, container) = try makeCache()
        let remote = StubReportRemoteDataSource(pageResults: [
            .failure(.network(URLError(.timedOut)))
        ])
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        do {
            _ = try await repository.fetchReports(filter: ReportFilter(), cursor: nil, size: 20)
            Issue.record("Expected the original network error")
        } catch let error as APIError {
            guard case let .network(underlying) = error else {
                Issue.record("Expected APIError.network")
                return
            }
            #expect((underlying as? URLError)?.code == .timedOut)
        }
        withExtendedLifetime(container) {}
    }

    @Test("non-network API failures never fall back to a populated cache")
    func nonNetworkFailuresDoNotFallback() async throws {
        let (cache, container) = try makeCache()
        let filter = ReportFilter()
        cache.saveList(try JSONEncoder().encode(ReportFixtures.page()), for: filter)
        let errors: [APIError] = [
            .unauthorized,
            .validation(message: "invalid"),
            .server(statusCode: 503),
            .apiError(code: "REPORT_NOT_FOUND", message: "리포트가 없어요"),
            .decoding(DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "bad"))),
        ]
        let remote = StubReportRemoteDataSource(pageResults: errors.map { .failure($0) })
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        for _ in errors {
            do {
                _ = try await repository.fetchReports(filter: filter, cursor: nil, size: 20)
                Issue.record("A non-network error incorrectly used the cache")
            } catch let error as APIError {
                if case .network = error { Issue.record("Expected a non-network API error") }
            }
        }
        withExtendedLifetime(container) {}
    }

    @Test("regenerate returns only the POST result and never queues or falls back")
    func regenerateHasNoFallback() async throws {
        let (cache, container) = try makeCache()
        let key = WorkReportKey(reportId: ReportFixtures.reportId, workType: .harvest)
        cache.saveFeedback(
            try JSONEncoder().encode(ReportFixtures.feedbackList(feedbacks: [ReportFixtures.feedbackItem(workType: .harvest)])),
            reportId: key.reportId
        )
        let remote = StubReportRemoteDataSource(regenerateResults: [
            .success(ReportFixtures.feedbackItem(workType: .harvest, status: "PENDING")),
            .failure(.network(URLError(.notConnectedToInternet))),
        ])
        let repository = DefaultReportRepository(remote: remote, cache: cache)

        let regenerated = try await repository.regenerate(key)
        #expect(regenerated.workType == .harvest)
        #expect(regenerated.state == .pending)
        #expect(remote.regeneratedKeys == [key])

        await #expect(throws: APIError.self) {
            _ = try await repository.regenerate(key)
        }
        #expect(remote.regeneratedKeys == [key, key])
        withExtendedLifetime(container) {}
    }

    private func makeCache() throws -> (ReportCache, ModelContainer) {
        let schema = Schema(versionedSchema: SchemaV2.self)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )
        return (ReportCache(modelContext: container.mainContext), container)
    }

    private func decode<Value: Decodable>(_ type: Value.Type, _ cached: CachedReportData?) throws -> Value {
        try JSONDecoder().decode(type, from: #require(cached).data)
    }
}
