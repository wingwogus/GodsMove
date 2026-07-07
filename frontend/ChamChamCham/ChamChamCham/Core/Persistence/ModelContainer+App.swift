//
//  ModelContainer+App.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftData

extension ModelContainer {
    static func makeApp() -> ModelContainer {
        let schema = Schema(versionedSchema: SchemaV1.self)
        let configuration = ModelConfiguration(schema: schema)
        return try! ModelContainer(for: schema, configurations: [configuration])
    }
}
