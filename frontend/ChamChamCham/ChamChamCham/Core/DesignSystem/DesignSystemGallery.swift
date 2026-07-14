//
//  DesignSystemGallery.swift
//  ChamChamCham
//
//  Created by iyungui on 7/6/26.
//
//  One place to preview every design-system component. Each component has its own named
//  `#Preview` — pick one from the Xcode canvas preview list (not a single combined screen).
//

import SwiftUI

// MARK: - Buttons

#Preview("AppButton") {
    ScrollView {
        VStack(alignment: .leading, spacing: Spacing.md) {
            AppButton("레이블", variant: .primary, size: .large) {}
            AppButton("레이블", variant: .secondary, size: .medium) {}
            AppButton("레이블", variant: .tertiary, size: .medium) {}
            AppButton("레이블", variant: .neutral, size: .small) {}
            AppButton("레이블", systemImage: "checkmark", variant: .primary, size: .medium) {}
            AppButton("레이블", variant: .primary, size: .medium) {}.disabled(true)
            AppButton("전폭 버튼", variant: .primary, size: .medium, fullWidth: true) {}
            HStack(spacing: Spacing.sm) {
                AppButton(systemImage: "plus", variant: .primary, size: .small) {}
                AppButton(systemImage: "plus", variant: .secondary, size: .medium) {}
                AppButton(systemImage: "plus", variant: .tertiary, size: .large) {}
                AppButton(systemImage: "plus", variant: .primary, size: .xlarge) {}
            }
        }
        .padding()
    }
}

#Preview("PrimaryButton") {
    VStack(spacing: Spacing.md) {
        PrimaryButton(title: "다음") {}
        PrimaryButton(title: "카카오로 시작하기") {}
    }
    .padding()
}

// MARK: - Toggle / Chip / Segmented

#Preview("AppToggle") {
    @Previewable @State var on = true
    @Previewable @State var off = false
    VStack(spacing: Spacing.lg) {
        AppToggle(isOn: $on)
        AppToggle(isOn: $off)
        AppToggle(isOn: $on).disabled(true)
        AppToggle(isOn: $off).disabled(true)
    }
    .padding()
}

#Preview("AppChip") {
    @Previewable @State var selected = true
    VStack(alignment: .leading, spacing: Spacing.md) {
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true, style: .solid)
            AppChip(label: "레이블", isSelected: true, style: .solid, systemImage: "checkmark")
            AppChip(label: "레이블", isSelected: true, style: .solid, leadingSystemImage: "checkmark")
        }
        HStack(spacing: Spacing.sm) {
            AppChip(label: "레이블", isSelected: true, style: .solidPastel)
            AppChip(label: "레이블", isSelected: false, style: .solidPastel)
            AppChip(label: "레이블", isSelected: false, style: .solidPastel, trailingSystemImage: "checkmark")
        }
        AppChip(label: "토글되는 칩", isSelected: selected, style: .solid) { selected.toggle() }
    }
    .padding()
}

#Preview("AppSegmentedControl") {
    @Previewable @State var selection = 0
    AppSegmentedControl(titles: ["레이블", "레이블"], selection: $selection)
        .padding()
}

#Preview("AppDivider") {
    VStack(spacing: Spacing.lg) {
        AppDivider(size: .small)
        AppDivider(size: .medium)
    }
    .frame(width: 390)
    .padding()
}

#Preview("AppSortButton") {
    @Previewable @State var expanded = false
    VStack(alignment: .leading, spacing: Spacing.lg) {
        AppSortButton(title: "최신순", isExpanded: false)
        AppSortButton(title: "최신순", isExpanded: true)
        AppSortButton(title: "최신순", isExpanded: expanded) { expanded.toggle() }
    }
    .padding()
}

// MARK: - Badges

#Preview("AppBadge") {
    VStack(alignment: .leading, spacing: Spacing.md) {
        HStack(spacing: Spacing.sm) {
            AppBadge(label: "레이블", style: .solid, variant: .primary)
            AppBadge(label: "레이블", style: .solidPastel, variant: .primary)
            AppBadge(label: "레이블", style: .solid, variant: .secondary)
            AppBadge(label: "레이블", style: .solidPastel, variant: .secondary)
        }
        HStack(spacing: Spacing.sm) {
            AppBadge(label: "레이블", size: .small, style: .solid, variant: .primary)
            AppBadge(label: "레이블", size: .small, style: .solidPastel, variant: .primary)
            AppBadge(label: "레이블", size: .small, style: .solid, variant: .secondary)
            AppBadge(label: "레이블", size: .small, style: .solidPastel, variant: .secondary)
        }
    }
    .padding()
}

