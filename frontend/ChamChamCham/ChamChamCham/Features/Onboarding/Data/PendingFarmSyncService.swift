//
//  PendingFarmSyncService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

actor PendingFarmSyncService {
    private let store: PendingFarmStore
    private let repository: any FarmRepository
    private var isSyncing = false

    init(store: PendingFarmStore, repository: any FarmRepository) {
        self.store = store
        self.repository = repository
    }

    func enqueue(_ requests: [SaveFarmRequestDTO], memberId: UUID) async {
        await store.replace(with: requests, memberId: memberId)
    }

    func syncPending(memberId: UUID) async {
        guard !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }

        while let request = await store.load(memberId: memberId).first {
            do {
                _ = try await repository.createFarm(request)
                await store.removeFirst(memberId: memberId)
            } catch {
                return
            }
        }
    }
}
