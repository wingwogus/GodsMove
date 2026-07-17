//
//  FullScreenImageViewer.swift
//  ChamChamCham
//
//  Created by iyungui on 7/17/26.
//

import SwiftUI

/// Full-screen viewer for a single remote image with pinch-to-zoom, double-tap zoom, and panning.
/// Pan is always clamped to the scaled image's own bounds, so the image can never be dragged past its
/// edges — no empty gutter ever shows inside the frame. Dismissed via the close button.
struct FullScreenImageViewer: View {
    let url: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            imageContent

            closeButton
        }
    }

    @ViewBuilder private var imageContent: some View {
        if let parsed = URL(string: url) {
            AsyncImage(url: parsed) { phase in
                switch phase {
                case .success(let image):
                    ZoomableImage(image: image)
                case .failure:
                    failurePlaceholder
                default:
                    ProgressView().tint(Color.Icon.inverse)
                }
            }
        } else {
            failurePlaceholder
        }
    }

    private var closeButton: some View {
        VStack {
            HStack {
                Button { dismiss() } label: {
                    AppIconView(source: .asset("close"), size: 24)
                        .foregroundStyle(Color.Icon.inverse)
                        .frame(width: 48, height: 48)
                }
                Spacer()
            }
            Spacer()
        }
        .padding(.horizontal, Spacing.sm)
    }

    private var failurePlaceholder: some View {
        VStack(spacing: Spacing.md) {
            AppIconView(source: .asset("photo"), size: 40)
                .foregroundStyle(Color.Icon.disabled)
            Text("이미지를 불러오지 못했어요.")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.inverse)
        }
    }
}

/// The zoom/pan surface. `imageSize` is the fitted size of the image at scale 1 (measured off the rendered
/// image), which the clamp uses to keep the pan inside the scaled image's bounds.
private struct ZoomableImage: View {
    let image: Image

    private static let minScale: CGFloat = 1
    private static let maxScale: CGFloat = 4
    private static let doubleTapScale: CGFloat = 2

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var imageSize: CGSize = .zero

    var body: some View {
        GeometryReader { geo in
            image
                .resizable()
                .scaledToFit()
                .background {
                    GeometryReader { proxy in
                        Color.clear
                            .onAppear { imageSize = proxy.size }
                            .onChange(of: proxy.size) { _, newValue in imageSize = newValue }
                    }
                }
                .scaleEffect(scale)
                .offset(offset)
                .frame(width: geo.size.width, height: geo.size.height)
                .contentShape(Rectangle())
                .gesture(magnification(in: geo.size))
                .simultaneousGesture(drag(in: geo.size))
                .onTapGesture(count: 2) { toggleZoom(in: geo.size) }
        }
    }

    private func magnification(in container: CGSize) -> some Gesture {
        MagnificationGesture()
            .onChanged { value in
                scale = min(max(lastScale * value, Self.minScale), Self.maxScale)
                offset = clamp(offset, scale: scale, in: container)
            }
            .onEnded { _ in
                lastScale = scale
                offset = clamp(offset, scale: scale, in: container)
                lastOffset = offset
            }
    }

    private func drag(in container: CGSize) -> some Gesture {
        DragGesture()
            .onChanged { value in
                let proposed = CGSize(
                    width: lastOffset.width + value.translation.width,
                    height: lastOffset.height + value.translation.height
                )
                offset = clamp(proposed, scale: scale, in: container)
            }
            .onEnded { _ in lastOffset = offset }
    }

    private func toggleZoom(in container: CGSize) {
        withAnimation(.easeInOut(duration: 0.25)) {
            if scale > Self.minScale {
                scale = Self.minScale
                offset = .zero
            } else {
                scale = Self.doubleTapScale
                offset = clamp(offset, scale: scale, in: container)
            }
            lastScale = scale
            lastOffset = offset
        }
    }

    /// Keeps `offset` within the region where the scaled image still fully covers each axis it overflows —
    /// so a drag can never expose blank space beyond the image's edges. Axes that fit stay centered.
    private func clamp(_ offset: CGSize, scale: CGFloat, in container: CGSize) -> CGSize {
        let scaledWidth = imageSize.width * scale
        let scaledHeight = imageSize.height * scale
        let maxX = max(0, (scaledWidth - container.width) / 2)
        let maxY = max(0, (scaledHeight - container.height) / 2)
        return CGSize(
            width: min(max(offset.width, -maxX), maxX),
            height: min(max(offset.height, -maxY), maxY)
        )
    }
}
