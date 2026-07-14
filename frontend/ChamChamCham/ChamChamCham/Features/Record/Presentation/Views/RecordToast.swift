//
//  RecordToast.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// Figma `toast-bar` (node `1520:22391`): dark bar + check icon + white message, auto-dismissing. Kept
/// feature-local for now (first use). Promote to `Core/DesignSystem` if a second feature needs the same toast.
struct RecordToastBar: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            AppIconView(source: .asset("check_circle"), size: 24)
                .foregroundStyle(Color.Icon.inverse)
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.inverse)
                .lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 48)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Object.bold) // #343434
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

/// Overlays a bottom toast that shows while `message != nil`, then auto-clears after `duration`.
private struct RecordToastModifier: ViewModifier {
    @Binding var message: String?
    var duration: Duration = .seconds(2)

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message {
                    RecordToastBar(message: message)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 24)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .task(id: message) {
                            try? await Task.sleep(for: duration)
                            withAnimation { self.message = nil }
                        }
                }
            }
            .animation(.easeInOut(duration: 0.2), value: message)
    }
}

extension View {
    /// Bottom auto-dismissing toast bound to an optional message.
    func recordToast(message: Binding<String?>) -> some View {
        modifier(RecordToastModifier(message: message))
    }
}
