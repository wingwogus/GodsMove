//
//  CategoryResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct CategoryResponseDTO: Decodable, Sendable {
    let code: String
    let label: String
}
