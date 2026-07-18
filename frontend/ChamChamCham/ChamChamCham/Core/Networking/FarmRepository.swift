//
//  FarmRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import Foundation

protocol FarmRepository: Sendable {
    func listFarms() async throws -> [StandaloneFarmResponseDTO]
    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO
    func updateFarm(id: UUID, _ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO
    func deleteFarm(id: UUID) async throws
}

struct RemoteFarmRepository: FarmRepository {
    let apiClient: APIClient

    func listFarms() async throws -> [StandaloneFarmResponseDTO] {
        try await apiClient.send(FarmEndpoint.list)
    }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        try await apiClient.send(FarmEndpoint.create(request))
    }

    func updateFarm(id: UUID, _ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        try await apiClient.send(FarmEndpoint.update(id, request))
    }

    func deleteFarm(id: UUID) async throws {
        _ = try await apiClient.send(FarmEndpoint.delete(id)) as EmptyDTO
    }
}
