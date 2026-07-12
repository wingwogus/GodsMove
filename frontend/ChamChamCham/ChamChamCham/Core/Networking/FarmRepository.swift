//
//  FarmRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

protocol FarmRepository: Sendable {
    func listFarms() async throws -> [StandaloneFarmResponseDTO]
    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO
}

struct RemoteFarmRepository: FarmRepository {
    let apiClient: APIClient

    func listFarms() async throws -> [StandaloneFarmResponseDTO] {
        try await apiClient.send(FarmEndpoint.list)
    }

    func createFarm(_ request: SaveFarmRequestDTO) async throws -> StandaloneFarmResponseDTO {
        try await apiClient.send(FarmEndpoint.create(request))
    }
}
