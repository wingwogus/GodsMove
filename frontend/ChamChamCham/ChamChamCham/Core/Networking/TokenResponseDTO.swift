//
//  TokenResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct TokenResponseDTO: Decodable, Sendable {
    let accessToken: String
    let refreshToken: String
}
