//
//  CropPickerSheet.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// "작물 추가" bottom sheet: search + a multi-select grid of the crop catalog. On 완료하기 it hands the
/// selected crops back to the caller (the feed adds them as browsable chips; the composer adds them as board
/// options). Purely a picker — there's no "follow board" backend endpoint, so selection is caller-local.
struct CropPickerSheet: View {
    let loadCrops: () async -> [Crop]
    let onComplete: ([Crop]) -> Void

    @State private var crops: [Crop] = []
    @State private var selectedIds: Set<UUID> = []
    @State private var searchText = ""
    @State private var isLoading = true
    @Environment(\.dismiss) private var dismiss

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.sm), count: 3)

    private var filteredCrops: [Crop] {
        let keyword = searchText.trimmingCharacters(in: .whitespaces)
        guard !keyword.isEmpty else { return crops }
        return crops.filter { $0.name.localizedCaseInsensitiveContains(keyword) }
    }

    private var selectedCrops: [Crop] {
        crops.filter { selectedIds.contains($0.id) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text("작물 추가")
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                Spacer()
                Button { dismiss() } label: {
                    AppIconView(source: .asset("close"), size: 20)
                        .foregroundStyle(Color.Icon.default)
                }
            }

            AppSearchBar(text: $searchText, placeholder: "작물 검색...")

            if !selectedCrops.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(selectedCrops) { crop in
                            AppChip(label: crop.name, isSelected: true, systemImage: "xmark") {
                                selectedIds.remove(crop.id)
                            }
                        }
                    }
                }
            }

            if isLoading {
                ProgressView().frame(maxWidth: .infinity).padding(.top, Spacing.xl)
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: Spacing.sm) {
                        ForEach(filteredCrops) { crop in
                            cropButton(crop)
                        }
                    }
                }
                .scrollDismissesKeyboard(.interactively)
            }

            AppButton("완료하기", variant: .primary, size: .medium, fullWidth: true) {
                onComplete(selectedCrops)
                dismiss()
            }
            .disabled(selectedIds.isEmpty)
        }
        .padding(Spacing.md)
        .dismissKeyboardOnTap()
        .task {
            crops = await loadCrops()
            isLoading = false
        }
    }

    private func cropButton(_ crop: Crop) -> some View {
        let isSelected = selectedIds.contains(crop.id)
        return Button {
            if isSelected { selectedIds.remove(crop.id) } else { selectedIds.insert(crop.id) }
        } label: {
            Text(crop.name)
                .appTypography(.bodyMedium)
                .foregroundStyle(isSelected ? Color.Text.inverse : Color.Text.subtle)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(isSelected ? Color.Object.bold : Color.Object.muted)
                )
        }
        .buttonStyle(.plain)
    }
}
