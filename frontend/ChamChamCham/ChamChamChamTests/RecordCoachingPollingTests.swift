//
//  RecordCoachingPollingTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@MainActor
@Suite("RecordDetailViewModel coaching polling")
struct RecordCoachingPollingTests {

    private func makeViewModel(
        coaching: [RecordCoaching],
        maxAttempts: Int
    ) -> (RecordDetailViewModel, StubCoachingRecordRepository) {
        let repository = StubCoachingRecordRepository(coachingScript: coaching)
        let viewModel = RecordDetailViewModel(
            recordId: UUID(),
            repository: repository,
            coachingPollInterval: .zero,
            coachingMaxAttempts: maxAttempts
        )
        return (viewModel, repository)
    }

    @Test("polls through notFound/pending until READY, then shows feedback")
    func pollsUntilReady() async {
        let feedback = CoachingFeedback(
            goodPoint: "물 주기 타이밍이 좋았어요.",
            nextActions: [CoachingNextAction(text: "이틀 뒤 잎 상태를 확인하세요.", due: .thisWeek)]
        )
        let (viewModel, repository) = makeViewModel(
            coaching: [
                RecordCoaching(status: .notFound, feedback: nil),
                RecordCoaching(status: .pending, feedback: nil),
                RecordCoaching(status: .ready, feedback: feedback)
            ],
            maxAttempts: 5
        )

        await viewModel.loadCoaching()

        guard case let .ready(shown) = viewModel.coachingState else {
            Issue.record("expected .ready, got \(viewModel.coachingState)")
            return
        }
        #expect(shown == feedback)
        #expect(await repository.coachingCallCount == 3)
    }

    @Test("stays in preparing when generation never completes within the attempt budget")
    func staysPreparingWhenAlwaysPending() async {
        let (viewModel, repository) = makeViewModel(
            coaching: [RecordCoaching(status: .pending, feedback: nil)],
            maxAttempts: 3
        )

        await viewModel.loadCoaching()

        guard case .preparing = viewModel.coachingState else {
            Issue.record("expected .preparing, got \(viewModel.coachingState)")
            return
        }
        #expect(await repository.coachingCallCount == 3)
    }

    @Test("FAILED status surfaces the unavailable state and stops polling")
    func failedBecomesUnavailable() async {
        let (viewModel, repository) = makeViewModel(
            coaching: [RecordCoaching(status: .failed, feedback: nil)],
            maxAttempts: 5
        )

        await viewModel.loadCoaching()

        guard case .unavailable = viewModel.coachingState else {
            Issue.record("expected .unavailable, got \(viewModel.coachingState)")
            return
        }
        #expect(await repository.coachingCallCount == 1)
    }

    @Test("loadCoaching runs at most once per instance")
    func loadCoachingRunsOnce() async {
        let feedback = CoachingFeedback(goodPoint: "좋아요.", nextActions: [])
        let (viewModel, repository) = makeViewModel(
            coaching: [RecordCoaching(status: .ready, feedback: feedback)],
            maxAttempts: 5
        )

        await viewModel.loadCoaching()
        await viewModel.loadCoaching() // guarded no-op

        #expect(await repository.coachingCallCount == 1)
    }
}

/// Minimal `RecordRepository` stub for coaching-poll tests: serves a scripted `RecordCoaching` sequence
/// (repeating the last element once exhausted) and counts calls. Every other method is unused here.
private actor StubCoachingRecordRepository: RecordRepository {
    private let coachingScript: [RecordCoaching]
    private var coachingIndex = 0
    private(set) var coachingCallCount = 0

    init(coachingScript: [RecordCoaching]) {
        self.coachingScript = coachingScript
    }

    func fetchCoaching(id: UUID) async throws -> RecordCoaching {
        coachingCallCount += 1
        let value = coachingScript[min(coachingIndex, coachingScript.count - 1)]
        coachingIndex += 1
        return value
    }

    func fetchRecords(_ query: RecordQuery) async throws -> RecordPage { throw StubCoachingError.unused }
    func fetchDetail(id: UUID) async throws -> RecordDetail { throw StubCoachingError.unused }
    func deleteRecord(id: UUID) async throws { throw StubCoachingError.unused }
    func fetchActiveCrops() async throws -> [ActiveCrop] { throw StubCoachingError.unused }
    func fetchFarmCrops() async throws -> [FarmWithCrops] { throw StubCoachingError.unused }
    func fetchWeather(farmId: UUID) async throws -> CurrentWeather { throw StubCoachingError.unused }
    func searchPesticides(keyword: String?) async throws -> [Pesticide] { throw StubCoachingError.unused }
    func fetchPests(pesticideId: UUID) async throws -> [Pest] { throw StubCoachingError.unused }
    func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID { throw StubCoachingError.unused }
}

private enum StubCoachingError: Error { case unused }
