//
//  ReportFilterSheets.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportFarmFilterSheet: View {
    let farms: [ReportFarmFilterOption]
    let selected: Set<UUID>
    let onApply: (Set<UUID>) -> Void

    @State private var draft: Set<UUID>
    @Environment(\.dismiss) private var dismiss

    init(
        farms: [ReportFarmFilterOption],
        selected: Set<UUID>,
        onApply: @escaping (Set<UUID>) -> Void
    ) {
        self.farms = farms
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        AppFilterSheetScaffold(title: "농장") {
            AppFlowLayout(spacing: Spacing.sm, lineSpacing: Spacing.sm) {
                ForEach(farms) { farm in
                    let selected = draft.contains(farm.id)
                    AppChip(label: farm.name, isSelected: selected, style: selected ? .solidPastel : .solid) {
                        if draft.contains(farm.id) {
                            draft.remove(farm.id)
                        } else {
                            draft.insert(farm.id)
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

struct ReportCropFilterSheet: View {
    let crops: [ReportCropFilterOption]
    let selected: Set<UUID>
    let onApply: (Set<UUID>) -> Void

    @State private var draft: Set<UUID>
    @Environment(\.dismiss) private var dismiss

    init(
        crops: [ReportCropFilterOption],
        selected: Set<UUID>,
        onApply: @escaping (Set<UUID>) -> Void
    ) {
        self.crops = crops
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        AppFilterSheetScaffold(title: "작물") {
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
        } onComplete: {
            onApply(draft)
            dismiss()
        }
    }
}

struct ReportWorkTypeFilterSheet: View {
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
        AppFilterSheetScaffold(title: "영농 활동", height: 274) {
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
