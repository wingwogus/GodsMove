//
//  RecentSearchTermRecord.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

/// Local-only "최근 검색어" persistence — no server sync exists for search history, so this is the
/// source of truth (not a cache in front of a network read, unlike `CachedMemberProfile`).
@Model
final class RecentSearchTermRecord {
    @Attribute(.unique) var id: UUID
    var keyword: String
    var searchedAt: Date

    init(id: UUID = UUID(), keyword: String, searchedAt: Date) {
        self.id = id
        self.keyword = keyword
        self.searchedAt = searchedAt
    }
}
