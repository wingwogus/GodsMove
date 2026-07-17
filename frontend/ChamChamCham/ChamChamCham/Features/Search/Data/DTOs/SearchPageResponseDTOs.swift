//
//  SearchPageResponseDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

// `GET /api/v1/search/{records,posts,policies}` responses — cursor-paged, each with a `totalCount`
// that drives the "총 N개" header.

struct SearchRecordPageResponseDTO: Decodable, Sendable {
    let items: [RecordSummaryResponseDTO]
    let nextCursor: String?
    let totalCount: Int
}

struct SearchPostPageResponseDTO: Decodable, Sendable {
    let items: [PostSummaryResponseDTO]
    let nextCursor: String?
    let totalCount: Int
}

struct SearchPolicyPageResponseDTO: Decodable, Sendable {
    let items: [SearchPolicyItemResponseDTO]
    let nextCursor: String?
    let totalCount: Int
}

// MARK: - Domain mapping

extension SearchRecordPageResponseDTO {
    func toDomain() -> SearchRecordPage {
        SearchRecordPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor, totalCount: totalCount)
    }
}

extension SearchPostPageResponseDTO {
    func toDomain() -> SearchPostPage {
        SearchPostPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor, totalCount: totalCount)
    }
}

extension SearchPolicyPageResponseDTO {
    func toDomain() -> SearchPolicyPage {
        SearchPolicyPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor, totalCount: totalCount)
    }
}
