//
//  RecordVoiceComposeView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// 음성으로 영농일지 기록하기 화면 (Figma `음성 기록하기`, node `1257:25950`).
///
/// 캡처는 idle 상태(인사 말풍선 + idle 마이크 + disabled 완료)만 정의한다. 나머지 상태는
/// 기존 토큰만으로 최소로 표현한 디자인 갭이다: preparing/processing은 마이크 원 안의
/// ProgressView, 대화 중(수음 켜짐)은 마이크 배경 `Object.primary`, 실패는 안내 말풍선 +
/// "다시 시도" 버튼. 마이크 탭은 대화 종료가 아니라 mute 토글이고, 종료는 완료 버튼 또는
/// AI의 초안(tool 호출) 완료가 담당한다.
struct RecordVoiceComposeView: View {
    @Environment(\.dismiss) private var dismiss
    private let viewModel: RecordVoiceComposeViewModel

    init(viewModel: RecordVoiceComposeViewModel) {
        self.viewModel = viewModel
    }

    private var vm: RecordVoiceComposeViewModel { viewModel }

    /// Figma 캡처의 프롬프트 문구. 모델 응답이 아니라 클라이언트 로컬 렌더.
    private static let greeting = "오늘 어떤 작업을 하셨나요?  마이크를 누르고 자유롭게  말씀해주세요!"

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "기록하기",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) {
                    vm.abandon()
                    dismiss()
                }
            )

            conversation

            micButton
                .padding(.bottom, 32)

            bottomButton
        }
        .navigationBarHidden(true)
    }

    // MARK: - 대화 말풍선

    private var conversation: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: Spacing.lg) {
                    AppChatBubble(message: Self.greeting)

                    ForEach(visibleTranscript) { item in
                        AppChatBubble(message: item.text, isMine: item.role == .user)
                    }

                    if vm.phase == .processing {
                        AppChatBubble(message: "말씀해주신 내용으로 기록을 정리하고 있어요…")
                    }
                    if case let .failed(message) = vm.phase {
                        AppChatBubble(message: message)
                    }

                    Color.clear.frame(height: 1).id(Self.bottomAnchor)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .onChange(of: vm.transcript) {
                withAnimation(.easeOut(duration: 0.15)) {
                    proxy.scrollTo(Self.bottomAnchor, anchor: .bottom)
                }
            }
        }
    }

    private var visibleTranscript: [VoiceTranscriptItem] {
        vm.transcript.filter { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private static let bottomAnchor = "conversation-bottom"

    // MARK: - 마이크 버튼 (96pt 원형)

    @ViewBuilder private var micButton: some View {
        switch vm.phase {
        case .idle, .preparing, .conversing, .processing, .reviewing:
            Button {
                vm.micTapped()
            } label: {
                micCircle
            }
            .buttonStyle(.plain)
            .accessibilityLabel(micAccessibilityLabel)
        case .failed, .cancelled:
            EmptyView()
        }
    }

    private var micCircle: some View {
        ZStack {
            if isMicBusy {
                ProgressView()
                    .tint(Color.Icon.inverse)
            } else {
                AppIconView(source: .asset("mic"), size: 40)
                    .foregroundStyle(Color.Icon.inverse)
            }
        }
        .frame(width: 96, height: 96)
        .background(micBackground)
        .clipShape(Circle())
    }

    private var isMicBusy: Bool {
        switch vm.phase {
        case .preparing, .processing, .reviewing: true
        default: false
        }
    }

    /// 수음 중(unmuted)일 때만 primary — 캡처의 idle(`Object.bold`)에서 가장 작은 이탈.
    private var micBackground: Color {
        if case .conversing(muted: false) = vm.phase { Color.Object.primary } else { Color.Object.bold }
    }

    private var micAccessibilityLabel: String {
        switch vm.phase {
        case .idle: "음성 기록 시작"
        case .conversing(muted: false): "마이크 끄기"
        case .conversing(muted: true): "마이크 켜기"
        default: "처리 중"
        }
    }

    // MARK: - 하단 버튼 (완료 / 실패 시 다시 시도)

    private var bottomButton: some View {
        VStack(spacing: 0) {
            if case .failed = vm.phase {
                AppButton("다시 시도", variant: .secondary, size: .large, fullWidth: true) {
                    vm.retryTapped()
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            } else {
                AppButton("완료", variant: .secondary, size: .large, fullWidth: true,
                          appearsDisabled: !vm.canFinish) {
                    vm.finishTapped()
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
        .background(Color.Background.default)
        .overlay(alignment: .top) { Rectangle().fill(Color.Border.subtle).frame(height: 1) }
    }
}
