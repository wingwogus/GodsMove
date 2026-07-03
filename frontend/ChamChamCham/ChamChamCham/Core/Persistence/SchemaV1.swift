//
//  SchemaV1.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftData

enum SchemaV1: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }
    static var models: [any PersistentModel.Type] { [] }
}
