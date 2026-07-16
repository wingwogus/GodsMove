//
//  ReportRecordHistoryView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportRecordHistoryView: View {
    let key: WorkReportKey

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: ReportRecordHistoryPresentation.title,
                isDetail: true,
                leading: .init(.asset("chevron_backward")) { dismiss() }
            )

            VStack(spacing: Spacing.md) {
                AppIconView(source: .asset("assignment-1"), size: 48)
                    .foregroundStyle(Color.Icon.disabled)
                Text(ReportRecordHistoryPresentation.message)
                    .appTypography(.bodyMedium)
                    .foregroundStyle(Color.Text.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 32)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.Background.default)
        .toolbar(.hidden, for: .navigationBar)
    }
}
