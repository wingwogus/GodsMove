//
//  PolicyListView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/14/26.
//

import SwiftUI

/// 정책 리스트 (Figma `홈 -> 정책 리스트`). Category chips + a cursor-paged list of recommended
/// policies. Policy **detail** has no native screen — confirmed 2026-07-14: tapping a row resolves
/// the program's application/source URL and opens it in the system browser (`openURL`). This is the
/// first use of external-link navigation in the app (no existing wrapper to reuse).
struct PolicyListView: View {
    private let container: DIContainer
    @State private var viewModel: PolicyListViewModel
    @State private var linkErrorMessage: String?
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    init(container: DIContainer) {
        self.container = container
        _viewModel = State(initialValue: PolicyListViewModel(repository: container.makePolicyRepository()))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "정책 리스트",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )
            categoryChipRow
            sortRow
            listContent
        }
        .navigationBarHidden(true)
        .task { await viewModel.onAppear() }
        .alert("링크를 열 수 없어요", isPresented: .init(
            get: { linkErrorMessage != nil },
            set: { if !$0 { linkErrorMessage = nil } }
        )) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(linkErrorMessage ?? "")
        }
    }

    private var categoryChipRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                let isAllSelected = viewModel.selectedCategory == nil
                AppChip(label: "전체", isSelected: isAllSelected, style: isAllSelected ? .solid : .solidPastel) {
                    Task { await viewModel.selectCategory(nil) }
                }
                ForEach(PolicyCategory.allCases) { category in
                    let isSelected = viewModel.selectedCategory == category
                    AppChip(
                        label: category.rawValue,
                        isSelected: isSelected,
                        style: isSelected ? .solid : .solidPastel
                    ) {
                        Task { await viewModel.selectCategory(category) }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, Spacing.md)
        }
        .background(Color.Background.subtle)
    }

    private var sortSelection: Binding<PolicySort> {
        Binding(
            get: { viewModel.sort },
            set: { newValue in Task { await viewModel.selectSort(newValue) } }
        )
    }

    private var sortRow: some View {
        HStack {
            Spacer()
            Picker(selection: sortSelection) {
                Text("추천순").tag(PolicySort.recommended)
                Text("최신순").tag(PolicySort.latest)
            } label: {
                AppSortButton(title: viewModel.sort == .latest ? "최신순" : "추천순")
            }
            .pickerStyle(.menu)
            .tint(Color.Text.subtle)
        }
        .frame(height: 48)
        .padding(.horizontal, 20)
    }

    @ViewBuilder private var listContent: some View {
        if viewModel.isLoading && viewModel.items.isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let errorMessage = viewModel.errorMessage {
            Text(errorMessage)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if viewModel.items.isEmpty {
            Text("해당 카테고리의 추천 정책이 없어요")
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(viewModel.items) { item in
                        Button {
                            openExternalLink(for: item)
                        } label: {
                            AppListItem(
                                size: .xlarge,
                                title: item.title,
                                organization: item.agencyName,
                                infoRows: [
                                    ("대상자", item.eligibilitySummary),
                                    ("지원내용", item.benefitSummary),
                                    ("접수기간", item.applicationPeriodLabel),
                                ]
                            )
                        }
                        .buttonStyle(.plain)
                        .task { await viewModel.loadMoreIfNeeded(currentItem: item) }
                    }
                }
            }
        }
    }

    private func openExternalLink(for item: PolicyRecommendation) {
        Task {
            do {
                guard let url = try await container.makePolicyRepository().fetchExternalLink(programId: item.programId) else {
                    linkErrorMessage = "이 정책은 연결된 링크가 없어요."
                    return
                }
                openURL(url)
            } catch {
                linkErrorMessage = HomeErrorMessage.text(for: error)
            }
        }
    }
}
