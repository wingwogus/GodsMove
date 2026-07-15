//
//  CommunitySharedViews.swift
//  ChamChamCham
//
//  Created by iyungui on 7/7/26.
//

import SwiftUI

/// Author avatar backed by a remote URL, falling back to a placeholder when the member has no photo or it
/// fails to load. Always uses `AppAvatar`'s thumbnail initializer (with `AnyView`) so the size type stays a
/// single `AppAvatar<AnyView>.Size` across call sites.
struct CommunityAvatar: View {
    let profileImageUrl: String?
    var size: AppAvatar<AnyView>.Size = .small

    var body: some View {
        AppAvatar(size: size) {
            AnyView(thumbnail)
        }
    }

    @ViewBuilder private var thumbnail: some View {
        if let profileImageUrl, let url = URL(string: profileImageUrl) {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Color.Object.muted
            }
        } else {
            Color.Object.muted.overlay {
                Image(systemName: "person.fill")
                    .foregroundStyle(Color.Icon.disabled)
            }
        }
    }
}

/// A remote image that fills a fixed square with rounded corners, used for post thumbnails and detail images.
struct CommunityRemoteImage: View {
    let url: String?
    var cornerRadius: CGFloat = 8

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Color.Object.muted)
            .overlay {
                if let url, let parsed = URL(string: url) {
                    AsyncImage(url: parsed) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                } else {
                    Image(systemName: "photo")
                        .font(.system(size: 24))
                        .foregroundStyle(Color.Icon.disabled)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

/// The [Q&A] / [crop] badge pair shown on rows and detail. Q&A only appears for `.question` posts.
struct CommunityTagRow: View {
    let postType: CommunityPostType
    let cropName: String

    var body: some View {
        HStack(spacing: Spacing.sm) {
            if postType == .question {
                AppBadge(label: "Q&A", size: .small, style: .solidPastel, variant: .secondary)
            }
            AppBadge(label: cropName, size: .small, style: .solidPastel, variant: .primary)
        }
    }
}

/// Author avatar + "닉네임 · N분 전" line, reused by the row and the detail header.
struct CommunityAuthorLine: View {
    let author: CommunityAuthor
    let createdAt: Date
    var avatarSize: AppAvatar<AnyView>.Size = .xSmall

    var body: some View {
        HStack(spacing: Spacing.sm) {
            CommunityAvatar(profileImageUrl: author.profileImageUrl, size: avatarSize)
            Text(authorText)
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
        }
    }

    private var authorText: String {
        let name = author.nickname ?? "익명"
        let time = CommunityRelativeTime.string(from: createdAt)
        return time.isEmpty ? name : "\(name) · \(time)"
    }
}

/// Heart + count and speech-bubble + count, shown on rows and detail.
struct CommunityMetrics: View {
    let likeCount: Int
    let likedByMe: Bool
    let commentCount: Int
    var onTapLike: (() -> Void)?

    var body: some View {
        HStack(spacing: Spacing.md) {
            Button {
                onTapLike?()
            } label: {
                Label("\(likeCount)", systemImage: likedByMe ? "heart.fill" : "heart")
                    .appTypography(.labelMedium)
                    .foregroundStyle(likedByMe ? Color.Icon.red : Color.Text.muted)
            }
            .buttonStyle(.plain)
            .disabled(onTapLike == nil)

            Label("\(commentCount)", systemImage: "bubble.left")
                .appTypography(.labelMedium)
                .foregroundStyle(Color.Text.muted)
        }
    }
}
