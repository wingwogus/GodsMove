//
//  ReportListCard.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportListCard: View {
    let summary: FarmingWorkReportSummary

    private var presentation: ReportListCardPresentation {
        ReportListCardPresentation(summary: summary)
    }

    var body: some View {
        AppCard(
            size: .large,
            title: presentation.title,
            captions: presentation.periodParts,
            badges: presentation.badges
        ) {
            ReportThumbnail(url: presentation.thumbnailURL, workType: presentation.workType)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint("리포트 상세를 열어요")
    }

    private var accessibilityLabel: String {
        "\(presentation.badges.joined(separator: ", ")), \(presentation.title), "
            + presentation.periodParts.joined(separator: "부터 ")
    }
}

private struct ReportThumbnail: View {
    let url: URL?
    let workType: WorkType

    var body: some View {
        Rectangle()
            .fill(Color.Object.muted)
            .overlay {
                if let url {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case let .success(image):
                            image.resizable().scaledToFill()
                        case .empty:
                            ProgressView()
                        case .failure:
                            illustration
                        @unknown default:
                            illustration
                        }
                    }
                } else {
                    illustration
                }
            }
            .clipped()
    }

    private var illustration: some View {
        AppIllustration(assetName: workType.illustAssetName, variant: .wide)
    }
}
