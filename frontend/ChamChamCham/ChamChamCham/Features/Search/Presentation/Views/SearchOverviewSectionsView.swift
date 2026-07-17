//
//  SearchOverviewSectionsView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// "전체" tab content: 나의 일지 / 정책 정보 / 게시글 sections, each up to 3 preview rows (the
/// backend caps this server-side) with a header showing the real `totalCount` and a "더보기"
/// chevron only when `hasMore` — tapping either switches the parent `AppTabBar` to that category
/// (Figma's own open question; the same screen re-filters rather than pushing a new one).
struct SearchOverviewSectionsView: View {
    @Environment(\.openURL) private var openURL

    let result: SearchAllResult?
    let isLoading: Bool
    let errorMessage: String?
    let onSelectCategory: (SearchCategory) -> Void

    var body: some View {
        ScrollView {
            content
        }
    }

    @ViewBuilder private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xl)
        } else if let errorMessage {
            emptyState(text: errorMessage)
        } else if let result {
            VStack(spacing: 0) {
                recordSection(result.records)
                AppDivider(size: .medium)
                policySection(result.policies)
                AppDivider(size: .medium)
                postSection(result.posts)
            }
            .padding(.bottom, 112)
        }
    }

    private func recordSection(_ section: SearchSection<FarmingRecordSummary>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "나의 일지", count: section.totalCount, hasMore: section.hasMore) {
                onSelectCategory(.records)
            }
            ForEach(Array(section.items.enumerated()), id: \.element.id) { index, record in
                NavigationLink(value: record) {
                    RecordRow(record: record, showsDivider: index != section.items.count - 1)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func policySection(_ section: SearchSection<SearchPolicyItem>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "정책 정보", count: section.totalCount, hasMore: section.hasMore) {
                onSelectCategory(.policies)
            }
            ForEach(Array(section.items.enumerated()), id: \.element.id) { index, item in
                Button {
                    if let url = item.sourceUrl { openURL(url) }
                } label: {
                    SearchPolicyRow(item: item, showsDivider: index != section.items.count - 1)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func postSection(_ section: SearchSection<CommunityPostSummary>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "게시글", count: section.totalCount, hasMore: section.hasMore) {
                onSelectCategory(.posts)
            }
            ForEach(Array(section.items.enumerated()), id: \.element.id) { index, post in
                NavigationLink(value: post) {
                    SearchPostRow(post: post, showsDivider: index != section.items.count - 1)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func sectionHeader(title: String, count: Int, hasMore: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 0) {
                HStack(spacing: 4) {
                    Text(title)
                        .appTypography(.titleLargeEmphasized)
                        .foregroundStyle(Color.Text.default)
                    Text("\(count)")
                        .appTypography(.titleLargeEmphasized)
                        .foregroundStyle(Color.Text.subtle)
                }
                Spacer()
                if hasMore {
                    AppIconView(source: .asset("arrow_forward_ios"), size: 24)
                        .foregroundStyle(Color.Icon.default)
                }
            }
            .padding(.horizontal, 20)
            .frame(height: 71)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func emptyState(text: String) -> some View {
        VStack(spacing: Spacing.md) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundStyle(Color.Icon.disabled)
            Text(text)
                .appTypography(.bodyMedium)
                .foregroundStyle(Color.Text.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.xl * 2)
    }
}
