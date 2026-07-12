//
//  CropCatalogService.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

protocol CropCatalogService: Sendable {
    func fetchCrops() async throws -> [Crop]
    func fetchCrops(categoryCode: String) async throws -> [Crop]
    func fetchCategories() async throws -> [CropCategory]
}

struct RemoteCropCatalogService: CropCatalogService {
    let apiClient: APIClient

    func fetchCrops() async throws -> [Crop] {
        let dtos: [CropResponseDTO] = try await apiClient.send(CropEndpoint.list)
        return dtos.map(Self.crop)
    }

    func fetchCrops(categoryCode: String) async throws -> [Crop] {
        let dtos: [CropResponseDTO] = try await apiClient.send(CropEndpoint.categoryCrops(categoryCode))
        return dtos.map(Self.crop)
    }

    func fetchCategories() async throws -> [CropCategory] {
        let dtos: [CategoryResponseDTO] = try await apiClient.send(CropEndpoint.categories)
        return dtos.map { CropCategory(code: $0.code, label: Self.displayLabel(code: $0.code, fallback: $0.label)) }
    }

    private static func crop(_ dto: CropResponseDTO) -> Crop {
        Crop(
            id: dto.id,
            name: dto.name,
            categoryCode: dto.usePartCategory,
            categoryLabel: displayLabel(code: dto.usePartCategory, fallback: dto.usePartCategoryLabel)
        )
    }

    private static func displayLabel(code: String, fallback: String) -> String {
        switch code {
        case "FRUIT":
            "열매·과실"
        case "STEM_BRANCH":
            "줄기·가지"
        default:
            fallback
        }
    }
}
