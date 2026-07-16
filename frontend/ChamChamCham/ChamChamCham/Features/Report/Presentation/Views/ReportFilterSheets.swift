//
//  ReportFilterSheets.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportFarmFilterSheet: View {
    let farms: [ReportFarmFilterOption]
    let selected: UUID?
    let onApply: (UUID?) -> Void

    @State private var draft: UUID?
    @Environment(\.dismiss) private var dismiss

    init(
        farms: [ReportFarmFilterOption],
        selected: UUID?,
        onApply: @escaping (UUID?) -> Void
    ) {
        self.farms = farms
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        ReportFilterSheetScaffold(title: "농장") {
            choiceGrid {
                AppSelectItem(title: "전체", isSelected: draft == nil) { draft = nil }
                ForEach(farms) { farm in
                    AppSelectItem(title: farm.name, isSelected: draft == farm.id) {
                        draft = farm.id
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
    let selected: UUID?
    let onApply: (UUID?) -> Void

    @State private var draft: UUID?
    @Environment(\.dismiss) private var dismiss

    init(
        crops: [ReportCropFilterOption],
        selected: UUID?,
        onApply: @escaping (UUID?) -> Void
    ) {
        self.crops = crops
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        ReportFilterSheetScaffold(title: "작물") {
            choiceGrid {
                AppSelectItem(title: "전체", isSelected: draft == nil) { draft = nil }
                ForEach(crops) { crop in
                    AppSelectItem(title: crop.name, isSelected: draft == crop.id) {
                        draft = crop.id
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
    let selected: WorkType?
    let onApply: (WorkType?) -> Void

    @State private var draft: WorkType?
    @Environment(\.dismiss) private var dismiss

    init(selected: WorkType?, onApply: @escaping (WorkType?) -> Void) {
        self.selected = selected
        self.onApply = onApply
        _draft = State(initialValue: selected)
    }

    var body: some View {
        ReportFilterSheetScaffold(title: "영농 활동") {
            choiceGrid {
                AppSelectItem(title: "전체", isSelected: draft == nil) { draft = nil }
                ForEach(WorkType.allCases, id: \.self) { workType in
                    AppSelectItem(title: workType.label, isSelected: draft == workType) {
                        draft = workType
                    }
                }
            }
        } onComplete: {
            onApply(draft)
            dismiss()
        }
    }
}

private struct ReportFilterSheetScaffold<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    let onComplete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.default)

            ScrollView {
                content
            }
            .scrollIndicators(.hidden)

            AppButton(
                "완료",
                variant: .secondary,
                size: .medium,
                fullWidth: true,
                action: onComplete
            )
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, Spacing.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

private func choiceGrid<Content: View>(
    @ViewBuilder content: () -> Content
) -> some View {
    LazyVGrid(
        columns: [
            GridItem(.flexible(), spacing: Spacing.sm),
            GridItem(.flexible(), spacing: Spacing.sm),
        ],
        spacing: Spacing.sm,
        content: content
    )
}
