//
//  SuggestionsResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// `GET /api/v1/search/suggestions` response. `keywords[0]` is the input keyword itself; any
/// remaining elements (up to 9) are related terms — the wire shape carries no per-item flag to
/// distinguish them, so the split is purely by array position.
struct SuggestionsResponseDTO: Decodable, Sendable {
    let keywords: [String]
}
