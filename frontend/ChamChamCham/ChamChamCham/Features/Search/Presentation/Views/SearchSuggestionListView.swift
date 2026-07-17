//
//  SearchSuggestionListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Typing state (검색어 입력 중, Figma capture 3): a flat list built from `keywords[0]`(동일 검색어,
/// always first — the backend guarantees this, so the list is never re-sorted here) +
/// `keywords[1...]`(연관 검색어) — Figma shows zero visual/structural difference between them, so
/// every row renders identically; tapping any row fills the search bar and submits immediately.
/// The portion of each suggestion matching the live `query` is bolded (real-time as the user types).
struct SearchSuggestionListView: View {
    let suggestions: [String]
    let query: String
    let onTapSuggestion: (String) -> Void

    var body: some View {
        // Scrollable so a long suggestion list can extend past the keyboard and still be reached;
        // the parent applies `.scrollDismissesKeyboard(.immediately)`.
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(Array(suggestions.enumerated()), id: \.offset) { index, term in
                    Button {
                        onTapSuggestion(term)
                    } label: {
                        HStack {
                            highlightedText(term)
                                .foregroundStyle(Color.Text.subtle)
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .frame(height: 58)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .overlay(alignment: .bottom) {
                        if index != suggestions.count - 1 {
                            Rectangle().fill(Color.Border.default).frame(height: 1)
                        }
                    }
                }
            }
        }
    }

    /// Splits `term` around the first case-insensitive occurrence of `query` and renders that
    /// slice with the emphasized (bold-ish) weight, everything else at the regular weight — the
    /// same Medium/SemiBold pairing `AppTabBar` already uses for unselected/selected labels.
    private func highlightedText(_ term: String) -> Text {
        guard !query.isEmpty, let range = term.range(of: query, options: [.caseInsensitive]) else {
            return textSegment(term, style: .titleMedium)
        }
        var result = Text("")
        let prefix = String(term[term.startIndex..<range.lowerBound])
        let match = String(term[range])
        let suffix = String(term[range.upperBound...])
        if !prefix.isEmpty { result = result + textSegment(prefix, style: .titleMedium) }
        result = result + textSegment(match, style: .titleMediumEmphasized)
        if !suffix.isEmpty { result = result + textSegment(suffix, style: .titleMedium) }
        return result
    }

    private func textSegment(_ string: String, style: AppTypography) -> Text {
        Text(string)
            .font(style.font)
            .tracking(style.size * style.trackingPercent)
    }
}
