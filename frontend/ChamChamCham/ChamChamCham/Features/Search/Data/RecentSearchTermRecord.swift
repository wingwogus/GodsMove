//
//  RecentSearchTermRecord.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

extension SchemaV3 {
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
}

/// App-facing alias for the latest frozen version of this model. The persisted shape lives inside the
/// versioned schema so a future breaking change adds `SchemaV4.RecentSearchTermRecord` while `SchemaV3`'s copy
/// stays frozen — an existing store keeps matching `SchemaV3` and migrates forward instead of being orphaned.
typealias RecentSearchTermRecord = SchemaV3.RecentSearchTermRecord
