//
//  AppImagePlaceholder.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Reusable checkerboard placeholder for Figma image slots that currently represent transparent
/// image content. Use a concrete image view in product screens once real media is available.
struct AppImagePlaceholder: View {
    var cornerRadius: CGFloat = 8
    var isCircle: Bool = false
    var squareSize: CGFloat = 16

    var body: some View {
        AppCheckerboardPattern(squareSize: squareSize)
            .clipShape(shape)
    }

    private var shape: AnyShape {
        isCircle ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

private struct AppCheckerboardPattern: View {
    var squareSize: CGFloat

    var body: some View {
        Canvas { context, size in
            let columns = Int(ceil(size.width / squareSize))
            let rows = Int(ceil(size.height / squareSize))

            for row in 0..<rows {
                for column in 0..<columns {
                    let origin = CGPoint(
                        x: CGFloat(column) * squareSize,
                        y: CGFloat(row) * squareSize
                    )
                    let rect = CGRect(origin: origin, size: CGSize(width: squareSize, height: squareSize))
                    let fill = (row + column).isMultiple(of: 2)
                        ? Color.Object.default
                        : Color.Object.muted
                    context.fill(Path(rect), with: .color(fill))
                }
            }
        }
        .background(Color.Object.default)
    }
}

#Preview {
    HStack(spacing: Spacing.md) {
        AppImagePlaceholder()
            .frame(width: 92, height: 92)
        AppImagePlaceholder(isCircle: true, squareSize: 12)
            .frame(width: 92, height: 92)
    }
    .padding()
}
