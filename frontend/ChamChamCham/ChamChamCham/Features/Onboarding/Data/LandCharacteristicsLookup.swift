//
//  LandCharacteristicsLookup.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

protocol LandCharacteristicsLookup: Sendable {
    func fetchLandCharacteristics(pnu: String) async throws -> LandCharacteristics
}
