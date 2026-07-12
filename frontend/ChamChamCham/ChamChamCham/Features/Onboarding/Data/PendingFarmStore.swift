//
//  PendingFarmStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

actor PendingFarmStore {
    private struct Batch: Codable {
        let memberId: UUID
        var requests: [SaveFarmRequestDTO]
    }

    private let defaults: UserDefaults
    private let key = "onboarding.pending-extra-farms"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func replace(with requests: [SaveFarmRequestDTO], memberId: UUID) {
        guard !requests.isEmpty else {
            defaults.removeObject(forKey: key)
            return
        }
        let batch = Batch(memberId: memberId, requests: requests)
        guard let data = try? JSONEncoder().encode(batch) else { return }
        defaults.set(data, forKey: key)
    }

    func load() -> [SaveFarmRequestDTO] {
        guard let data = defaults.data(forKey: key),
              let batch = try? JSONDecoder().decode(Batch.self, from: data) else {
            return []
        }
        return batch.requests
    }

    func load(memberId: UUID) -> [SaveFarmRequestDTO] {
        guard let batch = loadBatch(), batch.memberId == memberId else { return [] }
        return batch.requests
    }

    func removeFirst(memberId: UUID) {
        guard var batch = loadBatch(), batch.memberId == memberId, !batch.requests.isEmpty else { return }
        batch.requests.removeFirst()
        replace(with: batch.requests, memberId: memberId)
    }

    private func loadBatch() -> Batch? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(Batch.self, from: data)
    }
}
