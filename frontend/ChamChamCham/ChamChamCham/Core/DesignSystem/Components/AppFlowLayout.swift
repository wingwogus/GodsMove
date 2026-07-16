//
//  AppFlowLayout.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Wraps its children onto multiple rows, sizing each child to its own intrinsic width instead of
/// assuming a fixed count per row. Use for chip/badge groups whose label lengths vary — a plain
/// `HStack` compresses and truncates `Text` once a row's content overflows its container.
struct AppFlowLayout: Layout {
    enum RowAlignment {
        case leading
        case center
    }

    var spacing: CGFloat = Spacing.sm
    var lineSpacing: CGFloat = Spacing.sm
    var alignment: RowAlignment = .leading

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let rows = rows(for: subviews, maxWidth: maxWidth)
        let height = rows.reduce(CGFloat.zero) { partial, row in
            partial + (row.map(\.size.height).max() ?? 0)
        } + CGFloat(max(0, rows.count - 1)) * lineSpacing
        let width = rows.map(rowWidth).max() ?? 0
        return CGSize(width: maxWidth == .infinity ? width : maxWidth, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let rows = rows(for: subviews, maxWidth: bounds.width)
        var y = bounds.minY
        for row in rows {
            let rowHeight = row.map(\.size.height).max() ?? 0
            let leadingInset: CGFloat = {
                guard alignment == .center else { return 0 }
                return max(0, (bounds.width - rowWidth(row)) / 2)
            }()
            var x = bounds.minX + leadingInset
            for item in row {
                item.subview.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(item.size))
                x += item.size.width + spacing
            }
            y += rowHeight + lineSpacing
        }
    }

    private struct RowItem {
        let subview: LayoutSubview
        let size: CGSize
    }

    private func rowWidth(_ row: [RowItem]) -> CGFloat {
        row.reduce(CGFloat.zero) { $0 + $1.size.width } + CGFloat(max(0, row.count - 1)) * spacing
    }

    private func rows(for subviews: Subviews, maxWidth: CGFloat) -> [[RowItem]] {
        var rows: [[RowItem]] = [[]]
        var currentRowWidth: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let item = RowItem(subview: subview, size: size)
            let added = currentRowWidth == 0 ? size.width : currentRowWidth + spacing + size.width
            if added > maxWidth, currentRowWidth > 0 {
                rows.append([item])
                currentRowWidth = size.width
            } else {
                rows[rows.count - 1].append(item)
                currentRowWidth = added
            }
        }
        return rows
    }
}

#Preview {
    VStack(spacing: Spacing.lg) {
        AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm, alignment: .leading) {
            ForEach(["매발톱", "마타리", "멀꿀", "개별꽃", "가죽나무", "구절초", "산수유"], id: \.self) { name in
                AppBadge(label: name, style: .solid, variant: .primary)
            }
        }
        AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm, alignment: .center) {
            ForEach(["매발톱", "마타리", "멀꿀", "개별꽃", "가죽나무", "구절초", "산수유"], id: \.self) { name in
                AppBadge(label: name, style: .solid, variant: .primary)
            }
        }
    }
    .padding()
}
