//
//  SearchDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

// `GET /api/v1/search` response. Each section reuses the SAME wire shape as its own domain's list
// endpoint (`RecordSummaryResponseDTO`/`PostSummaryResponseDTO`) — no duplicate DTOs needed for
// those two; only the policy section has a search-specific item shape (`SearchPolicyItemResponseDTO`).

struct SearchAllResponseDTO: Decodable, Sendable {
    let records: SearchRecordSectionResponseDTO
    let policies: SearchPolicySectionResponseDTO
    let posts: SearchPostSectionResponseDTO
}

struct SearchRecordSectionResponseDTO: Decodable, Sendable {
    let items: [RecordSummaryResponseDTO]
    let totalCount: Int
}

struct SearchPolicySectionResponseDTO: Decodable, Sendable {
    let items: [SearchPolicyItemResponseDTO]
    let totalCount: Int
}

struct SearchPostSectionResponseDTO: Decodable, Sendable {
    let items: [PostSummaryResponseDTO]
    let totalCount: Int
}

// MARK: - Domain mapping

extension SearchAllResponseDTO {
    func toDomain() -> SearchAllResult {
        SearchAllResult(
            records: SearchSection(items: records.items.map { $0.toDomain() }, totalCount: records.totalCount),
            policies: SearchSection(items: policies.items.map { $0.toDomain() }, totalCount: policies.totalCount),
            posts: SearchSection(items: posts.items.map { $0.toDomain() }, totalCount: posts.totalCount)
        )
    }
}
