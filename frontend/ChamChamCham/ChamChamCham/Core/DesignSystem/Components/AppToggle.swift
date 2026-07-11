//
//  AppToggle.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `toggle`. A 48 x 28 pill switch: on = green (`object/primary`), off =
/// `object/strong`, and disabled states use a muted thumb.
struct AppToggle: View {
    static let trackSize = CGSize(width: 48, height: 28)
    static let thumbSize: CGFloat = 24

    @Binding var isOn: Bool

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Capsule()
            .fill(Self.trackColor(isOn: isOn, isEnabled: isEnabled))
            .frame(width: Self.trackSize.width, height: Self.trackSize.height)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle()
                    .fill(Self.thumbColor(isEnabled: isEnabled))
                    .frame(width: Self.thumbSize, height: Self.thumbSize)
                    .padding(2)
            }
            .animation(.easeInOut(duration: 0.15), value: isOn)
            .contentShape(Capsule())
            .onTapGesture {
                guard isEnabled else { return }
                isOn.toggle()
            }
    }

    static func trackColor(isOn: Bool, isEnabled: Bool) -> Color {
        if !isEnabled { return Color.Object.strong }
        return isOn ? Color.Object.primary : Color.Object.strong
    }

    static func thumbColor(isEnabled: Bool) -> Color {
        isEnabled ? Color.Object.default : Color.Object.muted
    }
}

#Preview {
    struct Demo: View {
        @State private var on = true
        @State private var off = false
        var body: some View {
            VStack(spacing: Spacing.lg) {
                AppToggle(isOn: $on)
                AppToggle(isOn: $off)
                AppToggle(isOn: $on).disabled(true)
                AppToggle(isOn: $off).disabled(true)
            }
            .padding()
        }
    }
    return Demo()
}
