//
//  SearchResultsView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Post-submit screen: the 4-tab `AppTabBar` (전체/나의 일지/정책 정보/게시글) + the selected
/// tab's content. Each tab's child view model is only actually (re)fetched the first time it's
/// selected for the current keyword — see `loadIfNeeded(keyword:)` on each view model.
struct SearchResultsView: View {
    @Environment(\.openURL) private var openURL

    let viewModel: SearchViewModel
    let overviewViewModel: SearchOverviewViewModel
    let recordViewModel: SearchRecordListViewModel
    let policyViewModel: SearchPolicyListViewModel
    let postViewModel: SearchPostListViewModel

    var body: some View {
        VStack(spacing: 0) {
            AppTabBar(
                titles: ["전체", "나의 일지", "정책 정보", "게시글"],
                selection: categoryBinding
            )
            .frame(height: 56)
            content
        }
    }

    private var categoryBinding: Binding<Int> {
        Binding(
            get: { viewModel.selectedCategory.rawValue },
            set: { viewModel.selectCategory(SearchCategory(rawValue: $0) ?? .all) }
        )
    }

    @ViewBuilder private var content: some View {
        switch viewModel.selectedCategory {
        case .all:
            SearchOverviewSectionsView(
                result: overviewViewModel.result,
                isLoading: overviewViewModel.isLoading,
                errorMessage: overviewViewModel.errorMessage,
                onSelectCategory: { viewModel.selectCategory($0) }
            )
            .task(id: viewModel.submittedKeyword) {
                await overviewViewModel.loadIfNeeded(keyword: viewModel.submittedKeyword)
            }

        case .records:
            SearchCategoryListView(
                items: recordViewModel.items,
                totalCount: recordViewModel.totalCount,
                isLoading: recordViewModel.isLoading,
                isLoadingMore: recordViewModel.isLoadingMore,
                errorMessage: recordViewModel.errorMessage,
                emptyText: "나의 일지 검색 결과가 없어요.",
                row: { record, showsDivider in
                    NavigationLink(value: record) {
                        RecordRow(record: record, showsDivider: showsDivider)
                    }
                    .buttonStyle(.plain)
                },
                onLoadMore: { await recordViewModel.loadMoreIfNeeded(currentItem: $0) },
                onRefresh: { await recordViewModel.reload(keyword: viewModel.submittedKeyword) }
            )
            .task(id: viewModel.submittedKeyword) {
                await recordViewModel.loadIfNeeded(keyword: viewModel.submittedKeyword)
            }

        case .policies:
            SearchCategoryListView(
                items: policyViewModel.items,
                totalCount: policyViewModel.totalCount,
                isLoading: policyViewModel.isLoading,
                isLoadingMore: policyViewModel.isLoadingMore,
                errorMessage: policyViewModel.errorMessage,
                emptyText: "정책 정보 검색 결과가 없어요.",
                row: { item, showsDivider in
                    Button {
                        if let url = item.sourceUrl { openURL(url) }
                    } label: {
                        SearchPolicyRow(item: item, showsDivider: showsDivider)
                    }
                    .buttonStyle(.plain)
                },
                onLoadMore: { await policyViewModel.loadMoreIfNeeded(currentItem: $0) },
                onRefresh: { await policyViewModel.reload(keyword: viewModel.submittedKeyword) }
            )
            .task(id: viewModel.submittedKeyword) {
                await policyViewModel.loadIfNeeded(keyword: viewModel.submittedKeyword)
            }

        case .posts:
            SearchCategoryListView(
                items: postViewModel.items,
                totalCount: postViewModel.totalCount,
                isLoading: postViewModel.isLoading,
                isLoadingMore: postViewModel.isLoadingMore,
                errorMessage: postViewModel.errorMessage,
                emptyText: "게시글 검색 결과가 없어요.",
                row: { post, showsDivider in
                    NavigationLink(value: post) {
                        SearchPostRow(post: post, showsDivider: showsDivider)
                    }
                    .buttonStyle(.plain)
                },
                onLoadMore: { await postViewModel.loadMoreIfNeeded(currentItem: $0) },
                onRefresh: { await postViewModel.reload(keyword: viewModel.submittedKeyword) }
            )
            .task(id: viewModel.submittedKeyword) {
                await postViewModel.loadIfNeeded(keyword: viewModel.submittedKeyword)
            }
        }
    }
}
