//
//  SchemaV3.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftData

enum SchemaV3: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(3, 0, 0) }
    static var models: [any PersistentModel.Type] {
        [CachedMemberProfile.self, CachedReportPayload.self, RecentSearchTermRecord.self]
    }
}
