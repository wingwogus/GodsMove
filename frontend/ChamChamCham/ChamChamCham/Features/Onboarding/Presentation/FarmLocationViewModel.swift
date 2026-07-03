//
//  FarmLocationViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import CoreLocation
import Observation

@Observable
@MainActor
final class FarmLocationViewModel {
    enum LookupState: Equatable {
        case idle
        case resolvingCoordinate
        case loadingParcel
        case parcelNotFound
        case loaded
        case failed(String)
    }

    var isSearching = false
    var searchResults: [JusoAddress] = []
    var selectedAddress: JusoAddress?
    var resolvedCoordinate: GeoPoint?
    var selectedParcel: FarmlandParcel?
    var manualAreaText: String = ""
    var lookupState: LookupState = .idle

    var canProceed: Bool {
        guard selectedAddress != nil else { return false }
        return selectedParcel != nil || !manualAreaText.trimmingCharacters(in: .whitespaces).isEmpty
    }

    private let addressSearch: any AddressSearching
    private let vworld: any FarmlandGeocoding & ParcelLookup
    private let landCharacteristics: any LandCharacteristicsLookup

    init(
        addressSearch: any AddressSearching = JusoAPIService(),
        vworld: any FarmlandGeocoding & ParcelLookup = VWorldAPIService(),
        landCharacteristics: any LandCharacteristicsLookup = LandCharacterAPIService()
    ) {
        self.addressSearch = addressSearch
        self.vworld = vworld
        self.landCharacteristics = landCharacteristics
    }

    func search(keyword: String) async {
        guard !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            searchResults = []
            return
        }
        isSearching = true
        defer { isSearching = false }
        do {
            searchResults = try await addressSearch.search(keyword: keyword)
        } catch {
            searchResults = []
            lookupState = .failed("주소 검색 중 오류가 발생했어요")
        }
    }

    func selectAddress(_ address: JusoAddress) async {
        selectedAddress = address
        selectedParcel = nil
        manualAreaText = ""
        resolvedCoordinate = nil
        await resolveCoordinate(for: address)
    }

    func handleMapTap(at coordinate: CLLocationCoordinate2D) async {
        await fetchParcel(at: coordinate)
    }

    func retry() async {
        lookupState = .idle
        if let selectedAddress, resolvedCoordinate == nil {
            await resolveCoordinate(for: selectedAddress)
        } else if let coordinate = resolvedCoordinate, selectedParcel == nil {
            await fetchParcel(at: coordinate.clLocationCoordinate)
        }
    }

    private func resolveCoordinate(for address: JusoAddress) async {
        lookupState = .resolvingCoordinate
        do {
            let coordinate = try await vworld.geocode(roadAddress: address.roadAddrPart1)
            resolvedCoordinate = GeoPoint(coordinate)
            await fetchParcel(at: coordinate)
        } catch {
            lookupState = .failed("주소의 좌표를 확인하지 못했어요")
        }
    }

    private func fetchParcel(at coordinate: CLLocationCoordinate2D) async {
        lookupState = .loadingParcel
        do {
            let parcel = try await vworld.fetchParcel(at: coordinate)
            selectedParcel = parcel
            lookupState = .loaded
            await fetchOfficialArea(pnu: parcel.pnu)
        } catch FarmLocationAPIError.noParcelFound {
            selectedParcel = nil
            lookupState = .parcelNotFound
        } catch {
            selectedParcel = nil
            lookupState = .failed("지적도 정보를 불러오지 못했어요")
        }
    }

    private func fetchOfficialArea(pnu: String) async {
        guard let info = try? await landCharacteristics.fetchLandCharacteristics(pnu: pnu) else { return }
        guard var parcel = selectedParcel, parcel.pnu == pnu else { return }
        parcel.areaSqm = info.officialAreaSqm
        parcel.jimok = info.jimokName
        selectedParcel = parcel
    }
}
