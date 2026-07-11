//
//  AppCommentInput.swift
//  ChamChamCham
//
//  Created by iyungui on 7/11/26.
//

import SwiftUI

/// Figma `comment-input`. Focus, text, and attachment state are kept independent so a screen can
/// render every component-set variant while still using the real text-field focus binding.
struct AppCommentInput: View {
    static let inputRowHeight: CGFloat = 48
    static let attachmentSize: CGFloat = 64
    static let attachmentCornerRadius: CGFloat = 4

    static func containerHeight(isFocused: Bool, hasImage: Bool) -> CGFloat {
        let imageHeight = hasImage ? attachmentSize : 0
        return 8 + inputRowHeight + imageHeight + (isFocused ? 12 : 32)
    }

    static func resolvedFilled(override: Bool?, hasText: Bool) -> Bool {
        override ?? hasText
    }

    static func submitBackground(isFilled: Bool) -> Color {
        isFilled ? Color.Object.bold : Color.Object.strong
    }

    @Binding private var text: String
    private let focusBinding: FocusState<Bool>.Binding
    private let attachment: AnyView?

    var placeholder: String = "댓글을 입력해주세요."
    var filled: Bool? = nil
    var isSubmitting: Bool = false
    var isPhotoEnabled: Bool = true
    var onPhotoTap: () -> Void = {}
    var onRemoveAttachment: () -> Void = {}
    var onSubmit: () -> Void = {}

    @Environment(\.isEnabled) private var isEnabled

    init<Attachment: View>(
        text: Binding<String>,
        isFocused: FocusState<Bool>.Binding,
        placeholder: String = "댓글을 입력해주세요.",
        filled: Bool? = nil,
        isSubmitting: Bool = false,
        isPhotoEnabled: Bool = true,
        onPhotoTap: @escaping () -> Void = {},
        onRemoveAttachment: @escaping () -> Void = {},
        onSubmit: @escaping () -> Void = {},
        @ViewBuilder attachment: () -> Attachment
    ) {
        _text = text
        focusBinding = isFocused
        self.placeholder = placeholder
        self.filled = filled
        self.isSubmitting = isSubmitting
        self.isPhotoEnabled = isPhotoEnabled
        self.onPhotoTap = onPhotoTap
        self.onRemoveAttachment = onRemoveAttachment
        self.onSubmit = onSubmit
        self.attachment = AnyView(attachment())
    }

    init(
        text: Binding<String>,
        isFocused: FocusState<Bool>.Binding,
        placeholder: String = "댓글을 입력해주세요.",
        filled: Bool? = nil,
        isSubmitting: Bool = false,
        isPhotoEnabled: Bool = true,
        onPhotoTap: @escaping () -> Void = {},
        onSubmit: @escaping () -> Void = {}
    ) {
        _text = text
        focusBinding = isFocused
        self.placeholder = placeholder
        self.filled = filled
        self.isSubmitting = isSubmitting
        self.isPhotoEnabled = isPhotoEnabled
        self.onPhotoTap = onPhotoTap
        onRemoveAttachment = {}
        self.onSubmit = onSubmit
        attachment = nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            inputRow

            if let attachment {
                HStack(spacing: 0) {
                    Color.clear.frame(width: Self.inputRowHeight)
                    AppImageUploadSlot(size: .small, onTap: onPhotoTap, onRemove: onRemoveAttachment) {
                        attachment
                    }
                    Spacer(minLength: 0)
                }
                .frame(height: Self.attachmentSize)
            }
        }
        .padding(.top, Spacing.sm)
        .padding(.bottom, focusBinding.wrappedValue ? 12 : 32)
        .padding(.trailing, 12)
        .frame(minHeight: Self.containerHeight(isFocused: focusBinding.wrappedValue, hasImage: attachment != nil), alignment: .top)
        .background(Color.Object.default)
        .overlay(Rectangle().stroke(Color.Border.default, lineWidth: 1))
    }

    private var inputRow: some View {
        HStack(spacing: 0) {
            Button(action: onPhotoTap) {
                Image(systemName: "photo")
                    .font(.system(size: 24, weight: .regular))
                    .foregroundStyle(Color.Icon.default)
                    .frame(width: Self.inputRowHeight, height: Self.inputRowHeight)
            }
            .buttonStyle(.plain)
            .disabled(!isPhotoEnabled)

            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder)
                        .foregroundStyle(Color.Text.muted)
                }

                TextField("", text: $text)
                    .foregroundStyle(Color.Text.default)
                    .focused(focusBinding)
                    .lineLimit(1)
            }
            .appTypography(.bodyLarge)
            .frame(maxWidth: .infinity, minHeight: Self.inputRowHeight, alignment: .leading)

            Button(action: onSubmit) {
                if isSubmitting {
                    ProgressView()
                        .tint(Color.Icon.inverse)
                        .frame(width: Self.inputRowHeight, height: Self.inputRowHeight)
                        .background(Circle().fill(Self.submitBackground(isFilled: true)))
                } else {
                    Image(systemName: "arrow.right")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundStyle(isFilled ? Color.Icon.inverse : Color.Icon.disabled)
                        .frame(width: Self.inputRowHeight, height: Self.inputRowHeight)
                        .background(Circle().fill(Self.submitBackground(isFilled: isFilled)))
                }
            }
            .buttonStyle(.plain)
            .disabled(!isFilled || isSubmitting)
        }
        .frame(height: Self.inputRowHeight)
    }

    private var isFilled: Bool {
        Self.resolvedFilled(
            override: filled,
            hasText: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        )
    }
}

#Preview {
    struct Demo: View {
        @State private var empty = ""
        @State private var filled = "댓글 내용"
        @FocusState private var emptyFocused: Bool
        @FocusState private var filledFocused: Bool

        var body: some View {
            VStack(spacing: Spacing.lg) {
                AppCommentInput(text: $empty, isFocused: $emptyFocused)
                AppCommentInput(text: $filled, isFocused: $filledFocused) {
                    AppImagePlaceholder(cornerRadius: 4)
                }
            }
            .frame(width: 390)
        }
    }

    return Demo()
}
