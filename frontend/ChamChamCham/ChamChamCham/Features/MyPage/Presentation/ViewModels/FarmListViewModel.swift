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

    /// Bottom `.appToast` message. Fixed copy per field per the plan — never composed per-field.
    var toastMessage: String?

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
        toastMessage = "농지 삭제 완료되었습니다."
    }

    // MARK: - Edit (rename / crops / location)

    /// Inline농지명 edit commit (`FarmCard` editing variant). No-op if unchanged or empty.
    func renameFarm(_ farm: StandaloneFarmResponseDTO, to newName: String) async {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != farm.name else { return }
        await update(farm, name: trimmed)
    }

    /// `CropPickerView` completion for an existing farm.
    func updateCrops(_ farm: StandaloneFarmResponseDTO, cropIds: [UUID]) async {
        await update(farm, cropIds: cropIds)
    }

    /// Address-edit completion (`FarmLocationPickerView`). Replaces every location-derived field with
    /// the freshly resolved `location` state. Returns whether the update succeeded, so the caller can
    /// decide whether to dismiss the picker.
    @discardableResult
    func updateLocation(_ farm: StandaloneFarmResponseDTO, location: FarmLocationViewModel) async -> Bool {
        await update(farm, location: location)
    }

    /// Rebuilds `SaveFarmRequestDTO` from an existing farm response and PUTs only the field(s) that
    /// changed, refreshing the list and surfacing the shared "농지 정보 수정 완료되었습니다." toast on success.
    @discardableResult
    private func update(
        _ farm: StandaloneFarmResponseDTO,
        name: String? = nil,
        cropIds: [UUID]? = nil,
        location: FarmLocationViewModel? = nil
    ) async -> Bool {
        guard let request = saveRequest(from: farm, name: name, cropIds: cropIds, location: location) else {
            errorMessage = "좌표 정보가 없는 밭이에요. 주소 수정을 먼저 진행해주세요."
            return false
        }
        do {
            _ = try await repository.updateFarm(id: farm.farmId, request)
            await load()
            toastMessage = "농지 정보 수정 완료되었습니다."
            return true
        } catch {
            errorMessage = "농지 정보를 수정하지 못했어요. 다시 시도해주세요."
            return false
        }
    }

    /// Reconstructs a `SaveFarmRequestDTO` from the farm's existing response fields, overwriting only
    /// the field(s) that actually changed (name / cropIds / location). Returns `nil` when neither the
    /// farm nor the supplied `location` has a resolved coordinate — legacy farms created without
    /// latitude/longitude can't be PUT until an address edit supplies one (see plan risk note).
    private func saveRequest(
        from farm: StandaloneFarmResponseDTO,
        name: String? = nil,
        cropIds: [UUID]? = nil,
        location: FarmLocationViewModel? = nil
    ) -> SaveFarmRequestDTO? {
        let latitude = location?.submissionCoordinate?.latitude ?? farm.latitude
        let longitude = location?.submissionCoordinate?.longitude ?? farm.longitude
        guard let latitude, let longitude else { return nil }

        // 도로명이 없는 농지는 지번을 roadAddress 자리에 채워 보낸다(백엔드 roadAddress non-null).
        let roadAddress: String? = location?.selectedAddress.map {
            $0.roadAddrPart1.isEmpty ? $0.jibunAddr : $0.roadAddrPart1
        }

        return SaveFarmRequestDTO(
            name: name ?? farm.name,
            roadAddress: roadAddress ?? farm.roadAddress,
            jibunAddress: location?.selectedAddress?.jibunAddr ?? farm.jibunAddress,
            latitude: latitude,
            longitude: longitude,
            pnu: location?.submissionPNU ?? farm.pnu,
            landCategory: location?.submissionLandCategory ?? farm.landCategory,
            areaSqm: location?.submissionAreaSqm ?? farm.areaSqm,
            areaIsManualEntry: location.map { $0.submissionAreaIsManualEntry } ?? farm.areaIsManualEntry,
            boundaryCoordinates: location?.submissionBoundaryCoordinates ?? farm.boundaryCoordinates,
            dataSource: location?.submissionDataSource ?? farm.dataSource,
            cropIds: cropIds ?? farm.crops.map(\.id)
        )
    }
}
