//
//  CropPickerView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Full-screen "작물 선택" picker. Reuses the onboarding crop-selection layout (`CropSelectionBody`)
/// but is self-contained: it loads its own catalog and owns a local selection, handing the chosen
/// crops back to the caller on 완료. Purely a picker — selection is caller-local (the feed adds them
/// as browsable chips, the composer as board options, farm-add as the farm's crops).
struct CropPickerView: View {
    let loadCrops: @MainActor () async -> [Crop]
    let loadCategories: @MainActor () async -> [CropCategory]
    /// Crops that should start selected (e.g. re-opening the picker on an existing farm).
    var initialSelection: [Crop] = []
    /// Maximum selectable crops, or `nil` for unlimited.
    var selectionLimit: Int? = nil
    let onComplete: ([Crop]) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var crops: [Crop] = []
    @State private var categories: [CropCategory] = []
    @State private var selectedCropIDs: [UUID] = []
    @State private var isLoading = true
    @State private var didSeedSelection = false

    private var subtitle: String {
        if let selectionLimit {
            return "재배 중인 작물을 검색해 선택해주세요.\n작물은 최대 \(selectionLimit)개까지 선택 가능합니다."
        }
        return "재배 중인 작물을 검색해 선택해주세요."
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "작물 선택",
                isDetail: true,
                leading: .init(.asset("close")) { dismiss() }
            )

            CropSelectionBody(
                title: "작물 선택하기",
                subtitle: subtitle,
                crops: crops,
                categories: categories,
                isLoading: isLoading,
                loadError: nil,
                onRetry: { Task { await load() } },
                selectedCropIDs: selectedCropIDs,
                selectionLimit: selectionLimit,
                onToggle: toggle,
                ctaTitle: "완료하기",
                onComplete: {
                    onComplete(selectedCrops)
                    dismiss()
                }
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.Background.default)
        .task {
            if !didSeedSelection {
                selectedCropIDs = initialSelection.map(\.id)
                didSeedSelection = true
            }
            await load()
        }
    }

    private var selectedCrops: [Crop] {
        selectedCropIDs.compactMap { id in crops.first { $0.id == id } }
    }

    private func load() async {
        isLoading = true
        crops = await loadCrops()
        categories = await loadCategories()
        isLoading = false
    }

    private func toggle(_ cropID: UUID) -> CropSelectionBody.ToggleOutcome {
        if let index = selectedCropIDs.firstIndex(of: cropID) {
            selectedCropIDs.remove(at: index)
            return .changed
        }
        if let selectionLimit, selectedCropIDs.count >= selectionLimit {
            return .limitReached
        }
        selectedCropIDs.append(cropID)
        return .changed
    }
}
