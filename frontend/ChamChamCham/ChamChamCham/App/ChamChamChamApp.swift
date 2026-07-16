//
//  ChamChamChamApp.swift
//  ChamChamCham
//
//  Created by iyungui on 7/2/26.
//

import Foundation
import SwiftUI
import SwiftData

@main
struct ChamChamChamApp: App {
    @State private var appState: AppState
    private let container: DIContainer
#if DEBUG
    private let reportPreviewRepository: ReportPreviewRepository?
#endif

    init() {
        container = DIContainer(modelContainer: .makeApp())
        KakaoSDKBootstrap.initialize()
        NaverSDKBootstrap.initialize()
        _appState = State(initialValue: Self.initialAppState(container: container))
#if DEBUG
        reportPreviewRepository = Self.makeReportPreviewRepository()
#endif
    }

    var body: some Scene {
        WindowGroup {
            rootContent
                .environment(appState)
                .onOpenURL { url in
                    if KakaoSDKBootstrap.handleOpenURL(url) { return }
                    _ = NaverSDKBootstrap.handleOpenURL(url)
                }
        }
        .modelContainer(container.modelContainer)
    }

    @ViewBuilder private var rootContent: some View {
#if DEBUG
        if let reportPreviewRepository {
            NavigationStack {
                ReportDetailView(
                    key: reportPreviewRepository.detail.key,
                    repository: reportPreviewRepository
                )
            }
        } else {
            RootView(container: container)
        }
#else
        RootView(container: container)
#endif
    }

    /// Derives cold-launch auth state synchronously, before SwiftUI's first render, so `RootView` never
    /// flashes the logged-out flow for a frame while `RootView.bootstrap()` awaits the actor-isolated
    /// `AuthTokenStore` (see its `hasStoredRefreshToken` doc comment).
    @MainActor
    private static func initialAppState(container: DIContainer) -> AppState {
        guard AuthTokenStore.hasStoredRefreshToken() else { return AppState() }
        guard let cached = CachedMemberProfile.fetchCached(in: container.modelContainer.mainContext) else {
            return AppState(isAuthenticated: true, isOnboarded: false)
        }
        return AppState(isAuthenticated: true, isOnboarded: cached.isOnboardingComplete)
    }

#if DEBUG
    @MainActor
    private static func makeReportPreviewRepository() -> ReportPreviewRepository? {
        let arguments = ProcessInfo.processInfo.arguments
        guard arguments.contains("--report-preview") else { return nil }

        let workType = previewArgument("--report-work-type", in: arguments)
            .flatMap(WorkType.init(rawValue:)) ?? .harvest
        let cycleStatus: ReportCycleStatus = arguments.contains("--report-active") ? .active : .completed
        let feedbackState = previewArgument("--report-feedback", in: arguments)
            .map(ReportFeedbackState.init(rawValue:)) ?? .ready
        let source: ReportResource<FarmingWorkReportDetail>.Source = arguments.contains("--report-offline")
            ? .cache(updatedAt: Date(timeIntervalSince1970: 1_730_000_000))
            : .network

        return ReportPreviewFixtures.repository(
            workType: workType,
            cycleStatus: cycleStatus,
            feedbackState: feedbackState,
            source: source
        )
    }

    private static func previewArgument(_ name: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: name), arguments.indices.contains(index + 1) else {
            return nil
        }
        return arguments[index + 1]
    }
#endif
}
