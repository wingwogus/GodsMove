//
//  ReportMetricCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportMetricCard: View {
    let metric: ReportMetricPresentation

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(metric.title)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(metric.value)
                .appTypography(.titleMediumEmphasized)
                .foregroundStyle(Color.Text.default)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 74, alignment: .topLeading)
        .background(Color.Object.subtle)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(metric.title), \(metric.value)")
    }
}
