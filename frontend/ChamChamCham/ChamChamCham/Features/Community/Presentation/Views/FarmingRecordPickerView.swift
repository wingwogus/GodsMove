//
//  FarmingRecordPickerView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import SwiftUI

/// Backs the "영농 기록 첨부하기" full-screen picker with the real 영농일지 data source. Crop filtering
/// re-queries the server (mirrors `RecordListViewModel`); keyword filtering is applied client-side over the
/// currently loaded page since it's just narrowing what's already on screen.
@MainActor
@Observable
final class FarmingRecordPickerState {
    var searchText = "" {
        didSet { clearSelectionIfNeeded() }
    }
    private(set) var selectedCropId: UUID?
    private(set) var selectedRecordID: UUID?
    private(set) var activeCrops: [ActiveCrop] = []
    private(set) var records: [FarmingRecordSummary] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let repository: any RecordRepository

    init(repository: any RecordRepository, selectedRecord: FarmingRecordSummary? = nil) {
        self.repository = repository
        selectedCropId = selectedRecord?.cropId
        selectedRecordID = selectedRecord?.id
    }

    var filteredRecords: [FarmingRecordSummary] {
        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else { return records }
        return records.filter { record in
            record.workType.label.localizedCaseInsensitiveContains(keyword)
                || record.memoPreview.localizedCaseInsensitiveContains(keyword)
                || record.cropName.localizedCaseInsensitiveContains(keyword)
        }
    }

    var selectedRecord: FarmingRecordSummary? {
        records.first { $0.id == selectedRecordID }
    }

    func load() async {
        guard records.isEmpty, activeCrops.isEmpty, !isLoading else { return }
        await reload()
    }

    func selectCrop(_ cropId: UUID?) async {
        guard selectedCropId != cropId else { return }
        selectedCropId = cropId
        await reload()
    }

    func selectRecord(_ id: UUID) {
        selectedRecordID = (selectedRecordID == id) ? nil : id
    }

    private func reload() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            async let cropsTask = repository.fetchActiveCrops()
            let filter = RecordFilter(cropIds: selectedCropId.map { [$0] } ?? [])
            let page = try await repository.fetchRecords(RecordQuery(filter: filter))
            records = page.items
            activeCrops = (try? await cropsTask) ?? activeCrops
            clearSelectionIfNeeded()
        } catch {
            records = []
            errorMessage = "영농 기록을 불러오지 못했어요."
        }
    }

    private func clearSelectionIfNeeded() {
        guard let selectedRecordID else { return }
        if !filteredRecords.contains(where: { $0.id == selectedRecordID }) {
            self.selectedRecordID = nil
        }
    }
}

struct FarmingRecordPickerView: View {
    enum Layout {
        static let horizontalInset: CGFloat = 20
        static let filterTopInset: CGFloat = 16
        static let chipAreaHeight: CGFloat = 64
        static let cardSpacing: CGFloat = 16
        static let listTopInset: CGFloat = 8
    }

    @Binding private var selectedRecord: FarmingRecordSummary?
    @State private var state: FarmingRecordPickerState
    @Environment(\.dismiss) private var dismiss

    init(repository: any RecordRepository, selectedRecord: Binding<FarmingRecordSummary?>) {
        _selectedRecord = selectedRecord
        _state = State(
            initialValue: FarmingRecordPickerState(
                repository: repository,
                selectedRecord: selectedRecord.wrappedValue
            )
        )
    }

    var body: some View {
        @Bindable var state = state

        VStack(spacing: 0) {
            AppTopAppBar(
                title: "영농 기록 첨부하기",
                isDetail: true,
                showBorder: false,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )

            filterArea(state: state)

            ScrollView {
                LazyVStack(spacing: Layout.cardSpacing) {
                    if state.isLoading && state.records.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.top, Spacing.xl)
                    } else if let errorMessage = state.errorMessage {
                        EmptyStateView(message: errorMessage)
                            .padding(.top, Spacing.xl)
                    } else if state.filteredRecords.isEmpty {
                        EmptyStateView(message: "조건에 맞는 기록이 없어요.")
                            .padding(.top, Spacing.xl)
                    } else {
                        ForEach(state.filteredRecords) { record in
                            Button {
                                state.selectRecord(record.id)
                            } label: {
                                AppCard(
                                    size: .small,
                                    title: record.workType.label,
                                    captions: [record.memoPreview],
                                    badges: [record.cropName],
                                    dateText: Self.dateText(for: record),
                                    isSelected: state.selectedRecordID == record.id
                                ) {
                                    RecordRemoteImage(url: record.thumbnailUrl, workType: record.workType)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, Layout.horizontalInset)
                .padding(.top, Layout.listTopInset)
                .padding(.bottom, Spacing.lg)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color.Background.default)
        .dismissKeyboardOnTap()
        .safeAreaInset(edge: .bottom) {
            selectBar
        }
        // 키보드가 하단 "선택" 바를 밀어 올리지 않도록 고정한다 (SearchView와 동일 패턴).
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .task { await state.load() }
    }

    static func dateText(for record: FarmingRecordSummary) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM/dd"
        return formatter.string(from: record.workedAt)
    }

    private func filterArea(state: FarmingRecordPickerState) -> some View {
        @Bindable var state = state

        return VStack(spacing: 0) {
            AppSearchBar(text: $state.searchText, placeholder: "어떤 기록을 올릴까요?")
                .padding(.horizontal, Layout.horizontalInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    AppChip(
                        label: "전체",
                        isSelected: state.selectedCropId == nil,
                        style: .solid
                    ) {
                        Task { await state.selectCrop(nil) }
                    }

                    ForEach(state.activeCrops) { crop in
                        AppChip(
                            label: crop.name,
                            isSelected: state.selectedCropId == crop.id,
                            style: .solid
                        ) {
                            Task { await state.selectCrop(crop.id) }
                        }
                    }
                }
                .padding(.horizontal, Layout.horizontalInset)
            }
            .frame(height: Layout.chipAreaHeight)
        }
        .padding(.top, Layout.filterTopInset)
    }

    private var selectBar: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(Color.Border.subtle)
                .frame(height: 1)

            AppButton(
                "선택",
                variant: .secondary,
                size: .medium,
                fullWidth: true
            ) {
                selectedRecord = state.selectedRecord
                dismiss()
            }
            .disabled(state.selectedRecordID == nil)
            .padding(.horizontal, Layout.horizontalInset)
            .padding(.vertical, 12)
        }
        .background(Color.Background.default)
    }
}
