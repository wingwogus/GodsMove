//
//  ExtraCropBoardStore.swift
//  ChamChamCham
//
//  Created by iyungui on 7/18/26.
//

import Foundation

/// Persists the crop boards a member adds via the community "+ 작물 추가" picker — there's no
/// "follow board" endpoint yet (`/boards` only returns 온보딩 crops), so these live in `UserDefaults`
/// keyed by member, mirroring `PendingFarmStore`. Without this they reset every relaunch and every
/// other screen (profile, home) has no way to know about them.
actor ExtraCropBoardStore {
    private struct State: Codable {
        var boardsByMember: [String: [CommunityBoard]] = [:]
    }

    private let defaults: UserDefaults
    private let key = "community.extra-crop-boards"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load(memberId: UUID) -> [CommunityBoard] {
        loadState().boardsByMember[memberId.uuidString] ?? []
    }

    func replace(with boards: [CommunityBoard], memberId: UUID) {
        var state = loadState()
        if boards.isEmpty {
            state.boardsByMember.removeValue(forKey: memberId.uuidString)
        } else {
            state.boardsByMember[memberId.uuidString] = boards
        }
        save(state)
    }

    private func loadState() -> State {
        guard let data = defaults.data(forKey: key),
              let state = try? JSONDecoder().decode(State.self, from: data) else { return State() }
        return state
    }

    private func save(_ state: State) {
        guard !state.boardsByMember.isEmpty else {
            defaults.removeObject(forKey: key)
            return
        }
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: key)
    }
}
