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
            VStack(alignment: .leading, spacing: Spacing.md) {
                AppTextField(placeholder: "도로명 주소를 입력하세요 (예: 판교역로 235)", text: $searchQuery, autoFocus: true)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.top, Spacing.md)

                if viewModel.isSearching {
                    LoadingView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.xl)
                } else if viewModel.searchResults.isEmpty {
                    EmptyStateView(message: "검색 결과가 없어요")
                        .padding(.top, Spacing.xl)
                } else {
                    List(viewModel.searchResults) { address in
                        Button {
                            onSelect(address)
                            dismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text(address.roadAddrPart1)
                                    .font(.appBody)
                                    .foregroundStyle(Color.appTextPrimary)
                                Text(address.jibunAddr)
                                    .font(.appCaption)
                                    .foregroundStyle(Color.appTextSecondary)
                            }
                        }
                    }
                    .listStyle(.plain)
                }

                Spacer()
            }
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
}

#Preview {
    AddressSearchSheet(viewModel: FarmLocationViewModel()) { _ in }
}
