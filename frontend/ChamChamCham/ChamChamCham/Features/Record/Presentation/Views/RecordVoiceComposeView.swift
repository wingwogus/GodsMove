//
//  RecordVoiceComposeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// 음성으로 영농일지 기록하기 화면 (Figma `음성 기록하기`, node `1257:25950`).
///
/// 현재는 캡처 스펙 그대로 옮긴 **정적 목업**이다. 마이크 버튼 상태 머신(idle→recording→
/// processing), STT 연동, 완료 활성화 조건, AI 구조화는 아직 없다 — 뷰모델/리포지토리 배선은
/// 후속 계획 단계에서 붙인다. 레이아웃은 그때 바로 데이터에 연결할 수 있게 잡아 둔다.
struct RecordVoiceComposeView: View {
    @Environment(\.dismiss) private var dismiss

    /// 목업용 예시 대화. 실제 구현에서는 프롬프트 + 전사 결과 말풍선 배열로 대체된다.
    private let messages: [(text: String, isMine: Bool)] = [
        ("오늘 어떤 작업을 하셨나요?  마이크를 누르고 자유롭게  말씀해주세요!", false),
        ("오늘 사과밭 전체적으로 소독약 쳤어. 아침 8시부터 12시까지 했고.", true),
    ]

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "기록하기",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )

            ScrollView {
                VStack(spacing: Spacing.lg) {
                    ForEach(Array(messages.enumerated()), id: \.offset) { _, item in
                        AppChatBubble(message: item.text, isMine: item.isMine)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }

            micButton
                .padding(.bottom, 32)

            bottomButton
        }
        .navigationBarHidden(true)
    }

    // MARK: - 마이크 버튼 (96pt 원형, idle)

    private var micButton: some View {
        Button {
            // TODO: 녹음 시작/정지 — 상태 머신은 계획 단계에서 연결
        } label: {
            AppIconView(source: .asset("mic"), size: 40)
                .foregroundStyle(Color.Icon.inverse)
                .frame(width: 96, height: 96)
                .background(Color.Object.bold)
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
    }

    // MARK: - 하단 완료 (전사 결과 없으면 비활성)

    private var bottomButton: some View {
        VStack(spacing: 0) {
            AppButton("완료", variant: .secondary, size: .large, fullWidth: true,
                      appearsDisabled: true) {
                // TODO: 저장 — 계획 단계에서 연결
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Color.Background.default)
        .overlay(alignment: .top) { Rectangle().fill(Color.Border.subtle).frame(height: 1) }
    }
}

#Preview {
    RecordVoiceComposeView()
}
