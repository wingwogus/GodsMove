//
//  SchemaV2.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftData

enum SchemaV2: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(2, 0, 0) }
    static var models: [any PersistentModel.Type] {
        [CachedMemberProfile.self, CachedReportPayload.self]
    }
}
