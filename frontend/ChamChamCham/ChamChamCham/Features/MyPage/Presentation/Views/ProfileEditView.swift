//
//  ProfileEditView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/13/26.
//

import SwiftUI

/// 프로필 수정 컨테이너: 기본 정보 / 농업 정보 탭. Presented from the profile main avatar edit affordance.
struct ProfileEditView: View {
    let container: DIContainer
    /// Bubbles up basic-info save success so `ProfileMainView` can show a toast after the cover dismisses.
    var onBasicInfoSaved: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTab = 0

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: "프로필 수정",
                isDetail: true,
                leading: .init(.asset("arrow_back_ios_new")) { dismiss() }
            )

            AppTabBar(titles: ["기본 정보", "농업 정보"], selection: $selectedTab)

            if selectedTab == 0 {
                ProfileBasicInfoView(
                    repository: container.makeMemberProfileRepository(),
                    mediaRepository: container.makeMediaUploadRepository(),
                    farmRepository: container.makeFarmRepository()
                ) {
                    onBasicInfoSaved()
                    dismiss()
                }
            } else {
                FarmListView(container: container)
            }
        }
        .background(Color.Background.default)
    }
}
