//
//  CachedReportPayload.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

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
