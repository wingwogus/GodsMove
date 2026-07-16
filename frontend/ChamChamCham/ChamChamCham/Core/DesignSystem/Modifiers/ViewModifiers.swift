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
