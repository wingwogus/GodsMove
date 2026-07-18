//
//  SchemaMigrationTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/18/26.
//

import Foundation
import SwiftData
import Testing
@testable import ChamChamCham

/// Locks the frozen-schema guarantee: an on-disk store stamped at an earlier version must migrate forward
/// through `AppSchemaMigrationPlan` without dropping the rows a real user would care about (onboarding cache,
/// search history), rather than being orphaned into a wipe. If a future edit unfreezes `SchemaV1/V2/V3` by
/// changing their model shapes, these tests break instead of a shipped user silently losing data.
@MainActor
@Suite("Schema migration preserves data", .serialized)
struct SchemaMigrationTests {
    private func makeStoreDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("schema-migration-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    @Test("V1 store (member only) migrates to V3 keeping the member and gaining empty report/search tables")
    func migrateV1ToV3() throws {
        let directory = try makeStoreDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appendingPathComponent("ChamChamCham.store")
        let memberId = UUID()

        do {
            let schema = Schema(versionedSchema: SchemaV1.self)
            let configuration = ModelConfiguration("migration", schema: schema, url: storeURL)
            let container = try ModelContainer(for: schema, configurations: [configuration])
            container.mainContext.insert(CachedMemberProfile(
                id: memberId,
                email: "farmer@example.com",
                name: "농부",
                nickname: "참참",
                phone: nil,
                birthDateRaw: nil,
                experienceLevel: 3,
                managementTypeRaw: "OWNER",
                profileImageUrl: nil,
                onboardingStatusRaw: "COMPLETE",
                missingFieldsRaw: [],
                updatedAt: Date(timeIntervalSince1970: 1)
            ))
            try container.mainContext.save()
        }

        let schema = Schema(versionedSchema: SchemaV3.self)
        let configuration = ModelConfiguration("migration", schema: schema, url: storeURL)
        let migrated = try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )

        #expect(CachedMemberProfile.fetchCached(in: migrated.mainContext)?.id == memberId)
        #expect(try migrated.mainContext.fetch(FetchDescriptor<CachedReportPayload>()).isEmpty)
        #expect(try migrated.mainContext.fetch(FetchDescriptor<RecentSearchTermRecord>()).isEmpty)
    }

    @Test("V2 store (member + report) migrates to V3 keeping both rows and gaining an empty search table")
    func migrateV2ToV3() throws {
        let directory = try makeStoreDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appendingPathComponent("ChamChamCham.store")
        let memberId = UUID()
        let reportKey = "report-key"

        do {
            let schema = Schema(versionedSchema: SchemaV2.self)
            let configuration = ModelConfiguration("migration", schema: schema, url: storeURL)
            let container = try ModelContainer(
                for: schema,
                migrationPlan: AppSchemaMigrationPlan.self,
                configurations: [configuration]
            )
            container.mainContext.insert(CachedMemberProfile(
                id: memberId,
                email: nil,
                name: nil,
                nickname: nil,
                phone: nil,
                birthDateRaw: nil,
                experienceLevel: nil,
                managementTypeRaw: nil,
                profileImageUrl: nil,
                onboardingStatusRaw: "COMPLETE",
                missingFieldsRaw: [],
                updatedAt: Date(timeIntervalSince1970: 1)
            ))
            container.mainContext.insert(CachedReportPayload(
                key: reportKey,
                kindRaw: "detail",
                payload: Data("{}".utf8),
                updatedAt: Date(timeIntervalSince1970: 1)
            ))
            try container.mainContext.save()
        }

        let schema = Schema(versionedSchema: SchemaV3.self)
        let configuration = ModelConfiguration("migration", schema: schema, url: storeURL)
        let migrated = try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )

        #expect(CachedMemberProfile.fetchCached(in: migrated.mainContext)?.id == memberId)
        let reports = try migrated.mainContext.fetch(FetchDescriptor<CachedReportPayload>())
        #expect(reports.count == 1)
        #expect(reports.first?.key == reportKey)
        #expect(try migrated.mainContext.fetch(FetchDescriptor<RecentSearchTermRecord>()).isEmpty)
    }
}
