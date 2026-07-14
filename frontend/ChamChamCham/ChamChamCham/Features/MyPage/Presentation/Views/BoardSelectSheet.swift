//
//  BoardSelectSheet.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// Figma `bottom-sheet / 게시판 선택 시`. Single-select crop board filter for the profile post list,
/// grouped into 진행중인 작물 / 기타 작물. Confirms with `완료`, returning the chosen board id (or nil
/// to clear). Tapping the selected chip again clears the selection.
struct BoardSelectSheet: View {
    let activeBoards: [CommunityBoard]
    let otherBoards: [CommunityBoard]
    var isLoading: Bool = false
    let initialSelection: UUID?
    let onComplete: (UUID?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selection: UUID?

    init(
        activeBoards: [CommunityBoard],
        otherBoards: [CommunityBoard],
        isLoading: Bool = false,
        initialSelection: UUID?,
        onComplete: @escaping (UUID?) -> Void
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
                ChipFlowLayout(spacing: Spacing.sm) {
                    ForEach(boards) { board in
                        AppChip(
                            label: board.cropName,
                            isSelected: selection == board.cropId,
                            style: .solidPastel
                        ) {
                            selection = (selection == board.cropId) ? nil : board.cropId
                        }
                    }
                }
            }
        }
    }
}

/// Wraps chips onto multiple rows (left-aligned). A small one-off `Layout`; there is no shared flow
/// layout in the design system yet.
private struct ChipFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth > 0, rowWidth + spacing + size.width > maxWidth {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth)
                rowWidth = size.width
                rowHeight = size.height
            } else {
                rowWidth += (rowWidth > 0 ? spacing : 0) + size.width
                rowHeight = max(rowHeight, size.height)
            }
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let maxX = bounds.maxX
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
