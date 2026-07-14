//
//  HomeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Home tab root (Figma `홈 / default`): weather + tip cards, a horizontal-scroll preview of recent
/// farming records, the top recommended policy, and popular community posts. The bottom nav bar is
/// provided by `MainTabView`, not here.
///
/// Scope decisions confirmed 2026-07-14 (see `docs/figma/home/2026-07-14-home-implementation-plan.md`):
/// - Weather detail is built with real temperature/condition + dummy extra fields (backend doesn't
///   provide feels-like/low-high/UV/precipitation/humidity/wind/5-day forecast yet).
/// - The policy card shows `applicationPeriodLabel` under the title instead of a computed D-day badge.
/// - Policy list row taps open the external application/source URL in the system browser.
/// - Search icon, notification icon, and the record/community section chevrons are inert placeholders
///   (no search screen, no unread-count API, no cross-tab navigation wiring yet).
struct HomeView: View {
    private let container: DIContainer
    @State private var viewModel: HomeViewModel
    @State private var path = NavigationPath()
    @State private var showCompose = false

    init(container: DIContainer) {
        self.container = container
        _viewModel = State(initialValue: HomeViewModel(
            recordRepository: container.makeRecordRepository(),
            communityRepository: container.makeCommunityRepository(),
            policyRepository: container.makePolicyRepository()
        ))
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "홈",
                    showBorder: false,
                    trailing: [.init(.asset("search")), .init(.asset("notifications"))]
                )
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.lg) {
                        weatherAndTipRow
                        recentRecordSection
                        policySection
                        popularPostsSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, Spacing.md)
                }
                .background(Color.Background.subtle)
                .refreshable { await viewModel.reload() }
            }
            .navigationBarHidden(true)
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .weatherDetail:
                    WeatherDetailView(state: viewModel.weatherState)
                case .policyList:
                    PolicyListView(container: container)
                }
            }
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container)
            }
        }
        .fullScreenCover(isPresented: $showCompose) {
            RecordComposeView(
                repository: container.makeRecordRepository(),
                mediaUpload: container.makeMediaUploadRepository()
            ) { _ in
                showCompose = false
                Task { await viewModel.reload() }
            }
        }
        .task { await viewModel.onAppear() }
    }

    // MARK: - Weather + Tip

    private var weatherAndTipRow: some View {
        HStack(spacing: Spacing.sm) {
            weatherCard.frame(maxWidth: .infinity)
            tipCard.frame(maxWidth: .infinity)
        }
    }

    private var isWeatherReady: Bool {
        if case .loaded = viewModel.weatherState { return true }
        return false
    }

    private var weatherCard: some View {
        Button {
            if isWeatherReady { path.append(HomeRoute.weatherDetail) }
        } label: {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HStack {
                    Text("날씨")
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.subtle)
                    Spacer()
                    AppIconView(source: .asset("arrow_forward"), size: 24)
                        .foregroundStyle(Color.Icon.default)
                }
                weatherCardContent
            }
            .padding(Spacing.md)
            .frame(height: 167, alignment: .top)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.Object.default)
            .overlay {
                RoundedRectangle(cornerRadius: 16).stroke(Color.Border.subtle, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var weatherCardContent: some View {
        switch viewModel.weatherState {
        case .loading:
            ProgressView().frame(maxWidth: .infinity, alignment: .center)
        case let .loaded(value):
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    AppIconView(source: .asset(WeatherIconMapping.assetName(for: value.weather.condition)), size: 40)
                    Text("\(value.weather.temperature)°")
                        .appTypography(.headlineMediumEmphasized)
                        .foregroundStyle(Color.Text.default)
                }
                Text("최저 \(value.detail.lowTemperature)° | 최고 \(value.detail.highTemperature)°")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
            }
        case let .failed(message):
            Text(message)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .lineLimit(2)
        }
    }

    /// Static placeholder copy — the "관수 간격" tip-generation logic isn't defined anywhere yet
    /// (BR/Swagger both silent, see home backend-conflicts C-7). Replace once that logic exists.
    private var tipCard: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("tip")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.inverse)
                .padding(.horizontal, Spacing.sm)
                .padding(.vertical, 4)
                .background(Color.Object.primary)
                .clipShape(Capsule())
            Text("오늘 날씨엔 관수가 잘 맞아요.\n작물 상태를 살펴보는 건 어떨까요?")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.primary)
        }
        .padding(Spacing.md)
        .frame(height: 167, alignment: .topLeading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.secondary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Recent record

    private var recentRecordSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("나의 최근 영농 기록")

            switch viewModel.recentRecordsState {
            case .loading:
                sectionLoading(height: 232)
            case let .loaded(records) where records.isEmpty:
                emptyStateText("아직 작성한 기록이 없어요")
            case let .loaded(records):
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(records) { record in
                            AppCard(
                                size: .medium,
                                title: record.memoPreview.isEmpty ? record.workType.label : record.memoPreview,
                                captions: [record.workType.label, weatherCaption(for: record)],
                                badges: [record.cropName],
                                dateText: dateText(record.workedAt)
                            ) {
                                RecordRemoteImage(url: record.thumbnailUrl)
                            }
                        }
                    }
                }
            case let .failed(message):
                emptyStateText(message)
            }

            AppButton("새로 작성하기", systemImage: "plus", variant: .secondary, size: .small, fullWidth: true) {
                showCompose = true
            }
        }
    }

    private func weatherCaption(for record: FarmingRecordSummary) -> String {
        let condition = record.weatherCondition.trimmingCharacters(in: .whitespaces)
        return condition.isEmpty ? "\(record.weatherTemperature)℃" : "\(condition) · \(record.weatherTemperature)℃"
    }

    private func dateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM.dd"
        return formatter.string(from: date)
    }

    // MARK: - Policy

    private var policySection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("오늘의 추천 정책") { path.append(HomeRoute.policyList) }

            switch viewModel.policyState {
            case .loading:
                sectionLoading(height: 103)
            case let .loaded(.some(policy)):
                Button {
                    path.append(HomeRoute.policyList)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(policy.title)
                                .appTypography(.titleMedium)
                                .foregroundStyle(Color.Text.subtle)
                                .lineLimit(1)
                            Text("기간: \(policy.applicationPeriodLabel)")
                                .appTypography(.labelMedium)
                                .foregroundStyle(Color.Text.muted)
                                .lineLimit(1)
                        }
                        Spacer()
                        AppIconView(source: .asset("arrow_forward"), size: 24)
                            .foregroundStyle(Color.Icon.inverse)
                            .frame(width: 48, height: 48)
                            .background(Color.Object.primary)
                            .clipShape(Circle())
                    }
                    .padding(Spacing.md)
                    .background(Color.Object.default)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12).stroke(Color.Border.subtle, lineWidth: 1)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            case .loaded(.none):
                emptyStateText("아직 추천 정책이 없어요")
            case let .failed(message):
                emptyStateText(message)
            }
        }
    }

    // MARK: - Popular posts

    private var popularPostsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader("나의 게시판 인기글")

            switch viewModel.popularPostsState {
            case .loading:
                sectionLoading(height: 200)
            case let .loaded(posts) where posts.isEmpty:
                emptyStateText("아직 인기글이 없어요")
            case let .loaded(posts):
                VStack(spacing: 0) {
                    ForEach(posts) { post in
                        Button {
                            path.append(post)
                        } label: {
                            AppListItem(
                                size: .small,
                                title: post.title,
                                caption: post.bodyPreview,
                                badges: postBadges(post),
                                showsDivider: post.id != posts.last?.id
                            ) {
                                CommunityRemoteImage(url: post.thumbnailUrl)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .background(Color.Object.default)
                .overlay {
                    RoundedRectangle(cornerRadius: 20).stroke(Color.Border.subtle, lineWidth: 1)
                }
                .clipShape(RoundedRectangle(cornerRadius: 20))
            case let .failed(message):
                emptyStateText(message)
            }
        }
    }

    private func postBadges(_ post: CommunityPostSummary) -> [String] {
        post.postType == .question ? ["Q&A", post.cropName] : [post.cropName]
    }

    // MARK: - Shared section chrome

    private func sectionHeader(_ title: String, action: (() -> Void)? = nil) -> some View {
        HStack {
            Text(title)
                .appTypography(.titleLargeEmphasized)
                .foregroundStyle(Color.Text.default)
            Spacer()
            Button {
                action?()
            } label: {
                AppIconView(source: .asset("arrow_forward_ios"), size: 24)
                    .foregroundStyle(Color.Icon.default)
            }
            .disabled(action == nil)
        }
    }

    private func sectionLoading(height: CGFloat) -> some View {
        ProgressView()
            .frame(maxWidth: .infinity, minHeight: height)
    }

    private func emptyStateText(_ text: String) -> some View {
        Text(text)
            .appTypography(.bodyMedium)
            .foregroundStyle(Color.Text.muted)
            .frame(maxWidth: .infinity, minHeight: 80, alignment: .center)
    }
}

/// Navigable destinations owned by `HomeView` itself (as opposed to `CommunityPostSummary`, which is
/// pushed directly since it already conforms to `Hashable`).
enum HomeRoute: Hashable {
    case weatherDetail
    case policyList
}
