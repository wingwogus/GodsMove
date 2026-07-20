//
//  ViewModifiers.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

private struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(Spacing.md)
            .background(Color.appBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

extension View {
    func cardStyle() -> some View {
        modifier(CardStyle())
    }

    /// Resigns the active text field/editor's first-responder status when the user taps
    /// anywhere else on this view. SwiftUI has no built-in "tap outside to dismiss keyboard"
    /// modifier, so this covers screens where the keyboard would otherwise stay up when the
    /// user taps a non-interactive area instead of scrolling.
    func dismissKeyboardOnTap() -> some View {
        onTapGesture {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
            )
        }
    }
}

// MARK: - Fixed bottom CTA + keyboard pattern
//
// Screens with a text field/editor and a fixed bottom CTA (`.safeAreaInset(edge: .bottom)` or
// `.overlay(alignment: .bottom)`) must apply `.ignoresSafeArea(.keyboard, edges: .bottom)` as
// the LAST modifier in the chain — after the modifier that adds the bottom CTA, not before it
// and not nested inside the CTA's own content closure. Applying it earlier, or only to the CTA
// view itself, does not stop the ancestor view from avoiding the keyboard, so the CTA still
// rides up and sticks to the keyboard instead of staying pinned to the screen bottom while the
// keyboard covers it.
//
//     VStack { ... }
//         .safeAreaInset(edge: .bottom) { bottomCTA }   // or .overlay(alignment: .bottom) { ... }
//         .ignoresSafeArea(.keyboard, edges: .bottom)   // must come after, wraps the whole chain
//
// Separately, any keyboard without a Return/완료 key (`.numberPad`, `.phonePad`, etc.) should get
// an explicit dismiss button via the keyboard accessory toolbar, since tap-outside/scroll-to-dismiss
// alone feels unresponsive for those fields:
//
//     .toolbar {
//         ToolbarItemGroup(placement: .keyboard) {
//             Spacer()
//             Button("완료") {
//                 UIApplication.shared.sendAction(
//                     #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
//                 )
//             }
//         }
//     }
