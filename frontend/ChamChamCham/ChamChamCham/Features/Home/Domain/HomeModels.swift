//
//  HomeModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// 정책 혜택 카테고리. Figma `홈 -> 정책 리스트` 캡처(2026-07-14)에서 확인된 10종.
/// rawValue는 백엔드 `PolicyBenefitCategory` enum 상수명(`fromKey`가 `.name`으로 매칭,
/// backend/application/.../policy/support/PolicyBenefitCategory.kt)과 정확히 일치해야 하며,
/// 한글 라벨을 그대로 보내면 `INVALID_INPUT` 400을 던진다 — 표시용 라벨은 `displayName`으로 분리.
/// 참고: docs/figma/home/2026-07-14-home-backend-conflicts.md (C-4)
enum PolicyCategory: String, Sendable, Hashable, CaseIterable, Identifiable {
    case subsidy = "GRANT"
    case financing = "FINANCE"
    case facility = "FACILITY_EQUIPMENT"
    case education = "EDUCATION"
    case welfare = "WELFARE"
    case certification = "CERTIFICATION"
    case marketAccess = "MARKET"
    case startup = "STARTUP"
    case environment = "ENVIRONMENT_INFRA"
    case etc = "ETC"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .subsidy: "지원금"
        case .financing: "융자·금융"
        case .facility: "시설·장비"
        case .education: "교육"
        case .welfare: "복지"
        case .certification: "인증"
        case .marketAccess: "판로"
        case .startup: "창업"
        case .environment: "환경·인프라"
        case .etc: "기타"
        }
    }
}

/// 정책 리스트 정렬. 백엔드 `PolicyRecommendationSort`(domain/policy/PolicyRecommendationSort.kt)를
/// 그대로 미러링 — `RECOMMENDED`/`LATEST` 두 값만 존재하고 기본값은 `RECOMMENDED`.
enum PolicySort: String, Sendable, Hashable, CaseIterable {
    case recommended = "RECOMMENDED"
    case latest = "LATEST"
}

/// 오늘의 추천 정책 / 정책 리스트 row. `GET /policies/recommendations`의 projection.
/// D-day 배지는 구조화된 마감일이 없어 표시하지 않고 `applicationPeriodLabel`을 그대로 노출
/// (2026-07-14 확정). 참고: docs/figma/home/2026-07-14-home-backend-conflicts.md (C-3)
struct PolicyRecommendation: Identifiable, Hashable, Sendable {
    let id: UUID
    let programId: UUID
    let title: String
    let agencyName: String
    let applicationPeriodLabel: String
    /// 정책 리스트 row의 "대상자" 열.
    let eligibilitySummary: String
    /// 정책 리스트 row의 "지원내용" 열.
    let benefitSummary: String
    let reason: String
    let score: Double
}

/// One cursor page of policy recommendations. `nextCursor == nil` means there are no more pages.
struct PolicyRecommendationPage: Sendable {
    let items: [PolicyRecommendation]
    let nextCursor: String?
}

