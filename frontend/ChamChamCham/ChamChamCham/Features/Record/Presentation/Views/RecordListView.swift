//
//  RecordListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 영농 기록 tab root (Figma `기록 메인 / default`): top app bar, 기록/리포트 tabs, the 작물/영농 활동/기간
/// filter chip row, a cursor-paged record list, and the floating "+" record button. The bottom nav bar is
/// provided by `MainTabView`, not here.
///
/// The record and report tabs share this navigation shell while keeping their repositories and state separate.
struct RecordListView: View {
    private let container: DIContainer
    private let repository: any RecordRepository
    private let reportRepository: any ReportRepository
    private let weatherRepository: any WeatherRepository
    private let mediaUpload: any MediaUploadRepository
    private let voiceRepository: any VoiceSessionRepository
    @State private var viewModel: RecordListViewModel
    @State private var reportViewModel: ReportListViewModel
    @State private var selectedTab = 0
    @State private var activeSheet: RecordFilterKind?
    /// Local to this tab: the speed-dial scrim dims both the content region and the docked nav bar,
    /// which live in the same view tree (see `body`), so no cross-view binding is needed.
    @State private var isSpeedDialOpen = false
    @State private var showCompose = false
    @State private var showVoiceCompose = false
    @State private var showSearch = false
    @State private var path = NavigationPath()
    @State private var toastMessage: String?
    @State private var isRecordFilterRowVisible = true
    @Binding private var selection: Int
    private let tabItems: [AppNavBar.Item]
    private let horizontalInset: CGFloat = 20

