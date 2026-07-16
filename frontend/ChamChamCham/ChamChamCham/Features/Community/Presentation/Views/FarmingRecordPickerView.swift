//
//  FarmingRecordPickerView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import SwiftUI

@MainActor
@Observable
final class FarmingRecordPickerState {
    var searchText = "" {
        didSet { clearSelectionIfNeeded() }
    }
    private(set) var selectedCropName: String?
    private(set) var selectedRecordID: UUID?
    let records: [FarmingRecordPreview]

    init(records: [FarmingRecordPreview], selectedRecord: FarmingRecordPreview? = nil) {
        self.records = records
        selectedCropName = selectedRecord?.cropName
        selectedRecordID = selectedRecord?.id
    }

    var cropNames: [String] {
        Array(Set(records.map(\.cropName))).sorted()
    }

    var filteredRecords: [FarmingRecordPreview] {
        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return records.filter { record in
            let cropMatches = selectedCropName == nil || record.cropName == selectedCropName
            let keywordMatches = keyword.isEmpty
                || record.title.localizedCaseInsensitiveContains(keyword)
                || record.caption.localizedCaseInsensitiveContains(keyword)
                || record.cropName.localizedCaseInsensitiveContains(keyword)
            return cropMatches && keywordMatches
        }
    }

    var selectedRecord: FarmingRecordPreview? {
        records.first { $0.id == selectedRecordID }
    }

    func selectCrop(_ cropName: String?) {
        selectedCropName = cropName
        clearSelectionIfNeeded()
    }

    func selectRecord(_ id: UUID) {
        selectedRecordID = id
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

    @Binding private var selectedRecord: FarmingRecordPreview?
    @State private var state: FarmingRecordPickerState
    @Environment(\.dismiss) private var dismiss

    init(selectedRecord: Binding<FarmingRecordPreview?>) {
        _selectedRecord = selectedRecord
        _state = State(
            initialValue: FarmingRecordPickerState(
                records: FarmingRecordPreview.samples,
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
                leading: .init(.asset("chevron_backward")) { dismiss() }
            )

            filterArea(state: state)

            ScrollView {
                LazyVStack(spacing: Layout.cardSpacing) {
                    if state.filteredRecords.isEmpty {
                        EmptyStateView(message: "조건에 맞는 기록이 없어요.")
                            .padding(.top, Spacing.xl)
                    } else {
                        ForEach(state.filteredRecords) { record in
                            Button {
                                state.selectRecord(record.id)
                            } label: {
                                AppCard(
                                    size: .small,
                                    title: record.title,
                                    captions: [record.caption],
                                    badges: [record.cropName, record.category],
                                    dateText: record.dateText,
                                    isSelected: state.selectedRecordID == record.id
                                ) {
                                    FarmingRecordImage(record: record, height: 96)
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
                        isSelected: state.selectedCropName == nil,
                        style: .solid
                    ) {
                        state.selectCrop(nil)
                    }

                    ForEach(state.cropNames, id: \.self) { cropName in
                        AppChip(
                            label: cropName,
                            isSelected: state.selectedCropName == cropName,
                            style: .solid
                        ) {
                            state.selectCrop(cropName)
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

struct FarmingRecordImage: View {
    let record: FarmingRecordPreview
    let height: CGFloat

    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(
                LinearGradient(
                    colors: [Color(hex: record.imageStartHex), Color(hex: record.imageEndHex)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .overlay {
                Image(systemName: "leaf.fill")
                    .font(.system(size: height > 90 ? 34 : 28))
                    .foregroundStyle(Color.Text.inverse.opacity(0.8))
            }
            .overlay(alignment: .top) {
                LinearGradient(
                    colors: [.black.opacity(0.28), .clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: height * 0.42)
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct FarmingRecordPreview: Identifiable, Hashable, Sendable {
    let id: UUID
    let dateText: String
    let cropName: String
    let category: String
    let title: String
    let caption: String
    let imageStartHex: UInt32
    let imageEndHex: UInt32

    static let samples: [FarmingRecordPreview] = [
        .init(
            id: UUID(uuidString: "0FC55A80-93F2-4C74-939B-B424842AD35C")!,
            dateText: "07/08",
            cropName: "딸기",
            category: "필수 데이터",
            title: "영농 활동",
            caption: "관수량과 생육 상태 기록",
            imageStartHex: 0x7FE19E,
            imageEndHex: 0x27865C
        ),
        .init(
            id: UUID(uuidString: "5C2877A9-0EA3-4851-860B-E33942D6D97E")!,
            dateText: "07/07",
            cropName: "토마토",
            category: "필수 데이터",
            title: "타이틀",
            caption: "병해충 확인 및 방제 메모",
            imageStartHex: 0xBAED4F,
            imageEndHex: 0x699018
        ),
        .init(
            id: UUID(uuidString: "2F41D692-A887-422E-9DDF-0EEAD2C63C8D")!,
            dateText: "07/06",
            cropName: "오이",
            category: "필수 데이터",
            title: "영농 활동 카테고리",
            caption: "작성 내용은 최대 2줄입니다.",
            imageStartHex: 0xC6F1CB,
            imageEndHex: 0x38C284
        ),
        .init(
            id: UUID(uuidString: "A0833192-76DF-48C4-AFCB-38EF4F36E3FE")!,
            dateText: "07/05",
            cropName: "상추",
            category: "필수 데이터",
            title: "토양 관리",
            caption: "멀칭 상태와 수분 보유량 확인",
            imageStartHex: 0xE6F7BF,
            imageEndHex: 0x8CC610
        ),
        .init(
            id: UUID(uuidString: "7C533075-A8DC-44B8-B4BE-E1EEC1BB30BF")!,
            dateText: "07/04",
            cropName: "딸기",
            category: "필수 데이터",
            title: "영농 활동 카테고리",
            caption: "작성 내용은 최대 2줄입니다. 작성 내용은 최대 2줄입니다.",
            imageStartHex: 0x95DFA5,
            imageEndHex: 0x33966A
        ),
        .init(
            id: UUID(uuidString: "D0A84045-3563-4D90-9DC7-51B5FDB25586")!,
            dateText: "07/03",
            cropName: "딸기",
            category: "필수 데이터",
            title: "영농 활동 카테고리",
            caption: "작성 내용은 최대 2줄입니다. 작성 내용은 최대 2줄입니다.",
            imageStartHex: 0xC2EBC9,
            imageEndHex: 0x55A977
        ),
    ]
}
