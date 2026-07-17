//
//  SearchModels.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

/// The 4 result tabs (Figma `tabs`: 전체/나의 일지/정책 정보/게시글). Raw value doubles as
/// `AppTabBar`'s `selection` index.
enum SearchCategory: Int, CaseIterable, Sendable {
    case all = 0
    case records
    case policies
    case posts
}

/// A row in the 정책 정보 search tab. Distinct from `PolicyRecommendation` (Home's recommendation
/// feed) — no `reason`/`score`, and carries `sourceUrl` inline so a tap can open it directly.
struct SearchPolicyItem: Identifiable, Hashable, Sendable {
    let id: UUID
    let title: String
    let agencyName: String
    let eligibilitySummary: String
    let benefitSummary: String
    let applicationPeriodLabel: String
    let sourceUrl: URL?
}

/// One category's preview inside the "전체" tab result — up to 3 items plus the real total, which
/// drives the section header's count and its "더보기" affordance (`items.count < totalCount`).
struct SearchSection<Item: Sendable>: Sendable {
    let items: [Item]
    let totalCount: Int

    var hasMore: Bool { items.count < totalCount }
}

/// `GET /api/v1/search` response — one preview section per category.
struct SearchAllResult: Sendable {
    let records: SearchSection<FarmingRecordSummary>
    let policies: SearchSection<SearchPolicyItem>
    let posts: SearchSection<CommunityPostSummary>
}

/// One cursor page of a single category's search results, each carrying the category's real total
/// (used for the "총 N개" header — never the placeholder text Figma showed).
struct SearchRecordPage: Sendable {
    let items: [FarmingRecordSummary]
    let nextCursor: String?
    let totalCount: Int
}

struct SearchPostPage: Sendable {
    let items: [CommunityPostSummary]
    let nextCursor: String?
    let totalCount: Int
}

struct SearchPolicyPage: Sendable {
    let items: [SearchPolicyItem]
    let nextCursor: String?
    let totalCount: Int
}

/// A previously-submitted search term, persisted locally (no server sync — `RecentSearchStore`).
struct RecentSearchTerm: Identifiable, Hashable, Sendable {
    let id: UUID
    let keyword: String
    let searchedAt: Date
}
