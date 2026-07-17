//
//  ReportCacheTests.swift
//  ChamChamChamTests
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData
import Testing
@testable import ChamChamCham

@MainActor
@Suite("Report SwiftData cache", .serialized)
struct ReportCacheTests {
    @Test("list payloads are isolated by filter and the accumulated page replaces the first page")
    func listKeysAndReplacement() throws {
        let (cache, container) = try makeCache()
        let farmId = UUID()
        let cropId = UUID()
        let all = ReportFilter()
        let filtered = ReportFilter(farmId: farmId, cropId: cropId, workTypes: [.harvest])
        let first = json("{\"items\":[1]}")
        let accumulated = json("{\"items\":[1,2]}")
        let other = json("{\"items\":[9]}")
        let firstDate = Date(timeIntervalSince1970: 10)
        let accumulatedDate = Date(timeIntervalSince1970: 20)

        cache.saveList(first, for: all, updatedAt: firstDate)
        cache.saveList(other, for: filtered, updatedAt: firstDate)
        cache.saveList(accumulated, for: all, updatedAt: accumulatedDate)

        #expect(cache.list(for: all)?.data == accumulated)
        #expect(cache.list(for: all)?.updatedAt == accumulatedDate)
        #expect(cache.list(for: filtered)?.data == other)
        withExtendedLifetime(container) {}
    }

    @Test("multi-selected work types produce a stable cache key regardless of Set iteration order")
    func multiWorkTypeCacheKeyIsOrderStable() throws {
        let (cache, container) = try makeCache()
        let filterA = ReportFilter(workTypes: [.watering, .harvest])
        let filterB = ReportFilter(workTypes: [.harvest, .watering])
        let payload = json("{\"items\":[1]}")

        cache.saveList(payload, for: filterA)

        #expect(cache.list(for: filterB)?.data == payload)
        withExtendedLifetime(container) {}
    }

    @Test("detail payloads use report and work type as a composite key")
    func detailCompositeKey() throws {
        let (cache, container) = try makeCache()
        let reportId = UUID()
        let planting = WorkReportKey(reportId: reportId, workType: .planting)
        let harvest = WorkReportKey(reportId: reportId, workType: .harvest)
        let plantingData = json("{\"kind\":\"planting\"}")
        let harvestData = json("{\"kind\":\"harvest\"}")

        cache.saveDetail(plantingData, for: planting)
        cache.saveDetail(harvestData, for: harvest)

        #expect(cache.detail(for: planting)?.data == plantingData)
        #expect(cache.detail(for: harvest)?.data == harvestData)
        withExtendedLifetime(container) {}
    }

    @Test("feedback payload is replaced per report")
    func feedbackReplacement() throws {
        let (cache, container) = try makeCache()
        let reportId = UUID()
        let pending = json("{\"feedbacks\":[]}")
        let ready = json("{\"feedbacks\":[{\"status\":\"READY\"}]}")
        let updatedAt = Date(timeIntervalSince1970: 30)

        cache.saveFeedback(pending, reportId: reportId)
        cache.saveFeedback(ready, reportId: reportId, updatedAt: updatedAt)

        #expect(cache.feedback(reportId: reportId)?.data == ready)
        #expect(cache.feedback(reportId: reportId)?.updatedAt == updatedAt)
        withExtendedLifetime(container) {}
    }

    @Test("invalid JSON is a cache miss and its row is deleted")
    func corruptPayloadCleanup() throws {
        let (cache, container) = try makeCache()
        let filter = ReportFilter(workTypes: [.watering])
        cache.saveList(Data([0xFF, 0x00, 0x01]), for: filter)

        #expect(cache.list(for: filter) == nil)
        let rows = try container.mainContext.fetch(FetchDescriptor<CachedReportPayload>())
        #expect(rows.isEmpty)
    }

    @Test("V1 member cache survives the on-disk lightweight migration to V2")
    func memberCacheMigration() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("report-cache-migration-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
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

        let schema = Schema(versionedSchema: SchemaV2.self)
        let configuration = ModelConfiguration("migration", schema: schema, url: storeURL)
        let migrated = try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )

        #expect(CachedMemberProfile.fetchCached(in: migrated.mainContext)?.id == memberId)
        #expect(try migrated.mainContext.fetch(FetchDescriptor<CachedReportPayload>()).isEmpty)
    }

    private func makeCache() throws -> (ReportCache, ModelContainer) {
        let schema = Schema(versionedSchema: SchemaV2.self)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        let container = try ModelContainer(
            for: schema,
            migrationPlan: AppSchemaMigrationPlan.self,
            configurations: [configuration]
        )
        return (ReportCache(modelContext: container.mainContext), container)
    }

    private func json(_ value: String) -> Data {
        Data(value.utf8)
    }
}
