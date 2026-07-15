//
//  RecordRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import Foundation

/// The single async entry point the record presentation layer talks to. Returns domain types (not DTOs),
/// mirroring `CommunityRepository`. List + detail + create are wired; update/delete land with the
/// (not-yet-captured) 수정/삭제 flow.
protocol RecordRepository: Sendable {
    func fetchRecords(_ query: RecordQuery) async throws -> RecordPage
    func fetchDetail(id: UUID) async throws -> RecordDetail
    func deleteRecord(id: UUID) async throws
    func fetchActiveCrops() async throws -> [ActiveCrop]
    func fetchFarmCrops() async throws -> [FarmWithCrops]
    func fetchWeather(farmId: UUID) async throws -> CurrentWeather
    func searchPesticides(keyword: String?) async throws -> [Pesticide]
    func fetchPests(pesticideId: UUID) async throws -> [Pest]

    @discardableResult
    func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID
}

struct RemoteRecordRepository: RecordRepository {
    let apiClient: APIClient

    func fetchRecords(_ query: RecordQuery) async throws -> RecordPage {
        let dto: RecordPageResponseDTO = try await apiClient.send(RecordEndpoint.listRecords(query))
        return dto.toDomain()
    }

    func fetchDetail(id: UUID) async throws -> RecordDetail {
        let dto: RecordDetailResponseDTO = try await apiClient.send(RecordEndpoint.recordDetail(id: id))
        return dto.toDomain()
    }

    func deleteRecord(id: UUID) async throws {
        _ = try await apiClient.send(RecordEndpoint.deleteRecord(id: id)) as EmptyDTO
    }

    func fetchActiveCrops() async throws -> [ActiveCrop] {
        // Flatten crops across farms, de-duplicating by crop id and preserving first-seen order.
        var seen = Set<UUID>()
        var crops: [ActiveCrop] = []
        for crop in try await fetchFarmCrops().flatMap(\.crops) where seen.insert(crop.id).inserted {
            crops.append(crop)
        }
        return crops
    }

    func fetchFarmCrops() async throws -> [FarmWithCrops] {
        let farms: [FarmCropsResponseDTO] = try await apiClient.send(RecordEndpoint.activeCrops)
        return farms.map { $0.toFarmWithCrops() }
    }

    func fetchWeather(farmId: UUID) async throws -> CurrentWeather {
        let dto: CurrentWeatherResponseDTO = try await apiClient.send(RecordEndpoint.farmWeather(farmId: farmId))
        return dto.toDomain()
    }

    func searchPesticides(keyword: String?) async throws -> [Pesticide] {
        let dto: PesticidePageResponseDTO = try await apiClient.send(
            RecordEndpoint.pesticides(keyword: keyword, cursor: nil, size: 20)
        )
        return dto.items.map { $0.toDomain() }
    }

    func fetchPests(pesticideId: UUID) async throws -> [Pest] {
        let dtos: [PestSummaryResponseDTO] = try await apiClient.send(RecordEndpoint.pests(pesticideId: pesticideId))
        return dtos.map { $0.toDomain() }
    }

    @discardableResult
    func createRecord(_ request: SaveRecordRequestDTO) async throws -> UUID {
        let response: RecordIdResponseDTO = try await apiClient.send(RecordEndpoint.createRecord(request))
        return response.id
    }
}
