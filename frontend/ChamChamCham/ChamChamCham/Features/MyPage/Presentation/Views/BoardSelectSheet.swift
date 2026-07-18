//
//  BoardSelectSheet.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// Figma `bottom-sheet / 게시판 선택 시`. Multi-select crop board filter for the profile post list,
/// grouped into 진행중인 작물 / 기타 작물. Confirms with `완료`, returning the chosen board ids (empty
/// set to clear). Tapping a selected chip again removes it from the selection.
struct BoardSelectSheet: View {
    let activeBoards: [CommunityBoard]
    let otherBoards: [CommunityBoard]
    var isLoading: Bool = false
    let initialSelection: Set<UUID>
    let onComplete: (Set<UUID>) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selection: Set<UUID>

    init(
        activeBoards: [CommunityBoard],
        otherBoards: [CommunityBoard],
        isLoading: Bool = false,
        initialSelection: Set<UUID>,
        onComplete: @escaping (Set<UUID>) -> Void
    ) {
        self.activeBoards = activeBoards
        self.otherBoards = otherBoards
        self.isLoading = isLoading
        self.initialSelection = initialSelection
        self.onComplete = onComplete
        _selection = State(initialValue: initialSelection)
    }

    var body: some View {
        VStack(spacing: 0) {
            grabber

            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("게시판 선택")
                    .appTypography(.titleMediumEmphasized)
                    .foregroundStyle(Color.Text.default)

                if isLoading {
                    LoadingView().frame(maxWidth: .infinity)
                } else {
                    section(title: "진행중인 작물", boards: activeBoards)
                    if !otherBoards.isEmpty {
                        Divider().overlay(Color.Border.default)
                        section(title: "기타 작물", boards: otherBoards)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.top, Spacing.md)

            Spacer(minLength: Spacing.md)

            AppButton("완료", variant: .secondary, size: .medium, fullWidth: true) {
                onComplete(selection)
                dismiss()
            }
            .padding(.horizontal, Spacing.lg - Spacing.xs)
            .padding(.bottom, Spacing.lg)
        }
        .background(Color.Object.default)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.hidden)
    }

    private var grabber: some View {
        Capsule()
            .fill(Color.Border.default)
            .frame(width: 48, height: 4)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.md)
    }

    @ViewBuilder private func section(title: String, boards: [CommunityBoard]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(title)
                .appTypography(.bodyLarge)
                .foregroundStyle(Color.Text.subtle)

            if boards.isEmpty {
                Text("해당 작물이 없어요.")
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
            } else {
                AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm, alignment: .leading) {
                    ForEach(boards) { board in
                        AppChip(
                            label: board.cropName,
                            isSelected: selection.contains(board.cropId),
                            style: .solidPastel
                        ) {
                            if !selection.insert(board.cropId).inserted {
                                selection.remove(board.cropId)
                            }
                        }
                    }
                }
            }
        }
    }
}
