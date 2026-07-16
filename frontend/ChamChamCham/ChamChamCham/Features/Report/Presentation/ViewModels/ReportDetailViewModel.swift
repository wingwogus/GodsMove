//
//  ReportDetailViewModel.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import Foundation

struct ReportFeedbackSnapshot: Equatable, Sendable {
    let state: ReportFeedbackState
    let content: ReportFeedbackContent?
}
@MainActor
@Observable
final class ReportDetailViewModel {
    typealias Sleep = @Sendable (Duration) async throws -> Void

    private(set) var detail: FarmingWorkReportDetail?
    private(set) var feedback: ReportFeedbackSnapshot?
    private(set) var isLoading = false
    private(set) var isPolling = false
    private(set) var isRegenerating = false
    private(set) var isOffline = false
    private(set) var cachedAt: Date?
    private(set) var errorMessage: String?
    private(set) var feedbackErrorMessage: String?

    private let key: WorkReportKey
    private let repository: any ReportRepository
    private let sleep: Sleep
    private let pollingInterval: Duration
    private let pollingLimit: Int

    private var hasLoaded = false
    private var isVisible = true
    private var isSceneActive = true
    private var pollingTask: Task<Void, Never>?

    init(
        key: WorkReportKey,
        repository: any ReportRepository,
        pollingInterval: Duration = .seconds(5),
        pollingLimit: Int = 24,
        sleep: @escaping Sleep = { duration in
            try await ContinuousClock().sleep(for: duration)
        }
    ) {
        self.key = key
        self.repository = repository
        self.pollingInterval = pollingInterval
        self.pollingLimit = pollingLimit
        self.sleep = sleep
    }

    var presentation: ReportDetailPresentation? {
        detail.map(ReportDetailPresentation.init(detail:))
    }

    var canRegenerate: Bool {
        guard !isOffline, !isRegenerating, detail?.status == .completed else { return false }
        return feedback?.state == .failed || feedback?.state == .stale
    }

    func onAppear() async {
        isVisible = true
        guard !hasLoaded else {
            resumePollingIfNeeded()
            return
        }
        hasLoaded = true

        if let cached = repository.loadCachedDetail(key) {
            applyDetail(cached)
        }

        isLoading = detail == nil
        errorMessage = nil
        defer { isLoading = false }

        do {
            let resource = try await repository.fetchDetail(key)
            guard !Task.isCancelled else { return }
            applyDetail(resource)
            await loadFeedbackIfNeeded()
        } catch {
            guard !Task.isCancelled else { return }
            if detail == nil {
                errorMessage = ReportErrorMessage.text(for: error)
            } else {
                await loadFeedbackIfNeeded()
            }
        }
    }

    func retry() async {
        hasLoaded = false
        await onAppear()
    }

    func refreshFeedback() async {
        guard detail?.status == .completed else { return }
        await fetchFeedback(startsPolling: true)
    }

    func regenerate() async {
        guard canRegenerate else { return }
        isRegenerating = true
        feedbackErrorMessage = nil
        defer { isRegenerating = false }

        do {
            let item = try await repository.regenerate(key)
            guard !Task.isCancelled else { return }
            applyFeedback(item)
            if item.state == .pending {
                startPolling()
            }
        } catch {
            guard !Task.isCancelled else { return }
            feedbackErrorMessage = ReportErrorMessage.text(for: error)
        }
    }

    func setSceneActive(_ active: Bool) {
        isSceneActive = active
        if active {
            resumePollingIfNeeded()
        } else {
            cancelPolling()
        }
    }

    func onDisappear() {
        isVisible = false
        cancelPolling()
    }

    private func applyDetail(_ resource: ReportResource<FarmingWorkReportDetail>) {
        detail = resource.value
        switch resource.source {
        case .network:
            isOffline = false
            cachedAt = nil
        case let .cache(updatedAt):
            isOffline = true
            cachedAt = updatedAt
        }
    }

    private func loadFeedbackIfNeeded() async {
        guard let detail else { return }
        guard detail.status == .completed else {
            feedback = nil
            cancelPolling()
            return
        }

        if let embedded = detail.feedback {
            feedback = ReportFeedbackSnapshot(state: embedded.state, content: embedded.content)
        }
        await fetchFeedback(startsPolling: true)
    }

    private func fetchFeedback(startsPolling: Bool) async {
        feedbackErrorMessage = nil
        do {
            let resource = try await repository.fetchFeedback(
                reportId: key.reportId,
                workType: key.workType
            )
            guard !Task.isCancelled else { return }
            if let item = resource.value {
                applyFeedback(item)
            }
            if case let .cache(updatedAt) = resource.source {
                isOffline = true
                cachedAt = updatedAt
            }
            if startsPolling {
                resumePollingIfNeeded()
            }
        } catch {
            guard !Task.isCancelled else { return }
            feedbackErrorMessage = ReportErrorMessage.text(for: error)
            if startsPolling {
                resumePollingIfNeeded()
            }
        }
    }

    private func applyFeedback(_ item: ReportFeedbackItem) {
        feedback = ReportFeedbackSnapshot(state: item.state, content: item.content)
        if item.state != .pending {
            cancelPolling()
        }
    }

    private func resumePollingIfNeeded() {
        guard feedback?.state == .pending else { return }
        startPolling()
    }

    private func startPolling() {
        guard pollingTask == nil,
              feedback?.state == .pending,
              isVisible,
              isSceneActive,
              !isOffline
        else { return }

        isPolling = true
        pollingTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer {
                self.isPolling = false
                self.pollingTask = nil
            }

            for _ in 0..<self.pollingLimit {
                do {
                    try await self.sleep(self.pollingInterval)
                    try Task.checkCancellation()
                    await self.fetchFeedback(startsPolling: false)
                } catch {
                    if Task.isCancelled { return }
                    self.feedbackErrorMessage = ReportErrorMessage.text(for: error)
                }

                guard self.feedback?.state == .pending,
                      self.isVisible,
                      self.isSceneActive,
                      !self.isOffline
                else { return }
            }
        }
    }

    private func cancelPolling() {
        pollingTask?.cancel()
        pollingTask = nil
        isPolling = false
    }
}
