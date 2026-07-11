//
//  AppImageUploadSlot.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `image-uploader`. Empty medium state shows the camera affordance; filled states accept a
/// thumbnail view and expose a size-specific remove icon when an action is supplied.
struct AppImageUploadSlot<Thumbnail: View>: View {
    enum Size {
        case small
        case medium

        var dimension: CGFloat {
            switch self {
            case .small: 64
            case .medium: 96
            }
        }

        var cornerRadius: CGFloat {
            switch self {
            case .small: 4
            case .medium: 8
            }
        }

        var removeIconSize: CGFloat {
            switch self {
            case .small: 20
            case .medium: 24
            }
        }

        var removeInset: CGFloat {
            switch self {
            case .small: 8
            case .medium: 12
            }
        }
    }

    var size: Size = .medium
    var label: String = "n/n"
    var onTap: () -> Void = {}
    var onRemove: (() -> Void)? = nil

    private let thumbnail: Thumbnail?

    init(
        size: Size = .medium,
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
                    .frame(width: size.dimension, height: size.dimension)
                    .clipShape(RoundedRectangle(cornerRadius: size.cornerRadius))
            }
            .buttonStyle(.plain)

            if thumbnail != nil, let onRemove {
                Button(action: onRemove) {
                    Image(systemName: "xmark")
                        .font(.system(size: size.removeIconSize * 0.45, weight: .bold))
                        .foregroundStyle(Color.Text.inverse)
                        .frame(width: size.removeIconSize, height: size.removeIconSize)
                        .background(Color.Object.bold)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .padding(size.removeInset)
            }
        }
        .frame(width: size.dimension, height: size.dimension)
    }

    @ViewBuilder private var slotContent: some View {
        if let thumbnail {
            thumbnail
        } else {
            VStack(spacing: 2) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(Color.Icon.default)
                Text(label)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.subtle)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.Object.muted)
        }
    }
}

extension AppImageUploadSlot where Thumbnail == EmptyView {
    init(
        size: Size = .medium,
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
