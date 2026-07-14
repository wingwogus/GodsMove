//
//  FarmListViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Observation

/// 프로필 수정 · 농업 정보 탭. Lists the member's registered farms (`GET /farms`) and supports a
/// delete-selection mode (`DELETE /farms/{id}`). Adding a farm is handled by `FarmAddView`.
@Observable
@MainActor
final class FarmListViewModel {
    private(set) var farms: [StandaloneFarmResponseDTO] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    var isDeleting = false
    private(set) var selectedForDeletion: Set<UUID> = []
    private(set) var isProcessingDelete = false

    @ObservationIgnored private let repository: any FarmRepository

    init(repository: any FarmRepository) {
        self.repository = repository
    }

    var count: Int { farms.count }

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            farms = try await repository.listFarms()
        } catch {
            errorMessage = "밭 목록을 불러오지 못했어요. 다시 시도해주세요."
        }
    }

    func toggleDeleteMode() {
        isDeleting.toggle()
        if !isDeleting { selectedForDeletion.removeAll() }
    }

    func isSelected(_ id: UUID) -> Bool { selectedForDeletion.contains(id) }

    func toggleSelection(_ id: UUID) {
        if selectedForDeletion.contains(id) {
            selectedForDeletion.remove(id)
        } else {
            selectedForDeletion.insert(id)
        }
    }

    func deleteSelected() async {
        guard !selectedForDeletion.isEmpty, !isProcessingDelete else { return }
        isProcessingDelete = true
        defer { isProcessingDelete = false }
        for id in selectedForDeletion {
            try? await repository.deleteFarm(id: id)
        }
        selectedForDeletion.removeAll()
        isDeleting = false
        await load()
    }
}
