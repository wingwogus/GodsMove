//
//  SearchPolicyItemResponseDTO.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// `SearchPolicyItemResponse` — distinct from `PolicyRecommendationItemResponseDTO` (Home's
/// recommendation feed): no `recommendationId`/`reason`/`score`, and it carries `sourceUrl` inline
/// so a search result row can open the link directly without a separate `fetchExternalLink` call.
/// The wire schema also has `applyStartsOn`/`applyEndsOn`/`createdAt`, but nothing in the search UI
/// consumes them (`AppListItem.xlarge` only needs `applicationPeriodLabel`), so they're left
/// undeclared — `Decodable` ignores JSON keys with no matching property.
struct SearchPolicyItemResponseDTO: Decodable, Sendable {
    let id: UUID
    let title: String
    let agencyName: String
    let eligibilitySummary: String
    let benefitSummary: String
    let applicationPeriodLabel: String
    let sourceUrl: String?
}

extension SearchPolicyItemResponseDTO {
    func toDomain() -> SearchPolicyItem {
        SearchPolicyItem(
            id: id,
            title: title,
            agencyName: agencyName,
            eligibilitySummary: eligibilitySummary,
            benefitSummary: benefitSummary,
            applicationPeriodLabel: applicationPeriodLabel,
            sourceUrl: sourceUrl.flatMap(URL.init(string:))
        )
    }
}
