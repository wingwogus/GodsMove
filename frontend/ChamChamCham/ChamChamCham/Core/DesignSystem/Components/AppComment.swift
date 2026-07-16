//
//  AppComment.swift
//  ChamChamCham
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI

/// Figma `comment`. The collapsed state shows three body lines, while the expanded state lets
/// the body grow and swaps the read-more action to `간략히 보기`. The `자세히 보기` action only appears
/// when the body actually overflows `collapsedLineLimit` lines — a short comment shows just `답글`.
struct AppComment<AvatarContent: View>: View {
    static var avatarSize: CGFloat { 32 }
    static var attachmentSize: CGFloat { 96 }
    static var attachmentCornerRadius: CGFloat { 8 }
    static var collapsedLineLimit: Int { 3 }
    static var nicknameColor: Color { Color.Text.subtle }
    static var dateColor: Color { Color.Text.muted }

    static func readMoreTitle(isReadMoreActive: Bool) -> String {
        isReadMoreActive ? "간략히 보기" : "자세히 보기"
    }

    let nickname: String
    var dateText: String = "mm/dd"
    var bodyText: String
    var isReadMoreActive: Bool = false
    var isMyComment: Bool = false
    var bodyColor: Color = Color.Text.default
    var showsActions: Bool = true
    var showsHeaderAction: Bool = true
    var onReadMore: () -> Void = {}
    var onReply: () -> Void = {}
    var onDelete: () -> Void = {}
    var onMore: () -> Void = {}

    private let avatar: AvatarContent?
    private let attachment: AnyView?

    /// Measured off-screen so `자세히 보기` is shown only when the body overflows `collapsedLineLimit`.
    /// Both heights come from hidden probes with fixed line limits, so they stay stable across expand/collapse.
    @State private var fullBodyHeight: CGFloat = 0
    @State private var clampedBodyHeight: CGFloat = 0

    private var isBodyTruncated: Bool { fullBodyHeight > clampedBodyHeight + 0.5 }

    init<Attachment: View>(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        isReadMoreActive: Bool = false,
        isMyComment: Bool = false,
        bodyColor: Color = Color.Text.default,
        showsActions: Bool = true,
        showsHeaderAction: Bool = true,
        onReadMore: @escaping () -> Void = {},
        onReply: @escaping () -> Void = {},
        onDelete: @escaping () -> Void = {},
        onMore: @escaping () -> Void = {},
        @ViewBuilder avatar: () -> AvatarContent,
        @ViewBuilder attachment: () -> Attachment
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.isReadMoreActive = isReadMoreActive
        self.isMyComment = isMyComment
        self.bodyColor = bodyColor
        self.showsActions = showsActions
        self.showsHeaderAction = showsHeaderAction
        self.onReadMore = onReadMore
        self.onReply = onReply
        self.onDelete = onDelete
        self.onMore = onMore
        self.avatar = avatar()
        self.attachment = AnyView(attachment())
    }

    init(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        isReadMoreActive: Bool = false,
        isMyComment: Bool = false,
        bodyColor: Color = Color.Text.default,
        showsActions: Bool = true,
        showsHeaderAction: Bool = true,
        onReadMore: @escaping () -> Void = {},
        onReply: @escaping () -> Void = {},
        onDelete: @escaping () -> Void = {},
        onMore: @escaping () -> Void = {},
        @ViewBuilder avatar: () -> AvatarContent
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.isReadMoreActive = isReadMoreActive
        self.isMyComment = isMyComment
        self.bodyColor = bodyColor
        self.showsActions = showsActions
        self.showsHeaderAction = showsHeaderAction
        self.onReadMore = onReadMore
        self.onReply = onReply
        self.onDelete = onDelete
        self.onMore = onMore
        self.avatar = avatar()
        self.attachment = nil
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            avatarView
            content
        }
        .padding(.horizontal, 20)
        .padding(.vertical, Spacing.sm)
        .background(Color.Object.default)
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            Text(bodyText)
                .appTypography(.bodyMedium)
                .foregroundStyle(bodyColor)
                .lineLimit(isReadMoreActive ? nil : Self.collapsedLineLimit)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background { truncationProbe }
                .padding(.top, Spacing.sm)

            if let attachment {
                attachment
                    .frame(width: Self.attachmentSize, height: Self.attachmentSize)
                    .clipShape(RoundedRectangle(cornerRadius: Self.attachmentCornerRadius))
                    .padding(.top, Spacing.sm)
            }

