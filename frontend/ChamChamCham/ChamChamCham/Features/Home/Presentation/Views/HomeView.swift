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
/// Scope decisions confirmed 2026-07-14 (see `docs/figma/home/2026-07-14-home-implementation-plan.md`),
/// weather now fully wired to real data per the 2026-07-16 `/api/v1/weather/*` redesign:
/// - The policy card shows `applicationPeriodLabel` under the title instead of a computed D-day badge.
/// - Policy list row taps open the external application/source URL in the system browser.
/// - The record/community section chevrons switch `MainTabView`'s tab `selection` (hoisted in via
///   `tabSelection`) rather than pushing a duplicate list screen onto Home's own `NavigationStack` —
///   those screens are tab roots with their own stack (and, for Record, a speed-dial binding owned by
///   `MainTabView`), so a push here would produce a second, independently-scrolled copy.
/// - Search icon and notification icon are still inert placeholders (no search screen, no
///   unread-count API yet).
struct HomeView: View {
    private let container: DIContainer
    @State private var viewModel: HomeViewModel
    @State private var path = NavigationPath()
    @State private var showCompose = false
    @State private var showSearch = false
    @Binding private var tabSelection: Int
    private let tabItems: [AppNavBar.Item]

    init(container: DIContainer, tabSelection: Binding<Int>, tabItems: [AppNavBar.Item]) {
        self.container = container
        _tabSelection = tabSelection
        self.tabItems = tabItems
        _viewModel = State(initialValue: HomeViewModel(
            recordRepository: container.makeRecordRepository(),
            communityRepository: container.makeCommunityRepository(),
            policyRepository: container.makePolicyRepository(),
            weatherRepository: container.makeWeatherRepository()
        ))
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "홈",
                    background: .subtle,
                    showBorder: false,
                    trailing: [.init(.asset("search")) { showSearch = true }]
                )
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.xl) {
                        weatherAndTipRow
                        recentRecordSection
                        policySection
                        popularPostsSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.lg)
                }
                .background(Color.Background.subtle)
                .refreshable {
                    // `reload()` sets each section's state to `.loading` before awaiting its fetch, which
                    // triggers a body rebuild. If that rebuild happens on the same Task `.refreshable` owns,
                    // SwiftUI cancels it mid-flight — the in-flight URLSession call then throws
                    // `URLError(.cancelled)`, which surfaces as "네트워크 연결을 확인해주세요" even though
                    // nothing was actually wrong. Running the reload on its own unstructured Task keeps it
                    // outside that cancellation path.
                    await Task { await viewModel.reload() }.value
                }
            }
            .navigationBarHidden(true)
            .appTabBarDock(items: tabItems, selection: $tabSelection)
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .weatherDetail:
                    WeatherDetailView(state: viewModel.weatherDetailState)
                        .task { await viewModel.loadWeatherDetailIfNeeded() }
                case .policyList:
                    PolicyListView(container: container)
                }
            }
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container)
            }
            .navigationDestination(for: FarmingRecordSummary.self) { record in
                RecordDetailView(
                    recordId: record.id,
                    repository: container.makeRecordRepository(),
                    weatherRepository: container.makeWeatherRepository(),
                    mediaUpload: container.makeMediaUploadRepository()
                ) {
                    Task { await viewModel.reload() }
                }
            }
        }
        .fullScreenCover(isPresented: $showCompose) {
            RecordComposeView(
                repository: container.makeRecordRepository(),
                weatherRepository: container.makeWeatherRepository(),
                mediaUpload: container.makeMediaUploadRepository()
            ) { _ in
                showCompose = false
                Task { await viewModel.reload() }
            }
        }
        .fullScreenCover(isPresented: $showSearch) {
            SearchView(container: container)
        }
        .task { await viewModel.onAppear() }
    }

    // MARK: - Weather + Tip

    private var weatherAndTipRow: some View {
        HStack(spacing: Spacing.md) {
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
        case let .loaded(weather):
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    AppIconView(source: .asset(WeatherIconMapping.assetName(for: weather.condition.code)), size: 40, renderingMode: .original)
                    Text("\(weather.temperature)°")
                        .appTypography(.headlineLarge)
                        .foregroundStyle(Color.Text.default)
                }
                HStack(spacing: Spacing.sm) {
                    Text("최저 \(weather.minTemperature.map { "\($0)" } ?? "-")°")
                    Rectangle()
                        .fill(Color.Border.strong)
                        .frame(width: 1, height: 12)
                    Text("최고 \(weather.maxTemperature.map { "\($0)" } ?? "-")°")
                }
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
            AppBadge(label: "tip", size: .small, style: .solid, variant: .primary)
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
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader("나의 최근 영농 기록") { tabSelection = 1 }

            switch viewModel.recentRecordsState {
            case .loading:
                sectionLoading(height: 232)
            case let .loaded(records) where records.isEmpty:
                emptyStateText("아직 작성한 영농 기록이 없어요.\n아래 버튼으로 첫 기록을 남겨보세요.")
            case let .loaded(records):
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.md) {
                        ForEach(records) { record in
                            Button {
                                path.append(record)
                            } label: {
                                AppCard(
                                    size: .medium,
                                    title: record.workType.label,
                                    captions: [record.memoPreview],
                                    badges: [record.cropName],
                                    dateText: dateText(record.workedAt)
                                ) {
                                    RecordRemoteImage(
                                        url: record.thumbnailUrl,
                                        workType: record.workType,
                                        illustVariant: .wide
                                    )
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .contentMargins(.horizontal, 20, for: .scrollContent)
                .padding(.horizontal, -20)
            case let .failed(message):
                emptyStateText(message)
            }

            AppButton("새로 작성하기", icon: .asset("add"), variant: .secondary, size: .small, fullWidth: true) {
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
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader("오늘의 추천 정책") { path.append(HomeRoute.policyList) }

            switch viewModel.policyState {
            case .loading:
                sectionLoading(height: 103)
            case let .loaded(.some(policy)):
                Button {
                    path.append(HomeRoute.policyList)
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            Text(policy.title)
                                .appTypography(.titleMediumEmphasized)
                                .foregroundStyle(Color.Text.subtle)
                                .lineLimit(1)
                            Text(policy.applicationPeriodLabel)
                                .appTypography(.bodyMedium)
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
                emptyStateText("조건에 맞는 추천 정책이 아직 없어요.\n위 화살표를 눌러 전체 정책을 확인해보세요.")
            case let .failed(message):
                emptyStateText(message)
            }
        }
    }

    // MARK: - Popular posts

    private var popularPostsSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader("나의 게시판 인기글") { tabSelection = 2 }

            switch viewModel.popularPostsState {
            case .loading:
                sectionLoading(height: 200)
            case let .loaded(posts) where posts.isEmpty:
                emptyStateText("등록한 작물 게시판에 인기글이 아직 없어요.\n위 화살표를 눌러 커뮤니티의 다른 이야기도 둘러보세요.")
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

    private func postBadges(_ post: CommunityPostSummary) -> [AppListItemBadge] {
        let category = AppListItemBadge(post.cropName, style: .solidPastel, variant: .primary)
        guard post.postType == .question else { return [category] }
        return [AppListItemBadge("Q&A", style: .solid, variant: .primary), category]
    }

    // MARK: - Shared section chrome

    private func sectionHeader(_ title: String, action: (() -> Void)? = nil) -> some View {
        Button {
            action?()
        } label: {
            HStack {
                Text(title)
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.default)
                Spacer()
                AppIconView(source: .asset("arrow_forward_ios"), size: 24)
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 44, height: 44)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
    }

    private func sectionLoading(height: CGFloat) -> some View {
        ProgressView()
            .frame(maxWidth: .infinity, minHeight: height)
    }

    private func emptyStateText(_ text: String) -> some View {
        Text(text)
            .appTypography(.bodyMedium)
            .foregroundStyle(Color.Text.muted)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: 80, alignment: .center)
    }
}

/// Navigable destinations owned by `HomeView` itself (as opposed to `CommunityPostSummary`, which is
/// pushed directly since it already conforms to `Hashable`).
enum HomeRoute: Hashable {
    case weatherDetail
    case policyList
}
