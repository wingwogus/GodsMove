//
//  AppImageUploadSlot.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `image` upload slot. Empty state shows the camera affordance; filled state accepts a
/// thumbnail view and can expose a remove action.
struct AppImageUploadSlot<Thumbnail: View>: View {
    var size: CGFloat = 96
    var label: String = "n/n"
    var onTap: () -> Void = {}
    var onRemove: (() -> Void)? = nil

    private let thumbnail: Thumbnail?

    init(
        size: CGFloat = 96,
        label: String = "n/n",
        onTap: @escaping () -> Void = {},
        onRemove: (() -> Void)? = nil,
        @ViewBuilder thumbnail: () -> Thumbnail
    ) {
        self.size = size
        self.label = label
        self.onTap = onTap
        self.onRemove = onRemove
        self.thumbnail = thumbnail()
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onTap) {
                slotContent
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)

            if thumbnail != nil, let onRemove {
                Button(action: onRemove) {
                    Image(systemName: "xmark")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(Color.Text.inverse)
                        .frame(width: 20, height: 20)
                        .background(Color.Object.bold)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(6)
            }
        }
        .frame(width: size, height: size)
    }

    @ViewBuilder private var slotContent: some View {
        if let thumbnail {
            thumbnail
        } else {
            VStack(spacing: 6) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 24))
                    .foregroundStyle(Color.Icon.default)
                Text(label)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.subtle)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.Object.muted)
        }
    }
}

extension AppImageUploadSlot where Thumbnail == EmptyView {
    init(
        size: CGFloat = 96,
        label: String = "n/n",
        onTap: @escaping () -> Void = {}
    ) {
        self.size = size
        self.label = label
        self.onTap = onTap
        self.onRemove = nil
        self.thumbnail = nil
    }
}

#Preview {
    HStack(spacing: Spacing.lg) {
        AppImageUploadSlot(onRemove: {}) {
            AppImagePlaceholder(cornerRadius: 8)
        }

        AppImageUploadSlot(label: "n/n")
    }
    .padding()
}
