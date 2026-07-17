//
//  SearchView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Root search screen, presented as a `fullScreenCover` from Home/영농 기록/정보 공유's search
/// icons. Owns its own `NavigationStack` (for pushing 기록/게시글 detail) and switches between the
/// idle(최근 검색어)/typing(제안)/results phases based on `SearchViewModel.query`/`submittedKeyword`.
struct SearchView: View {
    @Environment(\.dismiss) private var dismiss
    private let container: DIContainer

    @State private var viewModel: SearchViewModel
    @State private var overviewViewModel: SearchOverviewViewModel
    @State private var recordViewModel: SearchRecordListViewModel
    @State private var policyViewModel: SearchPolicyListViewModel
    @State private var postViewModel: SearchPostListViewModel

    init(container: DIContainer) {
        self.container = container
        let repository = container.makeSearchRepository()
        _viewModel = State(initialValue: SearchViewModel(
            repository: repository,
            recentSearchStore: container.recentSearchStore
        ))
        _overviewViewModel = State(initialValue: SearchOverviewViewModel(repository: repository))
        _recordViewModel = State(initialValue: SearchRecordListViewModel(repository: repository))
        _policyViewModel = State(initialValue: SearchPolicyListViewModel(repository: repository))
        _postViewModel = State(initialValue: SearchPostListViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                searchBar
                content
            }
            .navigationBarHidden(true)
            .navigationDestination(for: FarmingRecordSummary.self) { record in
                RecordDetailView(recordId: record.id, repository: container.makeRecordRepository()) {
                    Task { await recordViewModel.reload(keyword: viewModel.submittedKeyword) }
                }
            }
            .navigationDestination(for: CommunityPostSummary.self) { post in
                CommunityDetailView(postId: post.id, container: container)
            }
        }
        .task { viewModel.loadRecent() }
    }

    private var searchBar: some View {
        HStack(spacing: 0) {
            Button {
                dismiss()
            } label: {
                AppIconView(source: .asset("arrow_back_ios_new"), size: 32)
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)

            AppSearchBar(text: queryBinding)
                .onSubmit { viewModel.onSubmit(viewModel.query) }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, Spacing.sm)
    }

    private var queryBinding: Binding<String> {
        Binding(
            get: { viewModel.query },
            set: { newValue in
                viewModel.query = newValue
                if newValue.isEmpty {
                    viewModel.clearSubmission()
                } else {
                    // Real-time search-as-you-type: `loadSuggestions` debounces/cancels internally.
                    viewModel.loadSuggestions(for: newValue)
                }
            }
        )
    }

    @ViewBuilder private var content: some View {
        if viewModel.submittedKeyword != nil {
            SearchResultsView(
                viewModel: viewModel,
                overviewViewModel: overviewViewModel,
                recordViewModel: recordViewModel,
                policyViewModel: policyViewModel,
                postViewModel: postViewModel
            )
        } else if viewModel.query.isEmpty {
            SearchHistoryView(
                recentTerms: viewModel.recentTerms,
                onTapTerm: { viewModel.onSubmit($0.keyword) },
                onDeleteTerm: { viewModel.deleteRecentTerm($0.id) },
                onClearAll: { viewModel.clearAllRecent() }
            )
        } else {
            SearchSuggestionListView(
                suggestions: viewModel.suggestions,
                query: viewModel.query,
                onTapSuggestion: { viewModel.onSubmit($0) }
            )
        }
    }
}
