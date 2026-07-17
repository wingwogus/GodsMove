//
//  FarmAddViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation
import Observation

/// 밭 추가 흐름. Reuses the onboarding-decoupled engine (`FarmLocationViewModel` for JUSO address
/// search + V-World geocoding/parcel lookup) and `CropCatalogService`, then creates the farm via
/// `POST /farms` (`SaveFarmRequestDTO`). Does not touch the onboarding views.
@Observable
@MainActor
final class FarmAddViewModel {
    var farmName = ""
    var selectedCrops: [Crop] = []
    var hasAttemptedSave = false
    private(set) var isSubmitting = false
    var errorMessage: String?

    /// The reusable location engine (address search / geocoding / parcel lookup).
    @ObservationIgnored let location = FarmLocationViewModel()

    @ObservationIgnored private let farmRepository: any FarmRepository
    @ObservationIgnored private let cropCatalog: any CropCatalogService

    init(farmRepository: any FarmRepository, cropCatalog: any CropCatalogService) {
        self.farmRepository = farmRepository
        self.cropCatalog = cropCatalog
    }

    func loadCrops() async -> [Crop] {
        (try? await cropCatalog.fetchCrops()) ?? []
    }

    func loadCategories() async -> [CropCategory] {
        (try? await cropCatalog.fetchCategories()) ?? []
    }

    func selectAddress(_ address: JusoAddress) async {
        await location.selectAddress(address)
    }

    var selectedAddressText: String? { location.selectedAddress?.roadAddrPart1 }

    var parcelSummary: String? {
        guard let parcel = location.selectedParcel else { return nil }
        return "\(parcel.jimokName) · 약 \(Int(parcel.areaSqm))㎡"
    }

    var nameError: String? {
        guard hasAttemptedSave else { return nil }
        return farmName.trimmingCharacters(in: .whitespaces).isEmpty ? "농지명을 입력해주세요." : nil
    }

    var canSave: Bool {
        !farmName.trimmingCharacters(in: .whitespaces).isEmpty
            && location.canProceed
            && !isSubmitting
    }

    func save() async -> Bool {
        hasAttemptedSave = true
        guard canSave,
              let address = location.selectedAddress,
              let coordinate = location.submissionCoordinate else { return false }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        let request = SaveFarmRequestDTO(
            name: farmName,
            roadAddress: address.roadAddrPart1,
            jibunAddress: address.jibunAddr.isEmpty ? nil : address.jibunAddr,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            pnu: location.submissionPNU,
            landCategory: location.submissionLandCategory,
            areaSqm: location.submissionAreaSqm,
            areaIsManualEntry: location.submissionAreaIsManualEntry,
            boundaryCoordinates: location.submissionBoundaryCoordinates,
            dataSource: location.submissionDataSource,
            cropIds: selectedCrops.map(\.id)
        )
        do {
            _ = try await farmRepository.createFarm(request)
            return true
        } catch {
            errorMessage = "밭 추가에 실패했어요. 다시 시도해주세요."
            return false
        }
    }
}
