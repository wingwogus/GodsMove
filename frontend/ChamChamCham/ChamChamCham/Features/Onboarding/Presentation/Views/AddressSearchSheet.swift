//
//  AddressSearchSheet.swift
//  ChamChamCham
//
//  Created by iyungui on 7/3/26.
//

import SwiftUI

struct AddressSearchSheet: View {
    let viewModel: FarmLocationViewModel
    let onSelect: (JusoAddress) -> Void

    @State private var searchQuery = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                AppSearchBar(text: $searchQuery, placeholder: "도로명 주소를 입력하세요\n(예: 판교역로 235)")
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.md)
                    .padding(.bottom, Spacing.sm)

                results
            }
            .background(Color.Background.default)
            .navigationTitle("농지 주소 검색")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("닫기") { dismiss() }
                }
            }
            .task(id: searchQuery) {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                await viewModel.search(keyword: searchQuery)
            }
        }
    }

    @ViewBuilder
    private var results: some View {
        if viewModel.isSearching {
            LoadingView()
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xl)
            Spacer()
        } else if viewModel.searchResults.isEmpty {
            EmptyStateView(
                message: searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? "도로명 주소로 검색해보세요."
                    : "검색 결과가 없어요."
            )
            .padding(.top, Spacing.xl)
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(viewModel.searchResults) { address in
                        addressRow(address)
                        AppDivider(size: .small)
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private func addressRow(_ address: JusoAddress) -> some View {
        Button {
            onSelect(address)
            dismiss()
        } label: {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(address.roadAddrPart1)
                    .appTypography(.bodyLarge)
                    .foregroundStyle(Color.Text.default)
                    .multilineTextAlignment(.leading)
                Text(address.jibunAddr)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.lg)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    AddressSearchSheet(viewModel: FarmLocationViewModel()) { _ in }
}
