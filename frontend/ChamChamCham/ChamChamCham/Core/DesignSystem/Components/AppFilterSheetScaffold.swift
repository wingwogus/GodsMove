//
//  AppFilterSheetScaffold.swift
//  ChamChamCham
//
//  Created by iyungui on 7/18/26.
//

import SwiftUI

/// Common bottom-sheet chrome for filter pickers: title (SemiBold 20) + content + full-width 완료
/// button. The drag grabber and background dim come from the system sheet presentation.
///
/// Pass a fixed `height` for content whose size is known upfront (e.g. a fixed set of chips) — the
/// sheet renders at exactly that height with no internal scrolling. Pass `nil` for content whose
/// length isn't bounded (e.g. a user's full farm/crop list) to fall back to `.medium`/`.large`
/// detents with the content wrapped in a `ScrollView`.
struct AppFilterSheetScaffold<Content: View>: View {
    let title: String
    var height: CGFloat? = nil
    @ViewBuilder let content: Content
    let onComplete: () -> Void
    var isCompleteDisabled: () -> Bool = { false }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            if height != nil {
                content
            } else {
                ScrollView {
                    content
                }
                .scrollIndicators(.hidden)
            }

            AppButton("완료", variant: .secondary, size: .medium, fullWidth: true, action: onComplete)
                .disabled(isCompleteDisabled())
                .padding(.top, Spacing.md)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(FilterSheetDetents(height: height))
        .presentationDragIndicator(.visible)
    }
}

private struct FilterSheetDetents: ViewModifier {
    let height: CGFloat?

    func body(content: Content) -> some View {
        if let height {
            content.presentationDetents([.height(height)])
        } else {
            content.presentationDetents([.medium, .large])
        }
    }
}
