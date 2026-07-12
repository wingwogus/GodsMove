//
//  MyPageView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/12/26.
//

import SwiftUI

struct MyPageView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: MyPageViewModel

    init(authRepository: any AuthRepository) {
        _viewModel = State(initialValue: MyPageViewModel(authRepository: authRepository))
    }

    var body: some View {
        VStack(spacing: 16) {
            Text("마이페이지")
                .font(.title2)

            Button("로그아웃") {
                Task { await viewModel.logout(appState: appState) }
            }
            .disabled(viewModel.isSubmitting)

            Button("회원탈퇴") {
                viewModel.withdraw()
            }
            .disabled(viewModel.isSubmitting)

            if let message = viewModel.message {
                Text(message)
                    .font(.caption)
            }
        }
        .padding()
    }
}

#if DEBUG
#Preview {
    MyPageView(authRepository: PreviewAuthRepository())
        .environment(AppState())
}
#endif
