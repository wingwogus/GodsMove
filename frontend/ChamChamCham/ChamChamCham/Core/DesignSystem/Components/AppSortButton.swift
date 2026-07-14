//
//  AppSortButton.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// Figma `sort`. A compact, chrome-less sort trigger: a subtle label plus a chevron that points up
/// while expanded and down while collapsed. Pair it with a menu/sheet of sort options.
struct AppSortButton: View {
    let title: String
    var isExpanded: Bool = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            HStack(spacing: 0) {
                Text(title)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.subtle)
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.Icon.subtle)
                    .frame(width: 24, height: 24)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    struct Demo: View {
        @State private var expanded = false
        var body: some View {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                AppSortButton(title: "최신순", isExpanded: false)
                AppSortButton(title: "최신순", isExpanded: true)
                AppSortButton(title: "최신순", isExpanded: expanded) { expanded.toggle() }
            }
            .padding()
        }
    }
    return Demo()
}