    init(
        container: DIContainer,
        repository: any RecordRepository,
        reportRepository: any ReportRepository,
        weatherRepository: any WeatherRepository,
        mediaUpload: any MediaUploadRepository,
        voiceRepository: any VoiceSessionRepository,
        selection: Binding<Int>,
        tabItems: [AppNavBar.Item]
    ) {
        self.container = container
        self.repository = repository
        self.reportRepository = reportRepository
        self.weatherRepository = weatherRepository
        self.mediaUpload = mediaUpload
        self.voiceRepository = voiceRepository
        _selection = selection
        self.tabItems = tabItems
        _viewModel = State(initialValue: RecordListViewModel(repository: repository))
        _reportViewModel = State(initialValue: ReportListViewModel(repository: reportRepository) {
            try await repository.fetchFarmCrops()
        })
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                recordTabContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                dockedNavBar
            }
        }
        .recordToast(message: $toastMessage)
    }

    /// The app tab bar docked at the bottom of this tab's stack root. It carries its own speed-dial
    /// scrim so the dimming reaches over the bar — the content scrim inside `recordTabContent` can't,
    /// being bounded above it. Because the bar lives inside the `NavigationStack`, a pushed
    /// `RecordDetailView` slides over it and a pop reveals it (native `hidesBottomBarWhenPushed`).
    private var dockedNavBar: some View {
        AppNavBar(items: tabItems, selection: $selection)
            .background(Color.Background.default.ignoresSafeArea(edges: .bottom))
            .overlay {
                if isSpeedDialOpen {
                    Color.scrim
                        .ignoresSafeArea(edges: .bottom)
                        .transition(.opacity)
                        .onTapGesture { closeSpeedDial() }
                }
            }
    }

    private var recordTabContent: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "영농 기록",
                    showBorder: false,
                    trailing: [.init(.asset("search")) { showSearch = true }]
                )
                AppTabBar(titles: ["기록", "리포트"], selection: $selectedTab)
                    .frame(height: 56)

                if selectedTab == 0 {
                    recordList
                } else {
                    ReportListView(viewModel: reportViewModel)
                }
            }
            if selectedTab == 0 {
                speedDialOverlay
            }
        }
        .task { await viewModel.onAppear() }
        .onChange(of: selectedTab) { _, _ in
            withAnimation(.easeInOut(duration: 0.18)) {
                isRecordFilterRowVisible = true
            }
        }
        .fullScreenCover(isPresented: $showCompose) {
            NavigationStack {
                RecordComposeView(
                    repository: repository,
                    weatherRepository: weatherRepository,
                    mediaUpload: mediaUpload
                ) { newRecordId in
                    // 작성 완료 → 방금 만든 기록 상세로 이동 + 완료 토스트 + 리스트 갱신.
                    path.append(newRecordId)
                    toastMessage = "영농 기록 작성이 완료되었습니다."
                    Task { await viewModel.reload() }
                }
            }
        }
        .fullScreenCover(isPresented: $showVoiceCompose) {
            RecordVoiceFlowView(
                voiceRepository: voiceRepository,
                recordRepository: repository,
                weatherRepository: weatherRepository,
                mediaUpload: mediaUpload,
                onSessionInvalid: { toastMessage = "이미 처리된 음성 기록이에요." }
            ) { newRecordId in
                // 텍스트 작성 완료와 동일한 마무리: 상세 이동 + 토스트 + 리스트 갱신.
                path.append(newRecordId)
                toastMessage = "영농 기록 작성이 완료되었습니다."
                Task { await viewModel.reload() }
            }
        }
        .fullScreenCover(isPresented: $showSearch) {
            SearchView(container: container)
        }
        .sheet(item: $activeSheet) { kind in
            switch kind {
            case .crop:
                RecordCropFilterSheet(
                    crops: viewModel.activeCrops,
                    selected: viewModel.filter.cropIds
                ) { cropIds in
                    Task { await viewModel.applyCropFilter(cropIds) }
                }
            case .workType:
                RecordWorkTypeFilterSheet(selected: viewModel.filter.workTypes) { workTypes in
                    Task { await viewModel.applyWorkTypeFilter(workTypes) }
                }
            case .dateRange:
                RecordDateFilterSheet(
                    startDate: viewModel.filter.startDate,
                    endDate: viewModel.filter.endDate
                ) { start, end in
                    Task { await viewModel.applyDateFilter(startDate: start, endDate: end) }
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: UUID.self) { recordId in
            RecordDetailView(
                recordId: recordId,
                repository: repository,
                weatherRepository: weatherRepository,
                mediaUpload: mediaUpload
            ) {
                Task { await viewModel.reload() }
            }
        }
        .navigationDestination(for: ReportRoute.self) { route in
            switch route {
            case let .detail(key):
                ReportDetailView(key: key, repository: reportRepository)
                    .toolbar(.hidden, for: .tabBar)
            case let .recordHistory(key):
                ReportRecordHistoryView(key: key)
                    .toolbar(.hidden, for: .tabBar)
            }
        }
    }

    // MARK: - Filter chip row

    private var filterChipRow: some View {
        HStack(spacing: Spacing.sm) {
            filterChip(title: cropChipTitle, isSelected: !viewModel.filter.cropIds.isEmpty) { activeSheet = .crop }
            filterChip(title: workTypeChipTitle, isSelected: !viewModel.filter.workTypes.isEmpty) { activeSheet = .workType }
            filterChip(
                title: dateChipTitle,
                isSelected: viewModel.filter.startDate != nil || viewModel.filter.endDate != nil
            ) { activeSheet = .dateRange }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, horizontalInset)
        .frame(height: 60)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Background.subtle)
    }

    private func filterChip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        AppChip(
            label: title,
            isSelected: isSelected,
            style: .solidPastel,
            trailingSystemImage: .asset("keyboard_arrow_down"),
            action: action
        )
    }

    private var cropChipTitle: String {
        guard let names = viewModel.selectedCropNames, !names.isEmpty else { return "작물" }
        return names.count == 1 ? names[0] : "\(names[0]) 외 \(names.count - 1)"
    }

    private var workTypeChipTitle: String {
        let workTypes = viewModel.filter.workTypes
        guard !workTypes.isEmpty else { return "영농 활동" }
        let labels = WorkType.allCases.filter(workTypes.contains).map(\.label)
        return labels.count == 1 ? labels[0] : "\(labels[0]) 외 \(labels.count - 1)"
    }

    private var dateChipTitle: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        switch (viewModel.filter.startDate, viewModel.filter.endDate) {
        case let (start?, end?):
            return "\(formatter.string(from: start))~\(formatter.string(from: end))"
        case let (start?, nil):
            return "\(formatter.string(from: start))~"
        case let (nil, end?):
            return "~\(formatter.string(from: end))"
        default:
            return "기간"
        }
    }

    // MARK: - List

    private var recordList: some View {
        ZStack(alignment: .top) {
            ScrollView {
                VStack(spacing: 0) {
                    FilterRowPanObserver(isVisible: $isRecordFilterRowVisible)
                        .frame(height: 1)
                    if viewModel.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.top, Spacing.xl)
                    } else if let errorMessage = viewModel.errorMessage {
                        emptyState(text: errorMessage, systemImage: "exclamationmark.triangle")
                    } else if viewModel.records.isEmpty {
                        emptyState(
                            text: viewModel.filter.isEmpty
                                ? "아직 작성한 영농 기록이 없어요.\n오른쪽 아래 + 버튼으로 첫 기록을 남겨보세요."
                                : "선택한 조건에 맞는 기록이 없어요.\n다른 작물이나 기간으로 다시 확인해보세요.",
                            systemImage: "square.stack.3d.up.slash"
                        )
                    } else {
                        LazyVStack(spacing: 20) {
                            ForEach(viewModel.records) { record in
                                NavigationLink(value: record.id) {
                                    RecordRow(record: record)
                                }
                                .buttonStyle(.plain)
                                .task { await viewModel.loadMoreIfNeeded(currentItem: record) }
                            }
                            if viewModel.isLoadingMore {
                                ProgressView().padding(Spacing.md)
                            }
                        }
                        .padding(.top, 20)
                        .padding(.bottom, 112)
                    }
                }
                .padding(.top, isRecordFilterRowVisible ? 60 : 0)
            }
            .refreshable {
                // `reload()`이 fetch 전에 `isLoading = true`로 body 리빌드를 유발하면, `.refreshable`이
                // 소유한 Task가 그 리빌드로 취소되면서 진행 중인 URLSession 호출이 `URLError(.cancelled)`를
                // 던지고, 이것이 "네트워크 연결을 확인해주세요"로 오표시된다. 별도 unstructured Task에서
                // 돌려 취소 경로 밖으로 빼낸다. (HomeView 커밋 7ee27851과 동일한 수정)
                await Task { await viewModel.reload() }.value
            }
            filterChipRow
                .filterRowOverlay(isVisible: isRecordFilterRowVisible)
        }
        .clipped()
    }

    private func emptyState(text: String, systemImage: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }

    // MARK: - Floating record button + 스피드다이얼

    /// FAB 탭 시 딤(#1a1a1a@64%) + 음성/텍스트/닫기 스피드다이얼. (Figma `기록 버튼 탭 시`)
    @ViewBuilder private var speedDialOverlay: some View {
        if isSpeedDialOpen {
            Color.scrim
                .ignoresSafeArea()
                .onTapGesture { closeSpeedDial() }
                .transition(.opacity)
        }
        VStack(alignment: .trailing, spacing: 20) {
            if isSpeedDialOpen {
                speedDialItem(label: "음성으로 기록하기", icon: .asset("mic")) {
                    closeSpeedDial()
                    showVoiceCompose = true
                }
                speedDialItem(label: "텍스트로 기록하기", icon: .asset("edit")) {
                    closeSpeedDial()
                    showCompose = true
                }
            }
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { isSpeedDialOpen.toggle() }
            } label: {
                AppIconView(source: isSpeedDialOpen ? .asset("close") : .asset("add_2"), size: 40)
                    .foregroundStyle(isSpeedDialOpen ? Color.Icon.default : Color.Icon.inverse)
                    .frame(width: 72, height: 72)
                    .background(isSpeedDialOpen ? Color.Object.default : Color.Object.primary)
                    .clipShape(Circle())
                    .overlay {
                        if isSpeedDialOpen { Circle().stroke(Color.Border.default, lineWidth: 1) }
                    }
            }
            .accessibilityLabel(isSpeedDialOpen ? "닫기" : "영농 기록 작성")
        }
        .padding(.trailing, horizontalInset)
        .padding(.bottom, Spacing.xl)
    }

    /// Closes the speed-dial in an animation transaction so the content scrim and the shell's nav-bar
    /// scrim fade out together.
    private func closeSpeedDial() {
        withAnimation(.easeInOut(duration: 0.15)) { isSpeedDialOpen = false }
    }

    private func speedDialItem(label: String, icon: AppIconSource, action: @escaping () -> Void) -> some View {
        HStack(spacing: 16) {
            Text(label)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.inverse)
            Button(action: action) {
                AppIconView(source: icon, size: 28)
                    .foregroundStyle(Color.Icon.inverse)
                    .frame(width: 72, height: 72)
                    .background(Color.Object.primary)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .transition(.move(edge: .trailing).combined(with: .opacity))
    }
}

