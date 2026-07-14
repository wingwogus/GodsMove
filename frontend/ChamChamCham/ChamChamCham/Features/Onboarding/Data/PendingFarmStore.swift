//
//  PendingFarmStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

actor PendingFarmStore {
    private struct State: Codable {
        var requestsByMember: [String: [SaveFarmRequestDTO]] = [:]
    }

    private let defaults: UserDefaults
    private let key = "onboarding.pending-extra-farms"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func replace(with requests: [SaveFarmRequestDTO], memberId: UUID) {
        var state = loadState()
        if requests.isEmpty {
            state.requestsByMember.removeValue(forKey: memberId.uuidString)
        } else {
            state.requestsByMember[memberId.uuidString] = requests
        }
        save(state)
    }

    func load() -> [SaveFarmRequestDTO] {
        loadState().requestsByMember.values.flatMap { $0 }
    }

    func load(memberId: UUID) -> [SaveFarmRequestDTO] {
        loadState().requestsByMember[memberId.uuidString] ?? []
    }

    func removeFirst(memberId: UUID) {
        var requests = load(memberId: memberId)
        guard !requests.isEmpty else { return }
        requests.removeFirst()
        replace(with: requests, memberId: memberId)
    }

    private func loadState() -> State {
        guard let data = defaults.data(forKey: key),
              let state = try? JSONDecoder().decode(State.self, from: data) else { return State() }
        return state
    }

    private func save(_ state: State) {
        guard !state.requestsByMember.isEmpty else {
            defaults.removeObject(forKey: key)
            return
        }
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: key)
    }
}
