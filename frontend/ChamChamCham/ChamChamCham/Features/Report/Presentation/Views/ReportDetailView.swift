//
//  ReportDetailView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportDetailView: View {
    let key: WorkReportKey

    @State private var viewModel: ReportDetailViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(key: WorkReportKey, repository: any ReportRepository) {
        self.key = key
        _viewModel = State(initialValue: ReportDetailViewModel(key: key, repository: repository))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "",
                isDetail: true,
                showBorder: false,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() },
                trailing: [.init(.asset("more_vert"))]
            )
            content
        }
        .background(Color.Background.default)
        .toolbar(.hidden, for: .navigationBar)
        .task { await viewModel.onAppear() }
        .onChange(of: scenePhase) { _, phase in
            viewModel.setSceneActive(phase == .active)
        }
        .onDisappear { viewModel.onDisappear() }
    }

    @ViewBuilder private var content: some View {
        if let detail = viewModel.detail, let presentation = viewModel.presentation {
            detailScroll(detail: detail, presentation: presentation)
        } else if viewModel.isLoading {
            loadingState
        } else if let message = viewModel.errorMessage {
            errorState(message)
        } else {
            loadingState
        }
    }

    private func detailScroll(
        detail: FarmingWorkReportDetail,
        presentation: ReportDetailPresentation
    ) -> some View {
        GeometryReader { proxy in
            let inset = ReportDetailLayout.horizontalInset(availableWidth: proxy.size.width)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    if viewModel.isOffline {
                        cachedBanner
                    }
                    reportHeader(detail, metrics: presentation.metrics)
                    chartSection(presentation.charts)
                    ReportCoachingSection(
                        presentation: ReportCoachingPresentation(
                            cycleStatus: detail.status,
                            feedback: viewModel.feedback,
                            isOffline: viewModel.isOffline
                        ),
                        isPolling: viewModel.isPolling,
                        isRegenerating: viewModel.isRegenerating,
                        errorMessage: viewModel.feedbackErrorMessage,
                        onRefresh: { Task { await viewModel.refreshFeedback() } },
                        onRegenerate: { Task { await viewModel.regenerate() } }
                    )
//                    historyLink
                }
                .padding(.horizontal, inset)
                .padding(.top, 12)
                .padding(.bottom, Spacing.xl)
            }
            .refreshable {
                await viewModel.retry()
            }
        }
    }

    private func reportHeader(
        _ detail: FarmingWorkReportDetail,
        metrics: [ReportMetricPresentation]
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: Spacing.sm) {
                    reportBadges(detail)
                    Spacer(minLength: Spacing.sm)
                    periodLabel(detail)
                }

                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HStack(spacing: Spacing.sm) {
                        reportBadges(detail)
                    }
                    periodLabel(detail)
                }
            }

            Text(detail.workTypeLabel)
                .appTypography(.headlineMedium)
                .foregroundStyle(Color.Text.default)

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12),
                ],
                spacing: 12
            ) {
                ForEach(metrics) { metric in
                    ReportMetricCard(metric: metric)
                }
            }
        }
    }

    @ViewBuilder private func chartSection(_ charts: [ReportChartSection]) -> some View {
        if !charts.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.md) {
                sectionTitle("상세 정보")
                ForEach(charts) { chart in
                    ReportChartCard(model: chart.model)
                }
            }
        }
    }

    private var historyLink: some View {
        NavigationLink(value: ReportRoute.recordHistory(key)) {
            HStack(spacing: Spacing.md) {
                Text("기록 내역 리스트")
                    .appTypography(.titleLargeEmphasized)
                    .foregroundStyle(Color.Text.subtle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                AppIconView(source: .asset("arrow_forward_ios"), size: 24)
                    .foregroundStyle(Color.Icon.subtle)
            }
            .padding(.vertical, 20)
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(Color.Border.subtle)
                    .frame(height: 1)
            }
        }
        .buttonStyle(.plain)
    }

    private var cachedBanner: some View {
        HStack(spacing: Spacing.sm) {
            AppIconView(source: .asset("info_line"), size: 24)
                .foregroundStyle(Color.Icon.primary)
            Text("오프라인 상태예요. 마지막으로 저장된 리포트를 보여드려요.")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.md)
        .background(Color.Object.primarySubtle)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var loadingState: some View {
        VStack(spacing: Spacing.md) {
            ProgressView()
            Text("리포트를 불러오고 있어요.")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: Spacing.md) {
            AppIconView(source: .asset("error_line"), size: 40)
                .foregroundStyle(Color.Icon.disabled)
            Text(message)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
            AppButton("다시 시도", variant: .neutral, size: .small) {
                Task { await viewModel.retry() }
            }
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .appTypography(.titleLargeEmphasized)
            .foregroundStyle(Color.Text.subtle)
    }

    private func reportBadges(_ detail: FarmingWorkReportDetail) -> some View {
        Group {
            AppBadge(label: detail.cropName, style: .solidPastel, variant: .primary)
            AppBadge(label: detail.farmName, style: .solidPastel, variant: .secondary)
        }
    }

    private func periodLabel(_ detail: FarmingWorkReportDetail) -> some View {
        Text(periodText(detail))
            .appTypography(.labelMedium)
            .foregroundStyle(Color.Text.muted)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
    }

    private func periodText(_ detail: FarmingWorkReportDetail) -> String {
        let start = ReportDateParser.displayDate(detail.startsAt)
        let end = detail.endsAt.map(ReportDateParser.displayDate) ?? "진행 중"
        return "\(start) ~ \(end)"
    }
}
