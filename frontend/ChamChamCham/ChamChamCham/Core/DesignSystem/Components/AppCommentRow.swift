//
//  AppCommentRow.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//

import SwiftUI

/// Figma `comment` row. Body text is capped at three lines by default to match the design.
struct AppCommentRow<AvatarContent: View>: View {
    let nickname: String
    var dateText: String = "mm/dd"
    var bodyText: String
    var primaryActionTitle: String = "자세히 보기"
    var secondaryActionTitle: String = "답글"
    var lineLimit: Int? = 3
    var showsDivider: Bool = true
    var primaryAction: () -> Void = {}
    var secondaryAction: () -> Void = {}

    private let avatar: AvatarContent?

    init(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        primaryActionTitle: String = "자세히 보기",
        secondaryActionTitle: String = "답글",
        lineLimit: Int? = 3,
        showsDivider: Bool = true,
        primaryAction: @escaping () -> Void = {},
        secondaryAction: @escaping () -> Void = {},
        @ViewBuilder avatar: () -> AvatarContent
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.primaryActionTitle = primaryActionTitle
        self.secondaryActionTitle = secondaryActionTitle
        self.lineLimit = lineLimit
        self.showsDivider = showsDivider
        self.primaryAction = primaryAction
        self.secondaryAction = secondaryAction
        self.avatar = avatar()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                avatarView

                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(nickname)
                            .appTypography(.labelMedium)
                            .foregroundStyle(Color.Text.subtle)
                            .lineLimit(1)
                        Spacer(minLength: Spacing.md)
                        Text(dateText)
                            .appTypography(.bodyMedium)
                            .foregroundStyle(Color.Text.default)
                    }

                    Text(bodyText)
                        .appTypography(.bodyMediumEmphasized)
                        .foregroundStyle(Color.Text.default)
                        .lineLimit(lineLimit)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: Spacing.md) {
                        Button(primaryActionTitle, action: primaryAction)
                        Button(secondaryActionTitle, action: secondaryAction)
                    }
                    .buttonStyle(.plain)
                    .appTypography(.labelMedium)
                    .foregroundStyle(Color.Text.muted)
                }
            }
            .padding(Spacing.md)

            if showsDivider {
                Rectangle()
                    .fill(Color.Border.default)
                    .frame(height: 1)
                    .padding(.horizontal, Spacing.md)
            }
        }
        .background(Color.Object.default)
    }

    @ViewBuilder private var avatarView: some View {
        if let avatar {
            avatar
                .frame(width: 32, height: 32)
                .clipShape(Circle())
        } else {
            AppAvatar(size: .small) {
                AppImagePlaceholder(isCircle: true, squareSize: 6)
            }
        }
    }
}

extension AppCommentRow where AvatarContent == EmptyView {
    init(
        nickname: String,
        dateText: String = "mm/dd",
        bodyText: String,
        primaryActionTitle: String = "자세히 보기",
        secondaryActionTitle: String = "답글",
        lineLimit: Int? = 3,
        showsDivider: Bool = true,
        primaryAction: @escaping () -> Void = {},
        secondaryAction: @escaping () -> Void = {}
    ) {
        self.nickname = nickname
        self.dateText = dateText
        self.bodyText = bodyText
        self.primaryActionTitle = primaryActionTitle
        self.secondaryActionTitle = secondaryActionTitle
        self.lineLimit = lineLimit
        self.showsDivider = showsDivider
        self.primaryAction = primaryAction
        self.secondaryAction = secondaryAction
        self.avatar = nil
    }
}

#Preview {
    VStack(spacing: 0) {
        AppCommentRow(
            nickname: "닉네임",
            bodyText: "댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다.",
            primaryActionTitle: "간략히 보기"
        )

        AppCommentRow(
            nickname: "닉네임",
            bodyText: "댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다.",
            primaryActionTitle: "자세히 보기"
        )
    }
    .padding()
    .background(Color.Background.subtle)
}
