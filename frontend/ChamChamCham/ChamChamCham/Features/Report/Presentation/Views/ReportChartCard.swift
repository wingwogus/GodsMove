//
//  ReportChartCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportChartCard: View {
    let model: ReportChartModel

    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            header
            chart
            legend
        }
        .padding(20)
        .background(Color.Object.default)
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.Border.default, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .contain)
    }

    private var header: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                isExpanded.toggle()
            }
        } label: {
            HStack(spacing: Spacing.sm) {
                Text(model.title)
                    .appTypography(.labelMediumEmphasized)
                    .foregroundStyle(Color.Text.default)
                    .frame(maxWidth: .infinity, alignment: .leading)

                AppIconView(
                    source: .asset(isExpanded ? "keyboard_arrow_up" : "keyboard_arrow_down"),
                    size: 24
                )
                .foregroundStyle(Color.Icon.subtle)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(model.title) 그래프")
        .accessibilityValue(isExpanded ? "범례 펼쳐짐" : "범례 접힘")
        .accessibilityHint(isExpanded ? "두 번 탭하여 범례 접기" : "두 번 탭하여 전체 범례 펼치기")
    }

    @ViewBuilder private var chart: some View {
        switch model.style {
        case .stackedBar:
            ReportStackedBar(
                entries: model.entries,
                highlightedEntry: model.highlightedEntry(isExpanded: isExpanded)
            )
            .frame(height: isExpanded ? 56 : 80)
        case .semiDonut:
            ReportSemiDonut(
                entries: model.entries,
                highlightedEntry: model.highlightedEntry(isExpanded: isExpanded)
            )
            .frame(height: 155)
        }
    }

    @ViewBuilder private var legend: some View {
        let entries = model.legendEntries(isExpanded: isExpanded)
        if !entries.isEmpty {
            VStack(spacing: Spacing.sm) {
                ForEach(entries) { entry in
                let index = model.entries.firstIndex(of: entry) ?? 0
                HStack(alignment: .top, spacing: Spacing.sm) {
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                        .fill(Color.Chart.palette[index % Color.Chart.palette.count])
                        .frame(width: 12, height: 12)
                        .padding(.top, 5)
                        .accessibilityHidden(true)

                    Text(entry.label)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.subtle)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(entry.formattedValue)
                        .appTypography(.bodyMediumEmphasized)
                        .foregroundStyle(Color.Text.default)
                        .multilineTextAlignment(.trailing)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(entry.label), \(entry.formattedValue)")
                }
            }
            .animation(.easeInOut(duration: 0.2), value: isExpanded)
        }
    }
}

private struct ReportStackedBar: View {
    let entries: [ReportChartEntry]
    let highlightedEntry: ReportChartEntry?

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                HStack(spacing: 0) {
                    ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                        Color.Chart.palette[index % Color.Chart.palette.count]
                            .frame(width: max(0, proxy.size.width * entry.fraction))
                    }
                }

                if let highlightedEntry {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(highlightedEntry.label)
                            .appTypography(.labelMediumEmphasized)
                        Text(highlightedEntry.formattedValue)
                            .appTypography(.titleMediumEmphasized)
                    }
                    .foregroundStyle(Color.Text.inverse)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .padding(.horizontal, 12)
                    .frame(
                        width: max(0, proxy.size.width * highlightedEntry.fraction),
                        height: proxy.size.height,
                        alignment: .leading
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(Color.Object.muted)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilitySummary)
    }

    private var accessibilitySummary: String {
        entries.map { "\($0.label) \($0.formattedValue)" }.joined(separator: ", ")
    }
}

private struct ReportSemiDonut: View {
    let entries: [ReportChartEntry]
    let highlightedEntry: ReportChartEntry?

    var body: some View {
        ZStack(alignment: .bottom) {
            Canvas { context, size in
                let lineWidth: CGFloat = 56
                let radius = max(0, min(size.width / 2, size.height) - lineWidth / 2)
                let center = CGPoint(x: size.width / 2, y: size.height)
                var start = Angle.degrees(180)

                for (index, entry) in entries.enumerated() {
                    let sweep = 180 * entry.fraction
                    let gap = min(1.5, sweep / 4)
                    let end = Angle.degrees(start.degrees + sweep)
                    var path = Path()
                    path.addArc(
                        center: center,
                        radius: radius,
                        startAngle: .degrees(start.degrees + gap),
                        endAngle: .degrees(end.degrees - gap),
                        clockwise: false
                    )
                    context.stroke(
                        path,
                        with: .color(Color.Chart.palette[index % Color.Chart.palette.count]),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .butt)
                    )
                    start = end
                }
            }

            if let highlightedEntry {
                VStack(spacing: 0) {
                    Text(highlightedEntry.label)
                        .appTypography(.bodyMediumEmphasized)
                        .foregroundStyle(Color.Text.subtle)
                    Text(highlightedEntry.formattedValue)
                        .appTypography(.titleLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilitySummary)
    }

    private var accessibilitySummary: String {
        entries.map { "\($0.label) \($0.formattedValue)" }.joined(separator: ", ")
    }
}
