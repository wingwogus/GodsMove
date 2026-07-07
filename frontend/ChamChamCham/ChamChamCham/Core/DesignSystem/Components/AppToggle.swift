//
//  AppToggle.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `toggle`. A 56×32 pill switch: on = green (`object/primary`), off = dark
/// (`object/bold`), both grey out when disabled via the `.disabled(_:)` environment.
struct AppToggle: View {
    @Binding var isOn: Bool

    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Capsule()
            .fill(trackColor)
            .frame(width: 56, height: 32)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle()
                    .fill(knobColor)
                    .frame(width: 28, height: 28)
                    .padding(2)
            }
            .animation(.easeInOut(duration: 0.15), value: isOn)
            .contentShape(Capsule())
            .onTapGesture {
                guard isEnabled else { return }
                isOn.toggle()
            }
    }

    private var trackColor: Color {
        if !isEnabled { return Color.Object.disabled }
        return isOn ? Color.Object.primary : Color.Object.bold
    }

    private var knobColor: Color {
        isEnabled ? Color.Object.default : Color.Object.disabledSubtle
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
