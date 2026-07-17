//
//  SearchHistoryView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Idle state (검색어 미입력): "최근 검색어" header + "전체 삭제" + a wrap grid of chips, or a plain
/// empty line when there's no history yet (Figma captures 1/2).
struct SearchHistoryView: View {
    let recentTerms: [RecentSearchTerm]
    let onTapTerm: (RecentSearchTerm) -> Void
    let onDeleteTerm: (RecentSearchTerm) -> Void
    let onClearAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            header
            if recentTerms.isEmpty {
                Text("최근 검색어가 없어요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
            } else {
                AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
                    ForEach(recentTerms) { term in
                        chip(for: term)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(20)
    }

    private var header: some View {
        HStack {
            Text("최근 검색어")
                .appTypography(.titleLargeEmphasized)
                .foregroundStyle(Color.Text.default)
            Spacer()
            Button("전체 삭제", action: onClearAll)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.subtle)
                .disabled(recentTerms.isEmpty)
        }
    }

    private func chip(for term: RecentSearchTerm) -> some View {
        AppChip(
            label: term.keyword,
            isSelected: false,
            style: .solidPastel,
            trailingSystemImage: .asset("close_small")
        ) {
            onTapTerm(term)
        }
        // `AppChip`'s trailing icon isn't a separate tap target — overlay an invisible button over
        // it so the close icon deletes instead of re-searching (same trick `CommunityPostRow` uses
        // for its like-button hit area).
        .overlay(alignment: .trailing) {
            Button {
                onDeleteTerm(term)
            } label: {
                Color.clear.frame(width: 32, height: 32).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(term.keyword) 삭제")
        }
    }
}
