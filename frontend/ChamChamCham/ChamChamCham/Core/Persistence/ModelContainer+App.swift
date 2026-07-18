//
//  ModelContainer+App.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import Foundation
import SwiftData

extension ModelContainer {
    /// The versioned schema lineage (`SchemaV1…V3` + `AppSchemaMigrationPlan`) migrates an existing store
    /// forward across app updates, so onboarding cache and local search history survive schema changes.
    ///
    /// The catch is a last-resort safety net, not the normal path: everything persisted here is rebuildable
    /// local data — `CachedMemberProfile` (read-through cache), `CachedReportPayload` (report cache), and
    /// `RecentSearchTermRecord` (search history) — never a source of truth holding pending writes. If the
    /// on-disk store is genuinely unmigratable (corrupt, or stamped with a model version the plan cannot
    /// recognize → "Cannot use staged migration with an unknown model version"), the offline-first answer is
    /// to wipe the store and start fresh rather than crash on launch. A correctly frozen migration path keeps
    /// this from triggering on ordinary upgrades.
    static func makeApp() -> ModelContainer {
        let schema = Schema(versionedSchema: SchemaV3.self)
        let configuration = ModelConfiguration(schema: schema)

        do {
            return try container(schema: schema, configuration: configuration)
        } catch {
            destroyStore(at: configuration.url)
            do {
                return try container(schema: schema, configuration: configuration)
            } catch {
                fatalError("Failed to create ModelContainer even after resetting the local cache store: \(error)")
            }
        }
    }

    private static func container(
        schema: Schema,
        configuration: ModelConfiguration
    ) throws -> ModelContainer {
        try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )
    }

    /// Removes the SQLite store and its `-wal`/`-shm` sidecar files so the next load recreates an empty store.
    private static func destroyStore(at url: URL) {
        let fileManager = FileManager.default
        for suffix in ["", "-wal", "-shm"] {
            let sidecar = URL(fileURLWithPath: url.path + suffix)
            try? fileManager.removeItem(at: sidecar)
        }
    }
}