/// One row in the record list (Figma `list`, size `large`). Maps `RecordSummaryResponse` fields onto the
/// existing `AppListItem(size: .large)`.
///
/// Field mapping (to confirm with product — see capture doc): badges = [작물, workType별 상세값(없으면 숨김)],
/// title = 메모 미리보기, caption = 날씨, date = 작업일(MM/dd), thumbnail = 첨부 사진.
struct RecordRow: View {
    let record: FarmingRecordSummary
    var showsDivider: Bool = true

    var body: some View {
        AppListItem(
            size: .large,
            title: record.workType.label,
            caption: record.memoPreview,
            badges: badges,
            dateText: dateText,
            showsDivider: showsDivider
        ) {
            RecordRemoteImage(url: record.thumbnailUrl, workType: record.workType)
        }
    }

    private var badges: [AppListItemBadge] {
        var badges = [AppListItemBadge(record.cropName, style: .solidPastel, variant: .primary)]
        if let detailLabel = record.detailBadgeLabel {
            badges.append(AppListItemBadge(detailLabel))
        }
        return badges
    }

    private var weatherText: String {
        let condition = record.weatherCondition.trimmingCharacters(in: .whitespaces)
        if condition.isEmpty {
            return "\(record.weatherTemperature)℃"
        }
        return "\(condition) · \(record.weatherTemperature)℃"
    }

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: record.workedAt)
    }
}

/// Fixed-size remote thumbnail with a muted placeholder while loading and a work-type illustration
/// when the URL is absent or fails to load. Mirrors the community list's remote-image slot; kept
/// feature-local.
struct RecordRemoteImage: View {
    let url: String?
    let workType: WorkType
    var illustVariant: AppIllustration.Variant = .square
    var cornerRadius: CGFloat = 8

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Color.Object.muted)
            .overlay {
                if let url, let parsed = URL(string: url) {
                    AsyncImage(url: parsed) { phase in
                        switch phase {
                        case let .success(image):
                            image.resizable().scaledToFill()
                        case .empty:
                            ProgressView()
                        case .failure:
                            illustration
                        @unknown default:
                            illustration
                        }
                    }
                } else {
                    illustration
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }

    private var illustration: some View {
        AppIllustration(assetName: workType.illustAssetName, variant: illustVariant)
    }
}
