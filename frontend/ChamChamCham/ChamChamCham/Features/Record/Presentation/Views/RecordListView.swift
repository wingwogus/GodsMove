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
/// Scope: this is the read-only list from the captured screens. The 리포트 tab, search/notification icons, and
/// the "+" compose flow are not captured yet, so they render as placeholders / inert affordances.
struct RecordListView: View {
    private let repository: any RecordRepository
    private let mediaUpload: any MediaUploadRepository
    @State private var viewModel: RecordListViewModel
    @State private var selectedTab = 0
    @State private var activeSheet: RecordFilterKind?
    /// Owned by the shell (`MainTabView`) so the same flag dims both the content region *and* the
    /// nav bar in one animation transaction — the two scrims read the same value and fade together.
    @Binding private var isSpeedDialOpen: Bool
    @State private var showCompose = false
    @State private var path: [UUID] = []
    @State private var toastMessage: String?
    private let horizontalInset: CGFloat = 20

    init(
        repository: any RecordRepository,
        mediaUpload: any MediaUploadRepository,
        isSpeedDialOpen: Binding<Bool>
    ) {
        self.repository = repository
        self.mediaUpload = mediaUpload
        _isSpeedDialOpen = isSpeedDialOpen
        _viewModel = State(initialValue: RecordListViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack(path: $path) {
            recordTabContent
        }
        .recordToast(message: $toastMessage)
    }

    private var recordTabContent: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                AppTopAppBar(
                    title: "영농 기록",
                    showBorder: false,
                    trailing: [.init(.asset("search")), .init(.asset("notifications"))]
                )
                AppTabBar(titles: ["기록", "리포트"], selection: $selectedTab)
                    .frame(height: 56)

                if selectedTab == 0 {
                    filterChipRow
                    recordList
                } else {
                    reportPlaceholder
                }
            }
            if selectedTab == 0 {
                speedDialOverlay
            }
        }
        .task { await viewModel.onAppear() }
        .fullScreenCover(isPresented: $showCompose) {
            NavigationStack {
                RecordComposeView(repository: repository, mediaUpload: mediaUpload) { newRecordId in
                    // 작성 완료 → 방금 만든 기록 상세로 이동 + 완료 토스트 + 리스트 갱신.
                    path.append(newRecordId)
                    toastMessage = "영농 기록 작성이 완료되었습니다."
                    Task { await viewModel.reload() }
                }
            }
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
            RecordDetailView(recordId: recordId, repository: repository) {
                Task { await viewModel.reload() }
            }
            .toolbar(.hidden, for: .tabBar)
        }
    }

    // MARK: - Filter chip row

    private var filterChipRow: some View {
        HStack(spacing: Spacing.sm) {
            filterChip(title: cropChipTitle) { activeSheet = .crop }
            filterChip(title: workTypeChipTitle) { activeSheet = .workType }
            filterChip(title: dateChipTitle) { activeSheet = .dateRange }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, horizontalInset)
        .frame(height: 60)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Background.subtle)
    }

    private func filterChip(title: String, action: @escaping () -> Void) -> some View {
        AppChip(
            label: title,
            isSelected: false,
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
        ScrollView {
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.xl)
            } else if let errorMessage = viewModel.errorMessage {
                emptyState(text: errorMessage, systemImage: "exclamationmark.triangle")
            } else if viewModel.records.isEmpty {
                emptyState(text: "아직 영농 기록이 없어요.", systemImage: "square.stack.3d.up.slash")
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
        .refreshable { await viewModel.reload() }
    }

    private func emptyState(text: String, systemImage: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: systemImage)
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }

    // MARK: - 리포트 tab (not captured yet)

    private var reportPlaceholder: some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "chart.bar.doc.horizontal")
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text("리포트는 준비 중이에요.")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                    // 음성 기록 화면 미수집 — 후속.
                    closeSpeedDial()
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
/// Field mapping (to confirm with product — see capture doc): badges = [작물, 활동유형], title = 메모 미리보기,
/// caption = 날씨, date = 작업일(MM/dd), thumbnail = 첨부 사진.
struct RecordRow: View {
    let record: FarmingRecordSummary

    var body: some View {
        AppListItem(
            size: .large,
            title: record.workType.label,
            caption: record.memoPreview,
            badges: [
                AppListItemBadge(record.cropName, style: .solidPastel, variant: .primary),
                AppListItemBadge(record.workType.label),
            ],
            dateText: dateText
        ) {
            RecordRemoteImage(url: record.thumbnailUrl)
        }
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

/// Fixed-size remote thumbnail with a muted placeholder while loading / when absent. Mirrors the community
/// list's remote-image slot; kept feature-local.
struct RecordRemoteImage: View {
    let url: String?
    var cornerRadius: CGFloat = 8

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Color.Object.muted)
            .overlay {
                if let url, let parsed = URL(string: url) {
                    AsyncImage(url: parsed) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                } else {
                    AppIconView(source: .asset("photo"), size: 24)
                        .foregroundStyle(Color.Icon.disabled)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}
