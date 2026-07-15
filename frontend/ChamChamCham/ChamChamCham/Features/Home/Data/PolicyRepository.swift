//
//  PolicyRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

protocol PolicyRepository: Sendable {
    func fetchRecommendations(_ query: PolicyRecommendationQuery) async throws -> PolicyRecommendationPage

    /// Resolves the external link for a policy program (`applicationUrl`, falling back to `sourceUrl`).
    /// Returns `nil` when neither is present so the caller can show a "링크 없음" message instead of opening.
    func fetchExternalLink(programId: UUID) async throws -> URL?
}

struct RemotePolicyRepository: PolicyRepository {
    let apiClient: APIClient

    func fetchRecommendations(_ query: PolicyRecommendationQuery) async throws -> PolicyRecommendationPage {
        let dto: PolicyRecommendationPageResponseDTO = try await apiClient.send(PolicyEndpoint.recommendations(query))
        return dto.toDomain()
    }

    func fetchExternalLink(programId: UUID) async throws -> URL? {
        let dto: PolicyProgramLinkResponseDTO = try await apiClient.send(PolicyEndpoint.programLink(id: programId))
        let urlString = dto.applicationUrl?.isEmpty == false ? dto.applicationUrl : dto.sourceUrl
        return urlString.flatMap(URL.init(string:))
    }
}
