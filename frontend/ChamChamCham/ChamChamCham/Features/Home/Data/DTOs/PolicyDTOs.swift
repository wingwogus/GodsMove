//
//  PolicyDTOs.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

struct PolicyRecommendationItemResponseDTO: Decodable, Sendable {
    let recommendationId: UUID
    let policyProgramId: UUID
    let programTitle: String
    let agencyName: String
    let applicationPeriodLabel: String
    let eligibilitySummary: String
    let benefitSummary: String
    let reason: String
    let score: Double

    func toDomain() -> PolicyRecommendation {
        PolicyRecommendation(
            id: recommendationId,
            programId: policyProgramId,
            title: programTitle,
            agencyName: agencyName,
            applicationPeriodLabel: applicationPeriodLabel,
            eligibilitySummary: eligibilitySummary,
            benefitSummary: benefitSummary,
            reason: reason,
            score: score
        )
    }
}

struct PolicyRecommendationPageResponseDTO: Decodable, Sendable {
    let items: [PolicyRecommendationItemResponseDTO]
    let nextCursor: String?

    func toDomain() -> PolicyRecommendationPage {
        PolicyRecommendationPage(items: items.map { $0.toDomain() }, nextCursor: nextCursor)
    }
}

/// 정책 상세(`GET /policies/{policyProgramId}`)에서 외부 링크 이동에 필요한 필드만 디코딩한다.
/// 정책 상세는 네이티브 화면이 없고 이 값으로 시스템 브라우저를 여는 용도로만 쓴다(2026-07-14 확정).
struct PolicyProgramLinkResponseDTO: Decodable, Sendable {
    let id: UUID
    let applicationUrl: String?
    let sourceUrl: String?
}
