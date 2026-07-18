//
//  ReportCache.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation
import SwiftData

struct CachedReportData: Equatable, Sendable {
    let data: Data
    let updatedAt: Date
}

@MainActor
final class ReportCache {
    private enum Kind: String {
        case list
        case detail
        case feedback
    }

    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    func saveList(_ data: Data, for filter: ReportFilter, updatedAt: Date = Date()) {
        save(data, key: Self.listKey(for: filter), kind: .list, updatedAt: updatedAt)
    }

    func list(for filter: ReportFilter) -> CachedReportData? {
        read(key: Self.listKey(for: filter), kind: .list)
    }

    func saveDetail(_ data: Data, for key: WorkReportKey, updatedAt: Date = Date()) {
        save(data, key: Self.detailKey(for: key), kind: .detail, updatedAt: updatedAt)
    }

    func detail(for key: WorkReportKey) -> CachedReportData? {
        read(key: Self.detailKey(for: key), kind: .detail)
    }

    func saveFeedback(_ data: Data, reportId: UUID, updatedAt: Date = Date()) {
        save(data, key: Self.feedbackKey(reportId: reportId), kind: .feedback, updatedAt: updatedAt)
    }

    func feedback(reportId: UUID) -> CachedReportData? {
        read(key: Self.feedbackKey(reportId: reportId), kind: .feedback)
    }

    func removeList(for filter: ReportFilter) {
        remove(key: Self.listKey(for: filter))
    }

    func removeDetail(for key: WorkReportKey) {
        remove(key: Self.detailKey(for: key))
    }

    func removeFeedback(reportId: UUID) {
        remove(key: Self.feedbackKey(reportId: reportId))
    }

    private func save(_ data: Data, key: String, kind: Kind, updatedAt: Date) {
        if let existing = row(for: key) {
            existing.kindRaw = kind.rawValue
            existing.payload = data
            existing.updatedAt = updatedAt
        } else {
            modelContext.insert(CachedReportPayload(
                key: key,
                kindRaw: kind.rawValue,
                payload: data,
                updatedAt: updatedAt
            ))
        }
        try? modelContext.save()
    }

    private func read(key: String, kind: Kind) -> CachedReportData? {
        guard let cached = row(for: key), cached.kindRaw == kind.rawValue else { return nil }
        guard (try? JSONSerialization.jsonObject(with: cached.payload)) != nil else {
            modelContext.delete(cached)
            try? modelContext.save()
            return nil
        }
        return CachedReportData(data: cached.payload, updatedAt: cached.updatedAt)
    }

    private func remove(key: String) {
        guard let cached = row(for: key) else { return }
        modelContext.delete(cached)
        try? modelContext.save()
    }

    private func row(for key: String) -> CachedReportPayload? {
        let descriptor = FetchDescriptor<CachedReportPayload>()
        return (try? modelContext.fetch(descriptor))?.first { $0.key == key }
    }

    private static func listKey(for filter: ReportFilter) -> String {
        let farm = filter.farmId?.uuidString ?? "all"
        let crop = filter.cropId?.uuidString ?? "all"
        let workTypes = filter.workTypes.isEmpty
            ? "all"
            : filter.workTypes.map(\.rawValue).sorted().joined(separator: ",")
        return "list:farm=\(farm)|crop=\(crop)|workType=\(workTypes)"
    }

    private static func detailKey(for key: WorkReportKey) -> String {
        "detail:\(key.reportId.uuidString)|workType=\(key.workType.rawValue)"
    }

    private static func feedbackKey(reportId: UUID) -> String {
        "feedback:\(reportId.uuidString)"
    }
}
