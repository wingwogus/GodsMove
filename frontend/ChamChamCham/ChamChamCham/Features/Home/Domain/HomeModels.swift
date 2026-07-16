//
//  HomeModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import Foundation

/// 정책 혜택 카테고리. Figma `홈 -> 정책 리스트` 캡처(2026-07-14)에서 확인된 10종.
/// 백엔드 `benefitCategory`는 자유 문자열 쿼리 파라미터라 이 값들이 실제로 허용되는지 스키마로
/// 검증되지 않음 — 2026-07-14 사용자 확정으로 백엔드 확인 없이 우선 진행한다. 불일치 시 해당
/// 카테고리는 빈 목록으로만 보이고 에러가 되지 않아야 한다.
/// 참고: docs/figma/home/2026-07-14-home-backend-conflicts.md (C-4)
enum PolicyCategory: String, Sendable, Hashable, CaseIterable, Identifiable {
    case subsidy = "지원금"
    case financing = "융자·금융"
    case facility = "시설·장비"
    case education = "교육"
    case welfare = "복지"
    case certification = "인증"
    case marketAccess = "판로"
    case startup = "창업"
    case environment = "환경·인프라"
    case etc = "기타"

    var id: String { rawValue }
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

