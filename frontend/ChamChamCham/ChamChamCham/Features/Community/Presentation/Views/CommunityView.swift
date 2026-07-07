//
//  CommunityView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Layout-only wireframe for the community tab. No navigation to post detail or write screens yet —
/// the category chips, sort control, "+" button, and floating write button are inert placeholders.
struct CommunityView: View {
    @State private var selectedCategory = "전체"

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    header
                    categoryFilterRow
                    sortRow
                    postList
                }
                .padding(Spacing.md)
            }
            writeButton
        }
    }

    private var header: some View {
        HStack {
            Text("커뮤니티")
                .font(.largeTitle.bold())
            Spacer()
            Button {
            } label: {
                Image(systemName: "magnifyingglass")
                    .font(.title2)
            }
            Button {
            } label: {
                Image(systemName: "bell")
                    .font(.title2)
            }
        }
    }

    private var categoryFilterRow: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(categorySampleData, id: \.self) { category in
                Button {
                    selectedCategory = category
                } label: {
                    Text(category)
                        .font(.subheadline)
                        .padding(.horizontal, Spacing.md)
                        .padding(.vertical, Spacing.sm)
                        .background(
                            selectedCategory == category
                                ? Color(.label)
                                : Color(.secondarySystemBackground)
                        )
                        .foregroundStyle(
                            selectedCategory == category
                                ? Color(.systemBackground)
                                : Color(.label)
                        )
                        .clipShape(Capsule())
                }
            }
            Spacer()
            Button {
            } label: {
                Image(systemName: "plus")
                    .font(.headline)
                    .frame(width: 36, height: 36)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Circle())
            }
        }
    }

    private var sortRow: some View {
        HStack {
            Spacer()
            Button {
            } label: {
                HStack(spacing: Spacing.xs) {
                    Text("최신순")
                    Image(systemName: "chevron.down")
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
        }
    }

    private var postList: some View {
        VStack(spacing: 0) {
            ForEach(postSampleData) { post in
                PostRow(post: post)
                if post.id != postSampleData.last?.id {
                    Divider()
                }
            }
        }
    }

    private var writeButton: some View {
        Button {
        } label: {
            Image(systemName: "pencil")
                .font(.title2)
                .frame(width: 56, height: 56)
                .background(Color(.systemGray3))
                .foregroundStyle(Color(.systemBackground))
                .clipShape(Circle())
        }
        .padding(Spacing.md)
    }
}

private let categorySampleData = ["전체", "인삼", "황기", "당귀", "도라지"]

private struct PostSample: Identifiable {
    let id = UUID()
    let title: String
    let preview: String
    let tags: [String]
    let authorName: String
    let timeAgo: String
    let commentCount: Int
    let likeCount: Int
}

private let postSampleData: [PostSample] = [
    .init(
        title: "황기 발아율이 너무 낮은데 원인이 뭐임?",
        preview: "올해 파종했는데 싹이 거의 안 올라오네요...",
        tags: ["Q&A", "황기"],
        authorName: "나루지기",
        timeAgo: "10분 전",
        commentCount: 12,
        likeCount: 8
    ),
    .init(
        title: "인삼 밭 두둑 만드는 방법 공유합니다",
        preview: "작업 도구와 시기를 잘 맞추는 게 핵심입니다...",
        tags: ["인삼"],
        authorName: "초보농부",
        timeAgo: "30분 전",
        commentCount: 12,
        likeCount: 8
    ),
    .init(
        title: "인삼 홍삼 가공 수율 기준이 있나요?",
        preview: "올해 파종했는데 싹이 거의 안 올라오네요...",
        tags: ["Q&A", "인삼"],
        authorName: "홍삼러버",
        timeAgo: "1시간 전",
        commentCount: 12,
        likeCount: 8
    ),
    .init(
        title: "당귀 수확 후 건조 방법",
        preview: "그늘에서 천천히 말리는 게 향이 오래 갑니다.",
        tags: [],
        authorName: "당귀지기",
        timeAgo: "2시간 전",
        commentCount: 12,
        likeCount: 8
    ),
]

private struct PostRow: View {
    let post: PostSample

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(post.title)
                    .font(.headline)
                Text(post.preview)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                if !post.tags.isEmpty {
                    HStack(spacing: Spacing.xs) {
                        ForEach(post.tags, id: \.self) { tag in
                            Text(tag)
                                .font(.caption)
                                .padding(.horizontal, Spacing.sm)
                                .padding(.vertical, 4)
                                .background(Color(.secondarySystemBackground))
                                .clipShape(Capsule())
                        }
                    }
                }

                HStack(spacing: Spacing.sm) {
                    Circle()
                        .fill(Color(.systemGray4))
                        .frame(width: 20, height: 20)
                    Text("\(post.authorName) · \(post.timeAgo)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Label("\(post.commentCount)", systemImage: "bubble.left")
                    Label("\(post.likeCount)", systemImage: "heart")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.secondarySystemBackground))
                .frame(width: 80, height: 80)
                .overlay {
                    Image(systemName: "photo")
                        .foregroundStyle(.secondary)
                }
        }
        .padding(.vertical, Spacing.md)
    }
}

#Preview {
    CommunityView()
}
