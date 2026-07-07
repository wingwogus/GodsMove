//
//  APIEnvelope.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation

struct APIEnvelope<T: Decodable & Sendable>: Decodable, Sendable {
    let success: Bool
    let data: T?
    let error: APIErrorBody?
}

struct APIErrorBody: Decodable, Sendable {
    let code: String
    let message: String
}

/// Decodes successfully against any `ApiResponse<Unit>`-shaped body (e.g. `/logout`), where `data` is null.
struct EmptyDTO: Decodable, Sendable {}
