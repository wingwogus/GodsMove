//
//  PendingFarmSyncServiceTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/12/26.
//

import Foundation
import Testing
@testable import ChamChamCham

@Suite("Pending farm sync")
struct PendingFarmSyncServiceTests {
    @Test("sync removes successful requests in FIFO order")
    func successfulFIFO() async throws {
        let memberId = UUID()
        let defaults = isolatedDefaults()
        let store = PendingFarmStore(defaults: defaults)
        let repository = RecordingFarmRepository()
        let requests = try farmRequests(count: 2)
        let service = PendingFarmSyncService(store: store, repository: repository)

        await service.enqueue(requests, memberId: memberId)
        await service.syncPending(memberId: memberId)

        let createdNames = await repository.createdNames
        let pending = await store.load()
        #expect(createdNames == requests.map(\.name))
        #expect(pending.isEmpty)
    }

    @Test("sync keeps failed and unattempted requests")
    func partialFailure() async throws {
        let memberId = UUID()
        let defaults = isolatedDefaults()
        let store = PendingFarmStore(defaults: defaults)
        let repository = RecordingFarmRepository(failAtCall: 2)
        let requests = try farmRequests(count: 3)
        let service = PendingFarmSyncService(store: store, repository: repository)

        await service.enqueue(requests, memberId: memberId)
        await service.syncPending(memberId: memberId)

        let createdNames = await repository.createdNames
        let pendingNames = await store.load().map(\.name)
        #expect(createdNames == Array(requests.prefix(2)).map(\.name))
        #expect(pendingNames == Array(requests.dropFirst()).map(\.name))
    }

    @Test("a later retry drains the retained queue without replaying success")
    func retryAfterFailure() async throws {
        let memberId = UUID()
        let defaults = isolatedDefaults()
        let store = PendingFarmStore(defaults: defaults)
        let failingRepository = RecordingFarmRepository(failAtCall: 2)
        let requests = try farmRequests(count: 3)
        let firstService = PendingFarmSyncService(store: store, repository: failingRepository)

        await firstService.enqueue(requests, memberId: memberId)
        await firstService.syncPending(memberId: memberId)

        let retryRepository = RecordingFarmRepository()
        let retryService = PendingFarmSyncService(store: store, repository: retryRepository)
        await retryService.syncPending(memberId: memberId)

        let retriedNames = await retryRepository.createdNames
        let pending = await store.load()
        #expect(retriedNames == Array(requests.dropFirst()).map(\.name))
        #expect(pending.isEmpty)
    }

    @Test("pending farms never sync into a different member account")
    func isolatesQueueByMember() async throws {
        let ownerMemberId = UUID()
        let otherMemberId = UUID()
        let store = PendingFarmStore(defaults: isolatedDefaults())
        let repository = RecordingFarmRepository()
        let requests = try farmRequests(count: 2)
        let service = PendingFarmSyncService(store: store, repository: repository)

        await service.enqueue(requests, memberId: ownerMemberId)
        await service.syncPending(memberId: otherMemberId)

        let wrongMemberCreates = await repository.createdNames
        let retained = await store.load()
        #expect(wrongMemberCreates.isEmpty)
        #expect(retained == requests)

        await service.syncPending(memberId: ownerMemberId)

        let ownerCreates = await repository.createdNames
        let pending = await store.load()
        #expect(ownerCreates == requests.map(\.name))
        #expect(pending.isEmpty)
    }

    private func isolatedDefaults() -> UserDefaults {
        UserDefaults(suiteName: "pending-farms-\(UUID().uuidString)")!
    }

    private func farmRequests(count: Int) throws -> [SaveFarmRequestDTO] {
        try (1...count).map { index in
            var farm = OnboardingTestFactory.validDraft().representativeFarm
            farm.farmName = "추가농장\(index)"
            return try SaveFarmRequestDTO(farm: farm)
        }
    }
}

private actor RecordingFarmRepository: FarmRepository {
    private(set) var createdNames: [String] = []
    private var callCount = 0
    private let failAtCall: Int?

    init(failAtCall: Int? = nil) {
        self.failAtCall = failAtCall
    }

    func listFarms() async throws -> [StandaloneFarmResponseDTO] { [] }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        callCount += 1
        createdNames.append(request.name)
        if callCount == failAtCall { throw FakeUploadError() }
        return StandaloneFarmResponseDTO(
            farmId: UUID(),
            name: request.name,
            roadAddress: request.roadAddress,
            jibunAddress: request.jibunAddress,
            latitude: request.latitude,
            longitude: request.longitude,
            pnu: request.pnu,
            landCategory: request.landCategory,
            areaSqm: request.areaSqm,
            areaIsManualEntry: request.areaIsManualEntry,
            boundaryCoordinates: request.boundaryCoordinates,
            dataSource: request.dataSource,
            crops: []
        )
    }
}
