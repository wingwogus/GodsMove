//
//  CachedReportPayload.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

extension SchemaV3 {
    @Model
    final class CachedReportPayload {
        @Attribute(.unique) var key: String
        var kindRaw: String
        var payload: Data
        var updatedAt: Date

        init(key: String, kindRaw: String, payload: Data, updatedAt: Date) {
            self.key = key
            self.kindRaw = kindRaw
            self.payload = payload
            self.updatedAt = updatedAt
        }
    }
}

/// App-facing alias for the latest frozen version of this model. The persisted shape lives inside the
/// versioned schema so a future breaking change adds `SchemaV4.CachedReportPayload` while `SchemaV3`'s copy
/// stays frozen — an existing store keeps matching `SchemaV3` and migrates forward instead of being orphaned.
typealias CachedReportPayload = SchemaV3.CachedReportPayload