#Preview("AppNotificationBadge") {
    HStack(spacing: Spacing.md) {
        AppNotificationBadge(count: 1, variant: .new)
        AppNotificationBadge(count: 999, variant: .new)
        AppNotificationBadge(count: 1, variant: .primary)
        AppNotificationBadge(count: 1200, variant: .primary)
        AppNotificationBadge(count: nil, variant: .new)
    }
    .padding()
}

// MARK: - Inputs

#Preview("AppTextField") {
    @Previewable @State var empty = ""
    @Previewable @State var filled = "텍스트"
    VStack(spacing: Spacing.lg) {
        AppTextField(label: "레이블", placeholder: "텍스트", text: $empty,
                     isRequired: true, helperText: "메시지를 전달합니다.")
        AppTextField(label: "레이블", placeholder: "텍스트", text: $filled,
                     isRequired: true, helperText: "메시지를 전달합니다.")
        AppTextField(label: "레이블", placeholder: "텍스트", text: $empty,
                     isRequired: true, errorMessage: "메시지를 전달합니다.")
        AppTextField(label: "레이블", placeholder: "텍스트", text: $empty,
                     isRequired: true, helperText: "메시지를 전달합니다.")
            .disabled(true)
    }
    .padding()
}

#Preview("AppDateField") {
    @Previewable @State var empty: Date?
    @Previewable @State var picked: Date? = .now
    VStack(spacing: Spacing.lg) {
        AppDateField(label: "레이블", selection: $empty,
                     isRequired: true, helperText: "메시지를 전달합니다.")
        AppDateField(label: "레이블", selection: $picked,
                     isRequired: true, helperText: "메시지를 전달합니다.")
        AppDateField(label: "레이블", selection: $empty,
                     isRequired: true, errorMessage: "메시지를 전달합니다.")
        AppDateField(label: "레이블", selection: $empty,
                     isRequired: true, helperText: "메시지를 전달합니다.")
            .disabled(true)
    }
    .padding()
}

#Preview("AppTextEditor") {
    @Previewable @State var empty = ""
    @Previewable @State var filled = "텍스트"
    ScrollView {
        VStack(spacing: Spacing.lg) {
            AppTextEditor(label: "레이블", placeholder: "텍스트", text: $empty,
                          isRequired: true, helperText: "메시지를 전달합니다.", characterLimit: 200)
            AppTextEditor(label: "레이블", placeholder: "텍스트", text: $filled,
                          isRequired: true, errorMessage: "메시지를 전달합니다.", characterLimit: 200)
            AppTextEditor(label: "레이블", placeholder: "텍스트", text: $empty,
                          isRequired: true, helperText: "메시지를 전달합니다.")
                .disabled(true)
        }
        .padding()
    }
}

#Preview("AppSearchBar") {
    @Previewable @State var empty = ""
    @Previewable @State var filled = "감자"
    VStack(spacing: Spacing.md) {
        AppSearchBar(text: $empty)
        AppSearchBar(text: $filled)
    }
    .padding()
}

#Preview("AppDropdown") {
    @Previewable @State var selected: String?
    VStack(spacing: Spacing.lg) {
        AppDropdown("레이블", options: ["감자", "고구마", "당근"], selection: $selected,
                    isRequired: true, helperText: "메시지를 전달합니다.")
        AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant("감자"),
                    isRequired: true, helperText: "메시지를 전달합니다.")
        AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant(nil),
                    isRequired: true, errorMessage: "메시지를 전달합니다.")
        AppDropdown("레이블", options: ["감자", "고구마"], selection: .constant("감자"),
                    isRequired: true, errorMessage: "메시지를 전달합니다.")
    }
    .padding()
}

// MARK: - Navigation

#Preview("AppTabBar") {
    @Previewable @State var a = 0
    @Previewable @State var b = 0
    VStack(spacing: Spacing.xl) {
        AppTabBar(titles: ["레이블", "레이블"], selection: $a)
        AppTabBar(titles: ["전체", "전초", "뿌리·껍질", "뿌리줄기", "잎", "꽃"],
                  selection: $b, scrollable: true)
    }
}

#Preview("AppTopAppBar") {
    VStack(spacing: Spacing.xl) {
        AppTopAppBar(title: "타이틀", trailing: [.init("bell"), .init("gearshape")])
        AppTopAppBar(title: "타이틀", isDetail: true,
                     leading: .init("chevron.left"), trailing: [.init("ellipsis")])
        AppTopAppBar(title: "타이틀", background: .subtle,
                     trailing: [.init("bell")])
    }
}

#Preview("AppNavBar") {
    @Previewable @State var selection = 0
    VStack {
        Spacer()
        AppNavBar(
            items: [
                .init(title: "홈", icon: "house", selectedIcon: "house.fill"),
                .init(title: "영농 기록", icon: "doc.text", selectedIcon: "doc.text.fill"),
                .init(title: "정보 공유", icon: "bubble.left", selectedIcon: "bubble.left.fill"),
                .init(title: "프로필", icon: "person", selectedIcon: "person.fill"),
            ],
            selection: $selection
        )
    }
}

