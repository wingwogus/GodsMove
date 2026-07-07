//
//  CropCatalogService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

protocol CropCatalogService: Sendable {
    func fetchCrops() async throws -> [Crop]
    func fetchCategoryLabels() async throws -> [String]
}

struct RemoteCropCatalogService: CropCatalogService {
    let apiClient: APIClient

    func fetchCrops() async throws -> [Crop] {
        let dtos: [CropResponseDTO] = try await apiClient.send(CropEndpoint.list)
        return dtos.map { Crop(id: $0.id, name: $0.name, category: $0.usePartCategoryLabel) }
    }

    func fetchCategoryLabels() async throws -> [String] {
        let dtos: [CategoryResponseDTO] = try await apiClient.send(CropEndpoint.categories)
        return dtos.map(\.label)
    }
}
