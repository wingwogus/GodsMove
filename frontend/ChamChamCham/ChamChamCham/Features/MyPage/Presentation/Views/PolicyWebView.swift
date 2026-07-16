//
//  PolicyWebView.swift
//  ChamChamCham
//
//  Created by iyungui on 7/16/26.
//

import SwiftUI

/// Route pushed from `SettingsView`'s policy link rows onto its `NavigationStack`.
struct PolicyLink: Hashable {
    let title: String
    let url: URL

    static let privacyPolicy = PolicyLink(
        title: "개인정보처리방침",
        url: URL(string: "https://wingwogus.notion.site/3959e2d9440580e390e6d40d1159a63a?source=copy_link")!
    )
    static let termsOfService = PolicyLink(
        title: "서비스 이용약관",
        url: URL(string: "https://splendid-wilderness-836.notion.site/39f9e2d9440580e88721d221827b4805?source=copy_link")!
    )
    static let locationTerms = PolicyLink(
        title: "위치기반서비스 이용약관",
        url: URL(string: "https://splendid-wilderness-836.notion.site/39f9e2d944058076a708edf650eb83d8?source=copy_link")!
    )
}

/// Full-screen web view for the 개인정보처리방침 / 서비스 이용약관 / 위치기반서비스 이용약관 Notion pages.
struct PolicyWebView: View {
    let link: PolicyLink
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true

    var body: some View {
        VStack(spacing: 0) {
            AppTopAppBar(
                title: link.title,
                isDetail: true,
                leading: .init(.asset("chevron_backward")) { dismiss() }
            )

            ZStack {
                AppWebView(url: link.url, isLoading: $isLoading)
                if isLoading {
                    LoadingView()
                }
            }
        }
        .background(Color.Background.default)
    }
}
