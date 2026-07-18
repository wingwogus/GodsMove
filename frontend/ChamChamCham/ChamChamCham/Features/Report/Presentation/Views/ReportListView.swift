//
//  ReportListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

struct ReportListView: View {
    let viewModel: ReportListViewModel

    @State private var activeSheet: ReportFilterKind?

    var body: some View {
        GeometryReader { proxy in
            let inset = ReportListLayout.horizontalInset(availableWidth: proxy.size.width)
            VStack(spacing: 0) {
                filterRow(horizontalInset: inset)
                reportList(horizontalInset: inset)
            }
        }
        .task { await viewModel.onAppear() }
        .sheet(item: $activeSheet) { kind in
            switch kind {
            case .crop:
                ReportCropFilterSheet(
                    crops: viewModel.availableCrops,
                    selected: viewModel.filter.cropId
                ) { cropId in
                    Task { await viewModel.applyCropFilter(cropId) }
                }
            case .workType:
                ReportWorkTypeFilterSheet(selected: viewModel.filter.workTypes) { workTypes in
                    Task { await viewModel.applyWorkTypeFilter(workTypes) }
                }
            case .farm:
                ReportFarmFilterSheet(
                    farms: viewModel.farms,
                    selected: viewModel.filter.farmId
                ) { farmId in
                    Task { await viewModel.applyFarmFilter(farmId) }
                }
            }
        }
    }

    private func filterRow(horizontalInset: CGFloat) -> some View {
        let chips = ReportFilterChipPresentation.all(filter: viewModel.filter, farms: viewModel.farms)
        return ScrollView(.horizontal) {
            HStack(spacing: Spacing.sm) {
                ForEach(chips, id: \.kind.id) { chip in
                    AppChip(
                        label: chip.title,
                        isSelected: chip.isSelected,
                        style: .solidPastel,
                        trailingSystemImage: .asset("keyboard_arrow_down")
                    ) {
                        activeSheet = chip.kind
                    }
                }
            }
            .padding(.horizontal, horizontalInset)
        }
        .scrollIndicators(.hidden)
        .frame(height: 60)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.Background.subtle)
    }

    private func reportList(horizontalInset: CGFloat) -> some View {
        ScrollView {
            LazyVStack(spacing: ReportListLayout.cardSpacing) {
                if viewModel.isShowingCachedData {
                    cachedBanner
                }

                if viewModel.isLoading, viewModel.reports.isEmpty {
                    loadingState
                } else if let errorMessage = viewModel.errorMessage, viewModel.reports.isEmpty {
                    errorState(errorMessage)
                } else if viewModel.reports.isEmpty {
                    emptyState
                } else {
                    ForEach(viewModel.reports) { report in
                        NavigationLink(value: ReportRoute.detail(report.key)) {
                            ReportListCard(summary: report)
                        }
                        .buttonStyle(.plain)
                        .task { await viewModel.loadMoreIfNeeded(currentItem: report) }
                    }

                    if viewModel.isLoadingMore {
                        ProgressView()
                            .padding(Spacing.md)
                            .accessibilityLabel("리포트 더 불러오는 중")
                    }
                }
            }
            .padding(.horizontal, horizontalInset)
            .padding(.top, 20)
            .padding(.bottom, Spacing.xl)
        }
        .refreshable { await viewModel.refresh() }
        .overlay(alignment: .top) {
            if viewModel.isRefreshing {
                ProgressView()
                    .padding(.top, Spacing.sm)
                    .accessibilityLabel("리포트 새로고침 중")
            }
        }
    }

    private var cachedBanner: some View {
        HStack(spacing: Spacing.sm) {
            AppIconView(source: .asset("info_line"), size: 24)
                .foregroundStyle(Color.Icon.primary)
            Text("오프라인 상태예요. 마지막으로 불러온 리포트를 보여드려요.")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
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
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }

    private var emptyState: some View {
        VStack(spacing: Spacing.md) {
            AppIconView(source: .asset("assignment-1"), size: 40)
                .foregroundStyle(Color.Icon.disabled)
            Text(
                viewModel.filter.isEmpty
                    ? "아직 생성된 리포트가 없어요.\n영농 기록을 쌓으면 리포트가 자동으로 만들어져요."
                    : "선택한 조건에 맞는 리포트가 없어요.\n다른 작물이나 기간으로 다시 확인해보세요."
            )
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
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
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }
}
