//
//  AppToast.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `toast` message. The dark variant is for positive/neutral feedback; `error` uses the
/// red semantic tokens.
struct AppToast: View {
    enum Variant {
        case success
        case error
    }

    let message: String
    var variant: Variant = .success

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(iconColor)

            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(textColor)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, Spacing.md)
        .frame(minHeight: 64)
        .background(backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var iconName: String {
        variant == .error ? "exclamationmark.circle.fill" : "checkmark.circle.fill"
    }

    private var backgroundColor: Color {
        variant == .error ? Color.Object.redSubtle : Color.Object.bold
    }

    private var textColor: Color {
        variant == .error ? Color.Text.red : Color.Text.inverse
    }

    private var iconColor: Color {
        variant == .error ? Color.Icon.red : Color.Icon.inverse
    }
}

/// Overlays a bottom `AppToast` that shows while `message != nil`, then auto-clears after `duration`.
/// Modeled on `RecordToastModifier` (`Features/Record/Presentation/Views/RecordToast.swift`) — that one
/// stays feature-local to Record; this is the DS-promoted version for any other screen.
private struct AppToastModifier: ViewModifier {
    @Binding var message: String?
    var variant: AppToast.Variant = .success
    var duration: Duration = .seconds(2)

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let message {
                    AppToast(message: message, variant: variant)
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
    /// Bottom auto-dismissing `AppToast` bound to an optional message.
    func appToast(message: Binding<String?>, variant: AppToast.Variant = .success) -> some View {
        modifier(AppToastModifier(message: message, variant: variant))
    }
}

#Preview {
    VStack(spacing: Spacing.md) {
        AppToast(message: "메시지가 표시됩니다.")
            .frame(width: 350)
        AppToast(message: "메시지가 표시됩니다.", variant: .error)
            .frame(width: 350)
    }
    .padding()
    .background(Color.Background.subtle)
}
