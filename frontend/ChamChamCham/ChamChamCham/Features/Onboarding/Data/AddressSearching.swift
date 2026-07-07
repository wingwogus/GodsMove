//
//  AddressSearching.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

protocol AddressSearching: Sendable {
    func search(keyword: String) async throws -> [JusoAddress]
}
