//
//  RecordVoiceFlowView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// 음성 기록 플로우 컨테이너. fullScreenCover 안에서 대화 화면 → (핸드오프 시) 검토
/// 화면(RecordComposeView 재사용, confirm 저장)을 하나의 NavigationStack으로 묶는다.
/// BR-RECORD-001의 모드 전환 금지는 여기서 구조로 지켜진다 — 텍스트 작성 폼을 재사용해도
/// 저장 경로는 voice confirm 하나뿐이고, 텍스트 플로우로 갈아탈 진입점이 없다.
struct RecordVoiceFlowView: View {
    @State private var viewModel: RecordVoiceComposeViewModel
    @Environment(\.dismiss) private var dismiss
    private let voiceRepository: any VoiceSessionRepository
    private let recordRepository: any RecordRepository
    private let weatherRepository: any WeatherRepository
    private let mediaUpload: any MediaUploadRepository
    private let onSessionInvalid: () -> Void
    private let onComplete: (UUID) -> Void

    init(
        voiceRepository: any VoiceSessionRepository,
        recordRepository: any RecordRepository,
        weatherRepository: any WeatherRepository,
        mediaUpload: any MediaUploadRepository,
        onSessionInvalid: @escaping () -> Void,
        onComplete: @escaping (UUID) -> Void
    ) {
        self.voiceRepository = voiceRepository
        self.recordRepository = recordRepository
        self.weatherRepository = weatherRepository
        self.mediaUpload = mediaUpload
        self.onSessionInvalid = onSessionInvalid
        self.onComplete = onComplete
        _viewModel = State(initialValue: RecordVoiceComposeViewModel(
            voiceRepository: voiceRepository,
            recordRepository: recordRepository,
            transport: WebRTCVoiceTransport()
        ))
    }

    var body: some View {
        @Bindable var vm = viewModel
        NavigationStack {
            RecordVoiceComposeView(viewModel: viewModel)
                .navigationDestination(item: $vm.reviewHandoff) { handoff in
                    RecordComposeView(
                        repository: recordRepository,
                        weatherRepository: weatherRepository,
                        mediaUpload: mediaUpload,
                        saver: VoiceConfirmRecordSaver(
                            repository: voiceRepository, sessionId: handoff.sessionId
                        ),
                        prefill: handoff.prefill,
                        onSessionInvalid: {
                            onSessionInvalid()
                            dismiss()
                        }
                    ) { recordId in
                        onComplete(recordId)
                        dismiss()
                    }
                }
        }
        // 대화 단계에서 VOICE_002(이미 처리된 세션)를 만난 경우 — 재시도 없이 플로우 종료.
        .onChange(of: viewModel.sessionInvalidated) { _, invalidated in
            if invalidated {
                onSessionInvalid()
                dismiss()
            }
        }
    }
}