// MARK: - Media / Avatar

#Preview("AppAvatar") {
    HStack(alignment: .center, spacing: Spacing.lg) {
        AppAvatar(size: .large) { AppImagePlaceholder(isCircle: true, squareSize: 12) }
        AppAvatar(size: .large)
        AppAvatar(size: .medium) { AppImagePlaceholder(isCircle: true, squareSize: 8) }
        AppAvatar(size: .medium)
        AppAvatar(size: .small)
        AppAvatar(size: .xSmall)
    }
    .padding()
}

#Preview("AppImagePlaceholder") {
    HStack(spacing: Spacing.md) {
        AppImagePlaceholder()
            .frame(width: 92, height: 92)
        AppImagePlaceholder(isCircle: true, squareSize: 12)
            .frame(width: 92, height: 92)
    }
    .padding()
}

#Preview("AppImageUploadSlot") {
    HStack(spacing: Spacing.lg) {
        AppImageUploadSlot(size: .medium, onRemove: {}) {
            AppImagePlaceholder(cornerRadius: 8)
        }
        AppImageUploadSlot(size: .medium, label: "0/5")
        AppImageUploadSlot(size: .small, onRemove: {}) {
            AppImagePlaceholder(cornerRadius: 4)
        }
    }
    .padding()
}

// MARK: - Toast

#Preview("AppToast") {
    VStack(spacing: Spacing.md) {
        AppToast(message: "메시지가 표시됩니다.")
        AppToast(message: "메시지가 표시됩니다.", variant: .error)
    }
    .frame(width: 350)
    .padding()
    .background(Color.Background.subtle)
}

// MARK: - List / Comment

#Preview("AppListItem") {
    ScrollView {
        VStack(spacing: 0) {
            AppListItem(size: .xsmall, title: "타이틀")
            AppListItem(size: .small, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(size: .medium, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(size: .large, title: "타이틀", caption: "캡션", badges: ["레이블", "레이블"])
            AppListItem(
                size: .xlarge,
                title: "타이틀",
                organization: "기관",
                infoRows: [("대상자", "캡션"), ("지원금액", "캡션"), ("접수기간", "캡션")]
            )
        }
        .padding(.horizontal, Spacing.lg)
    }
    .background(Color.Background.subtle)
}

#Preview("AppComment") {
    VStack(spacing: 0) {
        AppComment(
            nickname: "닉네임",
            bodyText: "댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다. 댓글은 최대 3줄까지 보여집니다.",
            onReadMore: {}
        )
        AppComment(
            nickname: "닉네임",
            bodyText: "짧은 댓글.",
            isMyComment: true,
            onDelete: {},
            attachment: { AppImagePlaceholder(cornerRadius: 8) }
        )
    }
    .padding()
    .background(Color.Background.subtle)
}

#Preview("AppCommentInput") {
    @Previewable @State var empty = ""
    @Previewable @State var filled = "댓글 내용"
    @FocusState var emptyFocused: Bool
    @FocusState var filledFocused: Bool

    VStack(spacing: Spacing.lg) {
        AppCommentInput(text: $empty, isFocused: $emptyFocused)
        AppCommentInput(text: $filled, isFocused: $filledFocused) {
            AppImagePlaceholder(cornerRadius: 4)
        }
    }
    .frame(width: 390)
}

// MARK: - Cards

#Preview("AppCard") {
    ScrollView {
        VStack(spacing: Spacing.lg) {
            AppCard(size: .xsmall, title: "타이틀", captions: ["캡션", "캡션"])
            AppCard(size: .small, title: "비료 주기", captions: ["캡션"], badges: ["레이블", "레이블"])
            AppCard(size: .medium, title: "타이틀", captions: ["캡션", "캡션"], badges: ["레이블"])
            AppCard(
                size: .large,
                title: "타이틀",
                captions: ["mm.dd", "mm.dd"],
                badges: ["레이블", "레이블"]
            )
        }
        .padding()
        .background(Color.Background.subtle)
    }
}

#Preview("FarmCard") {
    ScrollView {
        VStack(spacing: Spacing.md) {
            FarmCard(
                farmName: "행복농장",
                roadAddress: "경기도 이천시 …",
                crops: ["인삼", "고추", "배추", "무", "파"]
            )
            FarmCard(
                farmName: "행복농장",
                roadAddress: "경기도 이천시 …",
                crops: ["인삼", "고추"],
                isSelected: true
            )
            FarmCard()
        }
        .padding()
        .background(Color.Background.subtle)
    }
}
