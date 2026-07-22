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
        AppFilterSheetScaffold(title: "진행중인 작물") {
            if crops.isEmpty {
                Text("진행중인 작물이 없어요.")
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
                    ForEach(crops) { crop in
                        let selected = draft.contains(crop.id)
                        AppChip(label: crop.name, isSelected: selected, style: selected ? .solidPastel : .solid) {
                            if draft.contains(crop.id) {
                                draft.remove(crop.id)
                            } else {
                                draft.insert(crop.id)
                            }
                        }
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
        AppFilterSheetScaffold(title: "영농 활동") {
            AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
                ForEach(WorkType.allCases, id: \.self) { workType in
                    let selected = draft.contains(workType)
                    AppChip(label: workType.label, isSelected: selected, style: selected ? .solidPastel : .solid) {
                        if draft.contains(workType) {
                            draft.remove(workType)
                        } else {
                            draft.insert(workType)
                        }
                    }
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
        AppFilterSheetScaffold(title: "작성 기간", height: 258) {
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
