//
//  ReportCoachingSection.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportCoachingSection: View {
    let presentation: ReportCoachingPresentation
    let isPolling: Bool
    let isRegenerating: Bool
    let errorMessage: String?
    let onRefresh: () -> Void
    let onRegenerate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(presentation.title)
                .appTypography(.titleLargeEmphasized)
                .foregroundStyle(Color.Text.default)

            if presentation.kind == .ready, !presentation.sections.isEmpty {
                if !presentation.message.isEmpty {
                    Text(presentation.message)
                        .appTypography(.bodyMedium)
                        .foregroundStyle(Color.Text.subtle)
                        .fixedSize(horizontal: false, vertical: true)
                }
                coachingCards
            } else {
                statusCard
            }

            if let errorMessage {
                Text(errorMessage)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.red)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if presentation.showsRefresh {
                AppButton(
                    isPolling ? "확인 중" : "코칭 상태 새로고침",
                    icon: .asset("refresh"),
                    variant: .neutral,
                    size: .small,
                    fullWidth: true,
                    action: onRefresh
                )
                .disabled(isPolling)
            }

            if presentation.showsRegenerate {
                AppButton(
                    isRegenerating ? "다시 만드는 중" : "코칭 다시 만들기",
                    icon: .asset("refresh"),
                    variant: .primary,
                    size: .medium,
                    fullWidth: true,
                    action: onRegenerate
                )
                .disabled(!presentation.regenerateEnabled || isRegenerating)
            }
        }
    }

    private var coachingCards: some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: Spacing.md) {
                ForEach(presentation.sections) { section in
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text(section.title)
                            .appTypography(.titleMediumEmphasized)
                            .foregroundStyle(Color.Text.primary)

                        ForEach(Array(section.messages.enumerated()), id: \.offset) { _, message in
                            HStack(alignment: .top, spacing: Spacing.sm) {
                                Circle()
                                    .fill(Color.Object.primary)
                                    .frame(width: 6, height: 6)
                                    .padding(.top, 9)
                                    .accessibilityHidden(true)
                                Text(message)
                                    .appTypography(.bodyMedium)
                                    .foregroundStyle(Color.Text.subtle)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .padding(20)
                    .frame(width: 280, alignment: .topLeading)
                    .background(Color.Object.primarySubtle)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .scrollIndicators(.hidden)
    }

    private var statusCard: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            if presentation.showsProgress {
                ProgressView()
                    .tint(Color.Object.primary)
                    .frame(width: 32, height: 32)
                    .accessibilityHidden(true)
            } else {
                AppIconView(source: statusIcon, size: 32)
                    .foregroundStyle(Color.Icon.primary)
                    .accessibilityHidden(true)
            }

            Text(presentation.message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.subtle)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(20)
        .background(Color.Object.primarySubtle)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var statusIcon: AppIconSource {
        switch presentation.kind {
        case .failed, .unavailable:
            .asset("error_line")
        case .stale:
            .asset("refresh")
        case .active, .pending, .ready:
            .asset("info_line")
        }
    }
}
