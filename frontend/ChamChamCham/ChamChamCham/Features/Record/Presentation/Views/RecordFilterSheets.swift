//
//  RecordFilterSheets.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// Which filter sheet is presented. Drives `.sheet(item:)` on the record list.
enum RecordFilterKind: Identifiable {
    case crop
    case workType
    case dateRange

    var id: Int {
        switch self {
        case .crop: 0
        case .workType: 1
        case .dateRange: 2
        }
    }
}

// MARK: - 진행중인 작물

/// Figma `bottom-sheet / 진행중인 작물`. Multi-select (deployed list API takes `cropIds`); tapping a selected
/// chip removes it from the selection. Applies on 완료.
struct RecordCropFilterSheet: View {
    let crops: [ActiveCrop]
    let selected: Set<UUID>
    let onApply: (Set<UUID>) -> Void

    @State private var draft: Set<UUID>
    @Environment(\.dismiss) private var dismiss

    init(crops: [ActiveCrop], selected: Set<UUID>, onApply: @escaping (Set<UUID>) -> Void) {
        self.crops = crops
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        RecordFilterSheetScaffold(title: "진행중인 작물", height: 274) {
            if crops.isEmpty {
                Text("진행중인 작물이 없어요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                RecordChipFlow(
                    items: crops,
                    isSelected: { draft.contains($0.id) },
                    label: { $0.name }
                ) { crop in
                    if draft.contains(crop.id) {
                        draft.remove(crop.id)
                    } else {
                        draft.insert(crop.id)
                    }
                }
            }
        } onComplete: {
            onApply(draft)
            dismiss()
        }
    }
}

// MARK: - 영농 활동

/// Figma `bottom-sheet / 영농 활동`. Multi-select (deployed list API takes `workTypes`). Eight types —
/// Figma's ninth chip 가공 is omitted because the backend enum has no matching value. Applies on 완료.
struct RecordWorkTypeFilterSheet: View {
    let selected: Set<WorkType>
    let onApply: (Set<WorkType>) -> Void

    @State private var draft: Set<WorkType>
    @Environment(\.dismiss) private var dismiss

    init(selected: Set<WorkType>, onApply: @escaping (Set<WorkType>) -> Void) {
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        RecordFilterSheetScaffold(title: "영농 활동", height: 274) {
            RecordChipFlow(
                items: WorkType.allCases,
                isSelected: { draft.contains($0) },
                label: { $0.label }
            ) { workType in
                if draft.contains(workType) {
                    draft.remove(workType)
                } else {
                    draft.insert(workType)
                }
            }
        } onComplete: {
            onApply(draft)
            dismiss()
        }
    }
}

// MARK: - 작성 기간

/// Figma `bottom-sheet / 작성 기간`. Two date fields (시작 ~ 종료). Applies on 완료.
struct RecordDateFilterSheet: View {
    let startDate: Date?
    let endDate: Date?
    let onApply: (Date?, Date?) -> Void

    @State private var draftStart: Date?
    @State private var draftEnd: Date?
    @Environment(\.dismiss) private var dismiss

    init(startDate: Date?, endDate: Date?, onApply: @escaping (Date?, Date?) -> Void) {
        self.startDate = startDate
        self.endDate = endDate
        self.onApply = onApply
        _draftStart = State(initialValue: startDate)
        _draftEnd = State(initialValue: endDate)
    }

    private var rangeError: String? {
        guard let start = draftStart, let end = draftEnd, start > end else { return nil }
        return "시작일이 종료일보다 늦어요."
    }

    var body: some View {
        RecordFilterSheetScaffold(title: "작성 기간", height: 258) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                AppDateField(selection: $draftStart, errorMessage: rangeError.map { _ in "" })
                AppDateField(selection: $draftEnd, errorMessage: rangeError)
            }
        } onComplete: {
            onApply(draftStart, draftEnd)
            dismiss()
        } isCompleteDisabled: {
            rangeError != nil
        }
    }
}

// MARK: - Shared scaffold

/// Common bottom-sheet chrome: title (SemiBold 20) + content + full-width 완료 button, matching the three
/// captured filter sheets. The drag grabber and background dim come from the system sheet presentation.
private struct RecordFilterSheetScaffold<Content: View>: View {
    let title: String
    /// Total sheet height (Figma `bottom-sheet` frame, grabber included): 274 for the two-row
    /// chip sheets (작물/영농 활동), 258 for the single-row date-range sheet.
    let height: CGFloat
    @ViewBuilder let content: Content
    let onComplete: () -> Void
    var isCompleteDisabled: () -> Bool = { false }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            content

            AppButton("완료", variant: .secondary, size: .medium, fullWidth: true, action: onComplete)
                .disabled(isCompleteDisabled())
                .padding(.top, Spacing.md)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .presentationDetents([.height(height)])
        .presentationDragIndicator(.visible)
    }
}

/// Multi-row wrapping chip group. Selected → green pastel; unselected → gray muted (matches the sheet spec:
/// `#e4f8e3` border/green text vs `#f3f3f3` fill).
private struct RecordChipFlow<Item: Hashable>: View {
    let items: [Item]
    let isSelected: (Item) -> Bool
    let label: (Item) -> String
    let onTap: (Item) -> Void

    var body: some View {
        RecordFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
            ForEach(items, id: \.self) { item in
                let selected = isSelected(item)
                AppChip(
                    label: label(item),
                    isSelected: selected,
                    style: selected ? .solidPastel : .solid
                ) {
                    onTap(item)
                }
            }
        }
    }
}

/// Minimal flow layout (iOS 16+ `Layout`) that wraps chips onto multiple rows. Feature-local: promote to the
/// design system only if a second screen needs wrapping.
private struct RecordFlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rows: [[CGSize]] = [[]]
        var rowWidth: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let added = rowWidth == 0 ? size.width : rowWidth + spacing + size.width
            if added > maxWidth, rowWidth > 0 {
                rows.append([size])
                rowWidth = size.width
            } else {
                rows[rows.count - 1].append(size)
                rowWidth = added
            }
        }
        let height = rows.reduce(CGFloat.zero) { partial, row in
            partial + (row.map(\.height).max() ?? 0)
        } + CGFloat(max(0, rows.count - 1)) * lineSpacing
        return CGSize(width: maxWidth == .infinity ? rowWidth : maxWidth, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > bounds.minX, x - bounds.minX + size.width > maxWidth {
                x = bounds.minX
                y += rowHeight + lineSpacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
