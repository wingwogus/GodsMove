//
//  FarmLocationAPIError.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

enum FarmLocationAPIError: Error, Sendable {
    case invalidURL
    case network(String)
    case decoding(String)
    case api(code: String, message: String)
    case noResult
    case noParcelFound
    case noData
}
