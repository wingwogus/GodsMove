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
        variant == .error ? Color.Icon.error : Color.Icon.inverse
    }
}

#Preview {
    HStack(spacing: Spacing.md) {
        AppToast(message: "메시지가 표시됩니다.")
            .frame(width: 350)
        AppToast(message: "메시지가 표시됩니다.", variant: .error)
            .frame(width: 350)
    }
    .padding()
    .background(Color.Background.subtle)
}
