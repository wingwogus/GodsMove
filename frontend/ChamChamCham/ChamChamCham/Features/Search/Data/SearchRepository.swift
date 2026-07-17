//
//  SearchRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

protocol SearchRepository: Sendable {
    func searchAll(keyword: String?) async throws -> SearchAllResult
    func searchRecords(keyword: String?, cursor: String?, size: Int) async throws -> SearchRecordPage
    func searchPosts(keyword: String?, cursor: String?, size: Int) async throws -> SearchPostPage
    func searchPolicies(keyword: String?, cursor: String?, size: Int) async throws -> SearchPolicyPage
    func suggestions(keyword: String?) async throws -> [String]
}

struct RemoteSearchRepository: SearchRepository {
    let apiClient: APIClient

    func searchAll(keyword: String?) async throws -> SearchAllResult {
        let dto: SearchAllResponseDTO = try await apiClient.send(SearchEndpoint.all(keyword: keyword))
        return dto.toDomain()
    }

    func searchRecords(keyword: String?, cursor: String?, size: Int) async throws -> SearchRecordPage {
        let dto: SearchRecordPageResponseDTO = try await apiClient.send(
            SearchEndpoint.records(keyword: keyword, cursor: cursor, size: size)
        )
        return dto.toDomain()
    }

    func searchPosts(keyword: String?, cursor: String?, size: Int) async throws -> SearchPostPage {
        let dto: SearchPostPageResponseDTO = try await apiClient.send(
            SearchEndpoint.posts(keyword: keyword, cursor: cursor, size: size)
        )
        return dto.toDomain()
    }

    func searchPolicies(keyword: String?, cursor: String?, size: Int) async throws -> SearchPolicyPage {
        let dto: SearchPolicyPageResponseDTO = try await apiClient.send(
            SearchEndpoint.policies(keyword: keyword, cursor: cursor, size: size)
        )
        return dto.toDomain()
    }

    func suggestions(keyword: String?) async throws -> [String] {
        let dto: SuggestionsResponseDTO = try await apiClient.send(SearchEndpoint.suggestions(keyword: keyword))
        return dto.keywords
    }
}
