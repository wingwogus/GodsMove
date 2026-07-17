//
//  LoginRequiredAlert.swift
//  ChamChamCham
//
//  Created by iyungui on 7/17/26.
//

import SwiftUI

extension View {
    /// Shared prompt for a guest hitting an action/screen that needs an account (like, comment, write,
    /// another member's profile, another tab). "로그인하기" drops guest mode, which routes `RootView`
    /// back to `LandingView`.
    func loginRequiredAlert(isPresented: Binding<Bool>, appState: AppState) -> some View {
        alert("로그인이 필요해요", isPresented: isPresented) {
            Button("로그인하기") { appState.isGuest = false }
            Button("취소", role: .cancel) {}
        } message: {
            Text("이 기능은 로그인 후 이용할 수 있어요.")
        }
    }
}
