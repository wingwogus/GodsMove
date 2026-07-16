//
//  AppChatBubble.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Figma `chat`. Used in the voice-driven 영농일지 recording flow: the `false` variant prompts
/// the farmer, and `true` echoes back their mic-transcribed speech as their own message.
struct AppChatBubble: View {
    let message: String
    var isMine: Bool = false

    var body: some View {
        HStack(spacing: 0) {
            if isMine { Spacer(minLength: Spacing.xl) }

            Text(message)
                .appTypography(isMine ? .titleMediumEmphasized : .titleMedium)
                .foregroundStyle(isMine ? Color.Text.inverse : Color.Text.default)
                .multilineTextAlignment(.leading)
                .padding(20)
                .background(isMine ? Color.Object.primary : Color.Object.muted)
                .clipShape(RoundedRectangle(cornerRadius: 20))

            if !isMine { Spacer(minLength: Spacing.xl) }
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: Spacing.md) {
        AppChatBubble(message: "오늘 어떤 작업을 하셨나요?  마이크를 누르고 자유롭게  말씀해주세요!")
        AppChatBubble(message: "오늘 사과밭 전체적으로 소독약 쳤어. 아침 8시부터 12시까지 했고.", isMine: true)
    }
    .padding()
    .background(Color.Background.default)
}
