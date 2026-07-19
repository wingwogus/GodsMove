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

    @FocusState private var searchFieldFocused: Bool

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
            // Pin the search bar (and tab bar) at the top: without this the whole VStack — search
            // bar included — is shoved up off-screen when the keyboard opens. The keyboard now just
            // overlays the bottom, and the scrollable content underneath can scroll past it.
            .ignoresSafeArea(.keyboard, edges: .bottom)
            // Any drag through the results/suggestions/history lists drops the keyboard. `.interactively`
            // (rather than `.immediately`) lets the drag/tap gesture itself decide whether to dismiss,
            // instead of a list scroll eagerly eating the first tap on a suggestion/recent-term row.
            .scrollDismissesKeyboard(.interactively)
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

            AppSearchBar(text: queryBinding, externalFocus: $searchFieldFocused)
                .onSubmit { submit(viewModel.query) }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, Spacing.sm)
    }

    private var queryBinding: Binding<String> {
        Binding(
            get: { viewModel.query },
            // User-driven edits flow through here. A programmatic `onSubmit` can also re-trigger this
            // setter once with the same value (Korean IME committing marked text on focus resign), so
            // `onQueryChanged` guards against no-op writes to avoid dropping out of the results phase.
            set: { viewModel.onQueryChanged($0) }
        )
    }

    /// Single funnel for a committed search (search-bar return, suggestion tap, recent-term tap):
    /// drops the search field's focus FIRST, then runs the view model's submit. Dropping focus
    /// before the state flips to the results phase means a single tap on a suggestion/recent-term
    /// row goes straight to results instead of its first tap being swallowed by keyboard dismissal.
    private func submit(_ keyword: String) {
        searchFieldFocused = false
        viewModel.onSubmit(keyword)
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
                onTapTerm: { submit($0.keyword) },
                onDeleteTerm: { viewModel.deleteRecentTerm($0.id) },
                onClearAll: { viewModel.clearAllRecent() }
            )
        } else {
            SearchSuggestionListView(
                suggestions: viewModel.suggestions,
                query: viewModel.query,
                onTapSuggestion: { submit($0) }
            )
        }
    }
}
