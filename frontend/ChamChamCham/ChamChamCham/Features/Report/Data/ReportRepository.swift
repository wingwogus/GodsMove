//
//  ReportRepository.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportResource<Value: Sendable>: Sendable {
    enum Source: Equatable, Sendable {
        case network
        case cache(updatedAt: Date)
    }

    let value: Value
    let source: Source
}

@MainActor
protocol ReportRepository: Sendable {
    func fetchReports(
        filter: ReportFilter,
        cursor: String?,
        size: Int
    ) async throws -> ReportResource<FarmingWorkReportPage>

    func fetchDetail(
        _ key: WorkReportKey
    ) async throws -> ReportResource<FarmingWorkReportDetail>

    func loadCachedDetail(
        _ key: WorkReportKey
    ) -> ReportResource<FarmingWorkReportDetail>?

    func fetchFeedback(
        reportId: UUID,
        workType: WorkType
    ) async throws -> ReportResource<ReportFeedbackItem?>

    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItem
}

@MainActor
final class DefaultReportRepository: ReportRepository {
    private let remote: any ReportRemoteDataSource
    private let cache: ReportCache
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(remote: any ReportRemoteDataSource, cache: ReportCache) {
        self.remote = remote
        self.cache = cache
    }

    func fetchReports(
        filter: ReportFilter,
        cursor: String?,
        size: Int
    ) async throws -> ReportResource<FarmingWorkReportPage> {
        do {
            let response = try await remote.fetchWorkItems(ReportQuery(
                farmId: filter.farmId,
                cropId: filter.cropId,
                workTypes: filter.workTypes,
                cursor: cursor,
                size: size
            ))
            let accumulated = accumulatedPage(response, filter: filter, appending: cursor != nil)
            if let data = try? encoder.encode(accumulated) {
                cache.saveList(data, for: filter)
            }
            return ReportResource(value: accumulated.toDomain(), source: .network)
        } catch let error as APIError {
            guard case .network = error,
                  let cached = cachedPage(for: filter)
            else { throw error }
            return ReportResource(value: cached.value.toDomain(), source: .cache(updatedAt: cached.updatedAt))
        }
    }

    func fetchDetail(
        _ key: WorkReportKey
    ) async throws -> ReportResource<FarmingWorkReportDetail> {
        do {
            let response = try await remote.fetchDetail(key)
            let detail = try response.toDomain()
            if let data = try? encoder.encode(response) {
                cache.saveDetail(data, for: key)
            }
            return ReportResource(value: detail, source: .network)
        } catch let error as APIError {
            guard case .network = error,
                  let cached = cachedDetail(for: key)
            else { throw error }
            return ReportResource(value: cached.value, source: .cache(updatedAt: cached.updatedAt))
        }
    }

    func loadCachedDetail(
        _ key: WorkReportKey
    ) -> ReportResource<FarmingWorkReportDetail>? {
        guard let cached = cachedDetail(for: key) else { return nil }
        return ReportResource(value: cached.value, source: .cache(updatedAt: cached.updatedAt))
    }

    func fetchFeedback(
        reportId: UUID,
        workType: WorkType
    ) async throws -> ReportResource<ReportFeedbackItem?> {
        do {
            let response = try await remote.fetchFeedback(reportId: reportId)
            let feedback = response.toDomain().feedbacks.first { $0.workType == workType }
            if let data = try? encoder.encode(response) {
                cache.saveFeedback(data, reportId: reportId)
            }
            return ReportResource(value: feedback, source: .network)
        } catch let error as APIError {
            guard case .network = error,
                  let cached = cachedFeedback(reportId: reportId)
            else { throw error }
            let feedback = cached.value.feedbacks.first { $0.workType == workType }
            return ReportResource(value: feedback, source: .cache(updatedAt: cached.updatedAt))
        }
    }

    func regenerate(_ key: WorkReportKey) async throws -> ReportFeedbackItem {
        let response = try await remote.regenerate(key)
        guard let feedback = response.toDomain() else {
            throw ReportMappingError.unsupportedWorkType(response.workType)
        }
        return feedback
    }

    private func accumulatedPage(
        _ response: FarmingWorkReportPageResponseDTO,
        filter: ReportFilter,
        appending: Bool
    ) -> FarmingWorkReportPageResponseDTO {
        guard appending, let cached = cachedPage(for: filter)?.value else { return response }

        var identities = Set<String>()
        let items = (cached.items + response.items).filter {
            identities.insert("\($0.reportId.uuidString)|\($0.workType)").inserted
        }
        return FarmingWorkReportPageResponseDTO(items: items, nextCursor: response.nextCursor)
    }

    private func cachedPage(
        for filter: ReportFilter
    ) -> (value: FarmingWorkReportPageResponseDTO, updatedAt: Date)? {
        guard let cached = cache.list(for: filter) else { return nil }
        guard let value = try? decoder.decode(FarmingWorkReportPageResponseDTO.self, from: cached.data) else {
            cache.removeList(for: filter)
            return nil
        }
        return (value, cached.updatedAt)
    }

    private func cachedDetail(
        for key: WorkReportKey
    ) -> (value: FarmingWorkReportDetail, updatedAt: Date)? {
        guard let cached = cache.detail(for: key) else { return nil }
        guard let response = try? decoder.decode(FarmingWorkReportDetailResponseDTO.self, from: cached.data),
              let value = try? response.toDomain()
        else {
            cache.removeDetail(for: key)
            return nil
        }
        return (value, cached.updatedAt)
    }

    private func cachedFeedback(
        reportId: UUID
    ) -> (value: ReportFeedbackList, updatedAt: Date)? {
        guard let cached = cache.feedback(reportId: reportId) else { return nil }
        guard let response = try? decoder.decode(ReportFeedbackListResponseDTO.self, from: cached.data) else {
            cache.removeFeedback(reportId: reportId)
            return nil
        }
        return (response.toDomain(), cached.updatedAt)
    }
}