            if showsActions {
                actionRow
                    .padding(.top, Spacing.sm)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
            Text(nickname)
                .appTypography(.labelMedium)
                .foregroundStyle(Self.nicknameColor)
                .lineLimit(1)
            Text("·")
                .appTypography(.labelMedium)
                .foregroundStyle(Self.dateColor)
            Text(dateText)
                .appTypography(.labelMedium)
                .foregroundStyle(Self.dateColor)
                .lineLimit(1)
            Spacer(minLength: Spacing.sm)

            if showsHeaderAction {
                if isMyComment {
                    Button("삭제", action: onDelete)
                        .appTypography(.labelMedium)
                        .foregroundStyle(Color.Text.red)
                } else {
                    Button(action: onMore) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundStyle(Color.Icon.subtle)
                            .frame(width: 20, height: 20)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .frame(minHeight: 20)
    }

    /// `자세히 보기` shows only when the body overflows (or is already expanded); otherwise just `답글`.
    private var showsReadMore: Bool { isBodyTruncated || isReadMoreActive }

    private var actionRow: some View {
        HStack(spacing: 12) {
            if showsReadMore {
                Button(Self.readMoreTitle(isReadMoreActive: isReadMoreActive), action: onReadMore)
                Rectangle()
                    .fill(Color.Object.strong)
                    .frame(width: 1, height: 12)
            }
            Button("답글", action: onReply)
        }
        .buttonStyle(.plain)
        .appTypography(.labelMedium)
        .foregroundStyle(Color.Text.muted)
        .frame(height: 20)
    }

    /// Hidden probes that measure the body's intrinsic height at both the clamped line limit and unlimited,
    /// at the real body width. `isReadMoreActive` doesn't affect these, so the truncation verdict is stable.
    /// Heights are reported via `onAppear`/`onChange` (MainActor-isolated) rather than a preference so the
    /// `@State` writes are Swift 6 strict-concurrency clean.
    private var truncationProbe: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                probeText(lineLimit: nil)
                    .background { heightReporter { fullBodyHeight = $0 } }
                probeText(lineLimit: Self.collapsedLineLimit)
                    .background { heightReporter { clampedBodyHeight = $0 } }
            }
            .frame(width: proxy.size.width, alignment: .topLeading)
            .hidden()
        }
    }

    private func probeText(lineLimit: Int?) -> some View {
        Text(bodyText)
            .appTypography(.bodyMedium)
            .lineLimit(lineLimit)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func heightReporter(_ assign: @escaping (CGFloat) -> Void) -> some View {
        GeometryReader { proxy in
            Color.clear
                .onAppear { assign(proxy.size.height) }
                .onChange(of: proxy.size.height) { _, newValue in assign(newValue) }
        }
    }

    @ViewBuilder private var avatarView: some View {
        if let avatar {
            avatar
                .frame(width: Self.avatarSize, height: Self.avatarSize)
                .clipShape(Circle())
        } else {
            AppAvatar(size: .small) {
                AppImagePlaceholder(isCircle: true, squareSize: 6)
            }
        }
    }
}

extension AppComment where AvatarContent == EmptyView {
    init<Attachment: View>(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        isReadMoreActive: Bool = false,
        isMyComment: Bool = false,
        bodyColor: Color = Color.Text.default,
        showsActions: Bool = true,
        showsHeaderAction: Bool = true,
        onReadMore: @escaping () -> Void = {},
        onReply: @escaping () -> Void = {},
        onDelete: @escaping () -> Void = {},
        onMore: @escaping () -> Void = {},
        @ViewBuilder attachment: () -> Attachment
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.isReadMoreActive = isReadMoreActive
        self.isMyComment = isMyComment
        self.bodyColor = bodyColor
        self.showsActions = showsActions
        self.showsHeaderAction = showsHeaderAction
        self.onReadMore = onReadMore
        self.onReply = onReply
        self.onDelete = onDelete
        self.onMore = onMore
        self.avatar = nil
        self.attachment = AnyView(attachment())
    }

    init(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        isReadMoreActive: Bool = false,
        isMyComment: Bool = false,
        bodyColor: Color = Color.Text.default,
        showsActions: Bool = true,
        showsHeaderAction: Bool = true,
        onReadMore: @escaping () -> Void = {},
        onReply: @escaping () -> Void = {},
        onDelete: @escaping () -> Void = {},
        onMore: @escaping () -> Void = {}
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.isReadMoreActive = isReadMoreActive
        self.isMyComment = isMyComment
        self.bodyColor = bodyColor
        self.showsActions = showsActions
        self.showsHeaderAction = showsHeaderAction
        self.onReadMore = onReadMore
        self.onReply = onReply
        self.onDelete = onDelete
        self.onMore = onMore
        self.avatar = nil
        self.attachment = nil
    }
}

/// Intrinsic body height with no line limit — compared against the clamped height to detect overflow.
private struct BodyFullHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

/// Body height clamped to `collapsedLineLimit` lines.
private struct BodyClampedHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

#Preview {
    VStack(spacing: 0) {
        AppComment(
            nickname: "닉네임 (짧은 댓글 — 자세히 보기 없음)",
            bodyText: "한 줄짜리 댓글입니다.",
            onReadMore: {}
        )

        AppComment(
            nickname: "닉네임 (3줄 초과 — 자세히 보기 표시)",
            bodyText: "댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다.",
            onReadMore: {}
        )

        AppComment(
            nickname: "닉네임",
            bodyText: "이미지가 있는 댓글입니다.",
            isReadMoreActive: true,
            isMyComment: true,
            onDelete: {},
            attachment: { AppImagePlaceholder(cornerRadius: 8) }
        )
    }
    .padding()
    .background(Color.Background.subtle)
}
